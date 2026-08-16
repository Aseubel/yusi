package com.aseubel.yusi.service.ai.runtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.aseubel.yusi.service.ai.tool.constant.AgentToolIdempotencyConstants;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class AgentToolIdempotencyMaintenance {

    private final AgentToolIdempotencyLedgerService ledgerService;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOrphanedClaimsOnStartup() {
        try {
            int recovered = ledgerService.recoverOrphanedClaims(LocalDateTime.now());
            log.info("Recovered {} orphaned agent tool idempotency claims", recovered);
        } catch (RuntimeException exception) {
            log.warn("Unable to recover orphaned agent tool idempotency claims", exception);
        }
    }

    @Scheduled(cron = AgentToolIdempotencyConstants.MAINTENANCE_CRON)
    public void clearExpiredLedgerStates() {
        try {
            int cleared = ledgerService.clearExpiredStates(LocalDateTime.now());
            log.info("Cleared {} expired agent tool idempotency states", cleared);
        } catch (RuntimeException exception) {
            log.warn("Unable to clear expired agent tool idempotency states", exception);
        }
    }
}
