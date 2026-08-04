package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.common.utils.SensitiveWordUtils;
import com.aseubel.yusi.pojo.dto.chat.AgentStreamEvent;
import com.aseubel.yusi.pojo.dto.chat.ChatCancelRequest;
import com.aseubel.yusi.pojo.dto.chat.ChatRequest;
import com.aseubel.yusi.service.ai.runtime.AiLockService;
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
    private final ChatStreamCancellationRegistry registry = new ChatStreamCancellationRegistry();
    private final AtomicReference<Runnable> submittedTask = new AtomicReference<>();

    private RecordingAiController controller;

    @BeforeEach
    void setUp() {
        controller = new RecordingAiController(threadPoolExecutor, sensitiveWordUtils);
        ReflectionTestUtils.setField(controller, "diaryAssistant", diaryAssistant);
        ReflectionTestUtils.setField(controller, "aiLockService", aiLockService);
        ReflectionTestUtils.setField(controller, "ossService", ossService);
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
                .build(), "zh-CN");
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
                .build(), null);
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
    void emitsSafeToolLifecycleEventsForMcpToolFailure() {
        TokenStream tokenStream = tokenStream();
        when(diaryAssistant.chatWithMessage(eq("user-1"), anyString(), anyList())).thenReturn(tokenStream);

        controller.chatStream(ChatRequest.builder()
                .requestId("request-tool")
                .message("查一下最新消息")
                .build(), "zh-CN");
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
        assertEquals("tool-call-1", toolStarted.toolCallId());
        assertEquals("web_search", toolStarted.toolName());
        assertEquals("mcp", toolStarted.toolSource());
        assertNotNull(toolCompleted);
        assertEquals("tool-call-1", toolCompleted.toolCallId());
        assertFalse(toolCompleted.success());
        assertEquals(1500L, toolCompleted.durationMs());
        assertTrue(controller.events().stream().allMatch(event -> event.text() == null));
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
