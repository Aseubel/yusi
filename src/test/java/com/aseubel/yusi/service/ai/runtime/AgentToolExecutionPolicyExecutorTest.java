package com.aseubel.yusi.service.ai.runtime;

import com.aseubel.yusi.service.ai.model.ModelRouteContext;
import com.aseubel.yusi.service.ai.model.ModelRouteContextHolder;
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
import java.util.concurrent.TimeUnit;

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

    private ToolExecutionRequest request() {
        return ToolExecutionRequest.builder()
                .name("testTool")
                .arguments("{}")
                .build();
    }
}
