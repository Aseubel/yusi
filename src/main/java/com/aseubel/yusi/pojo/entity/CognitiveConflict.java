package com.aseubel.yusi.pojo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 认知冲突标记（F11.3）。
 * 当新输入与已有 user-persona 或 lifeGraph 存在语义矛盾时，记录冲突供 Agent 在对话中澄清。
 *
 * @author Aseubel
 * @date 2026/06/03
 */
@Data
@Entity
@Builder
@Table(name = "cognitive_conflict")
@AllArgsConstructor
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CognitiveConflict {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 用户ID */
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    /** 冲突描述（自然语言，供 Agent 在对话中使用） */
    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    /** 旧的认知（已有画像中的） */
    @Column(name = "existing_belief", columnDefinition = "TEXT")
    private String existingBelief;

    /** 新的认知（与旧认知矛盾的新输入） */
    @Column(name = "new_observation", columnDefinition = "TEXT")
    private String newObservation;

    /** 来源：PERSONA / LIFEGRAPH */
    @Column(name = "source", nullable = false, length = 16)
    private String source;

    /** 是否已被 Agent 在对话中澄清过 */
    @Column(name = "resolved")
    @Builder.Default
    private Boolean resolved = false;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
