package com.aseubel.yusi.service.notification;

import cn.hutool.core.util.IdUtil;
import com.aseubel.yusi.pojo.entity.UserNotification;
import com.aseubel.yusi.redis.annotation.QueryCache;
import com.aseubel.yusi.redis.annotation.UpdateCache;
import com.aseubel.yusi.repository.UserNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final UserNotificationRepository notificationRepository;

    /**
     * 创建消息
     */
    @UpdateCache(key = "'notifications:user:' + #userId + ':*'", evictOnly = true)
    public UserNotification createNotification(String userId, String type, String title,
                                                String content, String refType, String refId,
                                                String extraData) {
        UserNotification notification = UserNotification.builder()
                .notificationId(IdUtil.fastSimpleUUID())
                .userId(userId)
                .type(type)
                .title(title)
                .content(content)
                .refType(refType)
                .refId(refId)
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
                UserNotification.NotificationType.MERGE_SUGGESTION.name(),
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
                UserNotification.NotificationType.SYSTEM.name(),
                title, content,
                null, null, null);
    }

    /**
     * 获取用户消息列表（分页）
     */
    @QueryCache(key = "'notifications:list:' + #userId + ':' + #page + ':' + #size", ttl = 30)
    public Page<UserNotification> getNotifications(String userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    /**
     * 获取用户指定类型的消息
     */
    @QueryCache(key = "'notifications:type:' + #userId + ':' + #type", ttl = 30)
    public List<UserNotification> getNotificationsByType(String userId, String type) {
        return notificationRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, type);
    }

    /**
     * 获取未读消息
     */
    @QueryCache(key = "'notifications:unread:' + #userId", ttl = 10)
    public List<UserNotification> getUnreadNotifications(String userId) {
        return notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId);
    }

    /**
     * 获取未读消息数量
     */
    @QueryCache(key = "'notifications:unread:count:' + #userId", ttl = 10)
    public long getUnreadCount(String userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    /**
     * 标记消息为已读
     */
    @Transactional
    @UpdateCache(key = "'notifications:user:' + #userId + ':*'", evictOnly = true)
    public boolean markAsRead(String userId, Long notificationId) {
        boolean result = notificationRepository.markAsRead(notificationId) > 0;
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
        notificationRepository.deleteById(notificationId);
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
}
