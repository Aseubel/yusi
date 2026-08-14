package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.config.ai.properties.ModelGatewayAdmissionProperties;
import com.aseubel.yusi.service.ai.mask.MaskResult;
import com.aseubel.yusi.service.ai.mask.SensitiveDataMaskService;
import com.aseubel.yusi.service.ai.runtime.ModelCallAttemptEvent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiResponsesChatRequestParameters;
import dev.langchain4j.model.anthropic.AnthropicChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialThinkingContext;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.PartialToolCallContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.model.output.TokenUsage;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;

class ModelProxyFactoryTest {

    @AfterEach
    void clearRouteContext() {
        ModelRouteContextHolder.clear();
    }

    @Test
    void maskedResponseForwardsUnmaskedTextWithOriginalStreamingContext() {
        StreamingChatResponseHandler downstream = mock(StreamingChatResponseHandler.class);
        StreamingHandle streamingHandle = mock(StreamingHandle.class);
        StreamingChatResponseHandler wrapped = createWrappedHandler(downstream);

        PartialResponseContext context = new PartialResponseContext(streamingHandle);
        wrapped.onPartialResponse(new PartialResponse("MASK"), context);

        verify(downstream).onPartialResponse(any(PartialResponse.class), eq(context));
        var partialCaptor = org.mockito.ArgumentCaptor.forClass(PartialResponse.class);
        verify(downstream).onPartialResponse(partialCaptor.capture(), eq(context));
        assertEquals("plain", partialCaptor.getValue().text());
        assertSame(streamingHandle, context.streamingHandle());
    }

    @Test
    void maskedProxyPreservesThinkingAndToolCallContexts() {
        StreamingChatResponseHandler downstream = mock(StreamingChatResponseHandler.class);
        StreamingChatResponseHandler wrapped = createWrappedHandler(downstream);
        StreamingHandle thinkingHandle = mock(StreamingHandle.class);
        StreamingHandle toolHandle = mock(StreamingHandle.class);
        PartialThinkingContext thinkingContext = new PartialThinkingContext(thinkingHandle);
        PartialToolCallContext toolContext = new PartialToolCallContext(toolHandle);
        PartialThinking thinking = mock(PartialThinking.class);
        PartialToolCall toolCall = mock(PartialToolCall.class, withSettings().stubOnly());

        wrapped.onPartialThinking(thinking, thinkingContext);
        wrapped.onPartialToolCall(toolCall, toolContext);

        verify(downstream).onPartialThinking(thinking, thinkingContext);
        verify(downstream).onPartialToolCall(toolCall, toolContext);
    }

    @Test
    void fallbackUsesTheSameDecisionAndCreatesANewAttempt() {
        ModelRouterService router = mock(ModelRouterService.class);
        ModelStateCenter stateCenter = mock(ModelStateCenter.class);
        SensitiveDataMaskService maskService = mock(SensitiveDataMaskService.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        ChatModel primary = mock(ChatModel.class);
        ChatModel backup = mock(ChatModel.class);
        ModelInstance primaryInstance = instance("primary", primary, mock(StreamingChatModel.class));
        ModelInstance backupInstance = instance("backup", backup, mock(StreamingChatModel.class));

        when(router.plan(any(ModelRouteContext.class))).thenReturn(new ModelRouteDecision(
                "request-1", "chat-zh", 7L, "balanced", List.of("fast"), List.of(
                new ModelRouteCandidate("balanced", primaryInstance, true, null),
                new ModelRouteCandidate("fast", backupInstance, true, "fallback-tier")),
                "policy=chat-zh;primary-tier=balanced"));
        when(stateCenter.allowRequest(anyString())).thenReturn(true);
        when(maskService.mask(anyString())).thenReturn(new MaskResult("plain", Map.of(), false));
        when(primary.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("HTTP 429 Too Many Requests"));
        when(backup.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from("ok"))
                .tokenUsage(new TokenUsage(12, 4, 16))
                .build());

        ModelProxyFactory factory = new ModelProxyFactory(router, stateCenter, maskService, publisher,
                new ModelUsageExtractor());
        ChatResponse response = factory.createChatProxy("chat")
                .chat(ChatRequest.builder().messages(List.of(UserMessage.from("hello"))).build());

        assertEquals("ok", response.aiMessage().text());
        var eventCaptor = org.mockito.ArgumentCaptor.forClass(ModelCallAttemptEvent.class);
        verify(publisher, times(2)).publishEvent(eventCaptor.capture());
        assertEquals(List.of("chat-zh", "chat-zh"), eventCaptor.getAllValues().stream()
                .map(ModelCallAttemptEvent::policyId).toList());
        assertEquals(List.of(false, true), eventCaptor.getAllValues().stream()
                .map(ModelCallAttemptEvent::fallbackUsed).toList());
    }

