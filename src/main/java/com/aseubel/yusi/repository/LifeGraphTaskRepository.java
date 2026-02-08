package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.LifeGraphTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LifeGraphTaskRepository extends JpaRepository<LifeGraphTask, Long> {

    @Query(value = "SELECT * FROM life_graph_task " +
            "WHERE status = 'PENDING' AND next_retry_at <= :now " +
            "ORDER BY created_at ASC " +
            "LIMIT :limit " +
            "FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<LifeGraphTask> findPendingTasksForUpdate(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Modifying
    @Transactional
    @Query("UPDATE LifeGraphTask t SET t.status = 'PROCESSING', t.updatedAt = :now WHERE t.id IN :ids")
    int markAsProcessing(@Param("ids") List<Long> ids, @Param("now") LocalDateTime now);

    @Modifying
    @Transactional
    @Query("UPDATE LifeGraphTask t SET t.status = 'PROCESSING', t.updatedAt = :now WHERE t.id = :id")
    int markAsProcessing(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Modifying
    @Transactional
    @Query("UPDATE LifeGraphTask t SET t.status = 'COMPLETED', t.updatedAt = :now WHERE t.id = :id")
    int markAsCompleted(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Modifying
    @Transactional
    @Query("UPDATE LifeGraphTask t SET " +
            "t.retryCount = t.retryCount + 1, " +
            "t.status = CASE WHEN t.retryCount + 1 >= t.maxRetries THEN 'FAILED' ELSE 'PENDING' END, " +
            "t.errorMessage = :errorMessage, " +
            "t.nextRetryAt = :nextRetryAt, " +
            "t.updatedAt = :now " +
            "WHERE t.id = :id")
    int incrementRetryAndSetNextAttempt(@Param("id") Long id,
            @Param("errorMessage") String errorMessage,
            @Param("nextRetryAt") LocalDateTime nextRetryAt,
            @Param("now") LocalDateTime now);

    @Query("SELECT t FROM LifeGraphTask t WHERE t.diaryId = :diaryId AND t.status = 'PENDING'")
    List<LifeGraphTask> findPendingByDiaryId(@Param("diaryId") String diaryId);

    @Modifying
    @Transactional
    @Query("DELETE FROM LifeGraphTask t WHERE t.status = 'COMPLETED' AND t.updatedAt < :before")
    int deleteCompletedBefore(@Param("before") LocalDateTime before);
}
