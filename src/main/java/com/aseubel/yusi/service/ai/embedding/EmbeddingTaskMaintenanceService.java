package com.aseubel.yusi.service.ai.embedding;

import com.aseubel.yusi.repository.EmbeddingTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EmbeddingTaskMaintenanceService {

    private final EmbeddingTaskRepository taskRepository;

    /**
     * 仅在短事务内重置任务并补齐缺失任务。
     */
    @Transactional
    public int resetAndRebuildPendingTasks(LocalDateTime now) {
        int resetCount = taskRepository.resetAllToPending(now);
        int insertedCount = taskRepository.insertMissingTasks(now);
        return resetCount + insertedCount;
    }
}