    @Test
    void admissionRejectionDoesNotInvokeProviderOrCountAsModelFailure() {
        ModelRouterService router = mock(ModelRouterService.class);
        ModelStateCenter stateCenter = mock(ModelStateCenter.class);
        SensitiveDataMaskService maskService = mock(SensitiveDataMaskService.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        ChatModel delegate = mock(ChatModel.class);
        ModelInstance selected = instance("limited", delegate, mock(StreamingChatModel.class));
        when(router.plan(any(ModelRouteContext.class))).thenReturn(new ModelRouteDecision(
                "request-limit", "chat", 1L, "chat", List.of(),
                List.of(new ModelRouteCandidate("chat", selected, true, null)),
                "policy=chat;primary-tier=chat"));
        when(stateCenter.allowRequest("limited")).thenReturn(true);

        ModelGatewayAdmissionProperties properties = new ModelGatewayAdmissionProperties();
        properties.getModel().setMaxRequests(1);
        RedissonClient redissonClient = mock(RedissonClient.class);
        RScript script = mock(RScript.class);
        when(redissonClient.getScript(any(StringCodec.class))).thenReturn(script);
        when(script.eval(any(RScript.Mode.class), anyString(), eq(RScript.ReturnType.INTEGER),
                any(), any(Object[].class))).thenReturn(0L);
        ModelBudgetAdmission admission = new ModelBudgetAdmission(properties, redissonClient);

        ModelProxyFactory factory = new ModelProxyFactory(router, stateCenter, maskService, publisher,
                new ModelUsageExtractor(), new ModelTokenEstimator(), admission);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> factory.createChatProxy("chat")
                .chat(ChatRequest.builder().messages(List.of(UserMessage.from("hello"))).build()))
                .isInstanceOf(ModelAdmissionDeniedException.class);
        verify(delegate, never()).chat(any(ChatRequest.class));
        verify(stateCenter, never()).recordFailure(anyString(), anyString(), anyLong(), any(Throwable.class));
        var eventCaptor = org.mockito.ArgumentCaptor.forClass(ModelCallAttemptEvent.class);
        verify(publisher).publishEvent(eventCaptor.capture());
        assertEquals("REJECTED", eventCaptor.getValue().status());
    }

    @Test
    void modelCallEventPreservesPromptIdentityFromRouteContext() {
        ModelRouterService router = mock(ModelRouterService.class);
        ModelStateCenter stateCenter = mock(ModelStateCenter.class);
        SensitiveDataMaskService maskService = mock(SensitiveDataMaskService.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        ChatModel delegate = mock(ChatModel.class);
        ModelInstance selected = instance("prompt-model", delegate, mock(StreamingChatModel.class));
        when(router.plan(any(ModelRouteContext.class))).thenReturn(new ModelRouteDecision(
                "request-prompt", "chat", 1L, "chat", List.of(),
                List.of(new ModelRouteCandidate("chat", selected, true, null)),
                "policy=chat;primary-tier=chat"));
        when(stateCenter.allowRequest("prompt-model")).thenReturn(true);
        when(maskService.mask(anyString())).thenReturn(new MaskResult("plain", Map.of(), false));
        when(delegate.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from("ok"))
                .build());

        ModelRouteContextHolder.set(ModelRouteContext.builder()
                .requestId("request-prompt")
                .runId("run-prompt")
                .userId("user-prompt")
                .scene("chat")
                .promptKey("chat")
                .promptVersion("v7")
                .promptLocale("zh-CN")
                .build());

        ModelProxyFactory factory = new ModelProxyFactory(router, stateCenter, maskService, publisher,
                new ModelUsageExtractor());
        factory.createChatProxy("chat")
                .chat(ChatRequest.builder().messages(List.of(UserMessage.from("hello"))).build());

        var eventCaptor = org.mockito.ArgumentCaptor.forClass(ModelCallAttemptEvent.class);
        verify(publisher).publishEvent(eventCaptor.capture());
        ModelCallAttemptEvent event = eventCaptor.getValue();
        assertEquals("chat", event.promptKey());
        assertEquals("v7", event.promptVersion());
        assertEquals("zh-CN", event.promptLocale());
    }

