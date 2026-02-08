package com.aseubel.yusi.pojo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;

@Data
@Entity
@Builder
@Table(name = "life_graph_entity_alias", uniqueConstraints = {
        @UniqueConstraint(name = "uk_life_graph_alias_user_norm", columnNames = { "user_id", "alias_norm" })
})
@DynamicInsert
@DynamicUpdate
@NoArgsConstructor
@AllArgsConstructor
public class LifeGraphEntityAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "alias_norm", nullable = false, length = 255)
    private String aliasNorm;

    @Column(name = "alias_display", nullable = false, length = 255)
    private String aliasDisplay;

    @Column(name = "confidence", nullable = false, precision = 4, scale = 3)
    private java.math.BigDecimal confidence;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null)
            createdAt = LocalDateTime.now();
        if (updatedAt == null)
            updatedAt = LocalDateTime.now();
        if (confidence == null)
            confidence = java.math.BigDecimal.valueOf(0.800);
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
        if (confidence == null)
            confidence = java.math.BigDecimal.valueOf(0.800);
    }
}

