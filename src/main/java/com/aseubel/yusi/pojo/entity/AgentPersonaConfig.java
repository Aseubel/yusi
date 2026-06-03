package com.aseubel.yusi.pojo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Agent 人格配置表。
 * 每个用户可定制其 AI 知己的语气风格、主动问候频率等。
 *
 * @author Aseubel
 * @date 2026/06/02
 */
@Data
@Entity
@Builder
@Table(name = "agent_persona_config")
@DynamicInsert
@DynamicUpdate
@AllArgsConstructor
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class AgentPersonaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 用户ID（唯一） */
    @Column(name = "user_id", unique = true, nullable = false, length = 64)
    private String userId;

    /** 人格风格：gentle（温柔知己，默认）/ lively（活泼陪伴）/ calm（沉静倾听）/ rational（理性分析） */
    @Column(name = "personality_style", length = 32)
    @Builder.Default
    private String personalityStyle = "gentle";

    /** 主动问候频率：off / low（每周最多1次）/ normal（每周最多2次） */
    @Column(name = "proactive_frequency", length = 16)
    @Builder.Default
    private String proactiveFrequency = "low";

    /** 静默时段开始（HH:mm），此期间不发送主动通知，null 表示不限 */
    @Column(name = "quiet_hours_start", length = 8)
    private String quietHoursStart;

    /** 静默时段结束（HH:mm） */
    @Column(name = "quiet_hours_end", length = 8)
    private String quietHoursEnd;

    /** 是否允许 Agent 提及纪念日 */
    // TODO Phase 5 (F8.4): 实现周年纪念日自动检测与提醒功能
    @Column(name = "anniversary_reminder_enabled")
    @Builder.Default
    private Boolean anniversaryReminderEnabled = true;

    /** 是否允许 Agent 发送周报 (F8.3 已实现) */
    @Column(name = "weekly_report_enabled")
    @Builder.Default
    private Boolean weeklyReportEnabled = true;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
