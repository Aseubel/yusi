package com.aseubel.yusi.service.ai.runtime;

import dev.langchain4j.agent.tool.ToolExecutionRequest;

@FunctionalInterface
public interface AgentToolExecutionAttemptObserver {

    AgentToolExecutionAttemptObserver NOOP = request -> {
    };

    void onRetry(ToolExecutionRequest request);
}
