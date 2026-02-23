package com.aseubel.yusi.pojo.entity;

import java.math.BigDecimal;
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

import java.time.LocalDateTime;

/**
 * 实体合并判断记录
 * 用于记录已分析过的候选对，避免重复调用 LLM
 */
@Data
@Entity
@Builder
@Table(
    name = "life_graph_merge_judgment",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_merge_judgment_pair", columnNames = { "user_id", "entity_id_a", "entity_id_b" })
    },
    indexes = {
        @Index(name = "idx_merge_judgment_user", columnList = "user_id"),
        @Index(name = "idx_merge_judgment_status", columnList = "status")
    }
)
@DynamicInsert
@DynamicUpdate
@NoArgsConstructor
@AllArgsConstructor
public class LifeGraphMergeJudgment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "entity_id_a", nullable = false)
    private Long entityIdA;

    @Column(name = "entity_id_b", nullable = false)
    private Long entityIdB;

    @Column(name = "name_a", nullable = false, length = 255)
    private String nameA;

    @Column(name = "name_b", nullable = false, length = 255)
    private String nameB;

    @Column(name = "type", nullable = false, length = 32)
    private String type;

    @Column(name = "sim_score", precision = 5)
    private Double simScore;

    /**
     * LLM判断结果: YES/NO
     */
    @Column(name = "merge_decision", length = 8)
    private String mergeDecision;

    @Column(name = "reason", length = 512)
    private String reason;

    @Column(name = "recommended_master_name", length = 255)
    private String recommendedMasterName;

    /**
     * 状态: PENDING-待处理, ACCEPTED-已接受, REJECTED-已拒绝
     */
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = "PENDING";
        }
    }
}
