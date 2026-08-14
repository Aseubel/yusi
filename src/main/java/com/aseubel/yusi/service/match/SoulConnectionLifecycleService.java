package com.aseubel.yusi.service.match;

import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.pojo.constant.MatchFeedbackAction;
import com.aseubel.yusi.pojo.constant.ProductEventSensitivity;
import com.aseubel.yusi.pojo.constant.ProductEventSource;
import com.aseubel.yusi.pojo.constant.SecurityAuditAction;
import com.aseubel.yusi.pojo.constant.SecurityAuditActorType;
import com.aseubel.yusi.pojo.constant.SecurityAuditDetailKeys;
import com.aseubel.yusi.pojo.constant.SecurityAuditOutcome;
import com.aseubel.yusi.pojo.constant.SecurityAuditResourceType;
import com.aseubel.yusi.pojo.constant.SoulConnectionAction;
import com.aseubel.yusi.pojo.entity.SoulConnection;
import com.aseubel.yusi.pojo.entity.SoulConnectionEvent;
import com.aseubel.yusi.pojo.entity.SoulConnectionStatus;
import com.aseubel.yusi.pojo.entity.SoulMatch;
import com.aseubel.yusi.repository.SoulConnectionRepository;
import com.aseubel.yusi.repository.SoulConnectionEventRepository;
import com.aseubel.yusi.service.event.ProductEventCommand;
import com.aseubel.yusi.service.event.ProductEventService;
import com.aseubel.yusi.service.security.SecurityAuditCommand;
import com.aseubel.yusi.service.security.SecurityAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class SoulConnectionLifecycleService {

    private final SoulConnectionRepository connectionRepository;
    private final SoulConnectionEventRepository eventRepository;
    private final ProductEventService productEventService;
    private final SecurityAuditService securityAuditService;

    public SoulConnectionLifecycleService(SoulConnectionRepository connectionRepository,
            SoulConnectionEventRepository eventRepository, ProductEventService productEventService) {
        this(connectionRepository, eventRepository, productEventService, null);
    }

    @Autowired
    public SoulConnectionLifecycleService(SoulConnectionRepository connectionRepository,
            SoulConnectionEventRepository eventRepository, ProductEventService productEventService,
            SecurityAuditService securityAuditService) {
        this.connectionRepository = connectionRepository;
        this.eventRepository = eventRepository;
        this.productEventService = productEventService;
        this.securityAuditService = securityAuditService;
    }

    public Optional<SoulConnection> findByMatchId(Long matchId) {
        return connectionRepository.findByMatchId(matchId);
    }

    @Transactional
    public SoulConnection accept(SoulMatch match, String actorUserId) {
        assertParticipant(match, actorUserId);
        SoulConnection connection = connectionRepository.findByMatchId(match.getId())
                .orElseGet(() -> newConnection(match));
        assertCanReactivate(connection);

        LocalDateTime now = LocalDateTime.now();
        SoulConnectionStatus fromStatus = normalizeStatus(connection);
        SoulConnectionStatus targetStatus = bothInterested(match)
                ? SoulConnectionStatus.STARTED
                : SoulConnectionStatus.WAITING_REPLY;
        if (targetStatus == fromStatus) {
            return connection;
        }
        connection.setStatus(targetStatus);
        if (connection.getStatus() == SoulConnectionStatus.STARTED && connection.getStartedAt() == null) {
            connection.setStartedAt(now);
        }
        return saveAndRecord(connection, match, fromStatus, SoulConnectionAction.ACCEPT,
                actorUserId, null, now);
    }

    @Transactional
    public SoulConnection decline(SoulMatch match, String actorUserId, String reasonCategory) {
        assertParticipant(match, actorUserId);
        SoulConnection connection = connectionRepository.findByMatchId(match.getId())
                .orElseGet(() -> newConnection(match));
        if (connection.getStatus() == SoulConnectionStatus.DECLINED) {
            return connection;
        }
        if (connection.getStatus() != SoulConnectionStatus.RECOMMENDED
                && connection.getStatus() != SoulConnectionStatus.WAITING_REPLY) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "当前连接状态不允许拒绝");
        }

        LocalDateTime now = LocalDateTime.now();
        SoulConnectionStatus fromStatus = connection.getStatus();
        connection.setStatus(SoulConnectionStatus.DECLINED);
        connection.setEndedAt(now);
        return saveAndRecord(connection, match, fromStatus, SoulConnectionAction.DECLINE,
                actorUserId, reasonCategory, now);
    }

    @Transactional
    public SoulConnection markMutualResonance(SoulMatch match, String actorUserId) {
        assertParticipant(match, actorUserId);
        SoulConnection connection = requireConnection(match);
        if (connection.getStatus() == SoulConnectionStatus.MUTUAL_RESONANCE) {
            return connection;
        }
        if (connection.getStatus() != SoulConnectionStatus.STARTED) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "连接尚未开始互动");
        }

        LocalDateTime now = LocalDateTime.now();
        SoulConnectionStatus fromStatus = connection.getStatus();
        connection.setStatus(SoulConnectionStatus.MUTUAL_RESONANCE);
        return saveAndRecord(connection, match, fromStatus, SoulConnectionAction.MUTUAL_RESONANCE,
                actorUserId, MatchFeedbackAction.DEEP_INTERACTION.code(), now);
    }

    @Transactional
    public SoulConnection end(SoulMatch match, String actorUserId, String reasonCategory) {
        assertParticipant(match, actorUserId);
        SoulConnection connection = requireConnection(match);
        if (connection.getStatus() == SoulConnectionStatus.ENDED) {
            return connection;
        }
        if (!connection.getStatus().allowsChat()
                && connection.getStatus() != SoulConnectionStatus.WAITING_REPLY) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "当前连接状态不允许结束");
        }

        LocalDateTime now = LocalDateTime.now();
        SoulConnectionStatus fromStatus = connection.getStatus();
        connection.setStatus(SoulConnectionStatus.ENDED);
        connection.setEndedAt(now);
        return saveAndRecord(connection, match, fromStatus, SoulConnectionAction.END,
                actorUserId, reasonCategory, now);
    }

    @Transactional
    public SoulConnection report(SoulMatch match, String actorUserId, String reasonCategory) {
        assertParticipant(match, actorUserId);
        SoulConnection connection = connectionRepository.findByMatchId(match.getId())
                .orElseGet(() -> newConnection(match));
        if (connection.getStatus() == SoulConnectionStatus.BLOCKED) {
            return connection;
        }
        if (connection.getStatus() == SoulConnectionStatus.REPORTED) {
            return connection;
        }
        if (connection.getStatus().isTerminal()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "当前连接已结束，无法举报");
        }

        LocalDateTime now = LocalDateTime.now();
        SoulConnectionStatus fromStatus = connection.getStatus();
        connection.setStatus(SoulConnectionStatus.REPORTED);
        SoulConnection saved = saveAndRecord(connection, match, fromStatus, SoulConnectionAction.REPORT,
                actorUserId, reasonCategory, now);
        recordSecurityAudit(saved, match, SecurityAuditAction.CONNECTION_REPORTED,
                actorUserId, reasonCategory, fromStatus, now);
        return saved;
    }

    @Transactional
    public SoulConnection block(SoulMatch match, String actorUserId, String reasonCategory) {
        assertParticipant(match, actorUserId);
        SoulConnection connection = connectionRepository.findByMatchId(match.getId())
                .orElseGet(() -> newConnection(match));
        if (connection.getStatus() == SoulConnectionStatus.BLOCKED) {
            return connection;
        }
        if (connection.getStatus() == SoulConnectionStatus.DECLINED
                || connection.getStatus() == SoulConnectionStatus.EXPIRED
                || connection.getStatus() == SoulConnectionStatus.ENDED) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "当前连接已结束，无法拉黑");
        }

        LocalDateTime now = LocalDateTime.now();
        SoulConnectionStatus fromStatus = connection.getStatus();
        connection.setStatus(SoulConnectionStatus.BLOCKED);
        connection.setEndedAt(now);
        SoulConnection saved = saveAndRecord(connection, match, fromStatus, SoulConnectionAction.BLOCK,
                actorUserId, reasonCategory, now);
        recordSecurityAudit(saved, match, SecurityAuditAction.CONNECTION_BLOCKED,
                actorUserId, reasonCategory, fromStatus, now);
        return saved;
    }

    public SoulConnectionStatus resolveStatus(SoulMatch match, String currentUserId) {
        Optional<SoulConnection> connection = connectionRepository.findByMatchId(match.getId());
        if (connection.isPresent()) {
            return connection.get().getStatus();
        }
        if (Integer.valueOf(2).equals(match.getStatusA()) || Integer.valueOf(2).equals(match.getStatusB())) {
            return SoulConnectionStatus.DECLINED;
        }
        if (Boolean.TRUE.equals(match.getIsMatched())
                || (Integer.valueOf(1).equals(match.getStatusA()) && Integer.valueOf(1).equals(match.getStatusB()))) {
            return SoulConnectionStatus.STARTED;
        }
        Integer currentStatus = currentUserId.equals(match.getUserAId()) ? match.getStatusA() : match.getStatusB();
        return Integer.valueOf(1).equals(currentStatus)
                ? SoulConnectionStatus.WAITING_REPLY
                : SoulConnectionStatus.RECOMMENDED;
    }

    public void assertChatAllowed(SoulMatch match, String actorUserId) {
        assertParticipant(match, actorUserId);
        Optional<SoulConnection> connection = connectionRepository.findByMatchId(match.getId());
        if (connection.isEmpty() && Boolean.TRUE.equals(match.getIsMatched())) {
            return;
        }
        if (connection.isEmpty() || !connection.get().getStatus().allowsChat()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "当前连接不可发送消息");
        }
    }

    private SoulConnection requireConnection(SoulMatch match) {
        return connectionRepository.findByMatchId(match.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_ERROR, "尚未建立连接"));
    }

    private SoulConnection newConnection(SoulMatch match) {
        LocalDateTime now = LocalDateTime.now();
        return SoulConnection.builder()
                .matchId(match.getId())
                .userAId(match.getUserAId())
                .userBId(match.getUserBId())
                .status(SoulConnectionStatus.RECOMMENDED)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private void assertCanReactivate(SoulConnection connection) {
        if (connection.getStatus() == null) {
            connection.setStatus(SoulConnectionStatus.RECOMMENDED);
        }
        if (connection.getStatus().isTerminal() || connection.getStatus() == SoulConnectionStatus.REPORTED) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "当前连接已结束或进入安全处理，无法恢复");
        }
    }

    private void assertParticipant(SoulMatch match, String actorUserId) {
        if (match == null || actorUserId == null
                || (!actorUserId.equals(match.getUserAId()) && !actorUserId.equals(match.getUserBId()))) {
            recordAccessDenied(match, actorUserId);
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该连接");
        }
    }

    private void recordAccessDenied(SoulMatch match, String actorUserId) {
        if (securityAuditService == null) {
            return;
        }
        Set<String> scopeUserIds = new java.util.LinkedHashSet<>();
        if (match != null) {
            if (match.getUserAId() != null && !match.getUserAId().isBlank()) {
                scopeUserIds.add(match.getUserAId());
            }
            if (match.getUserBId() != null && !match.getUserBId().isBlank()) {
                scopeUserIds.add(match.getUserBId());
            }
        }
        securityAuditService.record(SecurityAuditCommand.builder()
                .action(SecurityAuditAction.ACCESS_DENIED)
                .actorType(actorUserId == null || actorUserId.isBlank()
                        ? SecurityAuditActorType.SYSTEM : SecurityAuditActorType.USER)
                .actorUserId(actorUserId)
                .resourceType(SecurityAuditResourceType.CONNECTION)
                .resourceId(match == null || match.getId() == null ? null : String.valueOf(match.getId()))
                .outcome(SecurityAuditOutcome.DENIED)
                .reasonCode("NOT_PARTICIPANT")
                .details(Map.of(SecurityAuditDetailKeys.OPERATION, "PARTICIPANT_CHECK"))
                .scopeUserIds(scopeUserIds)
                .build());
    }

    private void recordSecurityAudit(SoulConnection connection, SoulMatch match,
            SecurityAuditAction action, String actorUserId, String reasonCategory,
            SoulConnectionStatus fromStatus, LocalDateTime occurredAt) {
        if (securityAuditService == null) {
            return;
        }
        Map<String, String> details = new LinkedHashMap<>();
        if (fromStatus != null) {
            details.put(SecurityAuditDetailKeys.FROM_STATUS, fromStatus.name());
        }
        if (connection.getStatus() != null) {
            details.put(SecurityAuditDetailKeys.TO_STATUS, connection.getStatus().name());
        }
        details.put(SecurityAuditDetailKeys.ACTION, action.code());
        if (reasonCategory != null) {
            details.put(SecurityAuditDetailKeys.REASON_CATEGORY, reasonCategory);
        }
        recordSecurityAudit(SecurityAuditCommand.builder()
                .action(action)
                .actorType(SecurityAuditActorType.USER)
                .actorUserId(actorUserId)
                .subjectUserId(otherParticipant(match, actorUserId))
                .resourceType(SecurityAuditResourceType.CONNECTION)
                .resourceId(connection.getId() == null ? null : String.valueOf(connection.getId()))
                .outcome(SecurityAuditOutcome.SUCCESS)
                .details(details)
                .scopeUserIds(participantIds(match))
                .occurredAt(occurredAt)
                .build());
    }

    private void recordSecurityAudit(SecurityAuditCommand command) {
        securityAuditService.record(command);
    }

    private String otherParticipant(SoulMatch match, String actorUserId) {
        if (match == null || actorUserId == null) {
            return null;
        }
        return actorUserId.equals(match.getUserAId()) ? match.getUserBId() : match.getUserAId();
    }

    private Set<String> participantIds(SoulMatch match) {
        Set<String> participantIds = new java.util.LinkedHashSet<>();
        if (match != null) {
            if (match.getUserAId() != null && !match.getUserAId().isBlank()) {
                participantIds.add(match.getUserAId());
            }
            if (match.getUserBId() != null && !match.getUserBId().isBlank()) {
                participantIds.add(match.getUserBId());
            }
        }
        return participantIds;
    }

    private boolean bothInterested(SoulMatch match) {
        return Integer.valueOf(1).equals(match.getStatusA()) && Integer.valueOf(1).equals(match.getStatusB());
    }

    private void audit(SoulConnection connection, String action, String actorUserId,
            String reasonCategory, LocalDateTime now) {
        connection.setLastAction(action);
        connection.setLastActionBy(actorUserId);
        connection.setReasonCategory(reasonCategory);
        connection.setUpdatedAt(now);
    }

    private SoulConnectionStatus normalizeStatus(SoulConnection connection) {
        if (connection.getStatus() == null) {
            connection.setStatus(SoulConnectionStatus.RECOMMENDED);
        }
        return connection.getStatus();
    }

    private SoulConnection saveAndRecord(SoulConnection connection, SoulMatch match,
            SoulConnectionStatus fromStatus, SoulConnectionAction action, String actorUserId,
            String reasonCategory, LocalDateTime now) {
        audit(connection, action.code(), actorUserId, reasonCategory, now);
        SoulConnection saved = connectionRepository.save(connection);
        if (saved.getId() == null) {
            throw new IllegalStateException("Connection ID is required before recording a lifecycle event");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fromStatus", fromStatus != null ? fromStatus.name() : null);
        payload.put("toStatus", saved.getStatus() != null ? saved.getStatus().name() : null);
        payload.put("action", action.code());
        if (reasonCategory != null) {
            payload.put("reasonCategory", reasonCategory);
        }
        com.aseubel.yusi.pojo.entity.ProductEvent productEvent = productEventService.record(ProductEventCommand.builder()
                .eventName(action.eventName())
                .source(ProductEventSource.CONNECTION.code())
                .sensitivity(ProductEventSensitivity.RESTRICTED.name())
                .userId(actorUserId)
                .actorUserId(actorUserId)
                .matchId(match.getId())
                .connectionId(saved.getId())
                .idempotencyKey("connection:" + saved.getId() + ":" + fromStatus + ":"
                        + saved.getStatus() + ":" + action.code())
                .scopeUserIds(Set.of(match.getUserAId(), match.getUserBId()))
                .payload(payload)
                .occurredAt(now)
                .build());
        eventRepository.save(SoulConnectionEvent.builder()
                .eventId(productEvent.getEventId())
                .eventName(action.eventName())
                .schemaVersion(1)
                .connectionId(saved.getId())
                .matchId(match.getId())
                .actorUserId(actorUserId)
                .fromStatus(fromStatus)
                .toStatus(saved.getStatus())
                .action(action.code())
                .reasonCategory(reasonCategory)
                .occurredAt(now)
                .build());
        return saved;
    }
}
