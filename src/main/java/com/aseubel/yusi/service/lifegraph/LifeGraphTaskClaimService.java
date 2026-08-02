package com.aseubel.yusi.service.lifegraph;

import com.aseubel.yusi.pojo.entity.LifeGraphTask;
import com.aseubel.yusi.repository.LifeGraphTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LifeGraphTaskClaimService {

    private final LifeGraphTaskRepository taskRepository;

    /**
     * 在短事务内完成任务抢占，避免在图谱构建期间长期持有数据库连接。
     */
    @Transactional
    public List<LifeGraphTask> claimPendingTasks(LocalDateTime now, int limit) {
        List<LifeGraphTask> tasks = taskRepository.findPendingTasksForUpdate(now, limit);
        if (tasks.isEmpty()) {
            return tasks;
        }

        taskRepository.markAsProcessing(
                tasks.stream().map(LifeGraphTask::getId).toList(), now);
        return tasks;
    }
}
