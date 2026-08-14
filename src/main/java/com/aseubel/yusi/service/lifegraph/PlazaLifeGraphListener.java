package com.aseubel.yusi.service.lifegraph;

import com.aseubel.yusi.common.constant.SourceType;
import com.aseubel.yusi.common.event.PlazaCardChangedEvent;
import com.aseubel.yusi.pojo.constant.TaskExecutionKeys;
import com.aseubel.yusi.pojo.constant.TaskExecutionSourceType;
import com.aseubel.yusi.pojo.constant.TaskExecutionType;
import com.aseubel.yusi.pojo.entity.SoulCard;
import com.aseubel.yusi.repository.SoulCardRepository;
import com.aseubel.yusi.service.task.TaskExecutionCommand;
import com.aseubel.yusi.service.task.TaskExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

/**
 * Runs full LifeGraph extraction only for cards authored by the card owner.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlazaLifeGraphListener {

    private static final String WORKER_ID = "plaza-life-graph";

    private final LifeGraphBuildService lifeGraphBuildService;
    private final TaskExecutionService taskExecutionService;
    private final SoulCardRepository cardRepository;

    @Async("threadPoolExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCardChanged(PlazaCardChangedEvent event) {
        if (event == null || event.getCommand() == null) {
            return;
        }
        String userId = event.getCommand().getUserId();
        String sourceId = event.getCommand().getSourceId();
        var execution = taskExecutionService.createOrGet(TaskExecutionCommand.builder()
                .taskType(TaskExecutionType.LIFE_GRAPH)
                .ownerUserId(userId)
                .sourceType(TaskExecutionSourceType.PLAZA.code())
                .sourceId(sourceId)
                .sourceVersion(String.valueOf(event.getSourceRevision()))
                .triggerEventId(event.getEventId())
                .idempotencyKey(TaskExecutionKeys.fromSourceRevision(TaskExecutionType.LIFE_GRAPH,
                        userId, TaskExecutionSourceType.PLAZA.code(), sourceId, event.getSourceRevision()))
                .build());
        taskExecutionService.claim(execution.getTaskId(), WORKER_ID, LocalDateTime.now());
        try {
            SoulCard currentCard = currentCard(event.getCommand().getSourceId());
            if (currentCard == null && event.getType() != PlazaCardChangedEvent.Type.DELETE
                    && isNumericId(event.getCommand().getSourceId())) {
                lifeGraphBuildService.deleteBySource(
                        event.getCommand().getUserId(), SourceType.PLAZA.code(), event.getCommand().getSourceId());
                taskExecutionService.succeed(execution.getTaskId(), null, LocalDateTime.now());
                return;
            }
            if (currentCard != null && isSuperseded(event.getSourceRevision(), currentCard.getSourceRevision())) {
                taskExecutionService.succeed(execution.getTaskId(), null, LocalDateTime.now());
                return;
            }
            if (event.getType() == PlazaCardChangedEvent.Type.DELETE) {
                lifeGraphBuildService.deleteBySource(
                        event.getCommand().getUserId(), SourceType.PLAZA.code(), event.getCommand().getSourceId());
            } else {
                lifeGraphBuildService.upsertFromPlaza(event.getCommand());
            }
            taskExecutionService.succeed(execution.getTaskId(), null, LocalDateTime.now());
        } catch (Exception exception) {
            taskExecutionService.fail(execution.getTaskId(), null, null, LocalDateTime.now());
            log.warn("Plaza LifeGraph source processing failed: sourceId={}",
                    event.getCommand().getSourceId(), exception);
        }
    }

    private SoulCard currentCard(String sourceId) {
        if (!isNumericId(sourceId)) {
            return null;
        }
        return cardRepository.findById(Long.valueOf(sourceId)).orElse(null);
    }

    private boolean isNumericId(String sourceId) {
        if (sourceId == null || sourceId.isBlank()) {
            return false;
        }
        try {
            Long.parseLong(sourceId);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private boolean isSuperseded(Long eventRevision, Long currentRevision) {
        return currentRevision != null && (eventRevision == null || eventRevision < currentRevision);
    }
}
