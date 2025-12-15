package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.service.ai.Assistant;
import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequestMapping("/api/ai")
@Auth
@CrossOrigin("*")
public class AiController {

    @Autowired
    private Assistant assistant;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @GetMapping(value = "/chat/stream", produces = "text/event-stream")
    public SseEmitter chatStream(@RequestParam String message) {
        // Set timeout to 3 minutes
        SseEmitter emitter = new SseEmitter(180000L);
        String userId = UserContext.getUserId();

        executor.execute(() -> {
            try {
                TokenStream tokenStream = assistant.chat(userId, message);
                tokenStream
                    .onNext(token -> {
                        try {
                            emitter.send(SseEmitter.event().data(token));
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .onComplete(response -> {
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
