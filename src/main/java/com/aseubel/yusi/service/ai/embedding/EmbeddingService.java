package com.aseubel.yusi.service.ai.embedding;

import com.aseubel.yusi.common.event.DiaryChangedEvent;
import com.aseubel.yusi.pojo.constant.TaskExecutionKeys;
import com.aseubel.yusi.pojo.constant.TaskExecutionSourceType;
import com.aseubel.yusi.pojo.constant.TaskExecutionType;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.pojo.entity.EmbeddingTask;
import com.aseubel.yusi.repository.EmbeddingTaskRepository;
import com.aseubel.yusi.service.task.TaskExecutionCommand;
import com.aseubel.yusi.service.task.TaskExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Embedding 任务创建服务
 * 
 * 职责变更（v2.0）：
 * - 原来：直接调用 Embedding API 和 Milvus
 * - 现在：仅创建任务记录，由 EmbeddingBatchService 批量消费
 * 
 * 设计优势：
 * 1. 任务与日记保存在同一事务，100% 不丢失
 * 2. 批量处理提高吞吐量（1000篇日记 → 1次批量API调用）
 * 3. 失败自动重试，支持指数退避
 *
 * @author Aseubel
 * @date 2025/5/7 下午1:34
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingTaskRepository taskRepository;
    private final TaskExecutionService taskExecutionService;

    /**
     * 异步监听日记变更事件，创建相应的 Embedding 任务
     */
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

    /**
     * 创建 UPSERT 任务
     * 如果已有相同日记的待处理任务，则跳过（去重）
     */
    private void createUpsertTask(Diary diary, String triggerEventId) {
        // 去重检查：如果已有相同日记的待处理任务，跳过
        List<EmbeddingTask> pending = taskRepository.findPendingByDiaryId(diary.getDiaryId());
        if (!pending.isEmpty()) {
            log.debug("日记 {} 已有待处理的 Embedding 任务，跳过重复创建", diary.getDiaryId());
            return;
        }

        var execution = taskExecutionService.createOrGet(TaskExecutionCommand.builder()
                .taskType(TaskExecutionType.EMBEDDING)
                .ownerUserId(diary.getUserId())
                .sourceType(TaskExecutionSourceType.DIARY.code())
                .sourceId(diary.getDiaryId())
                .sourceVersion(triggerEventId)
                .triggerEventId(triggerEventId)
                .idempotencyKey(TaskExecutionKeys.fromEvent(TaskExecutionType.EMBEDDING,
                        TaskExecutionSourceType.DIARY.code(), diary.getDiaryId(), triggerEventId))
                .build());
        if (taskRepository.findByTaskExecutionId(execution.getTaskId()).isPresent()) {
            return;
        }

        EmbeddingTask task = EmbeddingTask.createUpsertTask(diary.getDiaryId(), diary.getUserId(), triggerEventId);
        task.setTaskExecutionId(execution.getTaskId());
        taskRepository.save(task);
        log.debug("创建 Embedding UPSERT 任务: diaryId={}, userId={}, triggerEventId={}",
                diary.getDiaryId(), diary.getUserId(), triggerEventId);
    }

    /**
     * 创建 DELETE 任务
     */
    private void createDeleteTask(Diary diary, String triggerEventId) {
        var execution = taskExecutionService.createOrGet(TaskExecutionCommand.builder()
                .taskType(TaskExecutionType.EMBEDDING)
                .ownerUserId(diary.getUserId())
                .sourceType(TaskExecutionSourceType.DIARY.code())
                .sourceId(diary.getDiaryId())
                .sourceVersion(triggerEventId)
                .triggerEventId(triggerEventId)
                .idempotencyKey(TaskExecutionKeys.fromEvent(TaskExecutionType.EMBEDDING,
                        TaskExecutionSourceType.DIARY.code(), diary.getDiaryId(), triggerEventId))
                .build());
        if (taskRepository.findByTaskExecutionId(execution.getTaskId()).isPresent()) {
            return;
        }

        EmbeddingTask task = EmbeddingTask.createDeleteTask(diary.getDiaryId(), diary.getUserId(), triggerEventId);
        task.setTaskExecutionId(execution.getTaskId());
        taskRepository.save(task);
        log.debug("创建 Embedding DELETE 任务: diaryId={}, triggerEventId={}", diary.getDiaryId(), triggerEventId);
    }
}
