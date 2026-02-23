package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.pojo.entity.UserNotification;
import com.aseubel.yusi.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Auth
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 获取消息列表（分页）
     */
    @GetMapping
    public Response<Page<UserNotification>> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String userId = UserContext.getUserId();
        return Response.success(notificationService.getNotifications(userId, page, size));
    }

    /**
     * 获取指定类型的消息
     */
    @GetMapping("/type/{type}")
    public Response<List<UserNotification>> getNotificationsByType(@PathVariable String type) {
        String userId = UserContext.getUserId();
        return Response.success(notificationService.getNotificationsByType(userId, type));
    }

    /**
     * 获取未读消息
     */
    @GetMapping("/unread")
    public Response<List<UserNotification>> getUnreadNotifications() {
        String userId = UserContext.getUserId();
        return Response.success(notificationService.getUnreadNotifications(userId));
    }

    /**
     * 获取未读消息数量
     */
    @GetMapping("/unread/count")
    public Response<Long> getUnreadCount() {
        String userId = UserContext.getUserId();
        return Response.success(notificationService.getUnreadCount(userId));
    }

    /**
     * 标记消息为已读
     */
    @PostMapping("/{notificationId}/read")
    public Response<Boolean> markAsRead(@PathVariable Long notificationId) {
        return Response.success(notificationService.markAsRead(notificationId));
    }

    /**
     * 标记所有消息为已读
     */
    @PostMapping("/read-all")
    public Response<Integer> markAllAsRead() {
        String userId = UserContext.getUserId();
        return Response.success(notificationService.markAllAsRead(userId));
    }

    /**
     * 删除消息
     */
    @DeleteMapping("/{notificationId}")
    public Response<Void> deleteNotification(@PathVariable Long notificationId) {
        notificationService.deleteNotification(notificationId);
        return Response.success(null);
    }
}
