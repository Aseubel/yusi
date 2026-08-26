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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** Restricted, retryable deletion ledger. */
@Data
@Builder
@Entity
@Table(name = "account_deletion_request", uniqueConstraints = {
        @UniqueConstraint(name = "uk_account_deletion_request_id", columnNames = "request_id")
}, indexes = {
        @Index(name = "idx_account_deletion_target_status", columnList = "target_user_ref, status"),
        @Index(name = "idx_account_deletion_status_updated", columnList = "status, updated_at")
})
@NoArgsConstructor
@AllArgsConstructor
public class AccountDeletionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @Column(name = "target_user_ref", length = 128)
    private String targetUserRef;

    @Column(name = "requested_by_ref", length = 128)
    private String requestedByRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "failure_category", length = 48)
    private String failureCategory;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    void prePersist() {
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString().replace("-", "");
        }
        if (status == null) {
            status = Status.PENDING;
        }
        if (retryCount == null) {
            retryCount = 0;
        }
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    public enum Status {
        PENDING,
        RUNNING,
        PENDING_RETRY,
        SUPERSEDED,
        COMPLETED
    }
}
