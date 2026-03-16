package com.aseubel.yusi.controller;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.common.ratelimit.LimitType;
import com.aseubel.yusi.common.exception.AiLockException;
import com.aseubel.yusi.common.ratelimit.RateLimiter;
import com.aseubel.yusi.common.utils.SensitiveWordUtils;
import com.aseubel.yusi.repository.ChatMemoryMessageRepository;
import com.aseubel.yusi.config.ai.PersistentChatMemoryStore;
import com.aseubel.yusi.pojo.dto.chat.ChatRequest;
import com.aseubel.yusi.pojo.entity.ChatMemoryMessage;
import com.aseubel.yusi.service.ai.AiLockService;
import com.aseubel.yusi.service.ai.model.ModelRouteContext;
import com.aseubel.yusi.service.ai.model.ModelRouteContextHolder;
import com.aseubel.yusi.service.diary.Assistant;
import com.aseubel.yusi.service.oss.OssService;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
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
import java.net.URI;
import java.util.List;
import java.util.Locale;
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
    private ChatMemoryMessageRepository chatMemoryMessageRepository;

    @Autowired
    private PersistentChatMemoryStore chatMemoryStore;

    @Autowired
    private OssService ossService;

    private final ThreadPoolTaskExecutor threadPoolExecutor;

    private final SensitiveWordUtils sensitiveWordUtils;

    @Auth
    @GetMapping("/chat/history")
    public Response<List<Map<String, Object>>> getChatHistory() {
        String userId = UserContext.getUserId();
        List<ChatMemoryMessage> messages = 
                chatMemoryMessageRepository.findByMemoryIdOrderByCreatedAtAsc(userId);

        List<Map<String, Object>> history = messages.stream()
                .map(chatMemoryStore::toChatMessage)
                .filter(msg -> msg instanceof UserMessage || msg instanceof AiMessage)
                .map(msg -> {
                    Map<String, Object> item = new java.util.HashMap<>();
                    item.put("role", msg instanceof UserMessage ? "user" : "assistant");
                    
                    if (msg instanceof UserMessage userMsg) {
                        item.put("content", userMsg.singleText());
                        List<Image> images = userMsg.images();
                        if (images != null && !images.isEmpty()) {
                            List<String> imageUrls = images.stream()
                                    .map(img -> img.url() != null ? img.url().toString() : null)
                                    .filter(java.util.Objects::nonNull)
                                    .collect(Collectors.toList());
                            if (!imageUrls.isEmpty()) {
                                item.put("images", imageUrls);
                            }
                        }
                    } else if (msg instanceof AiMessage aiMsg) {
                        item.put("content", aiMsg.text() != null ? aiMsg.text() : "");
                    }
                    
                    return item;
                })
                .collect(Collectors.toList());

        return Response.success(history);
    }

    @Auth
    @RateLimiter(key = "chatStream", time = 60, count = 20, limitType = LimitType.USER)
    @PostMapping(value = "/chat/stream", produces = "text/event-stream")
    public SseEmitter chatStream(@RequestBody ChatRequest request,
            @RequestHeader(value = "Accept-Language", required = false) String language) {
        String userId = UserContext.getUserId();
        String message = request.getMessage();
        List<String> images = request.getImages();

        if (!aiLockService.tryAcquireLock(userId)) {
            throw new AiLockException("您有一个AI请求正在处理中，请等待完成后再试");
        }

        SseEmitter emitter = new SseEmitter(180000L);

        threadPoolExecutor.execute(() -> {
            try {
                String violationMessage = sensitiveWordUtils.checkAndHandleViolation(userId, message);
                if (violationMessage != null) {
                    emitter.send(SseEmitter.event().data(violationMessage));
                    emitter.complete();
                    return;
                }

                String sandwichContent = String.format(PersistentChatMemoryStore.SANDWITCH_TEMPLATE, message);
                
                UserMessage userMessage = buildUserMessage(sandwichContent, images);
                
                ModelRouteContextHolder.set(ModelRouteContext.builder()
                        .language(normalizeLanguage(language))
                        .scene("chat")
                        .build());

                TokenStream tokenStream = diaryAssistant.chatWithMessage(userId, userMessage);
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
                            ModelRouteContextHolder.clear();
                            aiLockService.releaseLock(userId);
                            emitter.complete();
                        })
                        .onError(error -> {
                            UserContext.clear();
                            ModelRouteContextHolder.clear();
                            aiLockService.releaseLock(userId);
                            emitter.completeWithError(error);
                        })
                        .start();
            } catch (Exception e) {
                log.error("Error during AI chat stream", e);
                UserContext.clear();
                ModelRouteContextHolder.clear();
                aiLockService.releaseLock(userId);
                emitter.completeWithError(e);
            }
        });

        emitter.onCompletion(() -> aiLockService.releaseLock(userId));
        emitter.onTimeout(() -> aiLockService.releaseLock(userId));
        emitter.onError(e -> aiLockService.releaseLock(userId));

        return emitter;
    }

    private UserMessage buildUserMessage(String text, List<String> images) {
        if (images == null || images.isEmpty()) {
            return UserMessage.from(text);
        }
        
        List<Image> imageList = images.stream()
                .filter(StrUtil::isNotBlank)
                .map(objectKey -> {
                    String url = ossService.generatePresignedUrl(objectKey);
                    return Image.builder()
                            .url(URI.create(url))
                            .build();
                })
                .collect(Collectors.toList());
        
        if (imageList.isEmpty()) {
            return UserMessage.from(text);
        }
        
        return UserMessage.from(imageList, text);
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "zh";
        }
        String value = language.toLowerCase(Locale.ROOT);
        if (value.startsWith("zh")) {
            return "zh";
        }
        if (value.startsWith("ja")) {
            return "ja";
        }
        if (value.startsWith("en")) {
            return "en";
        }
        return "zh";
    }
}
