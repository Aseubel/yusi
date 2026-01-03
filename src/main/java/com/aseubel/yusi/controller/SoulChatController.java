package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.pojo.dto.chat.SendMessageRequest;
import com.aseubel.yusi.pojo.entity.SoulMatch;
import com.aseubel.yusi.pojo.entity.SoulMessage;
import com.aseubel.yusi.repository.SoulMatchRepository;
import com.aseubel.yusi.repository.SoulMessageRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Auth
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/soul-chat")
@CrossOrigin("*")
public class SoulChatController {

    @Autowired
    private SoulMessageRepository messageRepository;

    @Autowired
    private SoulMatchRepository matchRepository;

    @PostMapping("/send")
    public Response<SoulMessage> sendMessage(@RequestBody SendMessageRequest request) {
        String senderId = UserContext.getUserId();
        
        // 验证 Match 是否存在且属于该用户
        SoulMatch match = matchRepository.findById(request.getMatchId())
                .orElseThrow(() -> new RuntimeException("匹配不存在"));
        
        if (!Boolean.TRUE.equals(match.getIsMatched())) {
            throw new RuntimeException("尚未建立连接，无法发送消息");
        }

        String receiverId;
        if (senderId.equals(match.getUserAId())) {
            receiverId = match.getUserBId();
        } else if (senderId.equals(match.getUserBId())) {
            receiverId = match.getUserAId();
        } else {
            throw new RuntimeException("无权发送消息");
        }

        SoulMessage message = SoulMessage.builder()
                .matchId(request.getMatchId())
                .senderId(senderId)
                .receiverId(receiverId)
                .content(request.getContent())
                .isRead(false)
                .createTime(LocalDateTime.now())
                .build();

        return Response.success(messageRepository.save(message));
    }

    @GetMapping("/history")
    public Response<List<SoulMessage>> getHistory(@RequestParam Long matchId) {
        String userId = UserContext.getUserId();
        
        // 验证权限
        SoulMatch match = matchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("匹配不存在"));
        
        if (!userId.equals(match.getUserAId()) && !userId.equals(match.getUserBId())) {
            throw new RuntimeException("无权查看消息");
        }

        return Response.success(messageRepository.findByMatchIdOrderByCreateTimeAsc(matchId));
    }

    @PostMapping("/read")
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
