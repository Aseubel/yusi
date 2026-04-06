package com.aseubel.yusi.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Controller
@RequiredArgsConstructor
public class WebSocketController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // 存储匹配的在线状态
    private final Map<Long, Map<String, Boolean>> matchOnlineStatus = new ConcurrentHashMap<>();

    /**
     * 处理在线状态请求
     */
    @MessageMapping("/soul-chat/status")
    public void handleStatusRequest(StatusRequest request) {
        Long matchId = request.getMatchId();
        // 广播当前在线状态
        broadcastStatus(matchId);
    }

    /**
     * 广播在线状态到匹配的所有用户
     */
    public void broadcastStatus(Long matchId) {
        Map<String, Boolean> statusMap = matchOnlineStatus.getOrDefault(matchId, new ConcurrentHashMap<>());
        boolean isAnyOnline = statusMap.values().stream().anyMatch(Boolean::valueOf);
        
        StatusResponse response = new StatusResponse();
        response.setMatchId(matchId);
        response.setOnline(isAnyOnline);
        
        messagingTemplate.convertAndSend("/topic/soul-chat/status/" + matchId, response);
    }

    /**
     * 更新用户在线状态
     */
    public void updateUserStatus(String userId, Long matchId, boolean isOnline) {
        matchOnlineStatus.computeIfAbsent(matchId, k -> new ConcurrentHashMap<>())
                .put(userId, isOnline);
        broadcastStatus(matchId);
    }

    @Data
    public static class StatusRequest {
        private Long matchId;
    }

    @Data
    public static class StatusResponse {
        private Long matchId;
        private boolean isOnline;
    }
}