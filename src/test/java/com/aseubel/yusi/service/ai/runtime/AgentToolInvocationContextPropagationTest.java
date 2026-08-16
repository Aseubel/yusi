package com.aseubel.yusi.service.ai.runtime;

import com.aseubel.yusi.service.ai.tool.AgentToolExecutionPolicy;
import com.aseubel.yusi.service.ai.tool.AgentToolRetryPolicy;
import com.aseubel.yusi.service.ai.tool.constant.AgentToolAccessMode;
import com.aseubel.yusi.service.ai.tool.constant.AgentToolIdempotencyMode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecutor;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentToolInvocationContextPropagationTest {

    @Test
    void contextIsCapturedBeforeSubmitAndVisibleOnlyDuringDelegate() {
        ExecutorService workers = Executors.newFixedThreadPool(1);
        try {
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name("testTool")
                    .build();
            AgentToolInvocationContext context = new AgentToolInvocationContext(
                    "user-1", "run-1", "local-1", "testTool", "local",
                    AgentToolAccessMode.READ, AgentToolIdempotencyMode.NONE, "v1");
            AgentToolInvocationContextProvider provider = identity ->
                    identity == request ? Optional.of(context) : Optional.empty();
            AtomicReference<AgentToolInvocationContext> seen = new AtomicReference<>();

            ToolExecutor delegate = (ignored, memoryId) -> {
                seen.set(AgentToolInvocationContextHolder.current());
                return "ok";
            };
            AgentToolExecutionPolicyExecutor executor = new AgentToolExecutionPolicyExecutor(
                    delegate, new AgentToolExecutionPolicy(Duration.ofSeconds(1)),
                    AgentToolRetryPolicy.DENY, AgentToolAccessMode.READ,
                    AgentToolIdempotencyMode.NONE, workers,
                    AgentToolExecutionAttemptObserver.NOOP, provider, "testTool");

            assertEquals("ok", executor.execute(request, "user-1"));
            assertEquals(context, seen.get());
            assertNull(AgentToolInvocationContextHolder.current());
        } finally {
            workers.shutdownNow();
        }
    }
}
