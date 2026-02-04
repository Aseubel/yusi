package com.aseubel.yusi.service.ai;

import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.pojo.entity.EmbeddingTask;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.DiaryRepository;
import com.aseubel.yusi.repository.EmbeddingTaskRepository;
import com.aseubel.yusi.repository.UserRepository;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Milvus Embedding 批量处理服务
 * 
 * 设计理念：
 * 1. 任务持久化：保存日记和保存任务在同一个本地事务中提交，100% 不丢失
 * 2. 批量处理：每秒扫描 task_table，批量调用 Embedding API 和 Milvus 写入
 * 3. 失败重试：支持指数退避重试，超过最大次数标记为失败
 * 4. 分布式安全：使用 FOR UPDATE SKIP LOCKED 实现多实例并发消费
 *
 * @author Aseubel
 * @date 2026/2/3
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingBatchService {

    private final EmbeddingTaskRepository taskRepository;
    private final DiaryRepository diaryRepository;
    private final UserRepository userRepository;
    private final MilvusEmbeddingStore milvusEmbeddingStore;
    private final EmbeddingModel embeddingModel;
    private final DocumentSplitter documentSplitter;

    /**
     * 每批处理的最大任务数
     */
    private static final int BATCH_SIZE = 50;

    /**
     * 定时扫描并处理待处理任务
     * 每秒执行一次，将多个日记变更打包成批处理
     */
    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void processPendingTasks() {
        LocalDateTime now = LocalDateTime.now();

        // 1. 获取待处理任务（带悲观锁）
        List<EmbeddingTask> tasks = taskRepository.findPendingTasksForUpdate(now, BATCH_SIZE);
        if (tasks.isEmpty()) {
            return;
        }

        log.info("开始处理 {} 个 Embedding 任务", tasks.size());

        // 2. 批量标记为处理中
        List<Long> taskIds = tasks.stream().map(EmbeddingTask::getId).collect(Collectors.toList());
        taskRepository.markAsProcessing(taskIds, now);

        // 3. 分组处理：UPSERT 和 DELETE 分开处理
        Map<EmbeddingTask.TaskType, List<EmbeddingTask>> grouped = tasks.stream()
                .collect(Collectors.groupingBy(EmbeddingTask::getTaskType));

        // 处理 UPSERT 任务（批量）
        List<EmbeddingTask> upsertTasks = grouped.getOrDefault(EmbeddingTask.TaskType.UPSERT, List.of());
        if (!upsertTasks.isEmpty()) {
            processUpsertBatch(upsertTasks, now);
        }

        // 处理 DELETE 任务
        List<EmbeddingTask> deleteTasks = grouped.getOrDefault(EmbeddingTask.TaskType.DELETE, List.of());
        for (EmbeddingTask task : deleteTasks) {
            processDeleteTask(task, now);
        }
    }

    /**
     * 批量处理 UPSERT 任务
     */
    private void processUpsertBatch(List<EmbeddingTask> tasks, LocalDateTime now) {
        // 收集所有需要处理的日记 ID
        Set<String> diaryIds = tasks.stream()
                .map(EmbeddingTask::getDiaryId)
                .collect(Collectors.toSet());

        // 批量查询日记
        Map<String, Diary> diaryMap = new HashMap<>();
        for (String diaryId : diaryIds) {
            Diary diary = diaryRepository.findByDiaryId(diaryId);
            if (diary != null) {
                diaryMap.put(diaryId, diary);
            }
        }

        // 准备批量 Embedding 数据
        List<TextSegment> allSegments = new ArrayList<>();
        List<String> allIds = new ArrayList<>();
        List<EmbeddingTask> successTasks = new ArrayList<>();
        List<String> toRemoveIds = new ArrayList<>();

        for (EmbeddingTask task : tasks) {
            Diary diary = diaryMap.get(task.getDiaryId());
            if (diary == null) {
                log.warn("日记 {} 不存在，跳过任务 {}", task.getDiaryId(), task.getId());
                taskRepository.markAsCompleted(task.getId(), now);
                continue;
            }

            // 检查用户隐私设置
            if (!isRagAllowed(task.getUserId())) {
                log.info("用户 {} 不允许 RAG，标记任务 {} 为完成", task.getUserId(), task.getId());
                taskRepository.markAsCompleted(task.getId(), now);
                continue;
            }

            // 获取明文内容
            String text = diary.getPlainContent();
            if (text == null || text.isEmpty()) {
                text = diary.getContent();
            }
            if (text == null || text.isEmpty()) {
                log.warn("日记 {} 内容为空，跳过", task.getDiaryId());
                taskRepository.markAsCompleted(task.getId(), now);
                continue;
            }

            // 准备 Metadata
            HashMap<String, Object> params = new HashMap<>();
            params.put("userId", diary.getUserId());
            if (diary.getEntryDate() != null) {
                params.put("entryDate", diary.getEntryDate().toString());
            }

            Document doc = Document.document(text, Metadata.from(params));
            List<TextSegment> segments = documentSplitter.split(doc);

            // 记录需要删除的旧 embedding
            toRemoveIds.add(diary.getDiaryId());

            // 为每个 segment 分配唯一 ID
            for (int i = 0; i < segments.size(); i++) {
                allSegments.add(segments.get(i));
                allIds.add(diary.getDiaryId() + "_" + i);
            }

            successTasks.add(task);
        }

        if (allSegments.isEmpty()) {
            return;
        }

        try {
            // 批量删除旧 Embedding
            for (String id : toRemoveIds) {
                try {
                    milvusEmbeddingStore.remove(id);
                } catch (Exception e) {
                    log.warn("删除旧 Embedding {} 失败: {}", id, e.getMessage());
                }
            }

            // 批量调用 Embedding API
            List<Embedding> embeddings = embeddingModel.embedAll(allSegments).content();

            // 批量写入 Milvus
            milvusEmbeddingStore.addAll(allIds, embeddings, allSegments);

            // 标记所有成功的任务
            for (EmbeddingTask task : successTasks) {
                taskRepository.markAsCompleted(task.getId(), now);
            }

            log.info("批量写入 {} 个 Embedding 完成，涉及 {} 个任务", embeddings.size(), successTasks.size());

        } catch (Exception e) {
            log.error("批量处理 UPSERT 任务失败", e);
            // 增加重试次数
            for (EmbeddingTask task : successTasks) {
                LocalDateTime nextRetry = calculateNextRetry(task.getRetryCount() + 1);
                taskRepository.incrementRetryAndSetNextAttempt(
                        task.getId(), e.getMessage(), nextRetry, now);
            }
        }
    }

    /**
     * 处理单个 DELETE 任务
     */
    private void processDeleteTask(EmbeddingTask task, LocalDateTime now) {
        try {
            milvusEmbeddingStore.remove(task.getDiaryId());
            taskRepository.markAsCompleted(task.getId(), now);
            log.info("删除日记 {} 的 Embedding 成功", task.getDiaryId());
        } catch (Exception e) {
            log.error("删除 Embedding 失败: {}", task.getDiaryId(), e);
            LocalDateTime nextRetry = calculateNextRetry(task.getRetryCount() + 1);
            taskRepository.incrementRetryAndSetNextAttempt(
                    task.getId(), e.getMessage(), nextRetry, now);
        }
    }

    /**
     * 计算下次重试时间（指数退避）
     * 第1次: 5秒后
     * 第2次: 25秒后
     * 第3次: 125秒后
     * ...
     */
    private LocalDateTime calculateNextRetry(int retryCount) {
        long delaySeconds = (long) Math.pow(5, retryCount);
        return LocalDateTime.now().plusSeconds(Math.min(delaySeconds, 3600)); // 最大1小时
    }

    /**
     * 检查用户是否允许 RAG 功能
     */
    private boolean isRagAllowed(String userId) {
        User user = userRepository.findByUserId(userId);
        if (user == null) {
            return false;
        }

        String keyMode = user.getKeyMode();
        if (keyMode == null || "DEFAULT".equals(keyMode)) {
            return true;
        }

        // CUSTOM 模式：仅当开启云端备份时允许 RAG
        return Boolean.TRUE.equals(user.getHasCloudBackup());
    }

    /**
     * 定期清理已完成的任务（每小时执行一次）
     * 保留最近24小时的已完成任务
     */
    @Scheduled(fixedDelay = 3600000)
    @Transactional
    public void cleanupCompletedTasks() {
        LocalDateTime before = LocalDateTime.now().minusHours(24);
        int deleted = taskRepository.deleteCompletedBefore(before);
        if (deleted > 0) {
            log.info("清理 {} 个已完成的 Embedding 任务", deleted);
        }
    }
}
