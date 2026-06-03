package com.aseubel.yusi.pojo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 灵魂周报/月报实体（F8.3 周期性回顾）。
 * Agent 基于 mid-memory 和近期日记生成的情感回顾与成长洞察。
 *
 * @author Aseubel
 * @date 2026/06/03
 */
@Data
@Entity
@Builder
@Table(name = "soul_report")
@AllArgsConstructor
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class SoulReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 用户ID */
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    /** 报告类型：WEEKLY / MONTHLY */
    @Column(name = "report_type", nullable = false, length = 16)
    private String reportType;

    /** 报告标题 */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** 报告正文（Markdown 格式，供前端渲染） */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 本周/本月起始日期 */
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    /** 本周/本月结束日期 */
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    /** 是否已通知用户 */
    @Column(name = "notified")
    @Builder.Default
    private Boolean notified = false;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
