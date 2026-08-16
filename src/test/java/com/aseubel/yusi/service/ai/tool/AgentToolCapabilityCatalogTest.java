package com.aseubel.yusi.service.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.aseubel.yusi.pojo.constant.AgentToolConstants;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        assertTrue(capability.parameterSchemaJson().contains("query"));
        assertTrue(capability.parameterSchemaJson().contains("startDate"));
        assertEquals(java.time.Duration.ofSeconds(15), capability.executionPolicy().timeout());
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
        assertTrue(capability.parameterSchemaJson().contains("query"));
        assertEquals(java.time.Duration.ofSeconds(20), capability.executionPolicy().timeout());
        assertEquals("v1", mapped.metadata().get(AgentToolCapabilityCatalog.METADATA_VERSION));
        assertNotNull(mapped.metadata().get(AgentToolCapabilityCatalog.METADATA_PERMISSION_SCOPES));
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
}
