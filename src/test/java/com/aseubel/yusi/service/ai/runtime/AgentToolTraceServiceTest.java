package com.aseubel.yusi.service.ai.runtime;

import com.aseubel.yusi.pojo.constant.AgentToolConstants;
import com.aseubel.yusi.pojo.entity.AgentToolTrace;
import com.aseubel.yusi.repository.AgentToolTraceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentToolTraceServiceTest {

    @Mock
    private AgentToolTraceRepository traceRepository;

    @Test
    void startCreatesLowSensitivityTraceWithLocalIdAndNullableUpstreamId() {
        when(traceRepository.findByUserIdAndRunIdAndToolCallId("user-1", "run-1", "local-1"))
                .thenReturn(Optional.empty());
        AgentToolTraceService service = new AgentToolTraceService(traceRepository);

        String result = service.start("user-1", "run-1", "local-1", null,
                AgentToolConstants.SEARCH_MEMORIES, AgentToolConstants.SOURCE_LOCAL);

        assertEquals("local-1", result);
        ArgumentCaptor<AgentToolTrace> captor = ArgumentCaptor.forClass(AgentToolTrace.class);
        verify(traceRepository).save(captor.capture());
        AgentToolTrace trace = captor.getValue();
        assertEquals("user-1", trace.getUserId());
        assertEquals("run-1", trace.getRunId());
        assertEquals("local-1", trace.getToolCallId());
        assertNull(trace.getUpstreamToolCallId());
        assertEquals(AgentToolTrace.Status.RUNNING, trace.getStatus());
        assertEquals(AgentToolConstants.SEARCH_MEMORIES, trace.getToolName());
    }

    @Test
    void startPersistsCapabilityVersionWithoutChangingExistingTraceFields() {
        when(traceRepository.findByUserIdAndRunIdAndToolCallId("user-1", "run-1", "local-version"))
                .thenReturn(Optional.empty());
        AgentToolTraceService service = new AgentToolTraceService(traceRepository);

        service.start("user-1", "run-1", "local-version", null,
                AgentToolConstants.SEARCH_MEMORIES, AgentToolConstants.SOURCE_LOCAL, "v1");

        ArgumentCaptor<AgentToolTrace> captor = ArgumentCaptor.forClass(AgentToolTrace.class);
        verify(traceRepository).save(captor.capture());
        AgentToolTrace trace = captor.getValue();
        assertEquals("v1", trace.getCapabilityVersion());
        assertEquals(AgentToolConstants.SEARCH_MEMORIES, trace.getToolName());
        assertEquals(AgentToolConstants.SOURCE_LOCAL, trace.getToolSource());
    }

    @Test
    void failedCompletionUsesFixedCategoryAndDuplicateCompletionDoesNotOverwriteTerminalRow() {
        AgentToolTrace trace = runningTrace("local-2");
        when(traceRepository.findByUserIdAndRunIdAndToolCallId("user-1", "run-1", "local-2"))
                .thenReturn(Optional.of(trace));
        AgentToolTraceService service = new AgentToolTraceService(traceRepository);

        service.complete("user-1", "run-1", "local-2", 120L, true);
        service.complete("user-1", "run-1", "local-2", 999L, false);

        assertEquals(AgentToolTrace.Status.FAILED, trace.getStatus());
        assertEquals(AgentToolTrace.FailureCategory.TOOL_FAILED, trace.getFailureCategory());
        assertEquals(120L, trace.getDurationMs());
        verify(traceRepository).save(trace);
    }

    @Test
    void terminalConvergenceClosesOnlyRunningRows() {
        AgentToolTrace running = runningTrace("local-running");
        AgentToolTrace completed = runningTrace("local-completed");
        completed.setStatus(AgentToolTrace.Status.COMPLETED);
        when(traceRepository.findByUserIdAndRunIdAndStatus(
                eq("user-1"), eq("run-1"), eq(AgentToolTrace.Status.RUNNING)))
                .thenReturn(List.of(running));
        AgentToolTraceService service = new AgentToolTraceService(traceRepository);

        service.closeRunning("user-1", "run-1", AgentToolTrace.Status.CANCELLED,
                AgentToolTrace.FailureCategory.CANCELLED);

        assertEquals(AgentToolTrace.Status.CANCELLED, running.getStatus());
        assertEquals(AgentToolTrace.FailureCategory.CANCELLED, running.getFailureCategory());
        assertEquals(AgentToolTrace.Status.COMPLETED, completed.getStatus());
        verify(traceRepository).save(running);
        verify(traceRepository, never()).save(completed);
    }

    private AgentToolTrace runningTrace(String toolCallId) {
        return AgentToolTrace.builder()
                .userId("user-1")
                .runId("run-1")
                .toolCallId(toolCallId)
                .toolName(AgentToolConstants.SEARCH_MEMORIES)
                .toolSource(AgentToolConstants.SOURCE_LOCAL)
                .status(AgentToolTrace.Status.RUNNING)
                .startedAt(LocalDateTime.now().minusSeconds(1))
                .build();
    }
}
