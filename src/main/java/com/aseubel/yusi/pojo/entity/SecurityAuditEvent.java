package com.aseubel.yusi.pojo.entity;

import cn.hutool.core.util.IdUtil;
import com.aseubel.yusi.pojo.constant.SecurityAuditAction;
import com.aseubel.yusi.pojo.constant.SecurityAuditActorType;
import com.aseubel.yusi.pojo.constant.SecurityAuditOutcome;
import com.aseubel.yusi.pojo.constant.SecurityAuditResourceType;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;

/** Immutable security audit record; its metadata is already redacted by the service. */
@Data
@Builder
@Entity
@Table(name = "security_audit_event", uniqueConstraints = {
        @UniqueConstraint(name = "uk_security_audit_event_id", columnNames = "event_id")
}, indexes = {
        @Index(name = "idx_security_audit_actor_time", columnList = "actor_user_id, occurred_at"),
        @Index(name = "idx_security_audit_subject_time", columnList = "subject_user_id, occurred_at"),
        @Index(name = "idx_security_audit_resource_time", columnList = "resource_type, resource_id, occurred_at"),
        @Index(name = "idx_security_audit_occurred_at", columnList = "occurred_at")
})
@DynamicInsert
@DynamicUpdate
@NoArgsConstructor
@AllArgsConstructor
public class SecurityAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 64)
    private SecurityAuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 16)
    private SecurityAuditActorType actorType;

    @Column(name = "actor_user_id", length = 64)
    private String actorUserId;

    @Column(name = "subject_user_id", length = 64)
    private String subjectUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 32)
    private SecurityAuditResourceType resourceType;

    @Column(name = "resource_id", length = 255)
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 16)
    private SecurityAuditOutcome outcome;

    @Column(name = "reason_code", length = 64)
    private String reasonCode;

    /** JSON contains only allow-listed categories; it is never user content. */
    @JsonIgnore
    @Column(name = "details_json", nullable = false, length = 1024)
    private String detailsJson;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @PrePersist
    public void prePersist() {
        if (eventId == null || eventId.isBlank()) {
            eventId = IdUtil.fastSimpleUUID();
        }
        if (occurredAt == null) {
            occurredAt = LocalDateTime.now();
        }
        if (detailsJson == null) {
            detailsJson = "{}";
        }
    }
}
