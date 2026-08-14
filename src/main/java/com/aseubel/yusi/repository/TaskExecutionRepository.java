package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.constant.TaskFailureCategory;
import com.aseubel.yusi.pojo.entity.TaskExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface TaskExecutionRepository extends JpaRepository<TaskExecution, Long> {

    Optional<TaskExecution> findByTaskId(String taskId);

    Optional<TaskExecution> findByIdempotencyKey(String idempotencyKey);

    @Modifying
    @Query("UPDATE TaskExecution t SET "
            + "t.status = 'RUNNING', t.claimedBy = :workerId, t.claimedAt = :now, "
            + "t.updatedAt = :now, t.version = t.version + 1 "
            + "WHERE t.taskId = :taskId "
            + "AND t.status IN ('PENDING', 'RETRY_WAIT') "
            + "AND (t.nextAttemptAt IS NULL OR t.nextAttemptAt <= :now)")
    int claim(@Param("taskId") String taskId, @Param("workerId") String workerId,
            @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE TaskExecution t SET "
            + "t.retryCount = t.retryCount + 1, "
            + "t.status = CASE WHEN t.retryCount + 1 >= t.maxRetries THEN 'FAILED' ELSE 'RETRY_WAIT' END, "
            + "t.failureCategory = :failureCategory, t.nextAttemptAt = :now, "
            + "t.claimedBy = null, t.claimedAt = null, t.updatedAt = :now, "
            + "t.completedAt = CASE WHEN t.retryCount + 1 >= t.maxRetries THEN :now ELSE t.completedAt END, "
            + "t.version = t.version + 1 "
            + "WHERE t.status = 'RUNNING' AND t.claimedAt < :staleBefore")
    int recoverStale(@Param("staleBefore") LocalDateTime staleBefore,
            @Param("now") LocalDateTime now,
            @Param("failureCategory") TaskFailureCategory failureCategory);
}
