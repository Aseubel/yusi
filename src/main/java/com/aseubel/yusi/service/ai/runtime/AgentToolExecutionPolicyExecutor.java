package com.aseubel.yusi.service.ai.runtime;

import com.aseubel.yusi.service.ai.model.ModelRouteContext;
import com.aseubel.yusi.service.ai.model.ModelRouteContextHolder;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
    private final Duration timeout;
    private final ExecutorService executor;
    private final String toolName;

    public AgentToolExecutionPolicyExecutor(ToolExecutor delegate, Duration timeout,
            ExecutorService executor) {
        this(delegate, timeout, executor, "unknown");
    }

    public AgentToolExecutionPolicyExecutor(ToolExecutor delegate, Duration timeout,
            ExecutorService executor, String toolName) {
        this.delegate = delegate;
        this.timeout = timeout;
        this.executor = executor;
        this.toolName = toolName == null || toolName.isBlank() ? "unknown" : toolName;
    }

    @Override
    public String execute(ToolExecutionRequest request, Object memoryId) {
        ModelRouteContext routeContext = ModelRouteContextHolder.getEffective();
        return await(() -> delegate.execute(request, memoryId), routeContext);
    }

    @Override
    public ToolExecutionResult executeWithContext(ToolExecutionRequest request,
            InvocationContext context) {
        ModelRouteContext routeContext = ModelRouteContextHolder.getEffective();
        return await(() -> delegate.executeWithContext(request, context), routeContext);
    }

    private <T> T await(Callable<T> operation, ModelRouteContext routeContext) {
        AgentCancellationToken cancellationToken = routeContext == null
                ? null
                : routeContext.getCancellationToken();
        if (isCancelled(cancellationToken)) {
            throw new AgentToolCancelledException(toolName);
        }

        Future<T> future = executor.submit(() -> executeWithContext(operation, routeContext));
        long deadline = System.nanoTime() + timeout.toNanos();
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

    private <T> T executeWithContext(Callable<T> operation, ModelRouteContext routeContext)
            throws Exception {
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
