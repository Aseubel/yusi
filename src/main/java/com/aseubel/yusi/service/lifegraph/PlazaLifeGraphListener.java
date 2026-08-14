package com.aseubel.yusi.service.lifegraph;

import com.aseubel.yusi.common.constant.SourceType;
import com.aseubel.yusi.common.event.PlazaCardChangedEvent;
import com.aseubel.yusi.pojo.constant.TaskExecutionKeys;
import com.aseubel.yusi.pojo.constant.TaskExecutionSourceType;
import com.aseubel.yusi.pojo.constant.TaskExecutionType;
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
                .sourceVersion(event.getEventId())
                .triggerEventId(event.getEventId())
                .idempotencyKey(TaskExecutionKeys.fromEvent(TaskExecutionType.LIFE_GRAPH,
                        TaskExecutionSourceType.PLAZA.code(), sourceId, event.getEventId()))
                .build());
        taskExecutionService.claim(execution.getTaskId(), WORKER_ID, LocalDateTime.now());
        try {
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
}
