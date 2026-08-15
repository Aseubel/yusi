package com.aseubel.yusi.service.ai.runtime;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.pojo.entity.AgentToolTrace;
import com.aseubel.yusi.repository.AgentToolTraceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AgentToolTraceService {

    private final AgentToolTraceRepository traceRepository;

    @Transactional
    public String start(String userId, String runId, String localToolCallId,
            String upstreamToolCallId, String toolName, String toolSource) {
        if (StrUtil.hasBlank(userId, runId, localToolCallId, toolName, toolSource)) {
            return localToolCallId;
        }

        return traceRepository.findByUserIdAndRunIdAndToolCallId(userId, runId, localToolCallId)
                .map(AgentToolTrace::getToolCallId)
                .orElseGet(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    traceRepository.save(AgentToolTrace.builder()
                            .userId(userId)
                            .runId(runId)
                            .toolCallId(localToolCallId)
                            .upstreamToolCallId(nullable(upstreamToolCallId))
                            .toolName(toolName)
                            .toolSource(toolSource)
                            .status(AgentToolTrace.Status.RUNNING)
                            .startedAt(now)
                            .createdAt(now)
                            .updatedAt(now)
                            .build());
                    return localToolCallId;
                });
    }

    @Transactional
    public void complete(String userId, String runId, String localToolCallId,
            Long durationMs, boolean failed) {
        if (StrUtil.hasBlank(userId, runId, localToolCallId)) {
            return;
        }
        traceRepository.findByUserIdAndRunIdAndToolCallId(userId, runId, localToolCallId)
                .filter(trace -> trace.getStatus() == AgentToolTrace.Status.RUNNING)
                .ifPresent(trace -> {
                    trace.finish(
                            failed ? AgentToolTrace.Status.FAILED : AgentToolTrace.Status.COMPLETED,
                            failed ? AgentToolTrace.FailureCategory.TOOL_FAILED : null,
                            LocalDateTime.now(), durationMs);
                    traceRepository.save(trace);
                });
    }

    @Transactional
    public void closeRunning(String userId, String runId, AgentToolTrace.Status status,
            AgentToolTrace.FailureCategory failureCategory) {
        if (StrUtil.hasBlank(userId, runId) || status == null || status == AgentToolTrace.Status.RUNNING) {
            return;
        }
        LocalDateTime completedAt = LocalDateTime.now();
        traceRepository.findByUserIdAndRunIdAndStatus(userId, runId, AgentToolTrace.Status.RUNNING)
                .forEach(trace -> {
                    trace.finish(status, failureCategory, completedAt, null);
                    traceRepository.save(trace);
                });
    }

    private String nullable(String value) {
        return StrUtil.isBlank(value) ? null : value;
    }
}
