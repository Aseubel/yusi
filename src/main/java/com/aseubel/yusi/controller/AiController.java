package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.common.ratelimit.LimitType;
import com.aseubel.yusi.common.exception.AiLockException;
import com.aseubel.yusi.common.ratelimit.RateLimiter;
import com.aseubel.yusi.config.ai.PersistentChatMemoryStore;
import com.aseubel.yusi.service.ai.AiLockService;
import com.aseubel.yusi.service.diary.Assistant;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.service.TokenStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai")
@CrossOrigin("*")
public class AiController {

    @Autowired
    @Qualifier("diaryAssistant")
    private Assistant diaryAssistant;

    @Autowired
    private AiLockService aiLockService;

    @Autowired
    private PersistentChatMemoryStore chatMemoryStore;

    private final ThreadPoolTaskExecutor threadPoolExecutor;

    /**
     * 获取聊天历史记录
     */
    @Auth
    @GetMapping("/chat/history")
    public Response<List<Map<String, String>>> getChatHistory() {
        String userId = UserContext.getUserId();
        List<ChatMessage> messages = chatMemoryStore.getMessages(userId);

        List<Map<String, String>> history = messages.stream()
                .filter(msg -> msg instanceof UserMessage || msg instanceof AiMessage)
                .map(msg -> {
                    String role = msg instanceof UserMessage ? "user" : "assistant";
                    String content = msg instanceof UserMessage
                            ? ((UserMessage) msg).singleText()
                            : ((AiMessage) msg).text();
                    return Map.of("role", role, "content", content != null ? content : "");
                })
                .collect(Collectors.toList());

        return Response.success(history);
    }

    @Auth
    @RateLimiter(key = "chatStream", time = 60, count = 20, limitType = LimitType.USER)
    @GetMapping(value = "/chat/stream", produces = "text/event-stream")
    public SseEmitter chatStream(@RequestParam String message) {
        String userId = UserContext.getUserId();

        // Try to acquire lock for this user
        if (!aiLockService.tryAcquireLock(userId)) {
            throw new AiLockException("您有一个AI请求正在处理中，请等待完成后再试");
        }

        // Set timeout to 3 minutes
        SseEmitter emitter = new SseEmitter(180000L);

        threadPoolExecutor.execute(() -> {
            try {
                // 在异步线程中设置用户上下文，确保 Tool 能够获取到正确的 userId
                UserContext.setUserId(userId);

                // 使用单例 Assistant，通过 userId 作为 memoryId 实现用户隔离
                TokenStream tokenStream = diaryAssistant.chat(userId, message);
                tokenStream
                        .onPartialResponse(token -> {
                            try {
                                emitter.send(SseEmitter.event().data(token));
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        })
                        .onCompleteResponse(response -> {
                            UserContext.clear();
                            aiLockService.releaseLock(userId);
                            emitter.complete();
                        })
                        .onError(error -> {
                            UserContext.clear();
                            aiLockService.releaseLock(userId);
                            emitter.completeWithError(error);
                        })
                        .start();
            } catch (Exception e) {
                log.error("Error during AI chat stream", e);
                UserContext.clear();
                aiLockService.releaseLock(userId);
                emitter.completeWithError(e);
            }
        });

        // Also release lock if client disconnects
        emitter.onCompletion(() -> aiLockService.releaseLock(userId));
        emitter.onTimeout(() -> aiLockService.releaseLock(userId));
        emitter.onError(e -> aiLockService.releaseLock(userId));

        return emitter;
    }
}
