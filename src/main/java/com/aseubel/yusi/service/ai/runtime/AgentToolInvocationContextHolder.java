package com.aseubel.yusi.service.ai.runtime;

import java.util.ArrayDeque;
import java.util.Deque;

public final class AgentToolInvocationContextHolder {

    private static final ThreadLocal<Deque<AgentToolInvocationContext>> HOLDER = new ThreadLocal<>();

    private AgentToolInvocationContextHolder() {
    }

    public static AgentToolInvocationContext current() {
        Deque<AgentToolInvocationContext> contexts = HOLDER.get();
        return contexts == null ? null : contexts.peek();
    }

    public static Scope open(AgentToolInvocationContext context) {
        if (context == null) {
            return new Scope(false);
        }
        Deque<AgentToolInvocationContext> contexts = HOLDER.get();
        if (contexts == null) {
            contexts = new ArrayDeque<>();
            HOLDER.set(contexts);
        }
        contexts.push(context);
        return new Scope(true);
    }

    private static void clear() {
        Deque<AgentToolInvocationContext> contexts = HOLDER.get();
        if (contexts == null || contexts.isEmpty()) {
            HOLDER.remove();
            return;
        }
        contexts.pop();
        if (contexts.isEmpty()) {
            HOLDER.remove();
        }
    }

    public static final class Scope implements AutoCloseable {

        private final boolean ownsFrame;
        private boolean closed;

        private Scope(boolean ownsFrame) {
            this.ownsFrame = ownsFrame;
        }

        @Override
        public void close() {
            if (!closed && ownsFrame) {
                closed = true;
                AgentToolInvocationContextHolder.clear();
            }
        }
    }
}
