package com.aseubel.yusi.service.ai.tool;

import com.aseubel.yusi.pojo.constant.AgentToolConstants;
import com.aseubel.yusi.service.ai.runtime.AgentToolExecutionAttemptObserver;
import com.aseubel.yusi.service.ai.runtime.AgentToolInvocationContextProvider;
import com.aseubel.yusi.service.ai.runtime.AgentToolExecutionPolicyExecutor;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/** Builds one execution boundary for local and provider-backed tools. */
@Component
public class AgentToolExecutionPolicyService {

    private final AgentToolCapabilityCatalog capabilityCatalog;

    private final ExecutorService executor;

    private final AgentToolExecutionAttemptObserver attemptObserver;

    private final AgentToolInvocationContextProvider invocationContextProvider;

    public AgentToolExecutionPolicyService(AgentToolCapabilityCatalog capabilityCatalog,
            @Qualifier("agentToolExecutionExecutor") ExecutorService executor) {
        this(capabilityCatalog, executor, AgentToolExecutionAttemptObserver.NOOP);
    }

    public AgentToolExecutionPolicyService(AgentToolCapabilityCatalog capabilityCatalog,
            @Qualifier("agentToolExecutionExecutor") ExecutorService executor,
            AgentToolExecutionAttemptObserver attemptObserver) {
        this(capabilityCatalog, executor, attemptObserver, AgentToolInvocationContextProvider.NOOP);
    }

    @Autowired
    public AgentToolExecutionPolicyService(AgentToolCapabilityCatalog capabilityCatalog,
            @Qualifier("agentToolExecutionExecutor") ExecutorService executor,
            AgentToolExecutionAttemptObserver attemptObserver,
            AgentToolInvocationContextProvider invocationContextProvider) {
        this.capabilityCatalog = capabilityCatalog;
        this.executor = executor;
        this.attemptObserver = attemptObserver;
        this.invocationContextProvider = invocationContextProvider;
    }

    public Map<ToolSpecification, ToolExecutor> localExecutors(Object... tools) {
        Map<ToolSpecification, ToolExecutor> executors = new LinkedHashMap<>();
        if (tools == null) {
            return executors;
        }
        for (Object tool : tools) {
            if (tool == null) {
                continue;
            }
            for (Method method : tool.getClass().getMethods()) {
                if (!method.isAnnotationPresent(Tool.class)) {
                    continue;
                }
                ToolSpecification specification = ToolSpecifications.toolSpecificationFrom(method);
                executors.put(specification, wrap(specification,
                        new DefaultToolExecutor(tool, method), AgentToolConstants.SOURCE_LOCAL));
            }
        }
        return executors;
    }

    public ToolProvider wrapProvider(ToolProvider provider) {
        if (provider == null) {
            return null;
        }
        return new ToolProvider() {
            @Override
            public ToolProviderResult provideTools(ToolProviderRequest request) {
                return wrapResult(provider.provideTools(request));
            }

            @Override
            public boolean isDynamic() {
                return provider.isDynamic();
            }
        };
    }

    private ToolProviderResult wrapResult(ToolProviderResult result) {
        if (result == null) {
            return null;
        }
        ToolProviderResult.Builder builder = ToolProviderResult.builder()
                .immediateReturnToolNames(result.immediateReturnToolNames());
        List<AiServiceTool> tools = result.aiServiceTools();
        if (tools != null) {
            for (AiServiceTool tool : tools) {
                ToolSpecification specification = tool.toolSpecification();
                ToolExecutor wrapped = wrap(specification, tool.toolExecutor(), AgentToolConstants.SOURCE_MCP);
                builder.add(tool.toBuilder().toolExecutor(wrapped).build());
            }
        }
        return builder.build();
    }

    private ToolExecutor wrap(ToolSpecification specification, ToolExecutor delegate, String source) {
        AgentToolCapability capability = capabilityCatalog.find(specification.name(), source)
                .orElse(null);
        AgentToolExecutionPolicy executionPolicy = capability == null
                ? AgentToolExecutionPolicy.DEFAULT
                : capability.executionPolicy();
        AgentToolRetryPolicy retryPolicy = capability == null
                ? AgentToolRetryPolicy.DENY
                : capability.retryPolicy();
        var accessMode = capability == null
                ? com.aseubel.yusi.service.ai.tool.constant.AgentToolAccessMode.UNKNOWN
                : capability.accessMode();
        var idempotencyMode = capability == null
                ? com.aseubel.yusi.service.ai.tool.constant.AgentToolIdempotencyMode.NONE
                : capability.idempotencyMode();
        return new AgentToolExecutionPolicyExecutor(delegate, executionPolicy, retryPolicy,
                accessMode, idempotencyMode, executor, attemptObserver,
                invocationContextProvider, specification.name());
    }
}
