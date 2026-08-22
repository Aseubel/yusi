package com.aseubel.yusi.service.ai.runtime;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.service.ai.tool.constant.AgentToolAccessMode;
import com.aseubel.yusi.service.ai.tool.constant.AgentToolIdempotencyMode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps the physical retry counter associated with the logical tool callback.
 * Request arguments and results are intentionally never copied or persisted.
 */
@Component
@Slf4j
public class AgentToolExecutionAttemptRegistry
        implements AgentToolExecutionAttemptObserver, AgentToolInvocationContextProvider {

    private final AgentToolTraceService traceService;
    private final Map<Object, Pending> pendingByRequest = new IdentityHashMap<>();

    public AgentToolExecutionAttemptRegistry(AgentToolTraceService traceService) {
        this.traceService = traceService;
    }

    public synchronized void register(String userId, String runId, Object requestIdentity,
            String upstreamToolCallId, String toolName, String toolSource, String localToolCallId) {
        register(userId, runId, requestIdentity, upstreamToolCallId, toolName, toolSource,
                localToolCallId, AgentToolAccessMode.UNKNOWN, AgentToolIdempotencyMode.NONE, null);
    }

    public synchronized void register(String userId, String runId, Object requestIdentity,
            String upstreamToolCallId, String toolName, String toolSource, String localToolCallId,
            AgentToolAccessMode accessMode, AgentToolIdempotencyMode idempotencyMode,
            String capabilityVersion) {
        if (requestIdentity == null || StrUtil.hasBlank(userId, runId, toolName, toolSource, localToolCallId)) {
            return;
        }
        pendingByRequest.put(requestIdentity,
                new Pending(userId, runId, localToolCallId, upstreamToolCallId, toolName, toolSource,
                        new AgentToolInvocationContext(userId, runId, localToolCallId, toolName,
                                toolSource, accessMode, idempotencyMode, capabilityVersion)));
    }

    @Override
    public synchronized Optional<AgentToolInvocationContext> find(Object requestIdentity) {
        if (requestIdentity == null) {
            return Optional.empty();
        }
        Pending pending = pendingByRequest.get(requestIdentity);
        return pending == null ? Optional.empty() : Optional.of(pending.context);
    }

    @Override
    public void onRetry(ToolExecutionRequest request) {
        Pending pending;
        synchronized (this) {
            pending = pendingByRequest.get(request);
            if (pending == null || pending.retryRecorded) {
                return;
            }
            pending.retryRecorded = true;
        }
        try {
            traceService.incrementAttemptCount(pending.userId, pending.runId, pending.localToolCallId);
        } catch (RuntimeException exception) {
            log.debug("Unable to persist agent tool retry count: operation=agent_tool_retry_count, runId={}, exceptionType={}", pending.runId, com.aseubel.yusi.common.utils.LowSensitivityLogSummary.exceptionType(exception));
        }
    }

    public synchronized void complete(Object requestIdentity) {
        if (requestIdentity != null) {
            pendingByRequest.remove(requestIdentity);
        }
    }

    public synchronized void clearRun(String userId, String runId) {
        if (StrUtil.hasBlank(userId, runId)) {
            return;
        }
        Iterator<Map.Entry<Object, Pending>> iterator = pendingByRequest.entrySet().iterator();
        while (iterator.hasNext()) {
            Pending pending = iterator.next().getValue();
            if (userId.equals(pending.userId) && runId.equals(pending.runId)) {
                iterator.remove();
            }
        }
    }

    private static final class Pending {
        private final String userId;
        private final String runId;
        private final String localToolCallId;
        @SuppressWarnings("unused")
        private final String upstreamToolCallId;
        @SuppressWarnings("unused")
        private final String toolName;
        @SuppressWarnings("unused")
        private final String toolSource;
        private final AgentToolInvocationContext context;
        private boolean retryRecorded;

        private Pending(String userId, String runId, String localToolCallId,
                String upstreamToolCallId, String toolName, String toolSource,
                AgentToolInvocationContext context) {
            this.userId = userId;
            this.runId = runId;
            this.localToolCallId = localToolCallId;
            this.upstreamToolCallId = upstreamToolCallId;
            this.toolName = toolName;
            this.toolSource = toolSource;
            this.context = context;
        }
    }
}
