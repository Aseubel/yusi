package com.aseubel.yusi.service.ai.runtime;

import com.aseubel.yusi.service.ai.model.ModelRouteContext;
import com.aseubel.yusi.service.ai.model.ModelRouteContextHolder;
import com.aseubel.yusi.service.ai.tool.AgentToolExecutionPolicy;
import com.aseubel.yusi.service.ai.tool.AgentToolRetryPolicy;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolExecutionPolicyExecutorTest {

    private final ExecutorService toolExecutor = Executors.newFixedThreadPool(1);

    @AfterEach
    void tearDown() {
        ModelRouteContextHolder.clear();
        toolExecutor.shutdownNow();
    }

    @Test
    void timeoutInterruptsDelegateAndRaisesFixedTimeoutException() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        ToolExecutor delegate = (request, memoryId) -> {
            started.countDown();
            try {
                Thread.sleep(5_000L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted", exception);
            }
            return "unexpected";
        };
        AgentToolExecutionPolicyExecutor executor = new AgentToolExecutionPolicyExecutor(
                delegate, Duration.ofMillis(50), toolExecutor);

        assertThrows(AgentToolTimeoutException.class,
                () -> executor.execute(request(), "user-1"));
        assertTrue(started.await(1, TimeUnit.SECONDS));
    }

    @Test
    void cancellationTokenInterruptsRunningDelegate() throws Exception {
        AgentCancellationToken token = new AgentCancellationToken();
        CountDownLatch started = new CountDownLatch(1);
        ToolExecutor delegate = (request, memoryId) -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted", exception);
            }
            return "unexpected";
        };
        AgentToolExecutionPolicyExecutor executor = new AgentToolExecutionPolicyExecutor(
                delegate, Duration.ofSeconds(5), toolExecutor);
        ExecutorService caller = Executors.newSingleThreadExecutor();

        Future<String> result = caller.submit(() -> {
            ModelRouteContextHolder.set(ModelRouteContext.builder()
                    .cancellationToken(token)
                    .build());
            try {
                return executor.execute(request(), "user-1");
            } finally {
                ModelRouteContextHolder.clear();
            }
        });

        assertTrue(started.await(1, TimeUnit.SECONDS));
        token.cancel();
        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> result.get(1, TimeUnit.SECONDS));
        assertInstanceOf(AgentToolCancelledException.class, failure.getCause());
        caller.shutdownNow();
    }

    @Test
    void retriesOneTimeoutAndThenReturnsTheSuccessfulResult() {
        AtomicInteger calls = new AtomicInteger();
        ToolExecutor delegate = (request, memoryId) -> {
            if (calls.incrementAndGet() == 1) {
                throw new AgentToolTimeoutException("testTool");
            }
            return "ok";
        };
        AgentToolExecutionPolicyExecutor executor = new AgentToolExecutionPolicyExecutor(
                delegate,
                new AgentToolExecutionPolicy(Duration.ofMillis(50), Duration.ofMillis(250)),
                AgentToolRetryPolicy.TIMEOUT_ONCE,
                toolExecutor,
                AgentToolExecutionAttemptObserver.NOOP,
                "testTool");

        assertEquals("ok", executor.execute(request(), "user-1"));
        assertEquals(2, calls.get());
    }

    @Test
    void ordinaryToolFailureIsNotRetried() {
        AtomicInteger calls = new AtomicInteger();
        ToolExecutor delegate = (request, memoryId) -> {
            calls.incrementAndGet();
            throw new IllegalStateException("tool failed");
        };
        AgentToolExecutionPolicyExecutor executor = new AgentToolExecutionPolicyExecutor(
                delegate,
                new AgentToolExecutionPolicy(Duration.ofMillis(50), Duration.ofMillis(250)),
                AgentToolRetryPolicy.TIMEOUT_ONCE,
                toolExecutor,
                AgentToolExecutionAttemptObserver.NOOP,
                "testTool");

        assertThrows(IllegalStateException.class, () -> executor.execute(request(), "user-1"));
        assertEquals(1, calls.get());
    }

    @Test
    void cancellationFailureIsNotRetried() {
        AtomicInteger calls = new AtomicInteger();
        ToolExecutor delegate = (request, memoryId) -> {
            calls.incrementAndGet();
            throw new AgentToolCancelledException("testTool");
        };
        AgentToolExecutionPolicyExecutor executor = new AgentToolExecutionPolicyExecutor(
                delegate,
                new AgentToolExecutionPolicy(Duration.ofMillis(50), Duration.ofMillis(250)),
                AgentToolRetryPolicy.TIMEOUT_ONCE,
                toolExecutor,
                AgentToolExecutionAttemptObserver.NOOP,
                "testTool");

        assertThrows(AgentToolCancelledException.class, () -> executor.execute(request(), "user-1"));
        assertEquals(1, calls.get());
    }

    @Test
    void cancellationDuringBackoffPreventsTheSecondAttempt() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AgentCancellationToken token = new AgentCancellationToken();
        ExecutorService caller = Executors.newSingleThreadExecutor();
        ScheduledExecutorService canceller = Executors.newSingleThreadScheduledExecutor();
        ToolExecutor delegate = (request, memoryId) -> {
            calls.incrementAndGet();
            throw new AgentToolTimeoutException("testTool");
        };
        AgentToolExecutionPolicyExecutor executor = new AgentToolExecutionPolicyExecutor(
                delegate,
                new AgentToolExecutionPolicy(Duration.ofMillis(50), Duration.ofSeconds(2)),
                new AgentToolRetryPolicy(1, Duration.ofSeconds(1)),
                toolExecutor,
                AgentToolExecutionAttemptObserver.NOOP,
                "testTool");
        Future<String> result = caller.submit(() -> {
            ModelRouteContextHolder.set(ModelRouteContext.builder()
                    .cancellationToken(token)
                    .build());
            try {
                return executor.execute(request(), "user-1");
            } finally {
                ModelRouteContextHolder.clear();
            }
        });

        canceller.schedule(token::cancel, 50, TimeUnit.MILLISECONDS);
        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> result.get(1, TimeUnit.SECONDS));
        assertInstanceOf(AgentToolCancelledException.class, failure.getCause());
        assertEquals(1, calls.get());
        canceller.shutdownNow();
        caller.shutdownNow();
    }

    @Test
    void logicalDeadlineBoundsBothAttempts() {
        AtomicInteger calls = new AtomicInteger();
        ToolExecutor delegate = (request, memoryId) -> {
            calls.incrementAndGet();
            try {
                Thread.sleep(100);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return "late";
        };
        AgentToolExecutionPolicyExecutor executor = new AgentToolExecutionPolicyExecutor(
                delegate,
                new AgentToolExecutionPolicy(Duration.ofMillis(40), Duration.ofMillis(100)),
                AgentToolRetryPolicy.TIMEOUT_ONCE,
                toolExecutor,
                AgentToolExecutionAttemptObserver.NOOP,
                "testTool");

        assertThrows(AgentToolTimeoutException.class, () -> executor.execute(request(), "user-1"));
        assertTrue(calls.get() <= 2);
    }

    private ToolExecutionRequest request() {
        return ToolExecutionRequest.builder()
                .name("testTool")
                .arguments("{}")
                .build();
    }
}
