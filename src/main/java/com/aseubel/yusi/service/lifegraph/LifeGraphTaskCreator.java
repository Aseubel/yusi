package com.aseubel.yusi.service.lifegraph;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.common.constant.SourceType;
import com.aseubel.yusi.common.event.DiaryChangedEvent;
import com.aseubel.yusi.pojo.dto.cognition.CognitionIngestCommand;
import com.aseubel.yusi.pojo.constant.TaskExecutionKeys;
import com.aseubel.yusi.pojo.constant.TaskExecutionSourceType;
import com.aseubel.yusi.pojo.constant.TaskExecutionType;
import com.aseubel.yusi.pojo.constant.SourceRevision;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.pojo.entity.LifeGraphTask;
import com.aseubel.yusi.repository.LifeGraphTaskRepository;
import com.aseubel.yusi.service.ai.runtime.AgentRunTraceService;
import com.aseubel.yusi.service.task.TaskExecutionCommand;
import com.aseubel.yusi.service.task.TaskExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LifeGraphTaskCreator {

    private final LifeGraphTaskRepository taskRepository;
    private final LifeGraphTaskBatchService batchService;
    private final ThreadPoolTaskExecutor threadPoolExecutor;
    private final TaskExecutionService taskExecutionService;
    private final AgentRunTraceService agentRunTraceService;

    @Async("threadPoolExecutor")
    @EventListener
    @Transactional
    public void onDiaryChanged(DiaryChangedEvent event) {
        Diary diary = event.getDiary();

        switch (event.getType()) {
            case WRITE:
            case MODIFY:
                createUpsertTask(diary, event.getEventId());
                break;
            case DELETE:
                createDeleteTask(diary, event.getEventId());
                break;
            default:
                break;
        }
    }

    private void createUpsertTask(Diary diary, String triggerEventId) {
        long sourceRevision = SourceRevision.initialOrCurrent(diary.getSourceRevision());
        List<LifeGraphTask> activeTasks = taskRepository.findByUserIdAndDiaryIdAndStatusIn(
                diary.getUserId(), diary.getDiaryId(),
                List.of(LifeGraphTask.TaskStatus.PENDING, LifeGraphTask.TaskStatus.PROCESSING));
        if (activeTasks.stream().anyMatch(task -> sameRevision(task.getSourceRevision(), sourceRevision)
                && (task.getStatus() == LifeGraphTask.TaskStatus.PENDING
                || task.getStatus() == LifeGraphTask.TaskStatus.PROCESSING))) {
            return;
        }
        boolean processing = activeTasks.stream()
                .anyMatch(task -> task.getStatus() == LifeGraphTask.TaskStatus.PROCESSING);

        String plainContent = diary.getPlainContent();
        boolean canProcessImmediately = !processing && plainContent != null && !plainContent.isBlank();

        String requestedRunId = IdUtil.fastSimpleUUID();
        var execution = taskExecutionService.createOrGet(TaskExecutionCommand.builder()
                .taskType(TaskExecutionType.LIFE_GRAPH)
                .ownerUserId(diary.getUserId())
                .sourceType(TaskExecutionSourceType.DIARY.code())
                .sourceId(diary.getDiaryId())
                .sourceVersion(String.valueOf(sourceRevision))
                .triggerEventId(triggerEventId)
                .runId(requestedRunId)
                .idempotencyKey(TaskExecutionKeys.fromSourceRevision(TaskExecutionType.LIFE_GRAPH,
                        diary.getUserId(), TaskExecutionSourceType.DIARY.code(), diary.getDiaryId(), sourceRevision))
                .build());
        if (execution.getRunId() == null || execution.getRunId().isBlank()) {
            execution = taskExecutionService.ensureRunId(execution.getTaskId(), requestedRunId);
        }
        agentRunTraceService.start(diary.getUserId(), execution.getRunId(), "life_graph");
        if (taskRepository.findByTaskExecutionId(execution.getTaskId()).isPresent()) {
            return;
        }

        LifeGraphTask task = LifeGraphTask.createUpsertTask(diary.getDiaryId(), diary.getUserId(),
                sourceRevision, triggerEventId);
        task.setTaskExecutionId(execution.getTaskId());
        if (canProcessImmediately) {
            task.setStatus(LifeGraphTask.TaskStatus.PROCESSING);
        }

        LifeGraphTask saved = taskRepository.save(task);

        if (canProcessImmediately) {
            threadPoolExecutor.execute(() -> {
                try {
                    batchService.processSingleTask(saved.getId(), diary, plainContent);
                } catch (Exception e) {
                    LocalDateTime now = LocalDateTime.now();
                    taskRepository.incrementRetryAndSetNextAttempt(saved.getId(), e.getMessage(),
                            batchService.calculateNextRetry(saved.getRetryCount() + 1), now);
                }
            });
        }
    }

    /**
     * 认知管道分叉派发：统一编排入口（由 LifeGraphCognitionBridgeService 在认知管道末尾调用）。
     *
     * <p>与日记事件路径（onDiaryChanged）共用同一幂等键
     * （LIFE_GRAPH + 用户 + DIARY + diaryId + sourceRevision），
     * 两条路径谁先到谁建任务，后到方经 createOrGet + findByTaskExecutionId 幂等收敛，不会重复抽取。
     *
     * <p>与事件路径的两点差异：
     * 1. 此处没有 Diary 实体与 plainContent，不做即时处理快速路径，
     *    任务以 PENDING 落库，由调度管道（processLifeGraphTasks）统一领取；
     * 2. 不调用 agentRunTraceService.start——复用认知 runId 时运行轨迹已由认知管道打开，
     *    事件路径先到时则已由其自行启动。
     */
    @Transactional
    public void dispatchFromCognition(CognitionIngestCommand command) {
        // 图谱抽取器当前只消费日记形态的输入；其余来源不做图谱派发（登记为演进项）
        if (command == null || StrUtil.isBlank(command.getUserId()) || StrUtil.isBlank(command.getSourceId())
                || !SourceType.DIARY.code().equalsIgnoreCase(command.getSourceType())) {
            return;
        }
        long sourceRevision = SourceRevision.initialOrCurrent(command.getSourceRevision());
        // runId 优先复用认知运行的 runId，让 Trace 贯穿认知与图谱两段；
        // 若事件路径已先建任务，createOrGet 幂等返回既有执行，沿用其 runId
        String requestedRunId = StrUtil.blankToDefault(command.getRunId(), IdUtil.fastSimpleUUID());
        var execution = taskExecutionService.createOrGet(TaskExecutionCommand.builder()
                .taskType(TaskExecutionType.LIFE_GRAPH)
                .ownerUserId(command.getUserId())
                .sourceType(TaskExecutionSourceType.DIARY.code())
                .sourceId(command.getSourceId())
                .sourceVersion(String.valueOf(sourceRevision))
                .runId(requestedRunId)
                .idempotencyKey(TaskExecutionKeys.fromSourceRevision(TaskExecutionType.LIFE_GRAPH,
                        command.getUserId(), TaskExecutionSourceType.DIARY.code(), command.getSourceId(),
                        sourceRevision))
                .build());
        if (taskRepository.findByTaskExecutionId(execution.getTaskId()).isPresent()) {
            // 任务已存在（事件路径先到，或本路径重放），幂等收敛
            return;
        }
        LifeGraphTask task = LifeGraphTask.createUpsertTask(command.getSourceId(), command.getUserId(),
                sourceRevision, null);
        task.setTaskExecutionId(execution.getTaskId());
        taskRepository.save(task);
    }

    private void createDeleteTask(Diary diary, String triggerEventId) {
        long sourceRevision = SourceRevision.initialOrCurrent(diary.getSourceRevision());
        String requestedRunId = IdUtil.fastSimpleUUID();
        var execution = taskExecutionService.createOrGet(TaskExecutionCommand.builder()
                .taskType(TaskExecutionType.LIFE_GRAPH)
                .ownerUserId(diary.getUserId())
                .sourceType(TaskExecutionSourceType.DIARY.code())
                .sourceId(diary.getDiaryId())
                .sourceVersion(String.valueOf(sourceRevision))
                .triggerEventId(triggerEventId)
                .runId(requestedRunId)
                .idempotencyKey(TaskExecutionKeys.fromSourceRevision(TaskExecutionType.LIFE_GRAPH,
                        diary.getUserId(), TaskExecutionSourceType.DIARY.code(), diary.getDiaryId(), sourceRevision))
                .build());
        if (execution.getRunId() == null || execution.getRunId().isBlank()) {
            execution = taskExecutionService.ensureRunId(execution.getTaskId(), requestedRunId);
        }
        agentRunTraceService.start(diary.getUserId(), execution.getRunId(), "life_graph");
        if (taskRepository.findByTaskExecutionId(execution.getTaskId()).isPresent()) {
            return;
        }

        LifeGraphTask task = LifeGraphTask.createDeleteTask(diary.getDiaryId(), diary.getUserId(),
                sourceRevision, triggerEventId);
        task.setTaskExecutionId(execution.getTaskId());
        taskRepository.save(task);
    }

    private boolean sameRevision(Long taskRevision, long sourceRevision) {
        return taskRevision != null && taskRevision == sourceRevision;
    }
}
