package com.aseubel.yusi.controller;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.common.ratelimit.LimitType;
import com.aseubel.yusi.common.exception.AiLockException;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.common.ratelimit.RateLimiter;
import com.aseubel.yusi.common.utils.SensitiveWordUtils;
import com.aseubel.yusi.repository.ChatMemoryMessageRepository;
import com.aseubel.yusi.config.ai.PersistentChatMemoryStore;
import com.aseubel.yusi.pojo.dto.agent.AgentPersonaConfigRequest;
import com.aseubel.yusi.pojo.dto.agent.AgentGrowthResponse;
import com.aseubel.yusi.pojo.dto.chat.ChatCancelRequest;
import com.aseubel.yusi.pojo.dto.chat.ChatRequest;
import com.aseubel.yusi.pojo.dto.chat.AgentStreamEvent;
import com.aseubel.yusi.pojo.entity.AgentPersonaConfig;
import com.aseubel.yusi.pojo.entity.ChatMemoryMessage;
import com.aseubel.yusi.pojo.entity.SoulReport;
import com.aseubel.yusi.repository.CognitiveConflictRepository;
import com.aseubel.yusi.repository.SoulReportRepository;
import com.aseubel.yusi.pojo.entity.CognitiveConflict;
import com.aseubel.yusi.service.agent.AgentPersonaConfigService;
import com.aseubel.yusi.service.agent.AgentGrowthService;
import com.aseubel.yusi.service.ai.model.ModelRouteContext;
import com.aseubel.yusi.service.ai.model.ModelRouteContextHolder;
import com.aseubel.yusi.service.ai.runtime.AgentRunTraceService;
import com.aseubel.yusi.service.ai.runtime.AiLockService;
import com.aseubel.yusi.service.ai.runtime.ChatStreamCancellationRegistry;
import com.aseubel.yusi.service.cognition.CognitiveConflictDetector;
import com.aseubel.yusi.service.cognition.MidMemoryFusionService;
import com.aseubel.yusi.service.diary.Assistant;
import com.aseubel.yusi.service.oss.OssService;
import com.aseubel.yusi.pojo.entity.UserNotification;
import com.aseubel.yusi.repository.UserNotificationRepository;
import com.aseubel.yusi.redis.service.IRedisService;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.service.TokenStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.URI;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai")
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

    /**
     * Kept with a default value so direct controller unit tests do not need a
     * Spring context just to exercise the stream lifecycle.
     */
    @Autowired
    private ObjectMapper objectMapper = new ObjectMapper();

    private final ThreadPoolTaskExecutor threadPoolExecutor;

    private final SensitiveWordUtils sensitiveWordUtils;

    @Autowired
    private AgentPersonaConfigService agentPersonaConfigService;

    @Autowired
    private SoulReportRepository soulReportRepository;

    @Autowired
    private AgentGrowthService agentGrowthService;

    @Autowired
    private CognitiveConflictDetector conflictDetector;

    @Autowired
    private MidMemoryFusionService fusionService;

    @Autowired
    private CognitiveConflictRepository conflictRepository;

    @Autowired
    private UserNotificationRepository notificationRepository;

    @Autowired
    private IRedisService redisService;

    @Autowired
    private ChatStreamCancellationRegistry chatStreamCancellationRegistry;

    @Autowired
    private AgentRunTraceService agentRunTraceService;

    @Auth
    @GetMapping("/chat/history")
    public Response<List<Map<String, Object>>> getChatHistory() {
        String userId = UserContext.getUserId();
        List<ChatMemoryMessage> messages = chatMemoryMessageRepository.findByMemoryIdOrderByCreatedAtAsc(userId);

        List<Map<String, Object>> history = messages.stream()
                .map(entity -> {
                    ChatMessage msg = chatMemoryStore.toChatMessage(entity);
                    if (!(msg instanceof UserMessage || msg instanceof AiMessage
                            || msg instanceof ToolExecutionResultMessage)) {
                        return null;
                    }

                    Map<String, Object> item = new java.util.HashMap<>();
                    item.put("role", msg instanceof UserMessage ? "user" : "assistant");
                    item.put("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);

                    if (msg instanceof UserMessage userMsg) {
                        String textContent = userMsg.contents().stream()
                                .filter(c -> c instanceof TextContent)
                                .map(c -> ((TextContent) c).text())
                                .collect(Collectors.joining("\n"));

                        if (textContent.isEmpty() && userMsg.contents().size() == 1
                                && userMsg.contents().get(0) instanceof TextContent) {
                            textContent = ((TextContent) userMsg.contents().get(0)).text();
                        } else if (textContent.isEmpty() && !userMsg.contents().isEmpty()) {
                            // Try to get text from singleText() if we couldn't find any TextContent
                            // directly
                            try {
                                textContent = userMsg.singleText();
                            } catch (Exception e) {
                                // Ignore exception, keep textContent empty
                            }
                        }

                        item.put("content", textContent);

                        List<ImageContent> imageContents = userMsg.contents().stream()
                                .filter(c -> c instanceof ImageContent)
                                .map(c -> (ImageContent) c)
                                .collect(Collectors.toList());
                        if (!imageContents.isEmpty()) {
                            List<String> imageUrls = imageContents.stream()
                                    .map(img -> img.image().url().toString())
                                    .filter(java.util.Objects::nonNull)
                                    .collect(Collectors.toList());
                            if (!imageUrls.isEmpty()) {
                                item.put("images", imageUrls);
                            }
                        } else {
                            // Backup check for images in string format if deserialization failed to map to
                            // ImageContent
                            String dbImagesStr = entity.getImages();
                            if (StrUtil.isNotBlank(dbImagesStr)) {
                                try {
                                    List<String> urls = cn.hutool.json.JSONUtil.toList(dbImagesStr, String.class);
                                    if (!urls.isEmpty()) {
                                        item.put("images", urls);
                                    }
                                } catch (Exception e) {
                                    // ignore JSON parse error
                                }
                            }
                        }
                    } else if (msg instanceof AiMessage aiMsg) {
                        if (aiMsg.text() != null && !aiMsg.text().isEmpty()) {
                            item.put("content", aiMsg.text());
                        }
                    }

                    return item;
                })
                .filter(item -> {
                    if (item == null) return false;
                    Object content = item.get("content");
                    return (content != null && !content.toString().trim().isEmpty()) || item.containsKey("images");
                })
                .collect(Collectors.toList());

        return Response.success(history);
    }

    @Auth
    @RateLimiter(key = "chatStream", time = 60, count = 20, limitType = LimitType.USER)
    @PostMapping(value = "/chat/stream", produces = "text/event-stream")
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        String userId = UserContext.getUserId();
        String requestId = request.getRequestId();
        String message = request.getMessage();
        List<String> images = request.getImages();

        if (StrUtil.isBlank(requestId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "requestId不能为空");
        }

        if (images != null && images.size() > 3) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "图片数量不能超过3张");
        }

        if (!aiLockService.tryAcquireLock(userId)) {
            throw new AiLockException("您有一个AI请求正在处理中，请等待完成后再试");
        }

        traceRunStarted(userId, requestId);

        SseEmitter emitter = new SseEmitter(180000L);
        ChatStreamCancellationRegistry.ChatStreamSession session;
        try {
            session = chatStreamCancellationRegistry.register(userId, requestId, emitter, () -> {
                traceRunCancelled(userId, requestId, "stream_closed");
                UserContext.clear();
                ModelRouteContextHolder.clear();
                aiLockService.releaseLock(userId);
            });
        } catch (RuntimeException exception) {
            aiLockService.releaseLock(userId);
            throw exception;
        }

        emitter.onCompletion(session::cancel);
        emitter.onTimeout(session::cancel);
        emitter.onError(error -> session.cancel());
        sendAgentEvent(emitter, session, AgentStreamEvent.runStarted(requestId));

        try {
            threadPoolExecutor.execute(() -> {
            AtomicReference<String> lastStage = new AtomicReference<>("preparing");
            try {
                if (!session.isActive()) {
                    return;
                }

                String violationMessage = sensitiveWordUtils.checkAndHandleViolation(userId, message);
                if (violationMessage != null) {
                    if (session.isActive()) {
                        traceRunCompleted(userId, requestId);
                        sendAgentEvent(emitter, session, AgentStreamEvent.responseDelta(requestId, violationMessage));
                        sendAgentEvent(emitter, session, AgentStreamEvent.runCompleted(requestId));
                        session.complete();
                    }
                    return;
                }
                if (!session.isActive()) {
                    return;
                }

                emitAgentStage(emitter, session, requestId, lastStage, "thinking");

                // 构建三明治模板内容，用于强调systemprompt
                String sandwichContent = String.format(PersistentChatMemoryStore.SANDWITCH_TEMPLATE, message);
                // 构建图片内容列表
                List<ImageContent> imageContents = buildImageContents(userId, images);
                if (!session.isActive()) {
                    return;
                }

                // 设置模型路由上下文
                ModelRouteContextHolder.set(ModelRouteContext.builder()
                        .requestId(requestId)
                        .runId(requestId)
                        .userId(userId)
                        .scene(PromptKey.CHAT.getKey())
                        .build());

                TokenStream tokenStream = diaryAssistant.chatWithMessage(userId, sandwichContent, imageContents);
                if (!session.isActive()) {
                    return;
                }

                tokenStream
                        .onPartialResponseWithContext((partialResponse, context) -> {
                            if (context != null) {
                                session.bind(context.streamingHandle());
                            }
                            if (!session.isActive()) {
                                return;
                            }
                            try {
                                if (StrUtil.isNotBlank(partialResponse.text())) {
                                    emitAgentStage(emitter, session, requestId, lastStage, "responding");
                                    sendAgentEvent(emitter, session,
                                            AgentStreamEvent.responseDelta(requestId, partialResponse.text()));
                                }
                            } catch (RuntimeException e) {
                                session.complete();
                            }
                        })
                        .onPartialThinkingWithContext((partialThinking, context) -> {
                            if (context != null) {
                                session.bind(context.streamingHandle());
                            }
                            emitAgentStage(emitter, session, requestId, lastStage, "thinking");
                        })
                        .onPartialToolCallWithContext((partialToolCall, context) -> {
                            if (context != null) {
                                session.bind(context.streamingHandle());
                            }
                        })
                        .onRetrieved(contents -> {
                            emitAgentStage(emitter, session, requestId, lastStage, "retrieving");
                        })
                        .onIntermediateResponse(response -> {
                            emitAgentStage(emitter, session, requestId, lastStage, "thinking");
                        })
                        .beforeToolExecution(beforeToolExecution -> {
                            if (beforeToolExecution == null || beforeToolExecution.request() == null) {
                                return;
                            }
                            var requestToExecute = beforeToolExecution.request();
                            String toolName = requestToExecute.name();
                            emitAgentStage(emitter, session, requestId, lastStage, "tool");
                            sendAgentEvent(emitter, session, AgentStreamEvent.toolStarted(
                                    requestId,
                                    requestToExecute.id(),
                                    toolName,
                                    resolveToolSource(toolName)));
                        })
                        .onToolExecuted(toolExecution -> {
                            if (toolExecution == null || toolExecution.request() == null) {
                                return;
                            }
                            var requestToExecute = toolExecution.request();
                            Long durationMs = toolExecution.duration() != null
                                    ? toolExecution.duration().toMillis()
                                    : null;
                            traceRunToolCompleted(session.getUserId(), requestId);
                            sendAgentEvent(emitter, session, AgentStreamEvent.toolCompleted(
                                    requestId,
                                    requestToExecute.id(),
                                    requestToExecute.name(),
                                    resolveToolSource(requestToExecute.name()),
                                    !toolExecution.hasFailed(),
                                    durationMs));
                            emitAgentStage(emitter, session, requestId, lastStage, "thinking");
                        })
                        .onCompleteResponse(response -> {
                            traceRunCompleted(userId, requestId);
                            sendAgentEvent(emitter, session, AgentStreamEvent.runCompleted(requestId));
                            session.complete();
                        })
                        .onError(error -> {
                            traceRunFailed(userId, requestId, "agent_error");
                            sendAgentEvent(emitter, session, AgentStreamEvent.runFailed(requestId));
                            session.complete();
                        })
                        .start();
            } catch (Exception e) {
                log.error("Error during AI chat stream", e);
                traceRunFailed(userId, requestId, "agent_error");
                sendAgentEvent(emitter, session, AgentStreamEvent.runFailed(requestId));
                session.complete();
            } finally {
                UserContext.clear();
                ModelRouteContextHolder.clear();
            }
            });
        } catch (RuntimeException exception) {
            session.complete();
            throw exception;
        }

        return emitter;
    }

    private void emitAgentStage(SseEmitter emitter, ChatStreamCancellationRegistry.ChatStreamSession session,
            String runId, AtomicReference<String> lastStage, String stage) {
        String previous;
        do {
            previous = lastStage.get();
            if (stage.equals(previous)) {
                return;
            }
        } while (!lastStage.compareAndSet(previous, stage));

        traceRunStage(session.getUserId(), runId, stage);
        sendAgentEvent(emitter, session, AgentStreamEvent.stage(runId, stage));
    }

    protected void sendAgentEvent(SseEmitter emitter, ChatStreamCancellationRegistry.ChatStreamSession session,
            AgentStreamEvent event) {
        if (!session.isActive()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                    .name(event.type())
                    .data(objectMapper.writeValueAsString(event)));
        } catch (IOException | IllegalStateException e) {
            session.complete();
        }
    }

    private String resolveToolSource(String toolName) {
        if ("web_search".equals(toolName)) {
            return "mcp";
        }
        if ("searchMemories".equals(toolName)
                || "searchLifeGraph".equals(toolName)
                || "searchDiary".equals(toolName)
                || "updateUserPersona".equals(toolName)) {
            return "local";
        }
        return "tool";
    }

    @Auth
    @PostMapping("/chat/cancel")
    public Response<Void> cancelChat(@RequestBody ChatCancelRequest request) {
        if (request == null || StrUtil.isBlank(request.getRequestId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "requestId不能为空");
        }
        String userId = UserContext.getUserId();
        String runId = request.getRequestId();
        chatStreamCancellationRegistry.cancel(userId, runId);
        traceRunCancelled(userId, runId, "user");
        return Response.success();
    }

    private void traceRunStarted(String userId, String runId) {
        runTrace("start", () -> agentRunTraceService.start(userId, runId, "chat"));
    }

    private void traceRunStage(String userId, String runId, String stage) {
        runTrace("stage", () -> agentRunTraceService.stage(userId, runId, stage));
    }

    private void traceRunToolCompleted(String userId, String runId) {
        runTrace("tool", () -> agentRunTraceService.toolCompleted(userId, runId));
    }

    private void traceRunCompleted(String userId, String runId) {
        runTrace("complete", () -> agentRunTraceService.complete(userId, runId));
    }

    private void traceRunFailed(String userId, String runId, String failureCategory) {
        runTrace("fail", () -> agentRunTraceService.fail(userId, runId, failureCategory));
    }

    private void traceRunCancelled(String userId, String runId, String cancelSource) {
        runTrace("cancel", () -> agentRunTraceService.cancel(userId, runId, cancelSource));
    }

    private void runTrace(String operation, Runnable action) {
        if (agentRunTraceService == null) {
            return;
        }
        try {
            action.run();
        } catch (RuntimeException exception) {
            log.warn("AgentRun trace {} failed; continuing chat stream", operation, exception);
        }
    }

    private List<ImageContent> buildImageContents(String userId, List<String> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        // 验证图片key
        return images.stream()
                .filter(StrUtil::isNotBlank)
                .map(objectKey -> {
                    String url = ossService.generateOwnedUrl(objectKey, userId);
                    return ImageContent.from(URI.create(url));
                })
                .collect(Collectors.toList());
    }

    // ──────────────── Agent 人格配置 ────────────────

    @Auth
    @GetMapping("/persona-config")
    public Response<AgentPersonaConfig> getPersonaConfig() {
        String userId = UserContext.getUserId();
        return Response.success(agentPersonaConfigService.getConfig(userId));
    }

    @Auth
    @PutMapping("/persona-config")
    public Response<AgentPersonaConfig> updatePersonaConfig(@RequestBody AgentPersonaConfigRequest request) {
        String userId = UserContext.getUserId();
        return Response.success(agentPersonaConfigService.updateConfig(userId, request));
    }

    // ──────────────── 灵魂周报（F8.3）────────────────

    @Auth
    @GetMapping("/soul-report/latest")
    public Response<SoulReport> getLatestReport() {
        String userId = UserContext.getUserId();
        return Response.success(soulReportRepository
                .findTopByUserIdAndReportTypeOrderByCreatedAtDesc(userId, "WEEKLY")
                .orElse(null));
    }

    @Auth
    @GetMapping("/soul-report/history")
    public Response<List<SoulReport>> getReportHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        String userId = UserContext.getUserId();
        return Response.success(soulReportRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size)));
    }

    // ──────────────── Agent 成长可见化（F8.5）────────────────

    @Auth
    @GetMapping("/agent-growth")
    public Response<AgentGrowthResponse> getAgentGrowth() {
        return Response.success(agentGrowthService.getGrowth(UserContext.getUserId()));
    }

    // ──────────────── 认知冲突检测（F11.3）────────────────

    @Auth
    @GetMapping("/cognitive-conflicts")
    public Response<List<CognitiveConflict>> getConflicts() {
        return Response.success(conflictDetector.getUnresolved(UserContext.getUserId()));
    }

    @Auth
    @PostMapping("/cognitive-conflicts/{id}/resolve")
    public Response<Void> resolveConflict(@PathVariable Long id) {
        conflictRepository.findById(id).ifPresent(conflict -> {
            if (conflict.getUserId().equals(UserContext.getUserId())) {
                conflict.setResolved(true);
                conflictRepository.save(conflict);
            }
        });
        return Response.success();
    }

    // ──────────────── 跨源记忆融合（F11.4）────────────────

    @Auth
    @PostMapping("/memory-fusion/run")
    public Response<Integer> runMemoryFusion() {
        return Response.success(fusionService.fuseUserMemories(UserContext.getUserId()));
    }

    // ──────────────── 聊天气泡主动问候注入 ────────────────

    @Auth
    @PostMapping("/chat/inject-greeting")
    @Transactional
    public Response<Void> injectGreeting(@RequestParam Long notificationId) {
        String userId = UserContext.getUserId();
        UserNotification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_ERROR, "通知不存在"));

        if (!notification.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 检查 extraData 避免重复注入
        String extraData = notification.getExtraData();
        if (extraData != null && extraData.contains("\"injected\":true")) {
            return Response.success();
        }

        // 插入到聊天历史中
        AiMessage aiMessage = AiMessage.from(notification.getContent());
        String serialized = ChatMessageSerializer.messagesToJson(List.of(aiMessage));

        ChatMemoryMessage entity = ChatMemoryMessage.builder()
                .memoryId(userId)
                .role("AI")
                .content(serialized)
                .createdAt(LocalDateTime.now())
                .build();
        chatMemoryMessageRepository.save(entity);

        // 更新 Redis 缓存
        redisService.remove("yusi:langchain:" + userId);

        // 更新通知的 extraData
        notification.setExtraData("{\"injected\":true}");
        notificationRepository.save(notification);

        return Response.success();
    }

}
