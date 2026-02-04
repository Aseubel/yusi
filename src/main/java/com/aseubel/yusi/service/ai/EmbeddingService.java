package com.aseubel.yusi.service.ai;

import com.aseubel.yusi.common.disruptor.Element;
import com.aseubel.yusi.common.disruptor.EventType;
import com.aseubel.yusi.common.repochain.Processor;
import com.aseubel.yusi.common.repochain.ProcessorChain;
import com.aseubel.yusi.common.repochain.Result;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.pojo.entity.EmbeddingTask;
import com.aseubel.yusi.repository.EmbeddingTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
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
public class EmbeddingService implements Processor<Element> {

    private final EmbeddingTaskRepository taskRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Result<Element> process(Element data, int index, ProcessorChain<Element> chain) {
        if (data.getEventType() != EventType.DIARY_WRITE
                && data.getEventType() != EventType.DIARY_MODIFY
                && data.getEventType() != EventType.DIARY_DELETE) {
            return chain.process(data, index);
        }

        Diary diary = (Diary) data.getData();

        switch (data.getEventType()) {
            case DIARY_WRITE:
            case DIARY_MODIFY:
                createUpsertTask(diary);
                break;
            case DIARY_DELETE:
                createDeleteTask(diary);
                break;
            default:
                break;
        }

        return chain.process(data, index);
    }

    /**
     * 创建 UPSERT 任务
     * 如果已有相同日记的待处理任务，则跳过（去重）
     */
    private void createUpsertTask(Diary diary) {
        // 去重检查：如果已有相同日记的待处理任务，跳过
        List<EmbeddingTask> pending = taskRepository.findPendingByDiaryId(diary.getDiaryId());
        if (!pending.isEmpty()) {
            log.debug("日记 {} 已有待处理的 Embedding 任务，跳过重复创建", diary.getDiaryId());
            return;
        }

        EmbeddingTask task = EmbeddingTask.createUpsertTask(diary.getDiaryId(), diary.getUserId());
        taskRepository.save(task);
        log.debug("创建 Embedding UPSERT 任务: diaryId={}, userId={}", diary.getDiaryId(), diary.getUserId());
    }

    /**
     * 创建 DELETE 任务
     */
    private void createDeleteTask(Diary diary) {
        EmbeddingTask task = EmbeddingTask.createDeleteTask(diary.getDiaryId(), diary.getUserId());
        taskRepository.save(task);
        log.debug("创建 Embedding DELETE 任务: diaryId={}", diary.getDiaryId());
    }
}
