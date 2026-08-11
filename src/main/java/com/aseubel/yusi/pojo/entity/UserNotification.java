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
import java.util.Locale;

/**
 * 用户统一消息表
 * 用于存储各类通知消息，便于统一消息中心展示
 */
@Data
@Entity
@Builder
@Table(
    name = "user_notification",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_notification_id", columnNames = { "notification_id" }),
        @UniqueConstraint(name = "uk_notification_user_announcement", columnNames = { "user_id", "announcement_id" })
    },
    indexes = {
        @Index(name = "idx_notification_user", columnList = "user_id"),
        @Index(name = "idx_notification_user_read", columnList = "user_id, is_read"),
        @Index(name = "idx_notification_user_type", columnList = "user_id, type"),
        @Index(name = "idx_notification_announcement", columnList = "announcement_id"),
        @Index(name = "idx_notification_created", columnList = "created_at")
    }
)
@DynamicInsert
@DynamicUpdate
@NoArgsConstructor
@AllArgsConstructor
public class UserNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_id", nullable = false, length = 64, unique = true)
    private String notificationId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    /**
     * Message type. SYSTEM is emitted by service workflows; ANNOUNCEMENT is
     * authored and published by an administrator.
     */
    @Column(name = "type", nullable = false, length = 32)
    private String type;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    /**
     * 关联类型: MERGE_JUDGMENT/DIARY/ENTITY 等
     */
    @Column(name = "ref_type", length = 32)
    private String refType;

    @Column(name = "ref_id", length = 64)
    private String refId;

    /** The immutable announcement that produced this inbox item, if any. */
    @Column(name = "announcement_id", length = 64)
    private String announcementId;

    @Column(name = "extra_data", columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    private String extraData;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @PrePersist
    public void prePersist() {
        if (notificationId == null) {
            notificationId = IdUtil.fastSimpleUUID();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (isRead == null) {
            isRead = false;
        }
    }

    /**
     * 消息类型枚举
     */
    public enum NotificationType {
        MERGE_SUGGESTION,   // 合并建议
        SYSTEM,             // 系统通知
        REMINDER,           // 提醒
        ANNOUNCEMENT,       // 管理员公告
        SOUL_WEEKLY_REPORT, // 灵魂周报
        AGENT_GREETING,     // 主动问候
        RESONANCE_SIGNAL,   // 共鸣信号
        MUTUAL_RESONANCE;   // 双向共鸣

        public static NotificationType fromValue(String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Notification type is required");
            }
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
    }

    /**
     * 关联类型枚举
     */
    public enum RefType {
        MERGE_JUDGMENT,     // 合并判断
        DIARY,              // 日记
        ENTITY,             // 实体
        USER,               // 用户
        ANNOUNCEMENT        // 管理员公告
    }
}
