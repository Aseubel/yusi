package com.aseubel.yusi.service.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aseubel.yusi.pojo.constant.AgentToolConstants;
import com.aseubel.yusi.service.ai.tool.constant.AgentToolCapabilityConstants;
import com.aseubel.yusi.service.ai.tool.constant.AgentToolPermission;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.mcp.client.McpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Runtime catalog for the tool contract exposed to the Agent.
 *
 * <p>Local schemas are generated from the actual {@code @Tool} methods. MCP
 * schemas are registered from the server-provided specification. User data is
 * intentionally outside this catalog.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentToolCapabilityCatalog {

    public static final String METADATA_VERSION = AgentToolCapabilityConstants.METADATA_VERSION;
    public static final String METADATA_PERMISSION_SCOPES = AgentToolCapabilityConstants.METADATA_PERMISSION_SCOPES;
    public static final String METADATA_SOURCE = AgentToolCapabilityConstants.METADATA_SOURCE;

    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, AgentToolCapability> capabilities = new ConcurrentHashMap<>();

    public void registerLocal(Object tool) {
        if (tool == null) {
            return;
        }
        try {
            ToolSpecifications.toolSpecificationsFrom(tool)
                    .forEach(specification -> register(specification, AgentToolConstants.SOURCE_LOCAL));
        } catch (RuntimeException exception) {
            log.warn("Unable to register local Agent tool capability", exception);
        }
    }

    public ToolSpecification mapMcpSpecification(McpClient client, ToolSpecification specification) {
        if (specification == null) {
            return null;
        }
        AgentToolCapability capability = register(specification, AgentToolConstants.SOURCE_MCP);
        return specification.toBuilder()
                .addMetadata(METADATA_VERSION, capability.version())
                .addMetadata(METADATA_PERMISSION_SCOPES, capability.permissionScopes())
                .addMetadata(METADATA_SOURCE, capability.source())
                .build();
    }

    public Optional<AgentToolCapability> find(String name, String source) {
        if (name == null || source == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(capabilities.get(key(name, source)));
    }

    public Optional<AgentToolCapability> findByName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return capabilities.values().stream()
                .filter(capability -> name.equals(capability.name()))
                .sorted(Comparator.comparing(AgentToolCapability::source))
                .findFirst();
    }

    public List<AgentToolCapability> list() {
        return capabilities.values().stream()
                .sorted(Comparator.comparing(AgentToolCapability::source)
                        .thenComparing(AgentToolCapability::name))
                .toList();
    }

    private AgentToolCapability register(ToolSpecification specification, String source) {
        AgentToolCapability capability = new AgentToolCapability(
                specification.name(),
                source,
                AgentToolCapabilityConstants.VERSION_V1,
                specification.description(),
                parameterSchemaJson(specification),
                permissionScopes(specification.name(), source),
                executionPolicy(specification.name(), source));
        capabilities.put(key(capability.name(), capability.source()), capability);
        return capability;
    }

    private String parameterSchemaJson(ToolSpecification specification) {
        try {
            JsonNode root = objectMapper.readTree(specification.toJson());
            JsonNode parameters = root.get("parameters");
            return parameters == null ? "{}" : objectMapper.writeValueAsString(parameters);
        } catch (Exception exception) {
            log.warn("Unable to serialize Agent tool parameter schema: tool={}", specification.name());
            return String.valueOf(specification.parameters());
        }
    }

    private Set<String> permissionScopes(String toolName, String source) {
        if (AgentToolConstants.UPDATE_USER_PERSONA.equals(toolName)) {
            return Set.of(AgentToolPermission.PERSONA_WRITE.code());
        }
        if (AgentToolConstants.SOURCE_MCP.equals(source)) {
            return Set.of(AgentToolPermission.NETWORK_READ.code());
        }
        if (AgentToolConstants.SEARCH_MEMORIES.equals(toolName)
                || AgentToolConstants.SEARCH_DIARY.equals(toolName)
                || AgentToolConstants.SEARCH_LIFE_GRAPH.equals(toolName)) {
            return Set.of(AgentToolPermission.MEMORY_READ.code());
        }
        return Set.of(AgentToolPermission.TOOL_EXECUTE.code());
    }

    private AgentToolExecutionPolicy executionPolicy(String toolName, String source) {
        if (AgentToolConstants.UPDATE_USER_PERSONA.equals(toolName)) {
            return AgentToolExecutionPolicy.PERSONA_WRITE;
        }
        if (AgentToolConstants.SOURCE_MCP.equals(source)) {
            return AgentToolExecutionPolicy.NETWORK_READ;
        }
        if (AgentToolConstants.SEARCH_MEMORIES.equals(toolName)
                || AgentToolConstants.SEARCH_DIARY.equals(toolName)
                || AgentToolConstants.SEARCH_LIFE_GRAPH.equals(toolName)) {
            return AgentToolExecutionPolicy.MEMORY_READ;
        }
        return AgentToolExecutionPolicy.DEFAULT;
    }

    private String key(String name, String source) {
        return source + ':' + name;
    }
}
