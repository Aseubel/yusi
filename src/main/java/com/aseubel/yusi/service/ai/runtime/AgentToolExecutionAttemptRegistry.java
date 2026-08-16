package com.aseubel.yusi.service.ai.runtime;

import cn.hutool.core.util.StrUtil;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Keeps the physical retry counter associated with the logical tool callback.
 * Request arguments and results are intentionally never copied or persisted.
 */
@Component
@Slf4j
public class AgentToolExecutionAttemptRegistry implements AgentToolExecutionAttemptObserver {

    private final AgentToolTraceService traceService;
    private final Map<Object, Pending> pendingByRequest = new IdentityHashMap<>();

    public AgentToolExecutionAttemptRegistry(AgentToolTraceService traceService) {
        this.traceService = traceService;
    }

    public synchronized void register(String userId, String runId, Object requestIdentity,
            String upstreamToolCallId, String toolName, String toolSource, String localToolCallId) {
        if (requestIdentity == null || StrUtil.hasBlank(userId, runId, toolName, toolSource, localToolCallId)) {
            return;
        }
        pendingByRequest.put(requestIdentity,
                new Pending(userId, runId, localToolCallId, upstreamToolCallId, toolName, toolSource));
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
            log.debug("Unable to persist agent tool retry count for run {}", pending.runId, exception);
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
        private boolean retryRecorded;

        private Pending(String userId, String runId, String localToolCallId,
                String upstreamToolCallId, String toolName, String toolSource) {
            this.userId = userId;
            this.runId = runId;
            this.localToolCallId = localToolCallId;
            this.upstreamToolCallId = upstreamToolCallId;
            this.toolName = toolName;
            this.toolSource = toolSource;
        }
    }
}
