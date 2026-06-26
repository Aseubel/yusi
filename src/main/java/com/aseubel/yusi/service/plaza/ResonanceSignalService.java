package com.aseubel.yusi.service.plaza;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.pojo.entity.ResonanceSignal;
import com.aseubel.yusi.repository.ResonanceSignalRepository;
import com.aseubel.yusi.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 广场共鸣信号服务（F9.2）。
 * 允许用户在广场匿名发送轻量共鸣信号给其他用户。
 *
 * @author Aseubel
 * @date 2026/06/03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResonanceSignalService {

    private final ResonanceSignalRepository signalRepository;
    private final NotificationService notificationService;

    /**
     * 发送匿名共鸣信号。
     */
    @Transactional
    public ResonanceSignal sendSignal(String fromUserId, String toUserId, Long cardId, String message) {
        if (fromUserId.equals(toUserId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不能向自己发送共鸣信号");
        }
        if (message != null && message.length() > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "附言不能超过100字");
        }

        // 检查是否已发送过（避免重复）
        ResonanceSignal existing = signalRepository.findByFromUserIdAndToUserId(fromUserId, toUserId)
                .orElse(null);
        if (existing != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "已经发送过共鸣信号");
        }

        LocalDateTime now = LocalDateTime.now();
        ResonanceSignal signal = ResonanceSignal.builder()
                .fromUserId(fromUserId)
                .toUserId(toUserId)
                .cardId(cardId)
                .message(StrUtil.blankToDefault(message, null))
                .isRead(false)
                .isMutual(false)
                .createdAt(now)
                .build();
        ResonanceSignal saved = signalRepository.save(signal);

        // 检查是否为相互共鸣
        ResonanceSignal reverse = signalRepository.findByFromUserIdAndToUserId(toUserId, fromUserId)
                .orElse(null);
        if (reverse != null) {
            // 相互共鸣！
            signal.setIsMutual(true);
            reverse.setIsMutual(true);
            signalRepository.save(signal);
            signalRepository.save(reverse);

            // TODO Phase 3 (F9.4): 双向共鸣时，将双方加入匹配推荐优先队列，或直接触发一次轻量匹配
            // 通知双方
            notifyMutualResonance(fromUserId, toUserId);
        } else {
            // 通知接收方
            notifyNewSignal(saved);
        }

        return saved;
    }

    /** 查询用户收到的共鸣信号列表 */
    public List<ResonanceSignal> getReceivedSignals(String userId, Pageable pageable) {
        return signalRepository.findByToUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    /** 标记信号为已读 */
    @Transactional
    public void markAsRead(Long signalId, String userId) {
        signalRepository.findById(signalId).ifPresent(signal -> {
            if (signal.getToUserId().equals(userId) && Boolean.FALSE.equals(signal.getIsRead())) {
                signal.setIsRead(true);
                signalRepository.save(signal);
            }
        });
    }

    /** 统计未读信号 */
    public long countUnread(String userId) {
        return signalRepository.countByToUserIdAndIsReadFalse(userId);
    }

    private void notifyNewSignal(ResonanceSignal signal) {
        notificationService.createNotification(
                signal.getToUserId(),
                "RESONANCE_SIGNAL",
                "有人与你产生了共鸣",
                "有人在广场感受到了与你的共鸣，向你发送了一个匿名信号。",
                null,
                null,
                null);
    }

    private void notifyMutualResonance(String userIdA, String userIdB) {
        String content = "你们互相发送了共鸣信号！这是一种奇妙的默契，也许可以了解更多。";
        for (String uid : new String[] { userIdA, userIdB }) {
            notificationService.createNotification(
                    uid,
                    "MUTUAL_RESONANCE",
                    "双向共鸣！",
                    content,
                    null,
                    null,
                    null);
        }
    }
}
