package com.aseubel.yusi.pojo.entity;

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
 * 用户统一消息表
 * 用于存储各类通知消息，便于统一消息中心展示
 */
@Data
@Entity
@Builder
@Table(
    name = "user_notification",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_notification_id", columnNames = { "notification_id" })
    },
    indexes = {
        @Index(name = "idx_notification_user", columnList = "user_id"),
        @Index(name = "idx_notification_user_read", columnList = "user_id, is_read"),
        @Index(name = "idx_notification_user_type", columnList = "user_id, type"),
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
     * 消息类型: MERGE_SUGGESTION/SYSTEM/REMINDER/ANNOUNCEMENT
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

    @Column(name = "extra_data", columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    private String extraData;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @PrePersist
    public void prePersist() {
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
        ANNOUNCEMENT        // 公告
    }

    /**
     * 关联类型枚举
     */
    public enum RefType {
        MERGE_JUDGMENT,     // 合并判断
        DIARY,              // 日记
        ENTITY,             // 实体
        USER                // 用户
    }
}
