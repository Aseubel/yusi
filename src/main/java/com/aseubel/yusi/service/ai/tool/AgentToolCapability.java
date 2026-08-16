package com.aseubel.yusi.service.ai.tool;

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
        AgentToolExecutionPolicy executionPolicy) {

    public AgentToolCapability {
        permissionScopes = permissionScopes == null ? Set.of() : Set.copyOf(permissionScopes);
        parameterSchemaJson = parameterSchemaJson == null ? "{}" : parameterSchemaJson;
        executionPolicy = executionPolicy == null ? AgentToolExecutionPolicy.DEFAULT : executionPolicy;
    }
}
