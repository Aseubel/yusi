package com.aseubel.yusi.service.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.aseubel.yusi.pojo.constant.AgentToolConstants;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentToolExecutionPolicyServiceTest {

    private final ExecutorService executor = Executors.newFixedThreadPool(1);

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void localAnnotatedToolsUseTheSharedExecutionBoundary() {
        AgentToolCapabilityCatalog catalog = mock(AgentToolCapabilityCatalog.class);
        when(catalog.find(eq("echo"), eq(AgentToolConstants.SOURCE_LOCAL)))
                .thenReturn(Optional.of(capability(Duration.ofSeconds(1))));
        AgentToolExecutionPolicyService service = new AgentToolExecutionPolicyService(catalog, executor);

        Map<ToolSpecification, ToolExecutor> executors = service.localExecutors(new LocalTool());

        assertEquals(1, executors.size());
        ToolExecutor toolExecutor = executors.values().iterator().next();
        assertEquals("hello", toolExecutor.execute(request("echo", "{\"value\":\"hello\"}"), "user-1"));
        verify(catalog).find("echo", AgentToolConstants.SOURCE_LOCAL);
    }

    @Test
    void dynamicMcpProviderExecutorsAreWrappedAndProviderMetadataIsPreserved() {
        AgentToolCapabilityCatalog catalog = mock(AgentToolCapabilityCatalog.class);
        when(catalog.find(anyString(), eq(AgentToolConstants.SOURCE_MCP)))
                .thenReturn(Optional.of(capability(Duration.ofSeconds(1))));
        AgentToolExecutionPolicyService service = new AgentToolExecutionPolicyService(catalog, executor);

        ToolSpecification specification = ToolSpecification.builder()
                .name(AgentToolConstants.WEB_SEARCH)
                .description("Search the web")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("query")
                        .required("query")
                        .build())
                .build();
        ToolExecutor delegate = (request, memoryId) -> "search-result";
        ToolProvider provider = new ToolProvider() {
            @Override
            public ToolProviderResult provideTools(dev.langchain4j.service.tool.ToolProviderRequest request) {
                return ToolProviderResult.builder()
                        .immediateReturnToolNames(Set.of(AgentToolConstants.WEB_SEARCH))
                        .add(AiServiceTool.builder()
                                .toolSpecification(specification)
                                .toolExecutor(delegate)
                                .returnBehavior(ReturnBehavior.IMMEDIATE)
                                .immediateReturn(true)
                                .build())
                        .build();
            }

            @Override
            public boolean isDynamic() {
                return true;
            }
        };

        ToolProvider wrapped = service.wrapProvider(provider);
        ToolProviderResult result = wrapped.provideTools(null);

        assertTrue(wrapped.isDynamic());
        assertEquals(Set.of(AgentToolConstants.WEB_SEARCH), result.immediateReturnToolNames());
        ToolExecutor wrappedExecutor = result.toolExecutorByName(AgentToolConstants.WEB_SEARCH);
        assertNotSame(delegate, wrappedExecutor);
        assertEquals("search-result", wrappedExecutor.execute(
                request(AgentToolConstants.WEB_SEARCH, "{\"query\":\"test\"}"), "user-1"));
        verify(catalog).find(AgentToolConstants.WEB_SEARCH, AgentToolConstants.SOURCE_MCP);
    }

    private AgentToolCapability capability(Duration timeout) {
        return new AgentToolCapability(
                "tool",
                AgentToolConstants.SOURCE_LOCAL,
                "v1",
                "test",
                "{}",
                Set.of(),
                new AgentToolExecutionPolicy(timeout));
    }

    private ToolExecutionRequest request(String name, String arguments) {
        return ToolExecutionRequest.builder()
                .name(name)
                .arguments(arguments)
                .build();
    }

    private static final class LocalTool {

        @Tool(name = "echo", value = "Echo a value")
        public String echo(@P("Value") String value) {
            return value;
        }
    }
}
