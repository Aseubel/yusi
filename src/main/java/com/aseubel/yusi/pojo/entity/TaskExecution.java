package com.aseubel.yusi.pojo.entity;

import cn.hutool.core.util.IdUtil;
import com.aseubel.yusi.pojo.constant.TaskExecutionStatus;
import com.aseubel.yusi.pojo.constant.TaskExecutionType;
import com.aseubel.yusi.pojo.constant.TaskFailureCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;

/**
 * Cross-domain task ledger. Domain task tables remain the worker projections;
 * this record carries ownership, correlation, retry and recovery state.
 */
@Data
@Builder
@Entity
@Table(name = "task_execution", uniqueConstraints = {
        @UniqueConstraint(name = "uk_task_execution_task_id", columnNames = "task_id"),
        @UniqueConstraint(name = "uk_task_execution_idempotency", columnNames = "idempotency_key")
}, indexes = {
        @Index(name = "idx_task_execution_owner_status", columnList = "owner_user_id, status"),
        @Index(name = "idx_task_execution_source", columnList = "source_type, source_id"),
        @Index(name = "idx_task_execution_status_attempt", columnList = "status, next_attempt_at"),
        @Index(name = "idx_task_execution_trigger", columnList = "trigger_event_id"),
        @Index(name = "idx_task_execution_run", columnList = "run_id")
})
@DynamicInsert
@DynamicUpdate
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false, length = 64)
    private String taskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 32)
    private TaskExecutionType taskType;

    @Column(name = "owner_user_id", length = 64)
    private String ownerUserId;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "source_id", nullable = false, length = 255)
    private String sourceId;

    @Column(name = "source_version", length = 128)
    private String sourceVersion;

    @Column(name = "trigger_event_id", length = 64)
    private String triggerEventId;

    @Column(name = "run_id", length = 64)
    private String runId;

    @Column(name = "idempotency_key", nullable = false, length = 191)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    @Builder.Default
    private TaskExecutionStatus status = TaskExecutionStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    @Builder.Default
    private Integer maxRetries = 5;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_category", length = 24)
    private TaskFailureCategory failureCategory;

    @Column(name = "checkpoint_json", length = 2048)
    private String checkpointJson;

    @Column(name = "claimed_by", length = 128)
    private String claimedBy;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (taskId == null || taskId.isBlank()) {
            taskId = IdUtil.fastSimpleUUID();
        }
        if (status == null) {
            status = TaskExecutionStatus.PENDING;
        }
        if (retryCount == null) {
            retryCount = 0;
        }
        if (maxRetries == null) {
            maxRetries = 5;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (nextAttemptAt == null && status == TaskExecutionStatus.PENDING) {
            nextAttemptAt = now;
        }
        if (version == null) {
            version = 0L;
        }
    }
}
