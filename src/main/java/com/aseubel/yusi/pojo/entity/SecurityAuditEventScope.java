package com.aseubel.yusi.pojo.entity;

import com.aseubel.yusi.pojo.constant.SecurityAuditScopeRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** User visibility scope for a security audit event. */
@Data
@Builder
@Entity
@Table(name = "security_audit_event_scope", uniqueConstraints = {
        @UniqueConstraint(name = "uk_security_audit_scope_event_user",
                columnNames = {"audit_event_id", "user_id"})
}, indexes = {
        @Index(name = "idx_security_audit_scope_user_event", columnList = "user_id, audit_event_id")
})
@NoArgsConstructor
@AllArgsConstructor
public class SecurityAuditEventScope {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "audit_event_id", nullable = false)
    private Long auditEventId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_role", nullable = false, length = 32)
    private SecurityAuditScopeRole scopeRole;
}
