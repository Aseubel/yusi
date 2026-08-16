package com.aseubel.yusi.service.ai.tool.constant;

import java.time.Duration;

public final class AgentToolIdempotencyConstants {

    public static final Duration CLAIM_LEASE = Duration.ofMinutes(5);
    public static final Duration LEDGER_RETENTION = Duration.ofDays(30);
    public static final String MAINTENANCE_CRON = "0 0 4 * * ?";

    public static final String BLOCKED_IN_PROGRESS =
            "TOOL_EXECUTION_BLOCKED: IDEMPOTENCY_IN_PROGRESS; operation was not executed.";
    public static final String BLOCKED_ALREADY_COMPLETED =
            "TOOL_EXECUTION_BLOCKED: IDEMPOTENCY_ALREADY_COMPLETED; operation was not repeated.";
    public static final String BLOCKED_PREVIOUS_FAILURE =
            "TOOL_EXECUTION_BLOCKED: IDEMPOTENCY_PREVIOUS_FAILURE; operation requires a new explicit invocation.";
    public static final String BLOCKED_UNKNOWN =
            "TOOL_EXECUTION_BLOCKED: IDEMPOTENCY_UNKNOWN; operation was not replayed to avoid duplicate side effects.";
    public static final String BLOCKED_CONTEXT_MISSING =
            "TOOL_EXECUTION_BLOCKED: IDEMPOTENCY_CONTEXT_MISSING; operation was not executed.";

    private AgentToolIdempotencyConstants() {
    }
}
