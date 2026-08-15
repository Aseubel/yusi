package com.aseubel.yusi.service.cognition.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.common.constant.SourceType;
import com.aseubel.yusi.common.event.ChatCognitionIngestEvent;
import com.aseubel.yusi.common.event.DiaryCognitionIngestEvent;
import com.aseubel.yusi.common.event.EmotionPlazaCognitionIngestEvent;
import com.aseubel.yusi.pojo.dto.cognition.CognitionIngestCommand;
import com.aseubel.yusi.pojo.dto.cognition.CognitionRoutingResult;
import com.aseubel.yusi.pojo.constant.SourceRevision;
import com.aseubel.yusi.pojo.constant.TaskExecutionKeys;
import com.aseubel.yusi.pojo.constant.TaskExecutionStatus;
import com.aseubel.yusi.pojo.constant.TaskExecutionType;
import com.aseubel.yusi.pojo.constant.TaskFailureCategory;
import com.aseubel.yusi.pojo.entity.TaskExecution;
import com.aseubel.yusi.service.ai.runtime.AgentRunTraceService;
import com.aseubel.yusi.service.cognition.AgentCognitionOrchestrator;
import com.aseubel.yusi.service.cognition.CognitionRoutingService;
import com.aseubel.yusi.service.cognition.ImageUnderstandingService;
import com.aseubel.yusi.service.lifegraph.LifeGraphCognitionBridgeService;
import com.aseubel.yusi.service.match.MatchProfileAssembler;
import com.aseubel.yusi.service.memory.MidMemoryUpdateService;
import com.aseubel.yusi.service.persona.UserPersonaUpdateService;
import com.aseubel.yusi.service.task.TaskExecutionCommand;
import com.aseubel.yusi.service.task.TaskExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentCognitionOrchestratorImpl implements AgentCognitionOrchestrator {

    private static final String WORKER_ID = "cognition-ingest";
    private static final String RUN_SCENE = "cognition_ingest";

    private final CognitionRoutingService cognitionRoutingService;
    private final UserPersonaUpdateService userPersonaUpdateService;
    private final MidMemoryUpdateService midMemoryUpdateService;
    private final LifeGraphCognitionBridgeService lifeGraphCognitionBridgeService;
    private final MatchProfileAssembler matchProfileAssembler;
    private final ImageUnderstandingService imageUnderstandingService;
    private final TaskExecutionService taskExecutionService;
    private final AgentRunTraceService agentRunTraceService;

    @Override
    public void ingest(CognitionIngestCommand command) {
        if (command == null || StrUtil.isBlank(command.getUserId())) {
            return;
        }
        TaskExecution execution = createExecution(command);
        if (execution == null || taskExecutionService.isTerminal(execution.getStatus())) {
            return;
        }

        String runId = StrUtil.blankToDefault(execution.getRunId(), command.getRunId());
        if (StrUtil.isBlank(runId)) {
            runId = IdUtil.fastSimpleUUID();
        }

        taskExecutionService.claim(execution.getTaskId(), WORKER_ID, java.time.LocalDateTime.now());
        AgentRunTraceService.RunScope scope = agentRunTraceService.open(
                command.getUserId(), runId, RUN_SCENE);
        try (scope) {
            ingestBusinessData(command);
            taskExecutionService.succeed(execution.getTaskId(), null, java.time.LocalDateTime.now());
            scope.complete();
        } catch (Exception exception) {
            TaskExecution retried = taskExecutionService.retry(
                    execution.getTaskId(), TaskFailureCategory.DEPENDENCY, null,
                    java.time.LocalDateTime.now());
            if (retried != null && retried.getStatus() == TaskExecutionStatus.FAILED) {
                scope.fail(TaskFailureCategory.DEPENDENCY.name().toLowerCase());
            } else {
                scope.retryWait();
            }
            log.warn("认知摄取任务失败: userId={}, sourceType={}, sourceId={}",
                    command.getUserId(), command.getSourceType(), command.getSourceId(), exception);
        }
    }

    private TaskExecution createExecution(CognitionIngestCommand command) {
        if (StrUtil.isBlank(command.getSourceType()) || StrUtil.isBlank(command.getSourceId())) {
            return null;
        }
        String runId = StrUtil.blankToDefault(command.getRunId(), IdUtil.fastSimpleUUID());
        long sourceRevision = SourceRevision.initialOrCurrent(command.getSourceRevision());
        TaskExecutionCommand taskCommand = TaskExecutionCommand.builder()
                .taskType(TaskExecutionType.COGNITION_INGEST)
                .ownerUserId(command.getUserId())
                .sourceType(command.getSourceType())
                .sourceId(command.getSourceId())
                .sourceVersion(String.valueOf(sourceRevision))
                .runId(runId)
                .idempotencyKey(TaskExecutionKeys.fromSourceRevision(
                        TaskExecutionType.COGNITION_INGEST,
                        command.getUserId(),
                        command.getSourceType(),
                        command.getSourceId(),
                        sourceRevision))
                .build();
        return taskExecutionService.createOrGet(taskCommand);
    }

    private void ingestBusinessData(CognitionIngestCommand command) {
        if (SourceType.DIARY.code().equalsIgnoreCase(command.getSourceType())) {
            midMemoryUpdateService.removeBySource(command.getUserId(), command.getSourceType(), command.getSourceId());
        }
        if (StrUtil.isBlank(command.getMaskedText())) {
            return;
        }
        if (command.getImageObjectKeys() != null && !command.getImageObjectKeys().isEmpty()) {
            String imageDescription = imageUnderstandingService.describe(command.getUserId(), command.getImageObjectKeys());
            if (StrUtil.isNotBlank(imageDescription)) {
                command.setMaskedText(command.getMaskedText() + "\n图片理解：" + imageDescription);
            }
        }
        CognitionRoutingResult routingResult = cognitionRoutingService.route(command);
        userPersonaUpdateService.mergeFromRouting(
                command.getUserId(), routingResult, command.getSourceType(), command.getSourceId());
        if (routingResult != null
                && StrUtil.isNotBlank(routingResult.getMidMemorySummary())
                && !SourceType.CHAT_SUMMARY.code().equalsIgnoreCase(command.getSourceType())) {
            midMemoryUpdateService.appendSnapshot(
                    command.getUserId(),
                    routingResult.getMidMemorySummary(),
                    routingResult.getMidMemoryImportance(),
                    routingResult.getMidMemoryCategory(),
                    command.getSourceType(),
                    command.getSourceId());
        }
        lifeGraphCognitionBridgeService.bridge(command, routingResult);
        matchProfileAssembler.refreshProfile(command.getUserId());
    }

    @Async("threadPoolExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDiaryCognitionIngest(DiaryCognitionIngestEvent event) {
        log.debug("收到日记认知摄取事件: userId={}, sourceId={}",
                event.getCommand().getUserId(), event.getCommand().getSourceId());
        ingest(event.getCommand());
    }

    @Async("threadPoolExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onChatCognitionIngest(ChatCognitionIngestEvent event) {
        log.debug("收到聊天认知摄取事件: userId={}, sourceId={}",
                event.getCommand().getUserId(), event.getCommand().getSourceId());
        ingest(event.getCommand());
    }

    @Async("threadPoolExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEmotionPlazaCognitionIngest(EmotionPlazaCognitionIngestEvent event) {
        log.debug("收到广场认知摄取事件: userId={}, sourceId={}",
                event.getCommand().getUserId(), event.getCommand().getSourceId());
        ingest(event.getCommand());
    }
}
