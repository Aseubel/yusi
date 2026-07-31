package com.aseubel.yusi.service.ai;

import dev.langchain4j.model.chat.response.StreamingHandle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks active AI chat streams so a disconnect or explicit cancel can stop
 * the provider stream and release request resources immediately.
 */
@Component
@Slf4j
public class ChatStreamCancellationRegistry {

    private final ConcurrentMap<RequestKey, ChatStreamSession> sessions = new ConcurrentHashMap<>();

    public ChatStreamSession register(String userId, String requestId, SseEmitter emitter, Runnable cleanup) {
        RequestKey key = new RequestKey(requireNonBlank(userId, "userId"), requireNonBlank(requestId, "requestId"));
        AtomicReference<ChatStreamSession> sessionRef = new AtomicReference<>();
        ChatStreamSession session = new ChatStreamSession(key, emitter, cleanup,
                () -> sessions.remove(key, sessionRef.get()));
        sessionRef.set(session);
        if (sessions.putIfAbsent(key, session) != null) {
            throw new IllegalStateException("An AI chat request with the same ID is already active");
        }
        return session;
    }

    public Optional<ChatStreamSession> find(String userId, String requestId) {
        if (userId == null || requestId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessions.get(new RequestKey(userId, requestId)));
    }

    public boolean cancel(String userId, String requestId) {
        return find(userId, requestId)
                .map(ChatStreamSession::cancel)
                .orElse(false);
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private record RequestKey(String userId, String requestId) {
    }

    public static final class ChatStreamSession {

        private enum State {
            ACTIVE,
            CANCELLED,
            COMPLETED,
            FAILED
        }

        private final RequestKey key;
        private final SseEmitter emitter;
        private final Runnable cleanup;
        private final Runnable removeFromRegistry;
        private final AtomicReference<State> state = new AtomicReference<>(State.ACTIVE);
        private final AtomicReference<StreamingHandle> streamingHandle = new AtomicReference<>();
        private final AtomicBoolean cleanupStarted = new AtomicBoolean();

        private ChatStreamSession(RequestKey key, SseEmitter emitter, Runnable cleanup,
                Runnable removeFromRegistry) {
            this.key = key;
            this.emitter = Objects.requireNonNull(emitter, "emitter");
            this.cleanup = Objects.requireNonNull(cleanup, "cleanup");
            this.removeFromRegistry = Objects.requireNonNull(removeFromRegistry, "removeFromRegistry");
        }

        public String getUserId() {
            return key.userId();
        }

        public String getRequestId() {
            return key.requestId();
        }

        public boolean isActive() {
            return state.get() == State.ACTIVE;
        }

        public void bind(StreamingHandle handle) {
            if (handle == null) {
                return;
            }
            if (!isActive()) {
                cancelHandle(handle);
                return;
            }

            streamingHandle.set(handle);
            if (!isActive() && streamingHandle.compareAndSet(handle, null)) {
                cancelHandle(handle);
            }
        }

        public boolean cancel() {
            if (!state.compareAndSet(State.ACTIVE, State.CANCELLED)) {
                return false;
            }

            cancelHandle(streamingHandle.getAndSet(null));
            try {
                emitter.complete();
            } finally {
                cleanupOnce();
            }
            return true;
        }

        public boolean complete() {
            if (!state.compareAndSet(State.ACTIVE, State.COMPLETED)) {
                return false;
            }

            streamingHandle.set(null);
            try {
                emitter.complete();
            } finally {
                cleanupOnce();
            }
            return true;
        }

        public boolean fail(Throwable error) {
            if (!state.compareAndSet(State.ACTIVE, State.FAILED)) {
                return false;
            }

            cancelHandle(streamingHandle.getAndSet(null));
            try {
                emitter.completeWithError(error);
            } finally {
                cleanupOnce();
            }
            return true;
        }

        private void cleanupOnce() {
            if (!cleanupStarted.compareAndSet(false, true)) {
                return;
            }
            try {
                cleanup.run();
            } finally {
                removeFromRegistry.run();
            }
        }

        private void cancelHandle(StreamingHandle handle) {
            if (handle == null) {
                return;
            }
            try {
                handle.cancel();
            } catch (RuntimeException exception) {
                log.debug("Unable to cancel AI streaming handle for request {}", key.requestId(), exception);
            }
        }
    }
}
