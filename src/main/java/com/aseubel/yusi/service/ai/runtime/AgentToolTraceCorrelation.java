package com.aseubel.yusi.service.ai.runtime;

import cn.hutool.core.util.StrUtil;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Correlates LangChain tool callbacks without trusting an optional upstream id.
 * One instance belongs to one chat AgentRun and must be discarded at run end.
 */
public final class AgentToolTraceCorrelation {

    private final Map<Object, Pending> byRequestIdentity = new IdentityHashMap<>();
    private final Map<String, Deque<Pending>> byUpstreamId = new HashMap<>();
    private final Map<String, Deque<Pending>> byToolName = new HashMap<>();

    public synchronized void register(Object requestIdentity, String upstreamToolCallId,
            String toolName, String localToolCallId) {
        if (requestIdentity == null || StrUtil.hasBlank(toolName, localToolCallId)) {
            return;
        }
        Pending pending = new Pending(requestIdentity, upstreamToolCallId, toolName, localToolCallId);
        byRequestIdentity.put(requestIdentity, pending);
        byToolName.computeIfAbsent(toolName, ignored -> new ArrayDeque<>()).addLast(pending);
        if (StrUtil.isNotBlank(upstreamToolCallId)) {
            byUpstreamId.computeIfAbsent(upstreamToolCallId, ignored -> new ArrayDeque<>()).addLast(pending);
        }
    }

    public synchronized Optional<String> resolve(Object requestIdentity, String upstreamToolCallId,
            String toolName) {
        Pending pending = requestIdentity == null ? null : byRequestIdentity.get(requestIdentity);
        if (pending == null && StrUtil.isNotBlank(upstreamToolCallId)) {
            pending = poll(byUpstreamId.get(upstreamToolCallId));
        }
        if (pending == null && StrUtil.isNotBlank(toolName)) {
            pending = poll(byToolName.get(toolName));
        }
        if (pending == null) {
            return Optional.empty();
        }

        remove(pending);
        return Optional.of(pending.localToolCallId());
    }

    public synchronized void clear() {
        byRequestIdentity.clear();
        byUpstreamId.clear();
        byToolName.clear();
    }

    private Pending poll(Deque<Pending> pending) {
        return pending == null ? null : pending.peekFirst();
    }

    private void remove(Pending pending) {
        if (byRequestIdentity.get(pending.requestIdentity()) == pending) {
            byRequestIdentity.remove(pending.requestIdentity());
        }
        removeFromQueue(byToolName.get(pending.toolName()), pending);
        if (StrUtil.isNotBlank(pending.upstreamToolCallId())) {
            removeFromQueue(byUpstreamId.get(pending.upstreamToolCallId()), pending);
        }
    }

    private void removeFromQueue(Deque<Pending> queue, Pending pending) {
        if (queue == null) {
            return;
        }
        queue.remove(pending);
    }

    private record Pending(Object requestIdentity, String upstreamToolCallId,
            String toolName, String localToolCallId) {
    }
}
