package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.common.exception.AiLockException;
import com.aseubel.yusi.common.ratelimit.LimitType;
import com.aseubel.yusi.common.ratelimit.RateLimiter;
import com.aseubel.yusi.pojo.dto.ai.DiaryChatRequest;
import com.aseubel.yusi.service.ai.AiLockService;
import com.aseubel.yusi.service.ai.DiaryAssistantFactory;
import com.aseubel.yusi.service.diary.Assistant;
import dev.langchain4j.service.TokenStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai")
@CrossOrigin("*")
public class AiController {

    @Autowired
    private DiaryAssistantFactory diaryAssistantFactory;

    @Autowired
    private AiLockService aiLockService;

    private final ThreadPoolTaskExecutor threadPoolExecutor;

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
                // 为当前请求创建专属 Assistant 实例，确保 Tool 能够获取到正确的 userId
                Assistant assistant = diaryAssistantFactory.createAssistant(userId);
                TokenStream tokenStream = assistant.chat(userId, message);
                tokenStream
                        .onPartialResponse(token -> {
                            try {
                                emitter.send(SseEmitter.event().data(token));
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        })
                        .onCompleteResponse(response -> {
                            aiLockService.releaseLock(userId);
                            emitter.complete();
                        })
                        .onError(error -> {
                            aiLockService.releaseLock(userId);
                            emitter.completeWithError(error);
                        })
                        .start();
            } catch (Exception e) {
                log.error("Error during AI chat stream", e);
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
