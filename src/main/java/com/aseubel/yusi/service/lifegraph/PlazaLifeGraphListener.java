package com.aseubel.yusi.service.lifegraph;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.common.constant.SourceType;
import com.aseubel.yusi.common.event.PlazaCardChangedEvent;
import com.aseubel.yusi.pojo.constant.TaskExecutionKeys;
import com.aseubel.yusi.pojo.constant.TaskExecutionSourceType;
import com.aseubel.yusi.pojo.constant.TaskExecutionType;
import com.aseubel.yusi.pojo.constant.TaskFailureCategory;
import com.aseubel.yusi.pojo.entity.SoulCard;
import com.aseubel.yusi.repository.SoulCardRepository;
import com.aseubel.yusi.service.ai.runtime.AgentRunTraceService;
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
    private final AgentRunTraceService agentRunTraceService;

    @Async("threadPoolExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCardChanged(PlazaCardChangedEvent event) {
        if (event == null || event.getCommand() == null) {
            return;
        }
        String userId = event.getCommand().getUserId();
        String sourceId = event.getCommand().getSourceId();
        String requestedRunId = StrUtil.blankToDefault(event.getCommand().getRunId(), IdUtil.fastSimpleUUID());
        var execution = taskExecutionService.createOrGet(TaskExecutionCommand.builder()
                .taskType(TaskExecutionType.LIFE_GRAPH)
                .ownerUserId(userId)
                .sourceType(TaskExecutionSourceType.PLAZA.code())
                .sourceId(sourceId)
                .sourceVersion(String.valueOf(event.getSourceRevision()))
                .triggerEventId(event.getEventId())
                .runId(requestedRunId)
                .idempotencyKey(TaskExecutionKeys.fromSourceRevision(TaskExecutionType.LIFE_GRAPH,
                        userId, TaskExecutionSourceType.PLAZA.code(), sourceId, event.getSourceRevision()))
                .build());
        if (StrUtil.isBlank(execution.getRunId())) {
            execution = taskExecutionService.ensureRunId(execution.getTaskId(), requestedRunId);
        }
        AgentRunTraceService.RunScope scope = agentRunTraceService.open(
                userId, execution.getRunId(), "life_graph");
        try {
            taskExecutionService.claim(execution.getTaskId(), WORKER_ID, LocalDateTime.now());
            SoulCard currentCard = currentCard(event.getCommand().getSourceId());
            if (currentCard == null && event.getType() != PlazaCardChangedEvent.Type.DELETE
                    && isNumericId(event.getCommand().getSourceId())) {
                lifeGraphBuildService.deleteBySource(
                        event.getCommand().getUserId(), SourceType.PLAZA.code(), event.getCommand().getSourceId());
                taskExecutionService.succeed(execution.getTaskId(), null, LocalDateTime.now());
                scope.complete();
                return;
            }
            if (currentCard != null && isSuperseded(event.getSourceRevision(), currentCard.getSourceRevision())) {
                taskExecutionService.succeed(execution.getTaskId(), null, LocalDateTime.now());
                scope.complete();
                return;
            }
            if (event.getType() == PlazaCardChangedEvent.Type.DELETE) {
                lifeGraphBuildService.deleteBySource(
                        event.getCommand().getUserId(), SourceType.PLAZA.code(), event.getCommand().getSourceId());
            } else {
                lifeGraphBuildService.upsertFromPlaza(event.getCommand());
            }
            taskExecutionService.succeed(execution.getTaskId(), null, LocalDateTime.now());
            scope.complete();
        } catch (Exception exception) {
            taskExecutionService.fail(execution.getTaskId(), TaskFailureCategory.DEPENDENCY,
                    null, LocalDateTime.now());
            scope.fail(TaskFailureCategory.DEPENDENCY.name().toLowerCase());
            log.warn("Plaza LifeGraph source processing failed: operation=plaza_life_graph, sourceId={}, exceptionType={}",
                    event.getCommand().getSourceId(), com.aseubel.yusi.common.utils.LowSensitivityLogSummary.exceptionType(exception));
        } finally {
            scope.close();
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