    @Test
    void streamDoesNotSwitchAfterFirstPartialResponse() {
        ModelRouterService router = mock(ModelRouterService.class);
        ModelStateCenter stateCenter = mock(ModelStateCenter.class);
        SensitiveDataMaskService maskService = mock(SensitiveDataMaskService.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        StreamingChatModel primary = mock(StreamingChatModel.class);
        StreamingChatModel backup = mock(StreamingChatModel.class);
        ModelInstance primaryInstance = instance("primary", mock(ChatModel.class), primary);
        ModelInstance backupInstance = instance("backup", mock(ChatModel.class), backup);
        when(router.plan(any(ModelRouteContext.class))).thenReturn(new ModelRouteDecision(
                "request-2", "chat-zh", 7L, "balanced", List.of("fast"), List.of(
                new ModelRouteCandidate("balanced", primaryInstance, true, null),
                new ModelRouteCandidate("fast", backupInstance, true, "fallback-tier")),
                "policy=chat-zh;primary-tier=balanced"));
        when(stateCenter.allowRequest(anyString())).thenReturn(true);
        when(maskService.mask(anyString())).thenReturn(new MaskResult("plain", Map.of(), false));
        doAnswer(invocation -> {
            StreamingChatResponseHandler handler = invocation.getArgument(1);
            handler.onPartialResponse("partial");
            handler.onError(new RuntimeException("HTTP 500 server error"));
            return null;
        }).when(primary).doChat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));

        StreamingChatResponseHandler downstream = mock(StreamingChatResponseHandler.class);
        ModelProxyFactory factory = new ModelProxyFactory(router, stateCenter, maskService, publisher,
                new ModelUsageExtractor());
        factory.createStreamingProxy("chat").doChat(
                ChatRequest.builder().messages(List.of(UserMessage.from("hello"))).build(), downstream);

