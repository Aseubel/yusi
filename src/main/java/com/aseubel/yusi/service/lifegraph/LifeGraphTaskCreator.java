package com.aseubel.yusi.service.lifegraph;

import com.aseubel.yusi.common.event.DiaryChangedEvent;
import com.aseubel.yusi.pojo.constant.TaskExecutionKeys;
import com.aseubel.yusi.pojo.constant.TaskExecutionSourceType;
import com.aseubel.yusi.pojo.constant.TaskExecutionType;
import com.aseubel.yusi.pojo.constant.SourceRevision;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.pojo.entity.LifeGraphTask;
import com.aseubel.yusi.repository.LifeGraphTaskRepository;
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

        var execution = taskExecutionService.createOrGet(TaskExecutionCommand.builder()
                .taskType(TaskExecutionType.LIFE_GRAPH)
                .ownerUserId(diary.getUserId())
                .sourceType(TaskExecutionSourceType.DIARY.code())
                .sourceId(diary.getDiaryId())
                .sourceVersion(String.valueOf(sourceRevision))
                .triggerEventId(triggerEventId)
                .idempotencyKey(TaskExecutionKeys.fromSourceRevision(TaskExecutionType.LIFE_GRAPH,
                        diary.getUserId(), TaskExecutionSourceType.DIARY.code(), diary.getDiaryId(), sourceRevision))
                .build());
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

    private void createDeleteTask(Diary diary, String triggerEventId) {
        long sourceRevision = SourceRevision.initialOrCurrent(diary.getSourceRevision());
        var execution = taskExecutionService.createOrGet(TaskExecutionCommand.builder()
                .taskType(TaskExecutionType.LIFE_GRAPH)
                .ownerUserId(diary.getUserId())
                .sourceType(TaskExecutionSourceType.DIARY.code())
                .sourceId(diary.getDiaryId())
                .sourceVersion(String.valueOf(sourceRevision))
                .triggerEventId(triggerEventId)
                .idempotencyKey(TaskExecutionKeys.fromSourceRevision(TaskExecutionType.LIFE_GRAPH,
                        diary.getUserId(), TaskExecutionSourceType.DIARY.code(), diary.getDiaryId(), sourceRevision))
                .build());
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
