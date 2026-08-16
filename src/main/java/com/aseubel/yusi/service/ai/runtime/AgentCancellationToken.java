package com.aseubel.yusi.service.ai.runtime;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Request-scoped cancellation signal that remains valid after the chat session
 * is removed from the active-session registry.
 */
public final class AgentCancellationToken {

    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final CountDownLatch cancellationSignal = new CountDownLatch(1);

    public boolean cancel() {
        boolean changed = cancelled.compareAndSet(false, true);
        if (changed) {
            cancellationSignal.countDown();
        }
        return changed;
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public boolean awaitCancellation(long timeout, TimeUnit unit) throws InterruptedException {
        if (timeout <= 0L) {
            return isCancelled();
        }
        return cancellationSignal.await(timeout, unit);
    }
}
