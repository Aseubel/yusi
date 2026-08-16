package com.aseubel.yusi.service.ai.tool;

import java.time.Duration;

public record AgentToolExecutionPolicy(Duration timeout, Duration totalDeadline) {

    public static final Duration MAX_TOTAL_DEADLINE = Duration.ofSeconds(30);

    public static final AgentToolExecutionPolicy MEMORY_READ =
            new AgentToolExecutionPolicy(Duration.ofSeconds(15), MAX_TOTAL_DEADLINE);
    public static final AgentToolExecutionPolicy PERSONA_WRITE =
            new AgentToolExecutionPolicy(Duration.ofSeconds(10), Duration.ofSeconds(10));
    public static final AgentToolExecutionPolicy NETWORK_READ =
            new AgentToolExecutionPolicy(Duration.ofSeconds(20), MAX_TOTAL_DEADLINE);
    public static final AgentToolExecutionPolicy DEFAULT =
            new AgentToolExecutionPolicy(Duration.ofSeconds(10), Duration.ofSeconds(10));

    public AgentToolExecutionPolicy(Duration timeout) {
        this(timeout, timeout);
    }

    public AgentToolExecutionPolicy {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Tool timeout must be positive");
        }
        if (totalDeadline == null || totalDeadline.isZero() || totalDeadline.isNegative()) {
            throw new IllegalArgumentException("Tool total deadline must be positive");
        }
        if (totalDeadline.compareTo(timeout) < 0) {
            throw new IllegalArgumentException("Tool total deadline must cover one attempt");
        }
        if (totalDeadline.compareTo(MAX_TOTAL_DEADLINE) > 0) {
            throw new IllegalArgumentException("Tool total deadline must not exceed 30 seconds");
        }
    }
}
