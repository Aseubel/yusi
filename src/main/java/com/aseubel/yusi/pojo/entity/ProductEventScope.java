package com.aseubel.yusi.pojo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** User scope for reading a product event without crossing account boundaries. */
@Data
@Builder
@Entity
@Table(name = "product_event_scope", uniqueConstraints = {
        @UniqueConstraint(name = "uk_product_event_scope_event_user", columnNames = {"event_id", "user_id"})
}, indexes = {
        @Index(name = "idx_product_event_scope_user_event", columnList = "user_id, event_id")
})
@NoArgsConstructor
@AllArgsConstructor
public class ProductEventScope {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "scope_role", nullable = false, length = 32)
    private String scopeRole;
}
