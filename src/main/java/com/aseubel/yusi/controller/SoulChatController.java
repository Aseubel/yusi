package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.common.ratelimit.LimitType;
import com.aseubel.yusi.common.ratelimit.RateLimiter;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.pojo.dto.chat.SendMessageRequest;
import com.aseubel.yusi.pojo.constant.ProductEventName;
import com.aseubel.yusi.pojo.constant.ProductEventSensitivity;
import com.aseubel.yusi.pojo.constant.ProductEventSource;
import com.aseubel.yusi.pojo.entity.SoulMatch;
import com.aseubel.yusi.pojo.entity.SoulMessage;
import com.aseubel.yusi.pojo.entity.SoulConnection;
import com.aseubel.yusi.pojo.entity.ProductEvent;
import com.aseubel.yusi.repository.SoulMatchRepository;
import com.aseubel.yusi.repository.SoulMessageRepository;
import com.aseubel.yusi.service.match.SoulConnectionLifecycleService;
import com.aseubel.yusi.service.event.ProductEventCommand;
import com.aseubel.yusi.service.event.ProductEventService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;

@Auth
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/soul-chat")
public class SoulChatController {

    @Autowired
    private SoulMessageRepository messageRepository;

    @Autowired
    private SoulMatchRepository matchRepository;

    @Autowired
    private SoulConnectionLifecycleService connectionLifecycleService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private WebSocketController webSocketController;

    @Autowired
    private ProductEventService productEventService;

    @PostMapping("/send")
    @Transactional
    @RateLimiter(key = "soul-chat-send", time = 60, count = 60, limitType = LimitType.USER)
    public Response<SoulMessage> sendMessage(@RequestBody SendMessageRequest request) {
        String senderId = UserContext.getUserId();

        // 验证 Match 是否存在且属于该用户
        SoulMatch match = matchRepository.findById(request.getMatchId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "匹配不存在"));

        connectionLifecycleService.assertChatAllowed(match, senderId);
        SoulConnection connection = connectionLifecycleService.findByMatchId(match.getId()).orElse(null);

        String receiverId;
        if (senderId.equals(match.getUserAId())) {
            receiverId = match.getUserBId();
        } else if (senderId.equals(match.getUserBId())) {
            receiverId = match.getUserAId();
        } else {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权发送消息");
        }

        SoulMessage message = SoulMessage.builder()
                .matchId(request.getMatchId())
                .connectionId(connection != null ? connection.getId() : null)
                .senderId(senderId)
                .receiverId(receiverId)
                .content(request.getContent())
                .isRead(false)
                .createTime(LocalDateTime.now())
                .build();

        SoulMessage saved = messageRepository.save(message);
        Map<String, Object> eventPayload = new HashMap<>();
        eventPayload.put("messageId", saved.getId());
        eventPayload.put("matchId", saved.getMatchId());
        if (saved.getConnectionId() != null) {
            eventPayload.put("connectionId", saved.getConnectionId());
        }
        ProductEvent event = productEventService.record(ProductEventCommand.builder()
                .eventName(ProductEventName.CHAT_MESSAGE_CREATED.value())
                .source(ProductEventSource.CHAT.code())
                .sensitivity(ProductEventSensitivity.RESTRICTED.name())
                .userId(senderId)
                .actorUserId(senderId)
                .matchId(saved.getMatchId())
                .connectionId(saved.getConnectionId())
                .idempotencyKey("soul-message:" + saved.getId())
                .scopeUserIds(Set.of(match.getUserAId(), match.getUserBId()))
                .payload(eventPayload)
                .build());
        saved.setSourceEventId(event.getEventId());
        saved = messageRepository.save(saved);

        // 更新发送者在线状态
        webSocketController.updateUserStatus(senderId, request.getMatchId(), true);

        // 广播消息到 WebSocket 主题
        messagingTemplate.convertAndSend("/topic/soul-chat/" + request.getMatchId(), saved);

        return Response.success(saved);
    }

    @GetMapping("/history")
    public Response<List<SoulMessage>> getHistory(@RequestParam Long matchId) {
        String userId = UserContext.getUserId();

        // 验证权限
        SoulMatch match = matchRepository.findById(matchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "匹配不存在"));

        if (!userId.equals(match.getUserAId()) && !userId.equals(match.getUserBId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看消息");
        }

        return Response.success(messageRepository.findByMatchIdOrderByCreateTimeAsc(matchId));
    }

    @PostMapping("/read")
    @RateLimiter(key = "soul-chat-read", time = 60, count = 60, limitType = LimitType.USER)
    public Response<Void> markAsRead(@RequestBody SendMessageRequest request) {
        String userId = UserContext.getUserId();
        messageRepository.markAsRead(request.getMatchId(), userId);
        return Response.success();
    }

    @GetMapping("/unread/count")
    public Response<Long> getUnreadCount() {
        String userId = UserContext.getUserId();
        return Response.success(messageRepository.countByReceiverIdAndIsReadFalse(userId));
    }
}
