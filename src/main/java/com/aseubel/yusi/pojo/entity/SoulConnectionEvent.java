package com.aseubel.yusi.pojo.entity;

import cn.hutool.core.util.IdUtil;
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

/**
 * Immutable product event history for a connection lifecycle transition.
 *
 * <p>The current connection row is a snapshot; this table preserves the
 * low-sensitivity state transition and its server-generated event identity.</p>
 */
@Data
@Entity
@Builder
@Table(
        name = "soul_connection_event",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_soul_connection_event_event_id",
                columnNames = "event_id"),
        indexes = {
                @Index(name = "idx_soul_connection_event_connection_time", columnList = "connection_id, occurred_at"),
                @Index(name = "idx_soul_connection_event_match_time", columnList = "match_id, occurred_at"),
                @Index(name = "idx_soul_connection_event_actor_time", columnList = "actor_user_id, occurred_at")
        })
@DynamicInsert
@DynamicUpdate
@NoArgsConstructor
@AllArgsConstructor
public class SoulConnectionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "event_name", nullable = false, length = 64)
    private String eventName;

    @Column(name = "schema_version", nullable = false)
    @Builder.Default
    private Integer schemaVersion = 1;

    @Column(name = "connection_id", nullable = false)
    private Long connectionId;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Column(name = "actor_user_id", length = 64)
    private String actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 32)
    private SoulConnectionStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32)
    private SoulConnectionStatus toStatus;

    @Column(name = "action", nullable = false, length = 32)
    private String action;

    @Column(name = "reason_category", length = 64)
    private String reasonCategory;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @PrePersist
    public void prePersist() {
        if (eventId == null || eventId.isBlank()) {
            eventId = IdUtil.fastSimpleUUID();
        }
        if (schemaVersion == null) {
            schemaVersion = 1;
        }
        if (occurredAt == null) {
            occurredAt = LocalDateTime.now();
        }
    }
}
