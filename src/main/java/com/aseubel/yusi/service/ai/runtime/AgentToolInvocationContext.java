package com.aseubel.yusi.service.ai.runtime;

import com.aseubel.yusi.service.ai.tool.constant.AgentToolAccessMode;
import com.aseubel.yusi.service.ai.tool.constant.AgentToolIdempotencyMode;

public record AgentToolInvocationContext(
        String userId,
        String runId,
        String localToolCallId,
        String toolName,
        String toolSource,
        AgentToolAccessMode accessMode,
        AgentToolIdempotencyMode idempotencyMode,
        String capabilityVersion) {

    public AgentToolInvocationContext {
        accessMode = accessMode == null ? AgentToolAccessMode.UNKNOWN : accessMode;
        idempotencyMode = idempotencyMode == null ? AgentToolIdempotencyMode.NONE : idempotencyMode;
    }
}