        verify(downstream).onPartialResponse("partial");
        verify(downstream).onError(any(Throwable.class));
        verifyNoInteractions(backup);
    }

    @Test
    void normalizesThinkingOnlyAssistantMessagesWithoutDroppingThinking() {
        AiMessage normalized = ModelProxyFactory.normalizeAssistantMessage(
                AiMessage.builder().thinking("internal reasoning").build());

        assertEquals("", normalized.text());
        assertEquals("internal reasoning", normalized.thinking());
    }

    @Test
    void buildsProtocolSpecificRequestParameters() {
        var routeParameters = new ModelRouteParameters(128, 128, 0.2D, 0.8D, 96, Map.of());

        ChatRequest input = ChatRequest.builder()
                .messages(List.of(UserMessage.from("hello")))
                .modelName("request-model")
                .parameters(OpenAiChatRequestParameters.builder().maxCompletionTokens(32).build())
                .build();

        ChatRequest responses = ModelProxyFactory.adaptChatRequest(ModelProtocol.RESPONSES, input, routeParameters);
        ChatRequest anthropic = ModelProxyFactory.adaptChatRequest(ModelProtocol.ANTHROPIC_MESSAGES, input, routeParameters);
        ChatRequest chatCompletions = ModelProxyFactory.adaptChatRequest(
                ModelProtocol.CHAT_COMPLETIONS, input, routeParameters);

        assertEquals(OpenAiResponsesChatRequestParameters.class, responses.parameters().getClass());
        assertEquals(128, responses.parameters().maxOutputTokens());
        assertEquals("request-model", responses.modelName());
        assertEquals(AnthropicChatRequestParameters.class, anthropic.parameters().getClass());
        assertEquals(128, anthropic.parameters().maxOutputTokens());
        assertEquals("request-model", anthropic.modelName());
        assertEquals(32, ((OpenAiChatRequestParameters) chatCompletions.parameters()).maxCompletionTokens());
    }

    @Test
    void enrichesRouteContextWithRequestTokenBudget() {
        ModelRouterService router = mock(ModelRouterService.class);
        ModelStateCenter stateCenter = mock(ModelStateCenter.class);
        SensitiveDataMaskService maskService = mock(SensitiveDataMaskService.class);
        ChatModel delegate = mock(ChatModel.class);
        ModelInstance instance = instance("budget-model", delegate, mock(StreamingChatModel.class));
        when(router.plan(any(ModelRouteContext.class))).thenReturn(new ModelRouteDecision(
                "request-budget", "chat", 1L, "chat", List.of(),
                List.of(new ModelRouteCandidate("chat", instance, true, null)),
                "policy=chat;primary-tier=chat"));
        when(stateCenter.allowRequest("budget-model")).thenReturn(true);
        when(maskService.mask(anyString())).thenReturn(new MaskResult("plain", Map.of(), false));
        when(delegate.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from("ok"))
                .build());

        ModelProxyFactory factory = new ModelProxyFactory(router, stateCenter, maskService);
        factory.createChatProxy("chat").chat(ChatRequest.builder()
                .messages(List.of(UserMessage.from("请帮我总结这段内容")))
                .parameters(OpenAiChatRequestParameters.builder().maxOutputTokens(64).build())
                .build());

        var contextCaptor = org.mockito.ArgumentCaptor.forClass(ModelRouteContext.class);
        verify(router).plan(contextCaptor.capture());
        assertThat(contextCaptor.getValue().getEstimatedInputTokens()).isPositive();
        assertEquals(64, contextCaptor.getValue().getReservedOutputTokens());
    }

    private StreamingChatResponseHandler createWrappedHandler(StreamingChatResponseHandler downstream) {
        ModelRouterService router = mock(ModelRouterService.class);
        ModelStateCenter stateCenter = mock(ModelStateCenter.class);
        SensitiveDataMaskService maskService = mock(SensitiveDataMaskService.class);
        StreamingChatModel delegate = mock(StreamingChatModel.class);
        ModelInstance selected = ModelInstance.builder()
                .id("model-1")
                .modelName("model-1")
                .scenes(Set.of())
                .chatModel(mock(dev.langchain4j.model.chat.ChatModel.class))
                .streamingChatModel(delegate)
                .build();

        when(router.plan(any(ModelRouteContext.class))).thenReturn(new ModelRouteDecision(
                "request-3", "chat", 1L, "chat", List.of(),
                List.of(new ModelRouteCandidate("chat", selected, true, null)),
                "policy=chat;primary-tier=chat"));
        when(stateCenter.allowRequest("model-1")).thenReturn(true);
        when(maskService.mask(anyString())).thenReturn(
                new MaskResult("MASK", Map.of("MASK", "plain"), true));
        when(maskService.unmask(any(), anyString())).thenAnswer(invocation ->
                invocation.<String>getArgument(1).replace("MASK", "plain"));
        doNothing().when(delegate).doChat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));

        ModelProxyFactory factory = new ModelProxyFactory(router, stateCenter, maskService);
        StreamingChatModel proxy = factory.createStreamingProxy("chat");
        proxy.doChat(ChatRequest.builder().messages(List.of(UserMessage.from("plain"))).build(), downstream);

        var handlerCaptor = org.mockito.ArgumentCaptor.forClass(StreamingChatResponseHandler.class);
        verify(delegate).doChat(any(ChatRequest.class), handlerCaptor.capture());
        return handlerCaptor.getValue();
    }

    private ModelInstance instance(String id, ChatModel chatModel, StreamingChatModel streamingChatModel) {
        return ModelInstance.builder()
                .id(id)
                .modelName(id)
                .provider("openai-compatible")
                .weight(100)
                .priority(1)
                .scenes(Set.of())
                .capabilities(Set.of(ModelCapability.CHAT, ModelCapability.STREAMING_CHAT))
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .build();
    }
}
