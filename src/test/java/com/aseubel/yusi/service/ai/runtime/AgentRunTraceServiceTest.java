package com.aseubel.yusi.service.ai.runtime;

import com.aseubel.yusi.pojo.entity.AgentRunTrace;
import com.aseubel.yusi.repository.AgentRunTraceRepository;
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

    @Test
    void startCreatesOnlyLowSensitivityLifecycleSummary() {
        when(traceRepository.findByUserIdAndRunId("user-1", "run-1")).thenReturn(Optional.empty());
        AgentRunTraceService service = new AgentRunTraceService(traceRepository);

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
        AgentRunTraceService service = new AgentRunTraceService(traceRepository);

        service.toolCompleted("user-1", "run-2");
        service.complete("user-1", "run-2");

        assertEquals(1, trace.getToolCount());
        assertEquals(AgentRunTrace.Status.COMPLETED, trace.getStatus());
        assertEquals("completed", trace.getCurrentStage());
        assertNotNull(trace.getCompletedAt());
        assertNotNull(trace.getDurationMs());
        assertTrue(trace.getDurationMs() >= 0);
        verify(traceRepository, org.mockito.Mockito.times(2)).save(trace);
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
        AgentRunTraceService service = new AgentRunTraceService(traceRepository);

        service.fail("user-1", "run-3", "agent_error");

        assertEquals(AgentRunTrace.Status.COMPLETED, trace.getStatus());
        verify(traceRepository, never()).save(trace);
    }

    @Test
    void scopedRunCarriesCorrelationAndClearsItWhenClosed() {
        when(traceRepository.findByUserIdAndRunId("user-1", "run-scope"))
                .thenReturn(Optional.empty());
        AgentRunTraceService service = new AgentRunTraceService(traceRepository);

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
}
