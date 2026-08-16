package com.aseubel.yusi.service.ai.tool;

import java.time.Duration;

public record AgentToolRetryPolicy(int maxRetries, Duration backoff) {

    public static final AgentToolRetryPolicy DENY = new AgentToolRetryPolicy(0, Duration.ZERO);
    public static final AgentToolRetryPolicy TIMEOUT_ONCE =
            new AgentToolRetryPolicy(1, Duration.ofMillis(100));

    public AgentToolRetryPolicy {
        if (maxRetries < 0 || maxRetries > 1) {
            throw new IllegalArgumentException("Agent tool retries must be between 0 and 1");
        }
        if (backoff == null || backoff.isNegative()) {
            throw new IllegalArgumentException("Agent tool retry backoff must not be negative");
        }
    }

    public String code() {
        return maxRetries == 0 ? "deny" : "timeout_once";
    }

    public boolean allowsRetry(int retryCount) {
        return retryCount >= 0 && retryCount < maxRetries;
    }
}
