package com.aseubel.yusi.service.ai.tool;

import java.time.Duration;

public record AgentToolExecutionPolicy(Duration timeout) {

    public static final AgentToolExecutionPolicy MEMORY_READ =
            new AgentToolExecutionPolicy(Duration.ofSeconds(15));
    public static final AgentToolExecutionPolicy PERSONA_WRITE =
            new AgentToolExecutionPolicy(Duration.ofSeconds(10));
    public static final AgentToolExecutionPolicy NETWORK_READ =
            new AgentToolExecutionPolicy(Duration.ofSeconds(20));
    public static final AgentToolExecutionPolicy DEFAULT =
            new AgentToolExecutionPolicy(Duration.ofSeconds(10));

    public AgentToolExecutionPolicy {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Tool timeout must be positive");
        }
    }
}
