package com.aseubel.yusi.service.ai.runtime;

import com.aseubel.yusi.pojo.entity.AgentToolTrace;
import com.aseubel.yusi.repository.AgentToolTraceRepository;
import com.aseubel.yusi.service.ai.tool.constant.AgentToolAccessMode;
import com.aseubel.yusi.service.ai.tool.constant.AgentToolIdempotencyMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentToolIdempotencyLedgerServiceTest {

    @Mock
    private AgentToolTraceRepository repository;

    @Test
    void claimUsesTheExistingToolCallIdAndReturnsClaimedWhenAtomicUpdateWins() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 16, 12, 0);
        AgentToolInvocationContext context = context();
        when(repository.claimIdempotency(anyString(), anyString(), anyString(), any(), any(),
                eq(now), eq(now.plusDays(30))))
                .thenReturn(1);
        AgentToolIdempotencyLedgerService ledger = new AgentToolIdempotencyLedgerService(repository);

        assertEquals(AgentToolIdempotencyLedgerService.ClaimDecision.CLAIMED,
                ledger.claim(context, now));

        verify(repository).claimIdempotency("user-1", "run-1", "local-1",
                AgentToolIdempotencyMode.IDEMPOTENT_WRITE,
                AgentToolTrace.IdempotencyStatus.CLAIMED, now, now.plusDays(30));
    }

    @Test
    void claimMapsExistingTerminalStateAndNeverClaimsNonIdempotentTrace() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 16, 12, 0);
        when(repository.claimIdempotency(anyString(), anyString(), anyString(), any(), any(),
                any(), any())).thenReturn(0);
        when(repository.findByUserIdAndRunIdAndToolCallId("user-1", "run-1", "local-1"))
                .thenReturn(Optional.of(traceWith(AgentToolTrace.IdempotencyStatus.COMPLETED)));
        AgentToolIdempotencyLedgerService ledger = new AgentToolIdempotencyLedgerService(repository);

        assertEquals(AgentToolIdempotencyLedgerService.ClaimDecision.ALREADY_COMPLETED,
                ledger.claim(context(), now));
        assertEquals(AgentToolIdempotencyLedgerService.ClaimDecision.NOT_APPLICABLE,
                ledger.claim(new AgentToolInvocationContext(
                        "user-1", "run-1", "local-2", "searchMemories", "local",
                        AgentToolAccessMode.READ, AgentToolIdempotencyMode.NONE, "v1"), now));
        verify(repository, never()).findByUserIdAndRunIdAndToolCallId("user-1", "run-1", "local-2");
    }

    @Test
    void resolveUnknownAndMaintenanceUpdatesAreConditionalAndLowSensitivity() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 16, 12, 0);
        AgentToolIdempotencyLedgerService ledger = new AgentToolIdempotencyLedgerService(repository);

        ledger.resolveUnknown(context(), now);
        ledger.recoverOrphanedClaims(now);
        ledger.clearExpiredStates(now);

        verify(repository).resolveIdempotency("user-1", "run-1", "local-1",
                AgentToolTrace.IdempotencyStatus.CLAIMED,
                AgentToolTrace.IdempotencyStatus.UNKNOWN, now, now.plusDays(30));
        verify(repository).recoverOrphanedClaims(
                AgentToolIdempotencyMode.IDEMPOTENT_WRITE,
                AgentToolTrace.IdempotencyStatus.CLAIMED,
                AgentToolTrace.IdempotencyStatus.UNKNOWN,
                now.minusMinutes(5), now, now.plusDays(30));
        verify(repository).clearExpiredStates(
                AgentToolIdempotencyMode.IDEMPOTENT_WRITE, now);
    }

    private AgentToolInvocationContext context() {
        return new AgentToolInvocationContext(
                "user-1", "run-1", "local-1", "updateUserPersona", "local",
                AgentToolAccessMode.WRITE, AgentToolIdempotencyMode.IDEMPOTENT_WRITE, "v1");
    }

    private AgentToolTrace traceWith(AgentToolTrace.IdempotencyStatus status) {
        return AgentToolTrace.builder()
                .userId("user-1")
                .runId("run-1")
                .toolCallId("local-1")
                .toolName("updateUserPersona")
                .toolSource("local")
                .idempotencyMode(AgentToolIdempotencyMode.IDEMPOTENT_WRITE)
                .idempotencyStatus(status)
                .status(AgentToolTrace.Status.RUNNING)
                .build();
    }
}
