package com.aseubel.yusi.pojo.entity;

import cn.hutool.core.util.IdUtil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Durable, append-only product event envelope.
 *
 * <p>This table stores business facts and correlation metadata only. It is not
 * a log sink and must never contain user prose, prompts, tool arguments, or
 * model output.</p>
 */
@Data
@Builder
@Entity
@Table(name = "product_event", uniqueConstraints = {
        @UniqueConstraint(name = "uk_product_event_event_id", columnNames = "event_id"),
        @UniqueConstraint(name = "uk_product_event_idempotency", columnNames = "idempotency_key")
}, indexes = {
        @Index(name = "idx_product_event_user_time", columnList = "user_id, occurred_at"),
        @Index(name = "idx_product_event_source_time", columnList = "source, occurred_at"),
        @Index(name = "idx_product_event_match", columnList = "match_id, occurred_at"),
        @Index(name = "idx_product_event_connection", columnList = "connection_id, occurred_at"),
        @Index(name = "idx_product_event_run", columnList = "run_id, occurred_at")
})
@DynamicInsert
@DynamicUpdate
@NoArgsConstructor
@AllArgsConstructor
public class ProductEvent {

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

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "actor_user_id", length = 64)
    private String actorUserId;

    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "run_id", length = 64)
    private String runId;

    @Column(name = "match_id")
    private Long matchId;

    @Column(name = "connection_id")
    private Long connectionId;

    @Column(name = "situation_id", length = 64)
    private String situationId;

    @Column(name = "source", nullable = false, length = 32)
    private String source;

    @Column(name = "sensitivity", nullable = false, length = 16)
    private String sensitivity;

    @Column(name = "idempotency_key", nullable = false, length = 160)
    private String idempotencyKey;

    @Column(name = "payload_json", nullable = false, columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    private String payloadJson;

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
