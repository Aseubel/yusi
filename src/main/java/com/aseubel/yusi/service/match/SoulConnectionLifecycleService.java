package com.aseubel.yusi.service.match;

import cn.hutool.core.util.IdUtil;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.pojo.constant.MatchFeedbackAction;
import com.aseubel.yusi.pojo.constant.SoulConnectionAction;
import com.aseubel.yusi.pojo.entity.SoulConnection;
import com.aseubel.yusi.pojo.entity.SoulConnectionEvent;
import com.aseubel.yusi.pojo.entity.SoulConnectionStatus;
import com.aseubel.yusi.pojo.entity.SoulMatch;
import com.aseubel.yusi.repository.SoulConnectionRepository;
import com.aseubel.yusi.repository.SoulConnectionEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SoulConnectionLifecycleService {

    private final SoulConnectionRepository connectionRepository;
    private final SoulConnectionEventRepository eventRepository;

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
        return saveAndRecord(connection, match, fromStatus, SoulConnectionAction.REPORT,
                actorUserId, reasonCategory, now);
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
        return saveAndRecord(connection, match, fromStatus, SoulConnectionAction.BLOCK,
                actorUserId, reasonCategory, now);
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
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该连接");
        }
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
        eventRepository.save(SoulConnectionEvent.builder()
                .eventId(IdUtil.fastSimpleUUID())
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
