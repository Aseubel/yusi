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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;

/** Durable publication record for administrator-authored announcements. */
@Data
@Entity
@Builder
@Table(
        name = "notification_announcement",
        indexes = {
                @Index(name = "idx_announcement_status_published", columnList = "status, published_at"),
                @Index(name = "idx_announcement_publisher", columnList = "published_by")
        }
)
@DynamicInsert
@DynamicUpdate
@NoArgsConstructor
@AllArgsConstructor
public class NotificationAnnouncement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "announcement_id", nullable = false, length = 64, unique = true)
    private String announcementId;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "recipient_count", nullable = false)
    @Builder.Default
    private Long recipientCount = 0L;

    @Column(name = "audience_type", nullable = false, length = 32)
    private String audienceType;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "published_by", nullable = false, length = 64)
    private String publishedBy;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (announcementId == null) {
            announcementId = IdUtil.fastSimpleUUID();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (publishedAt == null) {
            publishedAt = createdAt;
        }
        if (recipientCount == null) {
            recipientCount = 0L;
        }
        if (audienceType == null) {
            audienceType = AudienceType.ALL.name();
        }
        if (status == null) {
            status = Status.PUBLISHED.name();
        }
    }

    public enum AudienceType {
        ALL
    }

    public enum Status {
        PUBLISHED
    }
}
