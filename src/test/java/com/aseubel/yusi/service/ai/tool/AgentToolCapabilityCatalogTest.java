package com.aseubel.yusi.service.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.aseubel.yusi.pojo.constant.AgentToolConstants;
import com.aseubel.yusi.service.ai.tool.constant.AgentToolAccessMode;
import com.aseubel.yusi.service.ai.tool.constant.AgentToolIdempotencyMode;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolCapabilityCatalogTest {

    @Test
    void localCapabilityUsesActualToolSchemaAndPermissionPolicy() {
        AgentToolCapabilityCatalog catalog = new AgentToolCapabilityCatalog(new ObjectMapper());

        catalog.registerLocal(new LocalMemoryTool());

        AgentToolCapability capability = catalog.findByName(AgentToolConstants.SEARCH_MEMORIES).orElseThrow();
        assertEquals(AgentToolConstants.SOURCE_LOCAL, capability.source());
        assertEquals("v1", capability.version());
        assertEquals("memory.read", capability.permissionScopes().iterator().next());
        assertEquals(AgentToolAccessMode.READ, capability.accessMode());
        assertEquals(AgentToolRetryPolicy.TIMEOUT_ONCE, capability.retryPolicy());
        assertTrue(capability.parameterSchemaJson().contains("query"));
        assertTrue(capability.parameterSchemaJson().contains("startDate"));
        assertEquals(java.time.Duration.ofSeconds(15), capability.executionPolicy().timeout());
        assertEquals(Duration.ofSeconds(30), capability.executionPolicy().totalDeadline());
    }

    @Test
    void mcpCapabilityIsRegisteredAndVersionIsExposedAsToolMetadata() {
        AgentToolCapabilityCatalog catalog = new AgentToolCapabilityCatalog(new ObjectMapper());
        ToolSpecification specification = ToolSpecification.builder()
                .name(AgentToolConstants.WEB_SEARCH)
                .description("Search the web")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("query")
                        .required("query")
                        .build())
                .build();

        ToolSpecification mapped = catalog.mapMcpSpecification(null, specification);

        AgentToolCapability capability = catalog.findByName(AgentToolConstants.WEB_SEARCH).orElseThrow();
        assertEquals(AgentToolConstants.SOURCE_MCP, capability.source());
        assertEquals("network.read", capability.permissionScopes().iterator().next());
        assertEquals(AgentToolAccessMode.READ, capability.accessMode());
        assertEquals(AgentToolRetryPolicy.TIMEOUT_ONCE, capability.retryPolicy());
        assertTrue(capability.parameterSchemaJson().contains("query"));
        assertEquals(java.time.Duration.ofSeconds(20), capability.executionPolicy().timeout());
        assertEquals(Duration.ofSeconds(30), capability.executionPolicy().totalDeadline());
        assertEquals("v1", mapped.metadata().get(AgentToolCapabilityCatalog.METADATA_VERSION));
        assertNotNull(mapped.metadata().get(AgentToolCapabilityCatalog.METADATA_PERMISSION_SCOPES));
        assertEquals("read", mapped.metadata().get(AgentToolCapabilityCatalog.METADATA_ACCESS_MODE));
        assertEquals("timeout_once", mapped.metadata().get(AgentToolCapabilityCatalog.METADATA_RETRY_POLICY));
    }

    @Test
    void unknownMcpCapabilityDefaultsToUnknownAndDeniesRetry() {
        AgentToolCapabilityCatalog catalog = new AgentToolCapabilityCatalog(new ObjectMapper());
        ToolSpecification specification = ToolSpecification.builder()
                .name("unknown_mcp_tool")
                .description("Unknown provider tool")
                .build();

        catalog.mapMcpSpecification(null, specification);

        AgentToolCapability capability = catalog.find(
                "unknown_mcp_tool", AgentToolConstants.SOURCE_MCP).orElseThrow();
        assertEquals(AgentToolAccessMode.UNKNOWN, capability.accessMode());
        assertEquals(AgentToolRetryPolicy.DENY, capability.retryPolicy());
    }

    @Test
    void updatePersonaDeclaresIdempotentWrite() {
        AgentToolCapabilityCatalog catalog = new AgentToolCapabilityCatalog(new ObjectMapper());
        catalog.registerLocal(new PersonaTool());

        AgentToolCapability capability = catalog.findByName(AgentToolConstants.UPDATE_USER_PERSONA)
                .orElseThrow();

        assertEquals(AgentToolAccessMode.WRITE, capability.accessMode());
        assertEquals(AgentToolIdempotencyMode.IDEMPOTENT_WRITE, capability.idempotencyMode());
        assertEquals(AgentToolRetryPolicy.DENY, capability.retryPolicy());
    }

    @Test
    void legacyCapabilitiesDefaultToNoIdempotency() {
        AgentToolCapability capability = new AgentToolCapability(
                "legacy", AgentToolConstants.SOURCE_LOCAL, "v1", "test", "{}", Set.of(),
                new AgentToolExecutionPolicy(Duration.ofSeconds(1)));

        assertEquals(AgentToolIdempotencyMode.NONE, capability.idempotencyMode());
    }

    @Test
    void toolExecutionPolicyRejectsLogicalDeadlineAboveThirtySeconds() {
        assertThrows(IllegalArgumentException.class,
                () -> new AgentToolExecutionPolicy(Duration.ofSeconds(1), Duration.ofSeconds(31)));
    }

    private static final class LocalMemoryTool {

        @Tool(name = AgentToolConstants.SEARCH_MEMORIES, value = "Search memories")
        public String search(
                @P("The search query") String query,
                @P("Optional start date") String startDate,
                @P("Optional end date") String endDate) {
            return query + startDate + endDate;
        }
    }

    private static final class PersonaTool {

        @Tool(name = AgentToolConstants.UPDATE_USER_PERSONA, value = "Update persona")
        public String update(String preferredName) {
            return preferredName;
        }
    }
}
