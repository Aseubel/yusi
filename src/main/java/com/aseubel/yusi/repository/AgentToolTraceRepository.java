package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.AgentToolTrace;
import com.aseubel.yusi.service.ai.tool.constant.AgentToolIdempotencyMode;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentToolTraceRepository extends JpaRepository<AgentToolTrace, Long> {

    Optional<AgentToolTrace> findByUserIdAndRunIdAndToolCallId(
            String userId, String runId, String toolCallId);

    List<AgentToolTrace> findByUserIdAndRunIdAndStatus(
            String userId, String runId, AgentToolTrace.Status status);

    @Modifying
    @Transactional
    @Query("""
            update AgentToolTrace t set
                t.idempotencyStatus = :claimedStatus,
                t.idempotencyClaimedAt = :now,
                t.idempotencyResolvedAt = null,
                t.idempotencyExpiresAt = :expiresAt,
                t.updatedAt = :now
            where t.userId = :userId
              and t.runId = :runId
              and t.toolCallId = :toolCallId
              and t.idempotencyMode = :mode
              and (t.idempotencyStatus is null
                   or (t.idempotencyExpiresAt is not null and t.idempotencyExpiresAt <= :now))
            """)
    int claimIdempotency(String userId, String runId, String toolCallId,
            AgentToolIdempotencyMode mode, AgentToolTrace.IdempotencyStatus claimedStatus,
            java.time.LocalDateTime now, java.time.LocalDateTime expiresAt);

    @Modifying
    @Transactional
    @Query("""
            update AgentToolTrace t set
                t.idempotencyStatus = :resolvedStatus,
                t.idempotencyResolvedAt = :now,
                t.idempotencyExpiresAt = :expiresAt,
                t.updatedAt = :now
            where t.userId = :userId
              and t.runId = :runId
              and t.toolCallId = :toolCallId
              and t.idempotencyStatus = :expectedStatus
            """)
    int resolveIdempotency(String userId, String runId, String toolCallId,
            AgentToolTrace.IdempotencyStatus expectedStatus,
            AgentToolTrace.IdempotencyStatus resolvedStatus,
            java.time.LocalDateTime now, java.time.LocalDateTime expiresAt);

    @Modifying
    @Transactional
    @Query("""
            update AgentToolTrace t set
                t.idempotencyStatus = :resolvedStatus,
                t.idempotencyResolvedAt = :now,
                t.idempotencyExpiresAt = :expiresAt,
                t.updatedAt = :now
            where t.idempotencyMode = :mode
              and t.idempotencyStatus = :claimedStatus
              and t.idempotencyClaimedAt < :staleBefore
            """)
    int recoverOrphanedClaims(AgentToolIdempotencyMode mode,
            AgentToolTrace.IdempotencyStatus claimedStatus,
            AgentToolTrace.IdempotencyStatus resolvedStatus,
            java.time.LocalDateTime staleBefore,
            java.time.LocalDateTime now,
            java.time.LocalDateTime expiresAt);

    @Modifying
    @Transactional
    @Query("""
            update AgentToolTrace t set
                t.idempotencyStatus = null,
                t.idempotencyClaimedAt = null,
                t.idempotencyResolvedAt = null,
                t.idempotencyExpiresAt = null,
                t.updatedAt = :now
            where t.idempotencyMode = :mode
              and t.idempotencyExpiresAt <= :now
            """)
    int clearExpiredStates(AgentToolIdempotencyMode mode, java.time.LocalDateTime now);
}
