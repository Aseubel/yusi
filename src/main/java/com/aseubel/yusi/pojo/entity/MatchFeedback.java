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
 * 匹配反馈记录。
 * 收集用户对匹配推荐的行为反馈（接受/跳过）以及互动质量信号，
 * 反哺 Agent 认知层以优化后续推荐。
 *
 * @author Aseubel
 * @date 2026/06/03
 */
@Data
@Entity
@Builder
@Table(name = "match_feedback")
@AllArgsConstructor
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class MatchFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联的匹配记录 ID */
    @Column(name = "match_id", nullable = false)
    private Long matchId;

    /** 用户 ID */
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    /** 反馈动作: ACCEPT / SKIP / INTERACT / REPORT */
    @Column(name = "action", nullable = false, length = 16)
    private String action;

    /** 互动深度（仅在 INTERACT 时有意义）: 消息条数 */
    @Column(name = "interaction_depth")
    private Integer interactionDepth;

    /** 反馈时间 */
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
