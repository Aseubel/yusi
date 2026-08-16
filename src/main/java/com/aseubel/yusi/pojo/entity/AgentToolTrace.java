package com.aseubel.yusi.pojo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import com.aseubel.yusi.service.ai.tool.constant.AgentToolIdempotencyMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Low-sensitivity lifecycle metadata for one tool call inside an AgentRun.
 *
 * <p>Tool arguments, results, user queries and model content must not be stored here.</p>
 */
@Data
@Builder
@Entity
@DynamicUpdate
@Table(name = "agent_tool_trace", uniqueConstraints = {
        @UniqueConstraint(name = "uk_agent_tool_trace_user_run_call",
                columnNames = { "user_id", "run_id", "tool_call_id" })
}, indexes = {
        @Index(name = "idx_agent_tool_trace_user_run_created",
                columnList = "user_id, run_id, created_at"),
        @Index(name = "idx_agent_tool_trace_status_updated",
                columnList = "status, updated_at")
})
@NoArgsConstructor
@AllArgsConstructor
public class AgentToolTrace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "run_id", nullable = false, length = 64)
    private String runId;

    @Column(name = "tool_call_id", nullable = false, length = 64)
    private String toolCallId;

    @Column(name = "upstream_tool_call_id", length = 128)
    private String upstreamToolCallId;

    @Column(name = "tool_name", nullable = false, length = 64)
    private String toolName;

    @Column(name = "tool_source", nullable = false, length = 16)
    private String toolSource;

    @Column(name = "capability_version", length = 32)
    private String capabilityVersion;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "idempotency_mode", nullable = false, length = 24)
    @Builder.Default
    private AgentToolIdempotencyMode idempotencyMode = AgentToolIdempotencyMode.NONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "idempotency_status", length = 20)
    private IdempotencyStatus idempotencyStatus;

    @Column(name = "idempotency_claimed_at")
    private LocalDateTime idempotencyClaimedAt;

    @Column(name = "idempotency_resolved_at")
    private LocalDateTime idempotencyResolvedAt;

    @Column(name = "idempotency_expires_at")
    private LocalDateTime idempotencyExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.RUNNING;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_category", length = 32)
    private FailureCategory failureCategory;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (status == null) {
            status = Status.RUNNING;
        }
        if (attemptCount == null || attemptCount < 1) {
            attemptCount = 1;
        }
        if (idempotencyMode == null) {
            idempotencyMode = AgentToolIdempotencyMode.NONE;
        }
        if (startedAt == null) {
            startedAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void finish(Status terminalStatus, FailureCategory terminalFailureCategory,
            LocalDateTime completedAt, Long durationMs) {
        this.status = terminalStatus;
        this.failureCategory = terminalFailureCategory;
        this.completedAt = completedAt;
        this.durationMs = durationMs == null
                ? calculateDuration(startedAt, completedAt)
                : Math.max(0L, durationMs);
    }

    private long calculateDuration(LocalDateTime startedAt, LocalDateTime completedAt) {
        if (startedAt == null || completedAt == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(startedAt, completedAt).toMillis());
    }

    public enum Status {
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public enum FailureCategory {
        TOOL_FAILED,
        AGENT_ERROR,
        TIMEOUT,
        CANCELLED,
        UNKNOWN
    }

    public enum IdempotencyStatus {
        CLAIMED,
        COMPLETED,
        FAILED,
        UNKNOWN
    }
}
