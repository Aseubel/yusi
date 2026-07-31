package com.aseubel.yusi.service.ai;

import dev.langchain4j.model.chat.response.StreamingHandle;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ChatStreamCancellationRegistryTest {

    @Test
    void cancelBeforeHandleBindingCompletesEmitterAndRunsCleanupOnce() {
        ChatStreamCancellationRegistry registry = new ChatStreamCancellationRegistry();
        SseEmitter emitter = mock(SseEmitter.class);
        AtomicInteger cleanupCount = new AtomicInteger();

        registry.register("user-1", "request-1", emitter, cleanupCount::incrementAndGet);

        assertTrue(registry.cancel("user-1", "request-1"));
        assertFalse(registry.cancel("user-1", "request-1"));

        verify(emitter).complete();
        assertTrue(cleanupCount.compareAndSet(1, 1));
        assertTrue(registry.find("user-1", "request-1").isEmpty());
    }

    @Test
    void handleBoundAfterCancellationIsCancelledImmediately() {
        ChatStreamCancellationRegistry registry = new ChatStreamCancellationRegistry();
        SseEmitter emitter = mock(SseEmitter.class);
        CountingStreamingHandle handle = new CountingStreamingHandle();
        ChatStreamCancellationRegistry.ChatStreamSession session = registry.register(
                "user-1", "request-1", emitter, () -> {
                });

        assertTrue(session.cancel());
        session.bind(handle);

        assertTrue(handle.cancelCount.compareAndSet(1, 1));
        verify(emitter).complete();
    }

    @Test
    void repeatedCancellationCancelsAAlreadyBoundHandleOnlyOnce() {
        ChatStreamCancellationRegistry registry = new ChatStreamCancellationRegistry();
        SseEmitter emitter = mock(SseEmitter.class);
        CountingStreamingHandle handle = new CountingStreamingHandle();
        ChatStreamCancellationRegistry.ChatStreamSession session = registry.register(
                "user-1", "request-1", emitter, () -> {
                });
        session.bind(handle);

        assertTrue(session.cancel());
        assertFalse(session.cancel());

        assertTrue(handle.cancelCount.compareAndSet(1, 1));
        verify(emitter, times(1)).complete();
    }

    @Test
    void normalCompletionDoesNotCancelModelAndIsIdempotent() {
        ChatStreamCancellationRegistry registry = new ChatStreamCancellationRegistry();
        SseEmitter emitter = mock(SseEmitter.class);
        CountingStreamingHandle handle = new CountingStreamingHandle();
        ChatStreamCancellationRegistry.ChatStreamSession session = registry.register(
                "user-1", "request-1", emitter, () -> {
                });
        session.bind(handle);

        assertTrue(session.complete());
        assertFalse(session.complete());
        assertFalse(session.cancel());

        assertTrue(handle.cancelCount.compareAndSet(0, 0));
        verify(emitter, times(1)).complete();
    }

    @Test
    void failureCancelsModelAndRunsCleanup() {
        ChatStreamCancellationRegistry registry = new ChatStreamCancellationRegistry();
        SseEmitter emitter = mock(SseEmitter.class);
        CountingStreamingHandle handle = new CountingStreamingHandle();
        AtomicInteger cleanupCount = new AtomicInteger();
        ChatStreamCancellationRegistry.ChatStreamSession session = registry.register(
                "user-1", "request-1", emitter, cleanupCount::incrementAndGet);
        session.bind(handle);

        assertTrue(session.fail(new IllegalStateException("provider failed")));

        assertTrue(handle.cancelCount.compareAndSet(1, 1));
        assertTrue(cleanupCount.compareAndSet(1, 1));
        verify(emitter).completeWithError(org.mockito.ArgumentMatchers.any(Throwable.class));
        assertTrue(registry.find("user-1", "request-1").isEmpty());
    }

    @Test
    void cancellationIsIsolatedByUserId() {
        ChatStreamCancellationRegistry registry = new ChatStreamCancellationRegistry();
        SseEmitter firstEmitter = mock(SseEmitter.class);
        SseEmitter secondEmitter = mock(SseEmitter.class);
        registry.register("user-1", "request-1", firstEmitter, () -> {
        });
        registry.register("user-2", "request-1", secondEmitter, () -> {
        });

        assertFalse(registry.cancel("user-2", "missing-request"));
        assertTrue(registry.cancel("user-1", "request-1"));

        verify(firstEmitter).complete();
        verify(secondEmitter, never()).complete();
        assertTrue(registry.find("user-2", "request-1").isPresent());
    }

    private static final class CountingStreamingHandle implements StreamingHandle {
        private final AtomicInteger cancelCount = new AtomicInteger();

        @Override
        public void cancel() {
            cancelCount.incrementAndGet();
        }

        @Override
        public boolean isCancelled() {
            return cancelCount.get() > 0;
        }
    }
}
