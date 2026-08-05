package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.service.ai.mask.MaskResult;
import com.aseubel.yusi.service.ai.mask.SensitiveDataMaskService;
import com.aseubel.yusi.service.ai.runtime.ModelCallAttemptEvent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
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

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
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
        when(router.resolveSceneDefinition(anyString(), anyString())).thenReturn(null);
        when(stateCenter.allowRequest(anyString())).thenReturn(true);
        when(maskService.mask(anyString())).thenReturn(new MaskResult("plain", Map.of(), false));
        when(primary.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("HTTP 429 Too Many Requests"));
        when(backup.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from("ok"))
                .tokenUsage(new TokenUsage(12, 4, 16))
                .build());

        ModelProxyFactory factory = new ModelProxyFactory(router, stateCenter, maskService, publisher,
                new ModelUsageExtractor());
        ChatResponse response = factory.createChatProxy("zh", "chat")
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
        when(router.resolveSceneDefinition(anyString(), anyString())).thenReturn(null);
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
        factory.createStreamingProxy("zh", "chat").doChat(
                ChatRequest.builder().messages(List.of(UserMessage.from("hello"))).build(), downstream);

        verify(downstream).onPartialResponse("partial");
        verify(downstream).onError(any(Throwable.class));
        verifyNoInteractions(backup);
    }

    private StreamingChatResponseHandler createWrappedHandler(StreamingChatResponseHandler downstream) {
        ModelRouterService router = mock(ModelRouterService.class);
        ModelStateCenter stateCenter = mock(ModelStateCenter.class);
        SensitiveDataMaskService maskService = mock(SensitiveDataMaskService.class);
        StreamingChatModel delegate = mock(StreamingChatModel.class);
        ModelInstance selected = ModelInstance.builder()
                .id("model-1")
                .modelName("model-1")
                .languages(Set.of())
                .scenes(Set.of())
                .chatModel(mock(dev.langchain4j.model.chat.ChatModel.class))
                .streamingChatModel(delegate)
                .build();

        when(router.select(any(ModelRouteContext.class), anySet())).thenReturn(selected);
        when(router.resolveGroup(anyString(), anyString())).thenReturn("chat");
        when(router.resolveSceneDefinition(anyString(), anyString())).thenReturn(null);
        when(stateCenter.allowRequest("model-1")).thenReturn(true);
        when(maskService.mask(anyString())).thenReturn(
                new MaskResult("MASK", Map.of("MASK", "plain"), true));
        when(maskService.unmask(any(), anyString())).thenAnswer(invocation ->
                invocation.<String>getArgument(1).replace("MASK", "plain"));
        doNothing().when(delegate).doChat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));

        ModelProxyFactory factory = new ModelProxyFactory(router, stateCenter, maskService);
        StreamingChatModel proxy = factory.createStreamingProxy("zh", "chat");
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
                .languages(Set.of())
                .scenes(Set.of())
                .capabilities(Set.of(ModelCapability.CHAT, ModelCapability.STREAMING_CHAT))
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .build();
    }
}
