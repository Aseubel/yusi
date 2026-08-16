package com.aseubel.yusi.service.ai.runtime;

import com.aseubel.yusi.service.ai.model.ModelRouteContext;
import com.aseubel.yusi.service.ai.model.ModelRouteContextHolder;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import com.aseubel.yusi.service.ai.tool.AgentToolExecutionPolicy;
import com.aseubel.yusi.service.ai.tool.AgentToolRetryPolicy;

/**
 * Applies the execution boundary shared by local and MCP tools.
 *
 * <p>The delegate runs on a dedicated executor so the Agent thread can observe
 * request cancellation and enforce a deadline. Interrupting a delegate is
 * best-effort and relies on the underlying client honoring interruption.</p>
 */
public final class AgentToolExecutionPolicyExecutor implements ToolExecutor {

    private static final Duration POLL_INTERVAL = Duration.ofMillis(25);

    private final ToolExecutor delegate;
    private final AgentToolExecutionPolicy executionPolicy;
    private final AgentToolRetryPolicy retryPolicy;
    private final ExecutorService executor;
    private final AgentToolExecutionAttemptObserver attemptObserver;
    private final String toolName;

    public AgentToolExecutionPolicyExecutor(ToolExecutor delegate, Duration timeout,
            ExecutorService executor) {
        this(delegate, new AgentToolExecutionPolicy(timeout), AgentToolRetryPolicy.DENY,
                executor, AgentToolExecutionAttemptObserver.NOOP, "unknown");
    }

    public AgentToolExecutionPolicyExecutor(ToolExecutor delegate, Duration timeout,
            ExecutorService executor, String toolName) {
        this(delegate, new AgentToolExecutionPolicy(timeout), AgentToolRetryPolicy.DENY,
                executor, AgentToolExecutionAttemptObserver.NOOP, toolName);
    }

    public AgentToolExecutionPolicyExecutor(ToolExecutor delegate,
            AgentToolExecutionPolicy executionPolicy, AgentToolRetryPolicy retryPolicy,
            ExecutorService executor, AgentToolExecutionAttemptObserver attemptObserver,
            String toolName) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.executionPolicy = Objects.requireNonNull(executionPolicy, "executionPolicy");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.attemptObserver = Objects.requireNonNull(attemptObserver, "attemptObserver");
        this.toolName = toolName == null || toolName.isBlank() ? "unknown" : toolName;
    }

    @Override
    public String execute(ToolExecutionRequest request, Object memoryId) {
        ModelRouteContext routeContext = ModelRouteContextHolder.getEffective();
        return await(() -> delegate.execute(request, memoryId), routeContext, request);
    }

    @Override
    public ToolExecutionResult executeWithContext(ToolExecutionRequest request,
            InvocationContext context) {
        ModelRouteContext routeContext = ModelRouteContextHolder.getEffective();
        return await(() -> delegate.executeWithContext(request, context), routeContext, request);
    }

    private <T> T await(Callable<T> operation, ModelRouteContext routeContext,
            ToolExecutionRequest request) {
        AgentCancellationToken cancellationToken = routeContext == null
                ? null
                : routeContext.getCancellationToken();
        long logicalDeadline = System.nanoTime() + executionPolicy.totalDeadline().toNanos();
        int retryCount = 0;
        while (true) {
            checkCancelled(cancellationToken);
            long remaining = logicalDeadline - System.nanoTime();
            if (remaining <= 0L) {
                throw new AgentToolTimeoutException(toolName);
            }

            try {
                Duration attemptTimeout = Duration.ofNanos(Math.min(
                        executionPolicy.timeout().toNanos(), remaining));
                return awaitOneAttempt(operation, routeContext, request, attemptTimeout,
                        retryCount, cancellationToken);
            } catch (AgentToolTimeoutException timeoutException) {
                if (!retryPolicy.allowsRetry(retryCount)) {
                    throw timeoutException;
                }
                checkCancelled(cancellationToken);
                awaitRetryBackoff(retryPolicy.backoff(), cancellationToken, logicalDeadline);
                retryCount++;
            }
        }
    }

    private <T> T awaitOneAttempt(Callable<T> operation, ModelRouteContext routeContext,
            ToolExecutionRequest request, Duration attemptTimeout, int retryCount,
            AgentCancellationToken cancellationToken) {
        Future<T> future = executor.submit(() -> executeWithContext(operation, routeContext,
                request, retryCount, cancellationToken));
        long deadline = System.nanoTime() + attemptTimeout.toNanos();
        try {
            while (true) {
                if (isCancelled(cancellationToken)) {
                    future.cancel(true);
                    throw new AgentToolCancelledException(toolName);
                }

                long remaining = deadline - System.nanoTime();
                if (remaining <= 0L) {
                    future.cancel(true);
                    throw new AgentToolTimeoutException(toolName);
                }

                try {
                    return future.get(Math.min(remaining, POLL_INTERVAL.toNanos()), TimeUnit.NANOSECONDS);
                } catch (TimeoutException ignored) {
                    // Poll the cancellation token again before waiting longer.
                }
            }
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new AgentToolCancelledException(toolName);
        } catch (ExecutionException exception) {
            throw propagate(exception.getCause());
        }
    }

    private <T> T executeWithContext(Callable<T> operation, ModelRouteContext routeContext,
            ToolExecutionRequest request, int retryCount, AgentCancellationToken cancellationToken)
            throws Exception {
        checkCancelled(cancellationToken);
        if (retryCount > 0) {
            notifyRetry(request);
        }
        if (routeContext == null) {
            return operation.call();
        }
        ModelRouteContextHolder.set(routeContext);
        try {
            return operation.call();
        } finally {
            ModelRouteContextHolder.clear();
        }
    }

    private void awaitRetryBackoff(Duration backoff, AgentCancellationToken cancellationToken,
            long logicalDeadline) {
        long remaining = logicalDeadline - System.nanoTime();
        if (remaining <= 0L) {
            throw new AgentToolTimeoutException(toolName);
        }
        long waitNanos = Math.min(backoff.toNanos(), remaining);
        try {
            if (cancellationToken == null) {
                TimeUnit.NANOSECONDS.sleep(waitNanos);
            } else if (cancellationToken.awaitCancellation(waitNanos, TimeUnit.NANOSECONDS)) {
                throw new AgentToolCancelledException(toolName);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AgentToolCancelledException(toolName);
        }
        checkCancelled(cancellationToken);
        if (logicalDeadline - System.nanoTime() <= 0L) {
            throw new AgentToolTimeoutException(toolName);
        }
    }

    private void notifyRetry(ToolExecutionRequest request) {
        try {
            attemptObserver.onRetry(request);
        } catch (RuntimeException ignored) {
            // Trace accounting must never turn a valid tool retry into a tool failure.
        }
    }

    private void checkCancelled(AgentCancellationToken cancellationToken) {
        if (isCancelled(cancellationToken)) {
            throw new AgentToolCancelledException(toolName);
        }
    }

    private boolean isCancelled(AgentCancellationToken cancellationToken) {
        return cancellationToken != null && cancellationToken.isCancelled();
    }

    private RuntimeException propagate(Throwable cause) {
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        throw new RuntimeException(cause);
    }
}
