package com.aseubel.yusi.service.ai.runtime;

import cn.hutool.core.util.IdUtil;
import com.aseubel.yusi.pojo.entity.AgentToolTrace;
import com.aseubel.yusi.pojo.entity.AgentRunTrace;
import com.aseubel.yusi.repository.AgentRunTraceRepository;
import com.aseubel.yusi.service.ai.model.ModelRouteContext;
import com.aseubel.yusi.service.ai.model.ModelRouteContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Persists a low-sensitivity lifecycle summary for an AgentRun.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentRunTraceService {

    private final AgentRunTraceRepository traceRepository;
    private final AgentToolTraceService agentToolTraceService;

    public RunScope open(String userId, String runId, String scene) {
        String effectiveRunId = isBlank(runId) ? IdUtil.fastSimpleUUID() : runId;
        start(userId, effectiveRunId, scene);
        ModelRouteContextHolder.Scope contextScope = ModelRouteContextHolder.open(
                ModelRouteContext.builder()
                        .userId(userId)
                        .runId(effectiveRunId)
                        .scene(scene)
                        .build());
        return new RunScope(this, userId, effectiveRunId, contextScope);
    }

    @Transactional
    public void start(String userId, String runId, String scene) {
        if (isBlank(userId) || isBlank(runId)) {
            return;
        }
        if (traceRepository.findByUserIdAndRunId(userId, runId).isPresent()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        traceRepository.save(AgentRunTrace.builder()
                .userId(userId)
                .runId(runId)
                .scene(isBlank(scene) ? "unknown" : scene)
                .status(AgentRunTrace.Status.RUNNING)
                .currentStage("preparing")
                .toolCount(0)
                .startedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    @Transactional
    public void stage(String userId, String runId, String stage) {
        findRunning(userId, runId).ifPresent(trace -> {
            trace.setCurrentStage(stage);
            traceRepository.save(trace);
        });
    }

    @Transactional
    public void toolCompleted(String userId, String runId) {
        findRunning(userId, runId).ifPresent(trace -> {
            trace.setToolCount((trace.getToolCount() == null ? 0 : trace.getToolCount()) + 1);
            traceRepository.save(trace);
        });
    }

    @Transactional
    public void complete(String userId, String runId) {
        completeInternal(userId, runId, null);
    }

    @Transactional
    public void complete(String userId, String runId, long responseCharCount) {
        completeInternal(userId, runId, responseCharCount);
    }

    private void completeInternal(String userId, String runId, Long responseCharCount) {
        closeRunningTools(userId, runId, AgentToolTrace.Status.COMPLETED, null);
        finish(userId, runId, AgentRunTrace.Status.COMPLETED, null, null, responseCharCount);
    }

    @Transactional
    public void fail(String userId, String runId, String failureCategory) {
        failInternal(userId, runId, failureCategory, null);
    }

    @Transactional
    public void fail(String userId, String runId, String failureCategory, long responseCharCount) {
        failInternal(userId, runId, failureCategory, responseCharCount);
    }

    private void failInternal(String userId, String runId, String failureCategory, Long responseCharCount) {
        closeRunningTools(userId, runId, AgentToolTrace.Status.FAILED,
                AgentToolTrace.FailureCategory.AGENT_ERROR);
        finish(userId, runId, AgentRunTrace.Status.FAILED,
                isBlank(failureCategory) ? "agent_error" : failureCategory, null, responseCharCount);
    }

    @Transactional
    public void cancel(String userId, String runId, String cancelSource) {
        cancelInternal(userId, runId, cancelSource, null);
    }

    @Transactional
    public void cancel(String userId, String runId, String cancelSource, long responseCharCount) {
        cancelInternal(userId, runId, cancelSource, responseCharCount);
    }

    private void cancelInternal(String userId, String runId, String cancelSource, Long responseCharCount) {
        closeRunningTools(userId, runId, AgentToolTrace.Status.CANCELLED,
                AgentToolTrace.FailureCategory.CANCELLED);
        finish(userId, runId, AgentRunTrace.Status.CANCELLED, null,
                isBlank(cancelSource) ? "stream_closed" : cancelSource, responseCharCount);
    }

    private void closeRunningTools(String userId, String runId, AgentToolTrace.Status status,
            AgentToolTrace.FailureCategory failureCategory) {
        if (agentToolTraceService == null) {
            return;
        }
        try {
            agentToolTraceService.closeRunning(userId, runId, status, failureCategory);
        } catch (RuntimeException exception) {
            log.warn("AgentToolTrace terminal convergence failed for run {}", runId, exception);
        }
    }

    private void finish(String userId, String runId, AgentRunTrace.Status status,
            String failureCategory, String cancelSource, Long responseCharCount) {
        findRunning(userId, runId).ifPresent(trace -> {
            LocalDateTime completedAt = LocalDateTime.now();
            trace.setStatus(status);
            trace.setCurrentStage(status.name().toLowerCase());
            trace.setFailureCategory(failureCategory);
            trace.setCancelSource(cancelSource);
            if (responseCharCount != null) {
                trace.setResponseCharCount(Math.max(0L, responseCharCount));
            } else if (trace.getResponseCharCount() == null) {
                trace.setResponseCharCount(0L);
            }
            trace.setCompletedAt(completedAt);
            trace.setDurationMs(calculateDuration(trace.getStartedAt(), completedAt));
            traceRepository.save(trace);
        });
    }

    private Optional<AgentRunTrace> findRunning(String userId, String runId) {
        if (isBlank(userId) || isBlank(runId)) {
            return Optional.empty();
        }
        return traceRepository.findByUserIdAndRunId(userId, runId)
                .filter(trace -> trace.getStatus() == AgentRunTrace.Status.RUNNING);
    }

    private long calculateDuration(LocalDateTime startedAt, LocalDateTime completedAt) {
        if (startedAt == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(startedAt, completedAt).toMillis());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static final class RunScope implements AutoCloseable {

        private final AgentRunTraceService service;
        private final String userId;
        private final String runId;
        private final ModelRouteContextHolder.Scope contextScope;
        private boolean terminal;

        private RunScope(AgentRunTraceService service, String userId, String runId,
                ModelRouteContextHolder.Scope contextScope) {
            this.service = service;
            this.userId = userId;
            this.runId = runId;
            this.contextScope = contextScope;
        }

        public String runId() {
            return runId;
        }

        public void complete() {
            if (!terminal) {
                terminal = true;
                service.complete(userId, runId);
            }
        }

        public void fail(String failureCategory) {
            if (!terminal) {
                terminal = true;
                service.fail(userId, runId, failureCategory);
            }
        }

        public void retryWait() {
            if (!terminal) {
                service.stage(userId, runId, "retry_wait");
            }
        }

        @Override
        public void close() {
            contextScope.close();
        }
    }
}
