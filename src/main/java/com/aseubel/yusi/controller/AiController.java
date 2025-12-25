package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.common.ratelimit.LimitType;
import com.aseubel.yusi.common.ratelimit.RateLimiter;
import com.aseubel.yusi.service.diary.Assistant;
import dev.langchain4j.service.TokenStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Auth
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai")
@CrossOrigin("*")
public class AiController {

    @Autowired
    private Assistant diaryAssistant;

    private final ThreadPoolTaskExecutor executor;

    @RateLimiter(key = "chatStream", time = 60, count = 20, limitType = LimitType.USER)
    @GetMapping(value = "/chat/stream", produces = "text/event-stream")
    public SseEmitter chatStream(@RequestParam String message) {
        // Set timeout to 3 minutes
        SseEmitter emitter = new SseEmitter(180000L);
        String userId = UserContext.getUserId();

        executor.execute(() -> {
            try {
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
                         emitter.complete();
                    })
                    .onError(emitter::completeWithError)
                    .start();
            } catch (Exception e) {
                log.error("Error during AI chat stream", e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
