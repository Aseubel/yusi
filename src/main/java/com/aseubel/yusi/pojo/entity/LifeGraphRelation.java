package com.aseubel.yusi.pojo.entity;

import java.math.BigDecimal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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

@Data
@Entity
@Builder
@Table(name = "life_graph_relation", uniqueConstraints = {
        @UniqueConstraint(name = "uk_life_graph_relation_user_semantic_edge",
                columnNames = { "user_id", "semantic_source_id", "semantic_target_id", "type" })
})
@DynamicInsert
@DynamicUpdate
@NoArgsConstructor
@AllArgsConstructor
public class LifeGraphRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "semantic_source_id")
    private Long semanticSourceId;

    @Column(name = "semantic_target_id")
    private Long semanticTargetId;

    @Column(name = "type", nullable = false, length = 64)
    private String type;

    @Column(name = "confidence", nullable = false, precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(name = "weight", nullable = false)
    private Integer weight;

    @Column(name = "manual_weight", nullable = false)
    private Integer manualWeight;

    @Column(name = "first_seen")
    private LocalDateTime firstSeen;

    @Column(name = "last_seen")
    private LocalDateTime lastSeen;

    @Column(name = "evidence_diary_id", length = 255)
    private String evidenceDiaryId;

    @Column(name = "origin", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Origin origin = Origin.MANUAL;

    @Column(name = "props", columnDefinition = "JSON")
    private String props;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    @PrePersist
    public void prePersist() {
        if (createdAt == null)
            createdAt = LocalDateTime.now();
        if (updatedAt == null)
            updatedAt = LocalDateTime.now();
        if (weight == null)
            weight = 1;
        if (confidence == null)
            confidence = java.math.BigDecimal.valueOf(0.800);
        if (origin == null)
            origin = Origin.MANUAL;
        if (semanticSourceId == null)
            semanticSourceId = sourceId;
        if (semanticTargetId == null)
            semanticTargetId = targetId;
        if (manualWeight == null)
            manualWeight = origin == Origin.MANUAL ? weight : 0;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
        if (weight == null)
            weight = 1;
        if (confidence == null)
            confidence = java.math.BigDecimal.valueOf(0.800);
        if (origin == null)
            origin = Origin.MANUAL;
        if (semanticSourceId == null)
            semanticSourceId = sourceId;
        if (semanticTargetId == null)
            semanticTargetId = targetId;
        if (manualWeight == null)
            manualWeight = origin == Origin.MANUAL ? weight : 0;
    }

    public enum Origin {
        AUTO,
        MANUAL
    }
}
