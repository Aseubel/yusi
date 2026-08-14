package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.service.ai.mask.MaskResult;
import com.aseubel.yusi.service.ai.mask.SensitiveDataMaskService;
import com.aseubel.yusi.service.ai.runtime.ModelCallAttemptEvent;
import com.aseubel.yusi.common.constant.ModelCallStatus;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialThinkingContext;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.PartialToolCallContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.anthropic.AnthropicChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiResponsesChatRequestParameters;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ModelProxyFactory {

    private final ModelRouterService modelRouterService;
    private final ModelStateCenter modelStateCenter;
    private final SensitiveDataMaskService maskService;
    private final ApplicationEventPublisher eventPublisher;
    private final ModelUsageExtractor usageExtractor;
    private final ModelTokenEstimator tokenEstimator;
    private final ModelBudgetAdmission budgetAdmission;

    public ModelProxyFactory(ModelRouterService modelRouterService, ModelStateCenter modelStateCenter,
            SensitiveDataMaskService maskService) {
        this(modelRouterService, modelStateCenter, maskService, new NoopEventPublisher(),
                new ModelUsageExtractor(), new ModelTokenEstimator(), new ModelBudgetAdmission());
    }

    public ModelProxyFactory(ModelRouterService modelRouterService, ModelStateCenter modelStateCenter,
            SensitiveDataMaskService maskService, ApplicationEventPublisher eventPublisher,
            ModelUsageExtractor usageExtractor) {
        this(modelRouterService, modelStateCenter, maskService, eventPublisher, usageExtractor,
                new ModelTokenEstimator(), new ModelBudgetAdmission());
    }

    public ModelProxyFactory(ModelRouterService modelRouterService, ModelStateCenter modelStateCenter,
            SensitiveDataMaskService maskService, ApplicationEventPublisher eventPublisher,
            ModelUsageExtractor usageExtractor, ModelTokenEstimator tokenEstimator) {
        this(modelRouterService, modelStateCenter, maskService, eventPublisher, usageExtractor,
                tokenEstimator, new ModelBudgetAdmission());
    }

    @Autowired
    public ModelProxyFactory(ModelRouterService modelRouterService, ModelStateCenter modelStateCenter,
            SensitiveDataMaskService maskService, ApplicationEventPublisher eventPublisher,
            ModelUsageExtractor usageExtractor, ModelTokenEstimator tokenEstimator,
            ModelBudgetAdmission budgetAdmission) {
        this.modelRouterService = modelRouterService;
        this.modelStateCenter = modelStateCenter;
        this.maskService = maskService;
        this.eventPublisher = eventPublisher;
        this.usageExtractor = usageExtractor;
        this.tokenEstimator = tokenEstimator;
        this.budgetAdmission = budgetAdmission;
    }

    public ChatModel createChatProxy(String defaultScene) {
        InvocationHandler handler = new RoutingInvocationHandler(defaultScene, true);
        return (ChatModel) Proxy.newProxyInstance(ChatModel.class.getClassLoader(), new Class[] { ChatModel.class }, handler);
    }

    public StreamingChatModel createStreamingProxy(String defaultScene) {
        InvocationHandler handler = new RoutingInvocationHandler(defaultScene, false);
        return (StreamingChatModel) Proxy.newProxyInstance(StreamingChatModel.class.getClassLoader(),
                new Class[] { StreamingChatModel.class }, handler);
    }

    static AiMessage normalizeAssistantMessage(AiMessage message) {
        if (message != null && message.text() == null && !message.hasToolExecutionRequests()) {
            return message.toBuilder().text("").build();
        }
        return message;
    }

    private static List<ChatMessage> normalizeMessages(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return messages == null ? List.of() : messages;
        }
        return messages.stream()
                .map(message -> message instanceof AiMessage aiMessage
                        ? normalizeAssistantMessage(aiMessage) : message)
                .toList();
    }

    static ChatRequest adaptChatRequest(ModelProtocol protocol, ChatRequest request,
            ModelRouteParameters routeParameters) {
        if (request == null) {
            return null;
        }
        ChatRequestParameters parameters = buildProtocolParameters(
                ModelProtocol.normalize(protocol), request.parameters(), routeParameters);
        return request.toBuilder()
                .messages(normalizeMessages(request.messages()))
                .parameters(parameters)
                .build();
    }

    private static ChatRequestParameters buildProtocolParameters(ModelProtocol protocol,
            ChatRequestParameters base, ModelRouteParameters routeParameters) {
        ModelRouteParameters parameters = routeParameters == null
                ? new ModelRouteParameters(null, null, null, null, null, Map.of())
                : routeParameters;
        Integer maxOutputTokens = parameters.maxOutputTokens();
        if (maxOutputTokens == null) {
            maxOutputTokens = parameters.maxCompletionTokens();
        }
        Integer requestMaxOutputTokens = base == null ? null : base.maxOutputTokens();
        Integer requestMaxCompletionTokens = base instanceof OpenAiChatRequestParameters openAiParameters
                ? openAiParameters.maxCompletionTokens() : null;

        return switch (ModelProtocol.normalize(protocol)) {
            case CHAT_COMPLETIONS -> {
                OpenAiChatRequestParameters.Builder builder = OpenAiChatRequestParameters.builder();
                if (base != null) {
                    builder.overrideWith(base);
                }
                Integer effectiveMaxOutputTokens = smallerPositive(requestMaxOutputTokens,
                        parameters.maxOutputTokens());
                if (effectiveMaxOutputTokens != null) {
                    builder.maxOutputTokens(effectiveMaxOutputTokens);
                }
                if (parameters.temperature() != null) {
                    builder.temperature(parameters.temperature());
                }
                if (parameters.topP() != null) {
                    builder.topP(parameters.topP());
                }
                Integer effectiveMaxCompletionTokens = smallerPositive(requestMaxCompletionTokens,
                        parameters.maxCompletionTokens());
                if (effectiveMaxCompletionTokens != null) {
                    builder.maxCompletionTokens(effectiveMaxCompletionTokens);
                }
                if (!parameters.customParameters().isEmpty()) {
                    builder.customParameters(parameters.customParameters());
                }
                yield builder.build();
            }
            case RESPONSES -> {
                OpenAiResponsesChatRequestParameters.Builder builder = OpenAiResponsesChatRequestParameters.builder();
                if (base != null) {
                    builder.overrideWith(base);
                }
                Integer effectiveMaxOutputTokens = smallerPositive(requestMaxOutputTokens, maxOutputTokens);
                if (effectiveMaxOutputTokens != null) {
                    builder.maxOutputTokens(effectiveMaxOutputTokens);
                }
                if (parameters.temperature() != null) {
                    builder.temperature(parameters.temperature());
                }
                if (parameters.topP() != null) {
                    builder.topP(parameters.topP());
                }
                yield builder.build();
            }
            case ANTHROPIC_MESSAGES -> {
                AnthropicChatRequestParameters.Builder builder = AnthropicChatRequestParameters.builder();
                if (base != null) {
                    builder.overrideWith(base);
                }
                Integer effectiveMaxOutputTokens = smallerPositive(requestMaxOutputTokens, maxOutputTokens);
                if (effectiveMaxOutputTokens != null) {
                    builder.maxOutputTokens(effectiveMaxOutputTokens);
                }
                if (parameters.temperature() != null) {
                    builder.temperature(parameters.temperature());
                }
                if (parameters.topP() != null) {
                    builder.topP(parameters.topP());
                }
                yield builder.build();
            }
        };
    }

    private static Integer smallerPositive(Integer first, Integer second) {
        if (first == null || first <= 0) {
            return second == null || second <= 0 ? null : second;
        }
        if (second == null || second <= 0) {
            return first;
        }
        return Math.min(first, second);
    }

    private class RoutingInvocationHandler implements InvocationHandler {
        private final String defaultScene;
        private final boolean chatMode;

        private RoutingInvocationHandler(String defaultScene, boolean chatMode) {
            this.defaultScene = defaultScene;
            this.chatMode = chatMode;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }
            ModelRouteContext context = resolveContext(findChatRequest(args));
            ModelRouteDecision decision = modelRouterService.plan(context);
            List<ModelRouteCandidate> candidates = decision.attemptCandidates();
            if (candidates.isEmpty()) {
                throw new IllegalStateException("No available model instance for scene: "
                        + context.getScene());
            }

            ModelInvocationException lastError = null;
            int attemptIndex = 0;
            for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++) {
                ModelRouteCandidate candidate = candidates.get(candidateIndex);
                ModelInstance selected = candidate.instance();
                if (!chatMode) {
                    invokeStreamingAttempt(decision, context, method, args, candidates,
                            candidateIndex, attemptIndex++);
                    return null;
                }
                if (!modelStateCenter.allowRequest(selected.getId())) {
                    continue;
                }

                ModelBudgetPermit permit = budgetAdmission.reserve(context, candidate,
                        tokenBudget(context, decision.routeParameters()));
                if (!permit.granted()) {
                    publishAttempt(decision, context, candidate, null, 0L, null,
                            attemptIndex, ModelCallStatus.REJECTED.code(), permit.reservationKey());
                    lastError = new ModelAdmissionDeniedException(candidate.provider(), candidate.modelId(),
                            permit.reservationKey());
                    attemptIndex++;
                    continue;
                }

                long start = System.currentTimeMillis();
                try {
                    Object result = invokeWithRouteParameters(selected, selected.getChatModel(), method, args,
                            context, decision.routeParameters());
                    ChatResponse response = result instanceof ChatResponse chatResponse ? chatResponse : null;
                    ModelUsageSnapshot usage = usageExtractor.extract(response, selected);
                    budgetAdmission.reconcile(permit, usage);
                    long latency = System.currentTimeMillis() - start;
                    modelStateCenter.recordSuccess(selected.getId(), selected.getModelName(), latency);
                    publishAttempt(decision, context, candidate, usage, latency, null,
                            attemptIndex, ModelCallStatus.SUCCESS.code(), null);
                    return result;
                } catch (Throwable throwable) {
                    ModelInvocationException normalized = normalize(throwable, selected);
                    budgetAdmission.reconcile(permit, null);
                    long latency = System.currentTimeMillis() - start;
                    modelStateCenter.recordFailure(selected.getId(), selected.getModelName(), latency, normalized);
                    publishAttempt(decision, context, candidate,
                            ModelUsageSnapshot.unavailable(selected.getPriceVersion()), latency, null,
                            attemptIndex, ModelCallStatus.FAILED.code(), normalized.kind().name());
                    lastError = normalized;
                    log.warn("AI model invocation failed, attempt {}, model: {}, kind: {}, error: {}",
                            attemptIndex + 1, selected.getModelName(), normalized.kind(), normalized.getMessage());
                    if (!normalized.isFallbackEligible(false)) {
                        throw normalized;
                    }
                }
                attemptIndex++;
            }
            if (lastError != null) {
                throw lastError;
            }
            throw new IllegalStateException("No available model instance for scene: " + context.getScene());
        }

        private void invokeStreamingAttempt(ModelRouteDecision decision, ModelRouteContext context,
                Method method, Object[] args, List<ModelRouteCandidate> candidates,
                int candidateIndex, int attemptIndex) throws Throwable {
            ModelRouteCandidate candidate = candidates.get(candidateIndex);
            ModelInstance selected = candidate.instance();
            if (!modelStateCenter.allowRequest(selected.getId())) {
                if (candidateIndex + 1 < candidates.size()) {
                    invokeStreamingAttempt(decision, context, method, args, candidates,
                            candidateIndex + 1, attemptIndex);
                    return;
                }
                throw new IllegalStateException("No available streaming model candidate");
            }

            ModelBudgetPermit permit = budgetAdmission.reserve(context, candidate,
                    tokenBudget(context, decision.routeParameters()));
            if (!permit.granted()) {
                publishAttempt(decision, context, candidate, null, 0L, null,
                            attemptIndex, ModelCallStatus.REJECTED.code(), permit.reservationKey());
                if (candidateIndex + 1 < candidates.size()) {
                    invokeStreamingAttempt(decision, context, method, args, candidates,
                            candidateIndex + 1, attemptIndex + 1);
                    return;
                }
                throw new ModelAdmissionDeniedException(candidate.provider(), candidate.modelId(),
                        permit.reservationKey());
            }

            StreamingChatResponseHandler downstream = findStreamingHandler(args);
            if (downstream == null) {
                try {
                    invokeWithRouteParameters(selected, selected.getStreamingChatModel(), method, args,
                            context, decision.routeParameters());
                    budgetAdmission.reconcile(permit, null);
                } catch (Throwable throwable) {
                    budgetAdmission.reconcile(permit, null);
                    throw throwable;
                }
                return;
            }
            long start = System.currentTimeMillis();
            AtomicBoolean emitted = new AtomicBoolean(false);
            AtomicBoolean terminal = new AtomicBoolean(false);
            AtomicLong firstOutputAt = new AtomicLong(-1L);
            StreamingChatResponseHandler trackingHandler = new TrackingStreamingHandler(
                    downstream, decision, context, candidate, candidates, candidateIndex,
                    attemptIndex, start, emitted, terminal, firstOutputAt, method, args, permit);
            Object[] trackingArgs = args == null ? new Object[0] : args.clone();
            for (int index = 0; index < trackingArgs.length; index++) {
                if (trackingArgs[index] instanceof StreamingChatResponseHandler) {
                    trackingArgs[index] = trackingHandler;
                }
            }
            try {
                invokeWithRouteParameters(selected, selected.getStreamingChatModel(), method, trackingArgs,
                        context, decision.routeParameters());
            } catch (Throwable throwable) {
                if (terminal.get()) {
                    return;
                }
                terminal.set(true);
                handleStreamingFailure(decision, context, candidate, candidates, candidateIndex,
                        attemptIndex, start, emitted.get(), firstOutputAt.get(), method, args,
                        throwable, downstream, permit);
            }
        }

        private StreamingChatResponseHandler findStreamingHandler(Object[] args) {
            if (args == null) {
                return null;
            }
            for (Object arg : args) {
                if (arg instanceof StreamingChatResponseHandler handler) {
                    return handler;
                }
            }
            return null;
        }

        private void handleStreamingFailure(ModelRouteDecision decision, ModelRouteContext context,
                ModelRouteCandidate candidate, List<ModelRouteCandidate> candidates, int candidateIndex,
                int attemptIndex, long start, boolean emitted, long firstOutputAt,
                Method method, Object[] args, Throwable throwable, StreamingChatResponseHandler downstream,
                ModelBudgetPermit permit) {
            ModelInvocationException normalized = normalize(throwable, candidate.instance());
            budgetAdmission.reconcile(permit, null);
            long latency = System.currentTimeMillis() - start;
            modelStateCenter.recordFailure(candidate.modelId(), candidate.modelName(), latency, normalized);
            publishAttempt(decision, context, candidate,
                    ModelUsageSnapshot.unavailable(candidate.instance().getPriceVersion()), latency,
                    firstOutputAt < 0 ? null : firstOutputAt - start, attemptIndex, ModelCallStatus.FAILED.code(),
                    normalized.kind().name());
            if (!emitted && normalized.isFallbackEligible(false) && candidateIndex + 1 < candidates.size()) {
                try {
                    invokeStreamingAttempt(decision, context, method, args,
                            candidates, candidateIndex + 1, attemptIndex + 1);
                } catch (Throwable fallbackFailure) {
                    downstream.onError(normalize(fallbackFailure, candidates.get(candidateIndex + 1).instance()));
                }
                return;
            }
            downstream.onError(normalized);
        }

        private ModelInvocationException normalize(Throwable throwable, ModelInstance selected) {
            if (throwable instanceof ModelInvocationException normalized) {
                return normalized;
            }
            return ModelInvocationErrorClassifier.classify(throwable, selected.getProvider(), selected.getId());
        }

        private void publishAttempt(ModelRouteDecision decision, ModelRouteContext context,
                ModelRouteCandidate candidate, ModelUsageSnapshot usage, long latencyMs, Long ttftMs,
                int retryIndex, String status, String errorCode) {
            String requestId = firstNonBlank(context.getRequestId(), decision.requestId(), UUID.randomUUID().toString());
            String finishReason = usage == null ? null : usage.finishReason();
            ModelCallAttemptEvent event = new ModelCallAttemptEvent(
                    requestId,
                    UUID.randomUUID().toString(),
                    context.getRunId(),
                    context.getUserId(),
                    context.getScene(),
                    context.getPromptKey(),
                    context.getPromptVersion(),
                    context.getPromptLocale(),
                    decision.policyId(),
                    decision.policyVersion(),
                    decision.routeReason(),
                    decision.primaryTier(),
                    candidate.tierId(),
                    candidate.modelId(),
                    candidate.provider(),
                    candidate.modelName(),
                    usage,
                    latencyMs,
                    ttftMs,
                    retryIndex,
                    !candidate.tierId().equals(decision.primaryTier()),
                    status,
                    errorCode,
                    finishReason);
            try {
                eventPublisher.publishEvent(event);
            } catch (RuntimeException publishFailure) {
                log.warn("Failed to publish model attempt event attemptId={}: {}",
                        event.attemptId(), publishFailure.getMessage());
            }
        }

        private String firstNonBlank(String... values) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            return "unknown";
        }

        private class TrackingStreamingHandler implements StreamingChatResponseHandler {
            private final StreamingChatResponseHandler downstream;
            private final ModelRouteDecision decision;
            private final ModelRouteContext context;
            private final ModelRouteCandidate candidate;
            private final List<ModelRouteCandidate> candidates;
            private final int candidateIndex;
            private final int attemptIndex;
            private final long start;
            private final AtomicBoolean emitted;
            private final AtomicBoolean terminal;
            private final AtomicLong firstOutputAt;
            private final Method method;
            private final Object[] args;
            private final ModelBudgetPermit permit;

            private TrackingStreamingHandler(StreamingChatResponseHandler downstream,
                    ModelRouteDecision decision, ModelRouteContext context, ModelRouteCandidate candidate,
                    List<ModelRouteCandidate> candidates, int candidateIndex, int attemptIndex, long start,
                    AtomicBoolean emitted, AtomicBoolean terminal, AtomicLong firstOutputAt,
                    Method method, Object[] args, ModelBudgetPermit permit) {
                this.downstream = downstream;
                this.decision = decision;
                this.context = context;
                this.candidate = candidate;
                this.candidates = candidates;
                this.candidateIndex = candidateIndex;
                this.attemptIndex = attemptIndex;
                this.start = start;
                this.emitted = emitted;
                this.terminal = terminal;
                this.firstOutputAt = firstOutputAt;
                this.method = method;
                this.args = args;
                this.permit = permit;
            }

            @Override
            public void onPartialResponse(String partialResponse) {
                markOutput();
                downstream.onPartialResponse(partialResponse);
            }

            @Override
            public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext responseContext) {
                markOutput();
                downstream.onPartialResponse(partialResponse, responseContext);
            }

            @Override
            public void onPartialThinking(PartialThinking partialThinking) {
                markOutput();
                downstream.onPartialThinking(partialThinking);
            }

            @Override
            public void onPartialThinking(PartialThinking partialThinking, PartialThinkingContext thinkingContext) {
                markOutput();
                downstream.onPartialThinking(partialThinking, thinkingContext);
            }

            @Override
            public void onPartialToolCall(PartialToolCall partialToolCall) {
                markOutput();
                downstream.onPartialToolCall(partialToolCall);
            }

            @Override
            public void onPartialToolCall(PartialToolCall partialToolCall, PartialToolCallContext toolCallContext) {
                markOutput();
                downstream.onPartialToolCall(partialToolCall, toolCallContext);
            }

            @Override
            public void onCompleteToolCall(CompleteToolCall completeToolCall) {
                markOutput();
                downstream.onCompleteToolCall(completeToolCall);
            }

            @Override
            public void onUnmappedRawEvent(Object event) {
                markOutput();
                downstream.onUnmappedRawEvent(event);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                if (terminal.compareAndSet(false, true)) {
                    long latency = System.currentTimeMillis() - start;
                    ModelUsageSnapshot usage = usageExtractor.extract(completeResponse, candidate.instance());
                    budgetAdmission.reconcile(permit, usage);
                    modelStateCenter.recordSuccess(candidate.modelId(), candidate.modelName(), latency);
                    publishAttempt(decision, context, candidate, usage, latency,
                            firstOutputAt.get() < 0 ? null : firstOutputAt.get() - start,
                            attemptIndex, ModelCallStatus.SUCCESS.code(), null);
                }
                downstream.onCompleteResponse(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                if (!terminal.compareAndSet(false, true)) {
                    downstream.onError(error);
                    return;
                }
                handleStreamingFailure(decision, context, candidate, candidates, candidateIndex,
                        attemptIndex, start, emitted.get(), firstOutputAt.get(), method, args, error, downstream,
                        permit);
            }

            private void markOutput() {
                emitted.set(true);
                firstOutputAt.compareAndSet(-1L, System.currentTimeMillis());
            }
        }

        private ModelTokenBudget tokenBudget(ModelRouteContext context, ModelRouteParameters routeParameters) {
            long input = context == null || context.getEstimatedInputTokens() == null
                    ? 0L : Math.max(0L, context.getEstimatedInputTokens());
            long requestedOutput = context == null || context.getReservedOutputTokens() == null
                    ? Long.MAX_VALUE : Math.max(0L, context.getReservedOutputTokens());
            long routeOutput = routeParameters == null
                    ? ModelRouteParameters.DEFAULT_OUTPUT_TOKENS
                    : firstPositive(routeParameters.maxOutputTokens(), routeParameters.maxCompletionTokens(),
                            ModelRouteParameters.DEFAULT_OUTPUT_TOKENS);
            long output = Math.min(requestedOutput, routeOutput);
            return new ModelTokenBudget(input, output == Long.MAX_VALUE ? routeOutput : output);
        }

        private long firstPositive(Integer first, Integer second, int fallback) {
            if (first != null && first > 0) {
                return first;
            }
            if (second != null && second > 0) {
                return second;
            }
            return fallback;
        }

        private Object invokeWithRouteParameters(ModelInstance selected, Object delegate, Method method,
                Object[] args, ModelRouteContext context, ModelRouteParameters routeParameters) throws Throwable {
            if (args != null && args.length > 0 && args[0] instanceof ChatRequest chatRequest) {
                ChatRequest adaptedRequest = adaptChatRequest(selected.getProtocol(), chatRequest, routeParameters);
                Object[] adaptedArgs = args.clone();
                adaptedArgs[0] = adaptedRequest;
                return invokeWithMasking(context, delegate, method, adaptedArgs);
            }
            return invokeWithMasking(context, delegate, method, args);
        }

        // ── 脱敏核心逻辑 ──────────────────────────────────────

        /**
         * 在发送给外部 LLM 前对 ChatRequest 的所有消息统一脱敏，
         * 并在收到响应后统一还原。
         * <p>
         * 这确保了：
         * 1. 语义一致性 — SystemPrompt、聊天历史、用户消息、Tool 结果全部统一脱敏
         * 2. 无线程问题 — 拦截发生在最终 HTTP 调用前
         */
        private Object invokeWithMasking(ModelRouteContext context, Object delegate, Method method, Object[] args) throws Throwable {
            if (!context.isMaskSensitiveData()) {
                return method.invoke(delegate, args);
            }

            if (args == null || args.length == 0 || !(args[0] instanceof ChatRequest chatRequest)) {
                return method.invoke(delegate, args);
            }

            // 1. 规范化消息并拼接所有文本，统一脱敏
            List<ChatMessage> originalMessages = chatRequest.messages();
            List<ChatMessage> normalizedMessages = normalizeMessages(originalMessages);
            ChatRequest normalizedRequest = chatRequest.toBuilder()
                    .messages(normalizedMessages)
                    .build();
            Object[] normalizedArgs = args.clone();
            normalizedArgs[0] = normalizedRequest;
            String allText = extractAllText(normalizedMessages);
            MaskResult maskResult = maskService.mask(allText);

            if (!maskResult.isHasMasked()) {
                return method.invoke(delegate, normalizedArgs);
            }

            Map<String, String> mapping = maskResult.getMappingTable();
            log.debug("脱敏拦截: 映射表大小={}", mapping.size());

            // 2. 构建脱敏后的 ChatRequest
            List<ChatMessage> maskedMessages = maskMessages(normalizedMessages, mapping);
            ChatRequest maskedRequest = chatRequest.toBuilder()
                    .messages(maskedMessages)
                    .build();
            Object[] maskedArgs = new Object[args.length];
            maskedArgs[0] = maskedRequest;

            // 3. 处理流式响应（包装 handler 进行 unmask）
            if (!chatMode && args.length > 1 && args[1] instanceof StreamingChatResponseHandler originalHandler) {
                maskedArgs[1] = wrapStreamingHandler(originalHandler, mapping);
                for (int i = 2; i < args.length; i++) {
                    maskedArgs[i] = args[i];
                }
                return method.invoke(delegate, maskedArgs);
            }

            // 4. 同步响应：调用后 unmask
            for (int i = 1; i < args.length; i++) {
                maskedArgs[i] = args[i];
            }
            Object result = method.invoke(delegate, maskedArgs);
            if (result instanceof ChatResponse chatResponse) {
                return unmaskChatResponse(chatResponse, mapping);
            }
            return result;
        }

        /**
         * 提取所有消息的文本内容，拼接为一个字符串用于统一脱敏
         */
        private String extractAllText(List<ChatMessage> messages) {
            StringBuilder sb = new StringBuilder();
            for (ChatMessage msg : messages) {
                if (msg instanceof SystemMessage sm) {
                    sb.append(sm.text()).append("\n");
                } else if (msg instanceof UserMessage um) {
                    if (um.contents() != null) {
                        for (Content content : um.contents()) {
                            if (content instanceof TextContent tc) {
                                sb.append(tc.text()).append("\n");
                            }
                        }
                    }
                } else if (msg instanceof AiMessage am) {
                    if (am.text() != null) {
                        sb.append(am.text()).append("\n");
                    }
                    if (am.thinking() != null) {
                        sb.append(am.thinking()).append("\n");
                    }
                } else if (msg instanceof ToolExecutionResultMessage tm) {
                    sb.append(tm.text()).append("\n");
                }
            }
            return sb.toString();
        }

        /**
         * 对每条消息的文本内容应用脱敏替换
         */
        private List<ChatMessage> maskMessages(List<ChatMessage> messages, Map<String, String> mapping) {
            // 反转映射表：原始值 → 占位符
            Map<String, String> reverseMapping = new HashMap<>();
            for (Map.Entry<String, String> entry : mapping.entrySet()) {
                reverseMapping.put(entry.getValue(), entry.getKey());
            }

            return messages.stream().map(msg -> maskSingleMessage(msg, reverseMapping)).collect(Collectors.toList());
        }

        private ChatMessage maskSingleMessage(ChatMessage msg, Map<String, String> reverseMapping) {
            if (msg instanceof SystemMessage sm) {
                return SystemMessage.from(replaceAll(sm.text(), reverseMapping));
            } else if (msg instanceof UserMessage um) {
                // UserMessage 可能包含多种 Content（文本 + 图片），只替换文本部分
                List<Content> maskedContents = um.contents().stream().map(content -> {
                    if (content instanceof TextContent tc) {
                        return (Content) TextContent.from(replaceAll(tc.text(), reverseMapping));
                    }
                    return content;
                }).collect(Collectors.toList());
                return UserMessage.from(um.name(), maskedContents);
            } else if (msg instanceof AiMessage am) {
                AiMessage normalized = normalizeAssistantMessage(am);
                AiMessage.Builder builder = normalized.toBuilder();
                if (normalized.text() != null) {
                    builder.text(replaceAll(normalized.text(), reverseMapping));
                }
                if (normalized.thinking() != null) {
                    builder.thinking(replaceAll(normalized.thinking(), reverseMapping));
                }
                return builder.build();
            } else if (msg instanceof ToolExecutionResultMessage tm) {
                return ToolExecutionResultMessage.from(tm.id(), tm.toolName(),
                        replaceAll(tm.text(), reverseMapping));
            }
            return msg;
        }

        /**
         * 批量替换：将文本中的原始值替换为占位符
         */
        private String replaceAll(String text, Map<String, String> reverseMapping) {
            if (text == null || text.isEmpty()) return text;
            String result = text;
            for (Map.Entry<String, String> entry : reverseMapping.entrySet()) {
                result = result.replace(entry.getKey(), entry.getValue());
            }
            return result;
        }

        /**
         * 包装流式响应处理器，对每个 token 和最终响应做 unmask
         */
        private StreamingChatResponseHandler wrapStreamingHandler(
                StreamingChatResponseHandler original, Map<String, String> mapping) {
            return new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    original.onPartialResponse(maskService.unmask(mapping, partialResponse));
                }

                @Override
                public void onPartialResponse(PartialResponse partialResponse,
                        PartialResponseContext context) {
                    PartialResponse unmasked = new PartialResponse(
                            maskService.unmask(mapping, partialResponse.text()));
                    original.onPartialResponse(unmasked, context);
                }

                @Override
                public void onPartialThinking(PartialThinking partialThinking) {
                    original.onPartialThinking(partialThinking);
                }

                @Override
                public void onPartialThinking(PartialThinking partialThinking,
                        PartialThinkingContext context) {
                    original.onPartialThinking(partialThinking, context);
                }

                @Override
                public void onPartialToolCall(PartialToolCall partialToolCall) {
                    original.onPartialToolCall(partialToolCall);
                }

                @Override
                public void onPartialToolCall(PartialToolCall partialToolCall,
                        PartialToolCallContext context) {
                    original.onPartialToolCall(partialToolCall, context);
                }

                @Override
                public void onCompleteToolCall(CompleteToolCall completeToolCall) {
                    original.onCompleteToolCall(completeToolCall);
                }

                @Override
                public void onUnmappedRawEvent(Object event) {
                    original.onUnmappedRawEvent(event);
                }

                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    original.onCompleteResponse(unmaskChatResponse(completeResponse, mapping));
                }

                @Override
                public void onError(Throwable error) {
                    original.onError(error);
                }
            };
        }

        /**
         * 对 ChatResponse 中的 AiMessage 文本做 unmask
         */
        private ChatResponse unmaskChatResponse(ChatResponse response, Map<String, String> mapping) {
            if (response == null || response.aiMessage() == null) {
                return response;
            }
            AiMessage original = normalizeAssistantMessage(response.aiMessage());
            AiMessage.Builder builder = original.toBuilder();
            if (original.text() != null) {
                builder.text(maskService.unmask(mapping, original.text()));
            }
            if (original.thinking() != null) {
                builder.thinking(maskService.unmask(mapping, original.thinking()));
            }
            return response.toBuilder().aiMessage(builder.build()).build();
        }

        private ChatRequest findChatRequest(Object[] args) {
            if (args == null) {
                return null;
            }
            for (Object arg : args) {
                if (arg instanceof ChatRequest chatRequest) {
                    return chatRequest;
                }
            }
            return null;
        }

        private ModelRouteContext resolveContext(ChatRequest request) {
            ModelRouteContext context = ModelRouteContextHolder.get();
            String scene = context == null ? null : context.getScene();
            String resolvedScene = Objects.requireNonNullElse(scene, defaultScene);
            Integer estimatedInputTokens = context == null ? null : context.getEstimatedInputTokens();
            if (estimatedInputTokens == null && request != null) {
                estimatedInputTokens = tokenEstimator.estimate(request);
            }
            Integer reservedOutputTokens = context == null ? null : context.getReservedOutputTokens();
            if (reservedOutputTokens == null && request != null) {
                reservedOutputTokens = tokenEstimator.requestedOutputTokens(request);
            }
            return ModelRouteContext.builder()
                    .requestId(context == null ? null : context.getRequestId())
                    .runId(context == null ? null : context.getRunId())
                    .userId(context == null ? null : context.getUserId())
                    .scene(resolvedScene)
                    .promptKey(context == null ? null : context.getPromptKey())
                    .promptVersion(context == null ? null : context.getPromptVersion())
                    .promptLocale(context == null ? null : context.getPromptLocale())
                    .riskLevel(context == null ? null : context.getRiskLevel())
                    .estimatedInputTokens(estimatedInputTokens)
                    .reservedOutputTokens(reservedOutputTokens)
                    .maskSensitiveData(context == null || context.isMaskSensitiveData())
                    .build();
        }
    }

    private static final class NoopEventPublisher implements ApplicationEventPublisher {
        @Override
        public void publishEvent(Object event) {
        }
    }
}
