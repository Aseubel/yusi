package com.aseubel.yusi.service.ai.runtime;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.pojo.entity.AgentToolTrace;
import com.aseubel.yusi.repository.AgentToolTraceRepository;
import com.aseubel.yusi.service.ai.tool.constant.AgentToolIdempotencyConstants;
import com.aseubel.yusi.service.ai.tool.constant.AgentToolIdempotencyMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Slf4j
public class AgentToolIdempotencyLedgerService {

    private final AgentToolTraceRepository repository;

    public AgentToolIdempotencyLedgerService(AgentToolTraceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ClaimDecision claim(AgentToolInvocationContext context) {
        return claim(context, LocalDateTime.now());
    }

    @Transactional
    public ClaimDecision claim(AgentToolInvocationContext context, LocalDateTime now) {
        if (!validContext(context)) {
            return ClaimDecision.CONTEXT_MISSING;
        }
        if (context.idempotencyMode() != AgentToolIdempotencyMode.IDEMPOTENT_WRITE) {
            return ClaimDecision.NOT_APPLICABLE;
        }

        LocalDateTime expiresAt = now.plus(AgentToolIdempotencyConstants.LEDGER_RETENTION);
        int claimed = repository.claimIdempotency(
                context.userId(), context.runId(), context.localToolCallId(),
                AgentToolIdempotencyMode.IDEMPOTENT_WRITE,
                AgentToolTrace.IdempotencyStatus.CLAIMED, now, expiresAt);
        if (claimed > 0) {
            return ClaimDecision.CLAIMED;
        }

        return repository.findByUserIdAndRunIdAndToolCallId(
                        context.userId(), context.runId(), context.localToolCallId())
                .map(AgentToolTrace::getIdempotencyStatus)
                .map(this::mapExistingStatus)
                .orElse(ClaimDecision.UNKNOWN);
    }

    @Transactional
    public void resolveSuccess(AgentToolInvocationContext context) {
        resolveSuccess(context, LocalDateTime.now());
    }

    @Transactional
    public void resolveSuccess(AgentToolInvocationContext context, LocalDateTime now) {
        resolve(context, AgentToolTrace.IdempotencyStatus.COMPLETED, now);
    }

    @Transactional
    public void resolveFailure(AgentToolInvocationContext context) {
        resolveFailure(context, LocalDateTime.now());
    }

    @Transactional
    public void resolveFailure(AgentToolInvocationContext context, LocalDateTime now) {
        resolve(context, AgentToolTrace.IdempotencyStatus.FAILED, now);
    }

    @Transactional
    public void resolveUnknown(AgentToolInvocationContext context) {
        resolveUnknown(context, LocalDateTime.now());
    }

    @Transactional
    public void resolveUnknown(AgentToolInvocationContext context, LocalDateTime now) {
        if (validContext(context)) {
            resolve(context, AgentToolTrace.IdempotencyStatus.UNKNOWN, now);
        }
    }

    @Transactional
    public void resolveUnknown(String userId, String runId, String localToolCallId,
            LocalDateTime now) {
        if (StrUtil.hasBlank(userId, runId, localToolCallId)) {
            return;
        }
        resolveRow(userId, runId, localToolCallId, AgentToolTrace.IdempotencyStatus.UNKNOWN, now);
    }

    @Transactional
    public int recoverOrphanedClaims(LocalDateTime now) {
        LocalDateTime staleBefore = now.minus(AgentToolIdempotencyConstants.CLAIM_LEASE);
        LocalDateTime expiresAt = now.plus(AgentToolIdempotencyConstants.LEDGER_RETENTION);
        return repository.recoverOrphanedClaims(
                AgentToolIdempotencyMode.IDEMPOTENT_WRITE,
                AgentToolTrace.IdempotencyStatus.CLAIMED,
                AgentToolTrace.IdempotencyStatus.UNKNOWN,
                staleBefore, now, expiresAt);
    }

    @Transactional
    public int clearExpiredStates(LocalDateTime now) {
        return repository.clearExpiredStates(AgentToolIdempotencyMode.IDEMPOTENT_WRITE, now);
    }

    private void resolve(AgentToolInvocationContext context,
            AgentToolTrace.IdempotencyStatus status, LocalDateTime now) {
        if (!validContext(context)) {
            return;
        }
        resolveRow(context.userId(), context.runId(), context.localToolCallId(), status, now);
    }

    private void resolveRow(String userId, String runId, String localToolCallId,
            AgentToolTrace.IdempotencyStatus status, LocalDateTime now) {
        repository.resolveIdempotency(userId, runId, localToolCallId,
                AgentToolTrace.IdempotencyStatus.CLAIMED, status, now,
                now.plus(AgentToolIdempotencyConstants.LEDGER_RETENTION));
    }

    private ClaimDecision mapExistingStatus(AgentToolTrace.IdempotencyStatus status) {
        if (status == null || status == AgentToolTrace.IdempotencyStatus.CLAIMED) {
            return ClaimDecision.IN_PROGRESS;
        }
        return switch (status) {
            case COMPLETED -> ClaimDecision.ALREADY_COMPLETED;
            case FAILED -> ClaimDecision.PREVIOUS_FAILURE;
            case UNKNOWN -> ClaimDecision.UNKNOWN;
            case CLAIMED -> ClaimDecision.IN_PROGRESS;
        };
    }

    private boolean validContext(AgentToolInvocationContext context) {
        return context != null
                && !StrUtil.hasBlank(context.userId(), context.runId(), context.localToolCallId())
                && context.idempotencyMode() != null;
    }

    public enum ClaimDecision {
        CLAIMED,
        IN_PROGRESS,
        ALREADY_COMPLETED,
        PREVIOUS_FAILURE,
        UNKNOWN,
        CONTEXT_MISSING,
        NOT_APPLICABLE
    }
}
