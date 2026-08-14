package com.aseubel.yusi.service.notification;

import cn.hutool.core.util.IdUtil;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.pojo.dto.notification.AnnouncementResponse;
import com.aseubel.yusi.pojo.dto.notification.PublishAnnouncementRequest;
import com.aseubel.yusi.pojo.constant.ProductEventName;
import com.aseubel.yusi.pojo.constant.ProductEventSensitivity;
import com.aseubel.yusi.pojo.constant.ProductEventSource;
import com.aseubel.yusi.pojo.constant.SecurityAuditAction;
import com.aseubel.yusi.pojo.constant.SecurityAuditDetailKeys;
import com.aseubel.yusi.pojo.constant.SecurityAuditOperation;
import com.aseubel.yusi.pojo.constant.SecurityAuditOutcome;
import com.aseubel.yusi.pojo.constant.SecurityAuditReasonCode;
import com.aseubel.yusi.pojo.constant.SecurityAuditResourceType;
import com.aseubel.yusi.pojo.entity.NotificationAnnouncement;
import com.aseubel.yusi.pojo.entity.ProductEvent;
import com.aseubel.yusi.pojo.entity.UserNotification;
import com.aseubel.yusi.redis.annotation.QueryCache;
import com.aseubel.yusi.redis.annotation.UpdateCache;
import com.aseubel.yusi.repository.NotificationAnnouncementRepository;
import com.aseubel.yusi.repository.UserNotificationRepository;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.service.event.ProductEventCommand;
import com.aseubel.yusi.service.event.ProductEventService;
import com.aseubel.yusi.service.security.SecurityAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final UserNotificationRepository notificationRepository;
    private final NotificationAnnouncementRepository announcementRepository;
    private final UserRepository userRepository;
    private final ProductEventService productEventService;
    private final SecurityAuditService securityAuditService;

    /**
     * Creates a typed notification. Runtime producers should use this overload
     * so a new notification type cannot silently drift from the API contract.
     */
    @UpdateCache(key = "'notifications:user:' + #userId + ':*'", evictOnly = true)
    public UserNotification createNotification(String userId, UserNotification.NotificationType type,
                                                String title, String content, String refType, String refId,
                                                String extraData) {
        return createNotificationInternal(userId, type, title, content, refType, refId, extraData,
                null, null);
    }

    /** Creates a notification linked to a durable product event. */
    @UpdateCache(key = "'notifications:user:' + #userId + ':*'", evictOnly = true)
    public UserNotification createNotification(String userId, UserNotification.NotificationType type,
                                                String title, String content, String refType, String refId,
                                                String extraData, String sourceEventId) {
        return createNotificationInternal(userId, type, title, content, refType, refId, extraData,
                sourceEventId, null);
    }

    private UserNotification createNotificationInternal(String userId, UserNotification.NotificationType type,
                                                String title, String content, String refType, String refId,
                                                String extraData, String sourceEventId, String announcementId) {
        String notificationId = IdUtil.fastSimpleUUID();
        Map<String, Object> eventPayload = new HashMap<>();
        eventPayload.put("notificationId", notificationId);
        eventPayload.put("notificationType", type.name());
        if (refType != null) {
            eventPayload.put("refType", refType);
        }
        if (refId != null) {
            eventPayload.put("refId", refId);
        }
        ProductEvent event = productEventService.record(ProductEventCommand.builder()
                .eventName(ProductEventName.NOTIFICATION_CREATED.value())
                .source(ProductEventSource.NOTIFICATION.code())
                .sensitivity(ProductEventSensitivity.LOW.name())
                .userId(userId)
                .idempotencyKey("notification:" + notificationId)
                .scopeUserIds(Set.of(userId))
                .payload(eventPayload)
                .build());
        UserNotification notification = UserNotification.builder()
                .notificationId(notificationId)
                .userId(userId)
                .type(type.name())
                .title(title)
                .content(content)
                .refType(refType)
                .refId(refId)
                .announcementId(announcementId)
                .sourceEventId(sourceEventId != null ? sourceEventId : event.getEventId())
                .extraData(extraData)
                .isRead(false)
                .build();
        return notificationRepository.save(notification);
    }

    /**
     * 创建合并建议消息
     */
    @UpdateCache(key = "'notifications:user:' + #userId + ':*'", evictOnly = true)
    public UserNotification createMergeSuggestionNotification(String userId, Long judgmentId,
                                                               String nameA, String nameB, String type) {
        String title = "发现可能重复的实体";
        String content = String.format("\"%s\" 和 \"%s\" 可能是同一%s", nameA, nameB, getTypeLabel(type));
        return createNotification(userId,
                UserNotification.NotificationType.MERGE_SUGGESTION,
                title, content,
                UserNotification.RefType.MERGE_JUDGMENT.name(),
                String.valueOf(judgmentId),
                null);
    }

    /**
     * 创建系统通知
     */
    @UpdateCache(key = "'notifications:user:' + #userId + ':*'", evictOnly = true)
    public UserNotification createSystemNotification(String userId, String title, String content) {
        return createNotification(userId,
                UserNotification.NotificationType.SYSTEM,
                title, content,
                null, null, null);
    }

    /**
     * Publishes an administrator-authored announcement and materializes one
     * inbox item per current user. The publication record is kept separately
     * from inbox state so read/delete actions never mutate the source content.
     */
    @Transactional(rollbackFor = Exception.class)
    @UpdateCache(key = "'notifications:user:*'", evictOnly = true)
    public AnnouncementResponse publishAnnouncement(PublishAnnouncementRequest request, String publisherId) {
        if (request == null || request.getTitle() == null || request.getContent() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "公告标题和内容不能为空");
        }
        String title = request.getTitle().trim();
        String content = request.getContent().trim();
        if (title.isBlank() || content.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "公告标题和内容不能为空");
        }
        if (title.length() > 120 || content.length() > 5000) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "公告内容长度超出限制");
        }
        if (publisherId == null || publisherId.isBlank()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "发布者身份无效");
        }
        String audience = request.getAudience() == null || request.getAudience().isBlank()
                ? NotificationAnnouncement.AudienceType.ALL.name()
                : request.getAudience().trim().toUpperCase(Locale.ROOT);
        if (!NotificationAnnouncement.AudienceType.ALL.name().equals(audience)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "暂不支持该公告受众类型");
        }

        List<String> userIds = userRepository.findAllUserIds().stream()
                .filter(userId -> userId != null && !userId.isBlank())
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            throw new BusinessException(ErrorCode.OPERATION_FAILED, "当前没有可接收公告的用户");
        }

        LocalDateTime publishedAt = LocalDateTime.now();
        NotificationAnnouncement announcement = announcementRepository.save(NotificationAnnouncement.builder()
                .announcementId(IdUtil.fastSimpleUUID())
                .title(title)
                .content(content)
                .recipientCount((long) userIds.size())
                .audienceType(audience)
                .status(NotificationAnnouncement.Status.PUBLISHED.name())
                .publishedBy(publisherId)
                .publishedAt(publishedAt)
                .createdAt(publishedAt)
                .build());

        Set<String> eventScope = new LinkedHashSet<>(userIds);
        eventScope.add(publisherId);
        ProductEvent publicationEvent = productEventService.record(ProductEventCommand.builder()
                .eventName(ProductEventName.NOTIFICATION_ANNOUNCEMENT_PUBLISHED.value())
                .source(ProductEventSource.NOTIFICATION.code())
                .sensitivity(ProductEventSensitivity.LOW.name())
                .userId(publisherId)
                .actorUserId(publisherId)
                .idempotencyKey("announcement:" + announcement.getAnnouncementId())
                .scopeUserIds(eventScope)
                .payload(Map.of("announcementId", announcement.getAnnouncementId(),
                        "recipientCount", userIds.size()))
                .build());

        List<UserNotification> inboxItems = new ArrayList<>(userIds.size());
        for (String userId : userIds) {
            inboxItems.add(UserNotification.builder()
                    .notificationId(IdUtil.fastSimpleUUID())
                    .userId(userId)
                    .type(UserNotification.NotificationType.ANNOUNCEMENT.name())
                    .title(title)
                    .content(content)
                    .refType(UserNotification.RefType.ANNOUNCEMENT.name())
                    .refId(announcement.getAnnouncementId())
                    .announcementId(announcement.getAnnouncementId())
                    .sourceEventId(publicationEvent.getEventId())
                    .isRead(false)
                    .createdAt(publishedAt)
                    .build());
        }
        notificationRepository.saveAll(inboxItems);

        if (securityAuditService != null) {
            securityAuditService.recordAdmin(SecurityAuditAction.ANNOUNCEMENT_PUBLISHED, publisherId, null,
                    SecurityAuditResourceType.ANNOUNCEMENT, announcement.getAnnouncementId(),
                    SecurityAuditOutcome.SUCCESS, SecurityAuditReasonCode.ADMIN_MUTATION,
                    Map.of(
                            SecurityAuditDetailKeys.OPERATION, SecurityAuditOperation.PUBLISH.name(),
                            SecurityAuditDetailKeys.AUDIENCE, audience,
                            SecurityAuditDetailKeys.COUNT, String.valueOf(userIds.size())));
        }

        return AnnouncementResponse.from(announcement);
    }

    public Page<AnnouncementResponse> getAnnouncements(int page, int size) {
        Pageable pageable = PageRequest.of(normalizePage(page), normalizeSize(size));
        return announcementRepository.findAllByOrderByPublishedAtDescIdDesc(pageable)
                .map(AnnouncementResponse::from);
    }

    /**
     * 获取用户消息列表（分页）
     */
    @QueryCache(key = "'notifications:user:' + #userId + ':list:' + #page + ':' + #size + ':' + (#type == null || #type.isBlank() ? 'ALL' : #type.trim().toUpperCase())", ttl = 30)
    public Page<UserNotification> getNotifications(String userId, int page, int size, String type) {
        Pageable pageable = PageRequest.of(normalizePage(page), normalizeSize(size));
        String normalizedType = normalizeType(type);
        if (normalizedType == null) {
            return notificationRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable);
        }
        return notificationRepository.findByUserIdAndTypeOrderByCreatedAtDescIdDesc(userId, normalizedType, pageable);
    }

    /**
     * 获取未读消息
     */
    @QueryCache(key = "'notifications:user:' + #userId + ':unread'", ttl = 10)
    public List<UserNotification> getUnreadNotifications(String userId) {
        return notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDescIdDesc(userId);
    }

    /**
     * 获取未读消息数量
     */
    @QueryCache(key = "'notifications:user:' + #userId + ':unread-count'", ttl = 10)
    public long getUnreadCount(String userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    /**
     * 标记消息为已读
     */
    @Transactional
    @UpdateCache(key = "'notifications:user:' + #userId + ':*'", evictOnly = true)
    public boolean markAsRead(String userId, Long notificationId) {
        boolean result = notificationRepository.markAsRead(notificationId, userId) > 0;
        return result;
    }

    /**
     * 标记所有消息为已读
     */
    @Transactional
    @UpdateCache(key = "'notifications:user:' + #userId + ':*'", evictOnly = true)
    public int markAllAsRead(String userId) {
        return notificationRepository.markAllAsRead(userId);
    }

    /**
     * 删除消息
     */
    @Transactional
    @UpdateCache(key = "'notifications:user:' + #userId + ':*'", evictOnly = true)
    public void deleteNotification(String userId, Long notificationId) {
        notificationRepository.deleteByIdAndUserId(notificationId, userId);
    }

    private String getTypeLabel(String type) {
        return switch (type) {
            case "Person" -> "人物";
            case "Location" -> "地点";
            case "Organization" -> "组织";
            case "Event" -> "事件";
            case "Concept" -> "概念";
            default -> type;
        };
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return UserNotification.NotificationType.fromValue(type).name();
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的通知类型");
        }
    }

    private int normalizePage(int page) {
        if (page < 0 || page > 10000) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "页码不合法");
        }
        return page;
    }

    private int normalizeSize(int size) {
        if (size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "分页大小必须在1到100之间");
        }
        return size;
    }
}
