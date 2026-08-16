package com.aseubel.yusi.service.ai.tool;

import com.aseubel.yusi.service.ai.tool.constant.AgentToolAccessMode;
import com.aseubel.yusi.service.ai.tool.constant.AgentToolIdempotencyMode;

import java.util.Set;

/**
 * Immutable, non-user-specific contract metadata for one executable Agent tool.
 */
public record AgentToolCapability(
        String name,
        String source,
        String version,
        String description,
        String parameterSchemaJson,
        Set<String> permissionScopes,
        AgentToolExecutionPolicy executionPolicy,
        AgentToolAccessMode accessMode,
        AgentToolRetryPolicy retryPolicy,
        AgentToolIdempotencyMode idempotencyMode) {

    public AgentToolCapability(String name, String source, String version, String description,
            String parameterSchemaJson, Set<String> permissionScopes,
            AgentToolExecutionPolicy executionPolicy) {
        this(name, source, version, description, parameterSchemaJson, permissionScopes,
                executionPolicy, AgentToolAccessMode.UNKNOWN, AgentToolRetryPolicy.DENY,
                AgentToolIdempotencyMode.NONE);
    }

    public AgentToolCapability(String name, String source, String version, String description,
            String parameterSchemaJson, Set<String> permissionScopes,
            AgentToolExecutionPolicy executionPolicy, AgentToolAccessMode accessMode,
            AgentToolRetryPolicy retryPolicy) {
        this(name, source, version, description, parameterSchemaJson, permissionScopes,
                executionPolicy, accessMode, retryPolicy, AgentToolIdempotencyMode.NONE);
    }

    public AgentToolCapability {
        permissionScopes = permissionScopes == null ? Set.of() : Set.copyOf(permissionScopes);
        parameterSchemaJson = parameterSchemaJson == null ? "{}" : parameterSchemaJson;
        executionPolicy = executionPolicy == null ? AgentToolExecutionPolicy.DEFAULT : executionPolicy;
        accessMode = accessMode == null ? AgentToolAccessMode.UNKNOWN : accessMode;
        retryPolicy = retryPolicy == null ? AgentToolRetryPolicy.DENY : retryPolicy;
        idempotencyMode = idempotencyMode == null ? AgentToolIdempotencyMode.NONE : idempotencyMode;
    }
}
