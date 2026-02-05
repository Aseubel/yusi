package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.redis.annotation.QueryCache;
import com.aseubel.yusi.redis.annotation.UpdateCache;
import com.aseubel.yusi.pojo.contant.RoomStatus;
import com.aseubel.yusi.pojo.dto.chat.RoomChatRequest;
import com.aseubel.yusi.pojo.entity.RoomMessage;
import com.aseubel.yusi.pojo.entity.SituationRoom;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.RoomMessageRepository;
import com.aseubel.yusi.repository.SituationRoomRepository;
import com.aseubel.yusi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 房间临时聊天室
 * 与房间生命周期绑定：
 * - WAITING/IN_PROGRESS 状态可发送消息
 * - COMPLETED/CANCELLED 状态不可发送，但可查看历史
 */
@Auth
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/room-chat")
@CrossOrigin("*")
public class RoomChatController {

    private final RoomMessageRepository messageRepository;
    private final SituationRoomRepository roomRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 发送消息
     */
    @UpdateCache(cacheNames = "room:chat", key = "#request.roomCode")
    @PostMapping("/send")
    public Response<RoomMessage> sendMessage(@RequestBody RoomChatRequest request) {
        String userId = UserContext.getUserId();
        String roomCode = request.getRoomCode();

        // 验证房间存在且用户是成员
        SituationRoom room = roomRepository.findById(roomCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "房间不存在"));

        if (!room.getMembers().contains(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "你不是该房间的成员");
        }

        // 验证房间状态允许发送消息
        if (room.getStatus() == RoomStatus.COMPLETED || room.getStatus() == RoomStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "房间已结束，无法发送消息");
        }

        // 限制消息长度
        String content = request.getContent();
        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "消息内容不能为空");
        }
        if (content.length() > 500) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "消息内容过长，最多500字");
        }

        // 获取用户昵称
        String senderName = userRepository.findByUserId(userId).getUserName();
        if (senderName == null || senderName.isEmpty()) {
            senderName = "用户" + userId.substring(0, 4);
        }

        RoomMessage message = RoomMessage.builder()
                .roomCode(roomCode)
                .senderId(userId)
                .senderName(senderName)
                .content(content.trim())
                .createdAt(LocalDateTime.now())
                .build();

        RoomMessage saved = messageRepository.save(message);

        // 广播消息
        messagingTemplate.convertAndSend("/topic/room/" + roomCode, saved);

        return Response.success(saved);
    }

    /**
     * 获取房间聊天历史
     */
    @QueryCache(cacheNames = "room:chat", key = "#roomCode", ttl = 3600)
    @GetMapping("/history")
    public Response<List<RoomMessage>> getHistory(@RequestParam String roomCode) {
        String userId = UserContext.getUserId();

        // 验证房间存在且用户是成员
        SituationRoom room = roomRepository.findById(roomCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "房间不存在"));

        if (!room.getMembers().contains(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "你不是该房间的成员");
        }

        List<RoomMessage> messages = messageRepository.findByRoomCodeOrderByCreatedAtAsc(roomCode);
        return Response.success(messages);
    }

    /**
     * 获取增量消息（用于轮询更新）
     */
    @GetMapping("/poll")
    public Response<List<RoomMessage>> pollMessages(
            @RequestParam String roomCode,
            @RequestParam(required = false) String after) {
        String userId = UserContext.getUserId();

        // 验证房间存在且用户是成员
        SituationRoom room = roomRepository.findById(roomCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "房间不存在"));

        if (!room.getMembers().contains(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "你不是该房间的成员");
        }

        if (after == null || after.isEmpty()) {
            // 首次拉取，返回最近50条
            List<RoomMessage> messages = messageRepository.findRecentMessages(roomCode, 50);
            Collections.reverse(messages); // 反转为时间升序
            return Response.success(messages);
        }

        // 增量拉取
        LocalDateTime afterTime = LocalDateTime.parse(after);
        List<RoomMessage> messages = messageRepository.findByRoomCodeAndCreatedAtAfterOrderByCreatedAtAsc(roomCode,
                afterTime);
        return Response.success(messages);
    }
}
