package com.aseubel.yusi.service.ai.runtime;

import com.aseubel.yusi.pojo.constant.AgentToolConstants;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AgentToolExecutionAttemptRegistryTest {

    @Mock
    private AgentToolTraceService traceService;

    @Test
    void recordsOnlyOneRetryForOneRegisteredRequest() {
        AgentToolExecutionAttemptRegistry registry = new AgentToolExecutionAttemptRegistry(traceService);
        ToolExecutionRequest request = request();
        registry.register("user-1", "run-1", request, null,
                AgentToolConstants.SEARCH_MEMORIES, AgentToolConstants.SOURCE_LOCAL, "local-1");

        registry.onRetry(request);
        registry.onRetry(request);

        verify(traceService).incrementAttemptCount("user-1", "run-1", "local-1");
    }

    @Test
    void completedOrClearedRequestCannotUpdateTrace() {
        AgentToolExecutionAttemptRegistry registry = new AgentToolExecutionAttemptRegistry(traceService);
        ToolExecutionRequest completedRequest = request();
        registry.register("user-1", "run-1", completedRequest, null,
                AgentToolConstants.SEARCH_MEMORIES, AgentToolConstants.SOURCE_LOCAL, "local-1");
        registry.complete(completedRequest);
        registry.onRetry(completedRequest);

        ToolExecutionRequest clearedRequest = request();
        registry.register("user-1", "run-1", clearedRequest, null,
                AgentToolConstants.SEARCH_MEMORIES, AgentToolConstants.SOURCE_LOCAL, "local-2");
        registry.clearRun("user-1", "run-1");
        registry.onRetry(clearedRequest);

        verify(traceService, never()).incrementAttemptCount(eq("user-1"), eq("run-1"), eq("local-2"));
    }

    private ToolExecutionRequest request() {
        return ToolExecutionRequest.builder()
                .name(AgentToolConstants.SEARCH_MEMORIES)
                .arguments("{}")
                .build();
    }
}
