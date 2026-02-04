package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.EmbeddingTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Milvus Embedding 任务仓库
 *
 * @author Aseubel
 * @date 2026/2/3
 */
@Repository
public interface EmbeddingTaskRepository extends JpaRepository<EmbeddingTask, Long> {

    /**
     * 批量获取待处理的任务（带悲观锁，防止多实例重复消费）
     * 使用 FOR UPDATE SKIP LOCKED 实现无阻塞并发消费
     */
    @Query(value = "SELECT * FROM embedding_task " +
            "WHERE status = 'PENDING' AND next_retry_at <= :now " +
            "ORDER BY created_at ASC " +
            "LIMIT :limit " +
            "FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<EmbeddingTask> findPendingTasksForUpdate(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /**
     * 批量更新任务状态为处理中
     */
    @Modifying
    @Transactional
    @Query("UPDATE EmbeddingTask t SET t.status = 'PROCESSING', t.updatedAt = :now WHERE t.id IN :ids")
    int markAsProcessing(@Param("ids") List<Long> ids, @Param("now") LocalDateTime now);

    /**
     * 标记任务为完成
     */
    @Modifying
    @Transactional
    @Query("UPDATE EmbeddingTask t SET t.status = 'COMPLETED', t.updatedAt = :now WHERE t.id = :id")
    int markAsCompleted(@Param("id") Long id, @Param("now") LocalDateTime now);

    /**
     * 增加重试次数并设置下次重试时间（指数退避）
     */
    @Modifying
    @Transactional
    @Query("UPDATE EmbeddingTask t SET " +
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

    /**
     * 查找同一日记的待处理任务（用于去重）
     */
    @Query("SELECT t FROM EmbeddingTask t WHERE t.diaryId = :diaryId AND t.status = 'PENDING'")
    List<EmbeddingTask> findPendingByDiaryId(@Param("diaryId") String diaryId);

    /**
     * 删除已完成的任务（定期清理）
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM EmbeddingTask t WHERE t.status = 'COMPLETED' AND t.updatedAt < :before")
    int deleteCompletedBefore(@Param("before") LocalDateTime before);

    /**
     * 统计待处理任务数量（监控用）
     */
    @Query("SELECT COUNT(t) FROM EmbeddingTask t WHERE t.status = 'PENDING'")
    long countPending();

    /**
     * 统计失败任务数量（监控用）
     */
    @Query("SELECT COUNT(t) FROM EmbeddingTask t WHERE t.status = 'FAILED'")
    long countFailed();
}
