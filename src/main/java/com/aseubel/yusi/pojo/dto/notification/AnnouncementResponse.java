package com.aseubel.yusi.pojo.dto.notification;

import com.aseubel.yusi.pojo.entity.NotificationAnnouncement;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AnnouncementResponse {

    private String announcementId;
    private String title;
    private String content;
    private String audience;
    private String status;
    private String publishedBy;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private Long recipientCount;

    public static AnnouncementResponse from(NotificationAnnouncement announcement) {
        return AnnouncementResponse.builder()
                .announcementId(announcement.getAnnouncementId())
                .title(announcement.getTitle())
                .content(announcement.getContent())
                .recipientCount(announcement.getRecipientCount())
                .audience(announcement.getAudienceType())
                .status(announcement.getStatus())
                .publishedBy(announcement.getPublishedBy())
                .publishedAt(announcement.getPublishedAt())
                .createdAt(announcement.getCreatedAt())
                .build();
    }
}
