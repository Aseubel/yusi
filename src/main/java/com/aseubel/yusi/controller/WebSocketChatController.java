package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.pojo.contant.RoomStatus;
import com.aseubel.yusi.pojo.dto.chat.RoomChatRequest;
import com.aseubel.yusi.pojo.entity.RoomMessage;
import com.aseubel.yusi.pojo.entity.SituationRoom;
import com.aseubel.yusi.repository.RoomMessageRepository;
import com.aseubel.yusi.repository.SituationRoomRepository;
import com.aseubel.yusi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.time.LocalDateTime;

@Slf4j
@Controller
@RequiredArgsConstructor
@CrossOrigin("*")
public class WebSocketChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final RoomMessageRepository messageRepository;
    private final SituationRoomRepository roomRepository;
    private final UserRepository userRepository;

    /**
     * 处理 WebSocket 发送的消息
     * 客户端发送到 /app/room-chat/send
     */
    @MessageMapping("/room-chat/send")
    public void sendMessage(@Payload RoomChatRequest request) {
        // 注意：WebSocket 中可能无法直接通过 UserContext 获取用户ID，
        // 需前端在 payload 中传 userId，或者在握手拦截器中处理 Auth。
        // 为简化，这里假设前端在 request 中多传一个 senderId 字段，或者验证 Token。
        // 但 MVP 阶段，我们先依赖前端传 userId，并在 STOMP 连接时不做严格鉴权(需评估风险)。
        // 更好的做法是：HandshakeInterceptor 解析 Token 并放入 Session Attributes。

        // 由于 RoomChatRequest 原本只有 roomCode 和 content，我们需要一种方式获取发送者。
        // 暂时简单处理：前端通过 Header 传 Token 比较复杂，
        // 建议前端在 Message Body 里带上 userId (虽然不安全，但先跑通功能)。
        // 实际上后端 Controller 还是用 UserContext.getUserId() ?
        // STOMP 线程通常没有 HTTP Request 上下文，所以 UserContext.getUserId() 会失效。

        // 临时方案：让 request 包含 userId。这里先用一个 ThreadLocalHack 或者要求 request 扩展。
        // 但修改 DTO 比较麻烦。
        // 替代方案：不做服务端 Auth check (仅在 HTTP 握手时做)，这是 WebSocket 常见弱点。
        // 我们假设 userId 存在于 request 中 (需要修改 DTO 或用 Map)。

        // 让我们看看 RoomChatRequest 定义...
        // 它可能没有 userId。
        // 策略：前端发送时，把 userId 拼在 content 前面？不优雅。
        // 策略：修改 payload 为自定义 Map。
    }

    // 重新思考：Spring Security WebSocket 支持 @AuthenticationPrincipal。
    // 但项目似乎是自定义拦截器 Auth。
    // 简单起见，我们在 WebSocketConfig 添加拦截器？
    // 或者，最简单：前端直接调 HTTP 发送 (RoomChatController.sendMessage)，
    // 然后由 HTTP Controller 负责通过 SimpMessagingTemplate 广播推送到 topic。
    // 这样既保留了现有的 Auth 逻辑，又实现了实时推送。
    // 这里只保留订阅功能。

    // 没错，这是一个混合模式：Send via HTTP (REST), Receive via WebSocket (STOMP).
    // 这样不需要改动 Auth 逻辑，也不需要 WebSocketChatController 处理发送。
}
