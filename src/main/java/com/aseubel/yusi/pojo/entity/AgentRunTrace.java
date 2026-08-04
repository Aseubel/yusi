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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;

/**
 * Low-sensitivity server-side summary of one AgentRun.
 *
 * <p>This intentionally stores lifecycle metadata only. Prompts, model thinking,
 * tool arguments, tool results, and user content do not belong in this table.</p>
 */
@Data
@Builder
@Entity
@DynamicUpdate
@Table(name = "agent_run_trace", uniqueConstraints = {
        @UniqueConstraint(name = "uk_agent_run_trace_user_run", columnNames = { "user_id", "run_id" })
}, indexes = {
        @Index(name = "idx_agent_run_trace_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_agent_run_trace_status", columnList = "status")
})
@NoArgsConstructor
@AllArgsConstructor
public class AgentRunTrace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false, length = 64)
    private String runId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "scene", nullable = false, length = 32)
    private String scene;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.RUNNING;

    @Column(name = "current_stage", length = 32)
    private String currentStage;

    @Column(name = "tool_count", nullable = false)
    @Builder.Default
    private Integer toolCount = 0;

    @Column(name = "failure_category", length = 64)
    private String failureCategory;

    @Column(name = "cancel_source", length = 32)
    private String cancelSource;

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
        if (toolCount == null) {
            toolCount = 0;
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

    public enum Status {
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}
