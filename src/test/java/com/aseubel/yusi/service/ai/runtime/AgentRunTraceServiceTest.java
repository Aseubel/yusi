package com.aseubel.yusi.service.ai.runtime;

import com.aseubel.yusi.pojo.entity.AgentRunTrace;
import com.aseubel.yusi.repository.AgentRunTraceRepository;
import com.aseubel.yusi.pojo.entity.AgentToolTrace;
import com.aseubel.yusi.service.ai.model.ModelRouteContextHolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentRunTraceServiceTest {

    @Mock
    private AgentRunTraceRepository traceRepository;

    @Mock
    private AgentToolTraceService agentToolTraceService;

    @Test
    void startCreatesOnlyLowSensitivityLifecycleSummary() {
        when(traceRepository.findByUserIdAndRunId("user-1", "run-1")).thenReturn(Optional.empty());
        AgentRunTraceService service = service();

        service.start("user-1", "run-1", "chat");

        ArgumentCaptor<AgentRunTrace> captor = ArgumentCaptor.forClass(AgentRunTrace.class);
        verify(traceRepository).save(captor.capture());
        AgentRunTrace trace = captor.getValue();
        assertEquals("user-1", trace.getUserId());
        assertEquals("run-1", trace.getRunId());
        assertEquals("chat", trace.getScene());
        assertEquals(AgentRunTrace.Status.RUNNING, trace.getStatus());
        assertEquals("preparing", trace.getCurrentStage());
        assertEquals(0, trace.getToolCount());
        assertEquals(0L, trace.getResponseCharCount());
        assertNotNull(trace.getStartedAt());
    }

    @Test
    void terminalUpdatePreservesToolCountAndCalculatesDuration() {
        AgentRunTrace trace = AgentRunTrace.builder()
                .userId("user-1")
                .runId("run-2")
                .scene("chat")
                .status(AgentRunTrace.Status.RUNNING)
                .toolCount(0)
                .startedAt(LocalDateTime.now().minusSeconds(1))
                .build();
        when(traceRepository.findByUserIdAndRunId("user-1", "run-2")).thenReturn(Optional.of(trace));
        AgentRunTraceService service = service();

        service.toolCompleted("user-1", "run-2");
        service.complete("user-1", "run-2");

        assertEquals(1, trace.getToolCount());
        assertEquals(0L, trace.getResponseCharCount());
        assertEquals(AgentRunTrace.Status.COMPLETED, trace.getStatus());
        assertEquals("completed", trace.getCurrentStage());
        assertNotNull(trace.getCompletedAt());
        assertNotNull(trace.getDurationMs());
        assertTrue(trace.getDurationMs() >= 0);
        verify(traceRepository, org.mockito.Mockito.times(2)).save(trace);
        verify(agentToolTraceService).closeRunning("user-1", "run-2", AgentToolTrace.Status.COMPLETED, null);
    }

    @Test
    void completeStoresResponseCountWithoutChangingToolCount() {
        AgentRunTrace trace = runningTrace("run-response-complete");
        trace.setToolCount(3);
        when(traceRepository.findByUserIdAndRunId("user-1", "run-response-complete"))
                .thenReturn(Optional.of(trace));

        service().complete("user-1", "run-response-complete", 27L);

        assertEquals(3, trace.getToolCount());
        assertEquals(27L, trace.getResponseCharCount());
    }

    @Test
    void failAndCancelClampNegativeResponseCountsToZero() {
        AgentRunTrace failed = runningTrace("run-response-failed");
        AgentRunTrace cancelled = runningTrace("run-response-cancelled");
        when(traceRepository.findByUserIdAndRunId("user-1", "run-response-failed"))
                .thenReturn(Optional.of(failed));
        when(traceRepository.findByUserIdAndRunId("user-1", "run-response-cancelled"))
                .thenReturn(Optional.of(cancelled));

        service().fail("user-1", "run-response-failed", "agent_error", -1L);
        service().cancel("user-1", "run-response-cancelled", "user", -1L);

        assertEquals(0L, failed.getResponseCharCount());
        assertEquals(0L, cancelled.getResponseCharCount());
    }

    @Test
    void terminalTraceCannotBeOverwrittenByLateFailure() {
        AgentRunTrace trace = AgentRunTrace.builder()
                .userId("user-1")
                .runId("run-3")
                .scene("chat")
                .status(AgentRunTrace.Status.COMPLETED)
                .build();
        when(traceRepository.findByUserIdAndRunId("user-1", "run-3")).thenReturn(Optional.of(trace));
        AgentRunTraceService service = service();

        service.fail("user-1", "run-3", "agent_error");

        assertEquals(AgentRunTrace.Status.COMPLETED, trace.getStatus());
        verify(traceRepository, never()).save(trace);
    }

    @Test
    void scopedRunCarriesCorrelationAndClearsItWhenClosed() {
        when(traceRepository.findByUserIdAndRunId("user-1", "run-scope"))
                .thenReturn(Optional.empty());
        AgentRunTraceService service = service();

        try (AgentRunTraceService.RunScope scope = service.open("user-1", "run-scope", "life_graph")) {
            assertEquals("run-scope", scope.runId());
            assertEquals("run-scope", ModelRouteContextHolder.getEffective().getRunId());
            assertEquals("user-1", ModelRouteContextHolder.getEffective().getUserId());
            scope.complete();
        }

        assertNull(ModelRouteContextHolder.get());
        verify(traceRepository).save(org.mockito.ArgumentMatchers.argThat(trace ->
                trace.getUserId().equals("user-1")
                        && trace.getRunId().equals("run-scope")));
    }

    @Test
    void failAndCancelConvergeRunningToolTracesWithFixedCategories() {
        AgentRunTrace failedRun = runningTrace("run-failed");
        AgentRunTrace cancelledRun = runningTrace("run-cancelled");
        when(traceRepository.findByUserIdAndRunId("user-1", "run-failed"))
                .thenReturn(Optional.of(failedRun));
        when(traceRepository.findByUserIdAndRunId("user-1", "run-cancelled"))
                .thenReturn(Optional.of(cancelledRun));
        AgentRunTraceService service = service();

        service.fail("user-1", "run-failed", "agent_error");
        service.cancel("user-1", "run-cancelled", "user");

        verify(agentToolTraceService).closeRunning("user-1", "run-failed",
                AgentToolTrace.Status.FAILED, AgentToolTrace.FailureCategory.AGENT_ERROR);
        verify(agentToolTraceService).closeRunning("user-1", "run-cancelled",
                AgentToolTrace.Status.CANCELLED, AgentToolTrace.FailureCategory.CANCELLED);
    }

    private AgentRunTraceService service() {
        return new AgentRunTraceService(traceRepository, agentToolTraceService);
    }

    private AgentRunTrace runningTrace(String runId) {
        return AgentRunTrace.builder()
                .userId("user-1")
                .runId(runId)
                .scene("chat")
                .status(AgentRunTrace.Status.RUNNING)
                .startedAt(LocalDateTime.now().minusSeconds(1))
                .build();
    }
}
