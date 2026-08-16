package com.aseubel.yusi.service.ai.runtime;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Request-scoped cancellation signal that remains valid after the chat session
 * is removed from the active-session registry.
 */
public final class AgentCancellationToken {

    private final AtomicBoolean cancelled = new AtomicBoolean();

    public boolean cancel() {
        return cancelled.compareAndSet(false, true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }
}
