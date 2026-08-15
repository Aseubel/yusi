package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.common.utils.SensitiveWordUtils;
import com.aseubel.yusi.pojo.dto.chat.AgentStreamEvent;
import com.aseubel.yusi.pojo.dto.chat.ChatCancelRequest;
import com.aseubel.yusi.pojo.dto.chat.ChatRequest;
import com.aseubel.yusi.service.ai.runtime.AiLockService;
import com.aseubel.yusi.service.ai.runtime.AgentRunTraceService;
import com.aseubel.yusi.service.ai.runtime.AgentToolTraceService;
import com.aseubel.yusi.service.ai.runtime.ChatStreamCancellationRegistry;
import com.aseubel.yusi.service.diary.Assistant;
import com.aseubel.yusi.service.oss.OssService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutionResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiControllerCancellationTest {

    private final ThreadPoolTaskExecutor threadPoolExecutor = mock(ThreadPoolTaskExecutor.class);
    private final SensitiveWordUtils sensitiveWordUtils = mock(SensitiveWordUtils.class);
    private final AiLockService aiLockService = mock(AiLockService.class);
    private final Assistant diaryAssistant = mock(Assistant.class);
    private final OssService ossService = mock(OssService.class);
    private final AgentRunTraceService agentRunTraceService = mock(AgentRunTraceService.class);
    private final AgentToolTraceService agentToolTraceService = mock(AgentToolTraceService.class);
    private final ChatStreamCancellationRegistry registry = new ChatStreamCancellationRegistry();
    private final AtomicReference<Runnable> submittedTask = new AtomicReference<>();

    private RecordingAiController controller;

    @BeforeEach
    void setUp() {
        controller = new RecordingAiController(threadPoolExecutor, sensitiveWordUtils);
        ReflectionTestUtils.setField(controller, "diaryAssistant", diaryAssistant);
        ReflectionTestUtils.setField(controller, "aiLockService", aiLockService);
        ReflectionTestUtils.setField(controller, "ossService", ossService);
        ReflectionTestUtils.setField(controller, "agentRunTraceService", agentRunTraceService);
        ReflectionTestUtils.setField(controller, "agentToolTraceService", agentToolTraceService);
        ReflectionTestUtils.setField(controller, "chatStreamCancellationRegistry", registry);

        UserContext.setUserId("user-1");
        when(aiLockService.tryAcquireLock("user-1")).thenReturn(true);
        when(sensitiveWordUtils.checkAndHandleViolation(anyString(), anyString())).thenReturn(null);
        doAnswer(invocation -> {
            submittedTask.set(invocation.getArgument(0));
            return null;
        }).when(threadPoolExecutor).execute(any(Runnable.class));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private TokenStream tokenStream() {
        TokenStream tokenStream = mock(TokenStream.class, RETURNS_SELF);
        doReturn(tokenStream).when(tokenStream).onPartialResponseWithContext(any(BiConsumer.class));
        doReturn(tokenStream).when(tokenStream).onPartialThinkingWithContext(any(BiConsumer.class));
        doReturn(tokenStream).when(tokenStream).onPartialToolCallWithContext(any(BiConsumer.class));
        return tokenStream;
    }

    @Test
    void cancelEndpointCancelsHandleThatArrivesAfterTheCancelRequest() {
        TokenStream tokenStream = tokenStream();
        when(diaryAssistant.chatWithMessage(eq("user-1"), anyString(), anyList())).thenReturn(tokenStream);

        controller.chatStream(ChatRequest.builder()
                .requestId("request-1")
                .message("hello")
                .images(List.of())
                .build());
        submittedTask.get().run();
        UserContext.setUserId("user-1");

        ArgumentCaptor<BiConsumer<PartialResponse, PartialResponseContext>> responseCaptor =
                ArgumentCaptor.forClass(BiConsumer.class);
        verify(tokenStream).onPartialResponseWithContext(responseCaptor.capture());

        StreamingHandle handle = mock(StreamingHandle.class);
        Response<Void> result = controller.cancelChat(new ChatCancelRequest("request-1"));

        assertEquals(200, result.getCode());
        assertTrue(registry.find("user-1", "request-1").isEmpty());

        responseCaptor.getValue().accept(new PartialResponse("late token"), new PartialResponseContext(handle));

        verify(handle).cancel();
        verify(aiLockService).releaseLock("user-1");
    }

    @Test
    void cancellationIsIdempotentAndLateCompletionCannotReleaseLockTwice() {
        TokenStream tokenStream = tokenStream();
        when(diaryAssistant.chatWithMessage(eq("user-1"), anyString(), anyList())).thenReturn(tokenStream);

        controller.chatStream(ChatRequest.builder()
                .requestId("request-2")
                .message("hello")
                .build());
        submittedTask.get().run();
        UserContext.setUserId("user-1");

        ArgumentCaptor<java.util.function.Consumer<dev.langchain4j.model.chat.response.ChatResponse>> completionCaptor =
                ArgumentCaptor.forClass(java.util.function.Consumer.class);
        verify(tokenStream).onCompleteResponse(completionCaptor.capture());

        StreamingHandle handle = mock(StreamingHandle.class);
        ArgumentCaptor<BiConsumer<PartialResponse, PartialResponseContext>> responseCaptor =
                ArgumentCaptor.forClass(BiConsumer.class);
        verify(tokenStream).onPartialResponseWithContext(responseCaptor.capture());
        responseCaptor.getValue().accept(new PartialResponse("token"), new PartialResponseContext(handle));

        controller.cancelChat(new ChatCancelRequest("request-2"));
        controller.cancelChat(new ChatCancelRequest("request-2"));
        completionCaptor.getValue().accept(null);

        verify(handle, times(1)).cancel();
        verify(aiLockService, times(1)).releaseLock("user-1");
    }

    @Test
    void providerErrorEmitsRunFailedAndClosesSseNormally() {
        TokenStream tokenStream = tokenStream();
        when(diaryAssistant.chatWithMessage(eq("user-1"), anyString(), anyList())).thenReturn(tokenStream);

        controller.chatStream(ChatRequest.builder()
                .requestId("request-provider-error")
                .message("hello")
                .build());
        submittedTask.get().run();
        UserContext.setUserId("user-1");

        ArgumentCaptor<Consumer<Throwable>> errorCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(tokenStream).onError(errorCaptor.capture());

        errorCaptor.getValue().accept(new IllegalStateException("provider failed"));

        assertTrue(controller.events().stream().anyMatch(event -> "run.failed".equals(event.type())));
        assertTrue(registry.find("user-1", "request-provider-error").isEmpty());
        verify(aiLockService).releaseLock("user-1");
    }

    @Test
    void completedChatPassesUnicodeCodePointCountToRunTrace() {
        TokenStream tokenStream = tokenStream();
        when(diaryAssistant.chatWithMessage(eq("user-1"), anyString(), anyList())).thenReturn(tokenStream);

        controller.chatStream(ChatRequest.builder()
                .requestId("request-response")
                .message("hello")
                .build());
        submittedTask.get().run();
        UserContext.setUserId("user-1");

        ArgumentCaptor<BiConsumer<PartialResponse, PartialResponseContext>> responseCaptor =
                ArgumentCaptor.forClass(BiConsumer.class);
        ArgumentCaptor<Consumer<dev.langchain4j.model.chat.response.ChatResponse>> completeCaptor =
                ArgumentCaptor.forClass(Consumer.class);
        verify(tokenStream).onPartialResponseWithContext(responseCaptor.capture());
        verify(tokenStream).onCompleteResponse(completeCaptor.capture());

        responseCaptor.getValue().accept(new PartialResponse("你好"), null);
        completeCaptor.getValue().accept(null);

        verify(agentRunTraceService).complete("user-1", "request-response", 2L);
    }

    @Test
    void providerFailurePassesPartialResponseCountToRunTrace() {
        TokenStream tokenStream = tokenStream();
        when(diaryAssistant.chatWithMessage(eq("user-1"), anyString(), anyList())).thenReturn(tokenStream);

        controller.chatStream(ChatRequest.builder()
                .requestId("request-response-failed")
                .message("hello")
                .build());
        submittedTask.get().run();
        UserContext.setUserId("user-1");

        ArgumentCaptor<BiConsumer<PartialResponse, PartialResponseContext>> responseCaptor =
                ArgumentCaptor.forClass(BiConsumer.class);
        ArgumentCaptor<Consumer<Throwable>> errorCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(tokenStream).onPartialResponseWithContext(responseCaptor.capture());
        verify(tokenStream).onError(errorCaptor.capture());

        responseCaptor.getValue().accept(new PartialResponse("partial"), null);
        errorCaptor.getValue().accept(new IllegalStateException("provider failed"));

        verify(agentRunTraceService).fail("user-1", "request-response-failed", "agent_error", 7L);
    }

    @Test
    void cancellationPassesPartialResponseCountToRunTraceCleanup() {
        TokenStream tokenStream = tokenStream();
        when(diaryAssistant.chatWithMessage(eq("user-1"), anyString(), anyList())).thenReturn(tokenStream);

        controller.chatStream(ChatRequest.builder()
                .requestId("request-response-cancelled")
                .message("hello")
                .build());
        submittedTask.get().run();
        UserContext.setUserId("user-1");

        ArgumentCaptor<BiConsumer<PartialResponse, PartialResponseContext>> responseCaptor =
                ArgumentCaptor.forClass(BiConsumer.class);
        verify(tokenStream).onPartialResponseWithContext(responseCaptor.capture());
        responseCaptor.getValue().accept(new PartialResponse("partial"), null);

        controller.cancelChat(new ChatCancelRequest("request-response-cancelled"));

        verify(agentRunTraceService).cancel("user-1", "request-response-cancelled", "stream_closed", 7L);
    }

    @Test
    void sensitiveWordFallbackPassesFallbackResponseCountToRunTrace() {
        TokenStream tokenStream = tokenStream();
        when(sensitiveWordUtils.checkAndHandleViolation(anyString(), anyString()))
                .thenReturn("请换一种方式描述这个问题");

        controller.chatStream(ChatRequest.builder()
                .requestId("request-response-fallback")
                .message("blocked")
                .build());
        submittedTask.get().run();

        verify(agentRunTraceService).complete("user-1", "request-response-fallback", 12L);
        assertTrue(controller.events().stream()
                .anyMatch(event -> "请换一种方式描述这个问题".equals(event.text())));
        verify(diaryAssistant, org.mockito.Mockito.never())
                .chatWithMessage(eq("user-1"), anyString(), anyList());
    }

    @Test
    void emitsSafeToolLifecycleEventsForMcpToolFailure() {
        TokenStream tokenStream = tokenStream();
        when(diaryAssistant.chatWithMessage(eq("user-1"), anyString(), anyList())).thenReturn(tokenStream);

        controller.chatStream(ChatRequest.builder()
                .requestId("request-tool")
                .message("查一下最新消息")
                .build());
        submittedTask.get().run();
        UserContext.setUserId("user-1");

        ArgumentCaptor<Consumer<BeforeToolExecution>> beforeCaptor = ArgumentCaptor.forClass(Consumer.class);
        ArgumentCaptor<Consumer<ToolExecution>> completedCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(tokenStream).beforeToolExecution(beforeCaptor.capture());
        verify(tokenStream).onToolExecuted(completedCaptor.capture());

        InvocationContext invocationContext = mock(InvocationContext.class);
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("tool-call-1")
                .name("web_search")
                .arguments("{\"query\":\"secret\"}")
                .build();

        beforeCaptor.getValue().accept(BeforeToolExecution.builder()
                .request(request)
                .invocationContext(invocationContext)
                .build());
        completedCaptor.getValue().accept(ToolExecution.builder()
                .request(request)
                .result(ToolExecutionResult.builder()
                        .isError(true)
                        .resultText("private tool output")
                        .build())
                .startTime(LocalDateTime.of(2026, 8, 4, 12, 0, 0))
                .finishTime(LocalDateTime.of(2026, 8, 4, 12, 0, 1, 500_000_000))
                .invocationContext(invocationContext)
                .build());

        AgentStreamEvent toolStarted = controller.events().stream()
                .filter(event -> "tool.started".equals(event.type()))
                .findFirst()
                .orElse(null);
        AgentStreamEvent toolCompleted = controller.events().stream()
                .filter(event -> "tool.completed".equals(event.type()))
                .findFirst()
                .orElse(null);

        assertNotNull(toolStarted);
        assertNotNull(toolStarted.toolCallId());
        assertFalse(toolStarted.toolCallId().isBlank());
        assertEquals("web_search", toolStarted.toolName());
        assertEquals("mcp", toolStarted.toolSource());
        assertNotNull(toolCompleted);
        assertEquals(toolStarted.toolCallId(), toolCompleted.toolCallId());
        assertFalse(toolCompleted.success());
        assertEquals(1500L, toolCompleted.durationMs());
        assertTrue(controller.events().stream().allMatch(event -> event.text() == null));
        verify(agentToolTraceService).start(eq("user-1"), eq("request-tool"),
                eq(toolStarted.toolCallId()), eq("tool-call-1"), eq("web_search"), eq("mcp"));
        verify(agentToolTraceService).complete(eq("user-1"), eq("request-tool"),
                eq(toolStarted.toolCallId()), eq(1500L), eq(true));
    }

    @Test
    void generatesLocalToolIdWhenUpstreamIdIsNull() {
        TokenStream tokenStream = tokenStream();
        when(diaryAssistant.chatWithMessage(eq("user-1"), anyString(), anyList())).thenReturn(tokenStream);

        controller.chatStream(ChatRequest.builder()
                .requestId("request-tool-null-id")
                .message("搜索我的记忆")
                .build());
        submittedTask.get().run();
        UserContext.setUserId("user-1");

        ArgumentCaptor<Consumer<BeforeToolExecution>> beforeCaptor = ArgumentCaptor.forClass(Consumer.class);
        ArgumentCaptor<Consumer<ToolExecution>> completedCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(tokenStream).beforeToolExecution(beforeCaptor.capture());
        verify(tokenStream).onToolExecuted(completedCaptor.capture());

        InvocationContext invocationContext = mock(InvocationContext.class);
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id(null)
                .name("searchMemories")
                .arguments("{\"query\":\"private\"}")
                .build();

        beforeCaptor.getValue().accept(BeforeToolExecution.builder()
                .request(request)
                .invocationContext(invocationContext)
                .build());
        completedCaptor.getValue().accept(ToolExecution.builder()
                .request(request)
                .result(ToolExecutionResult.builder()
                        .isError(false)
                        .resultText("private result")
                        .build())
                .startTime(LocalDateTime.of(2026, 8, 4, 12, 0, 0))
                .finishTime(LocalDateTime.of(2026, 8, 4, 12, 0, 1))
                .invocationContext(invocationContext)
                .build());

        AgentStreamEvent toolStarted = controller.events().stream()
                .filter(event -> "tool.started".equals(event.type()))
                .findFirst()
                .orElseThrow();
        AgentStreamEvent toolCompleted = controller.events().stream()
                .filter(event -> "tool.completed".equals(event.type()))
                .findFirst()
                .orElseThrow();

        assertTrue(toolStarted.toolCallId() != null && !toolStarted.toolCallId().isBlank());
        assertEquals(toolStarted.toolCallId(), toolCompleted.toolCallId());
        assertTrue(toolCompleted.success());
        verify(agentToolTraceService).start(eq("user-1"), eq("request-tool-null-id"),
                eq(toolStarted.toolCallId()), eq((String) null), eq("searchMemories"), eq("local"));
        verify(agentToolTraceService).complete(eq("user-1"), eq("request-tool-null-id"),
                eq(toolStarted.toolCallId()), eq(1000L), eq(false));
    }

    private static final class RecordingAiController extends AiController {

        private final List<AgentStreamEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();

        private RecordingAiController(ThreadPoolTaskExecutor threadPoolExecutor,
                SensitiveWordUtils sensitiveWordUtils) {
            super(threadPoolExecutor, sensitiveWordUtils);
        }

        @Override
        protected void sendAgentEvent(SseEmitter emitter,
                ChatStreamCancellationRegistry.ChatStreamSession session,
                AgentStreamEvent event) {
            if (session.isActive()) {
                events.add(event);
            }
        }

        private List<AgentStreamEvent> events() {
            return events;
        }
    }
}
