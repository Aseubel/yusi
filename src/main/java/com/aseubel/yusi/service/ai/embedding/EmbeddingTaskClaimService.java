package com.aseubel.yusi.service.ai.embedding;

import com.aseubel.yusi.pojo.entity.EmbeddingTask;
import com.aseubel.yusi.repository.EmbeddingTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmbeddingTaskClaimService {

    private final EmbeddingTaskRepository taskRepository;

    /**
     * 在短事务内完成任务抢占，避免在外部模型和向量库调用期间长期持有数据库连接。
     */
    @Transactional
    public List<EmbeddingTask> claimPendingTasks(LocalDateTime now, int limit) {
        List<EmbeddingTask> tasks = taskRepository.findPendingTasksForUpdate(now, limit);
        if (tasks.isEmpty()) {
            return tasks;
        }

        taskRepository.markAsProcessing(
                tasks.stream().map(EmbeddingTask::getId).toList(), now);
        return tasks;
    }
}
