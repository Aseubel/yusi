package com.aseubel.yusi.service.match;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.pojo.constant.MatchFeedbackAction;
import com.aseubel.yusi.pojo.entity.MatchFeedback;
import com.aseubel.yusi.pojo.entity.SoulConnection;
import com.aseubel.yusi.pojo.entity.SoulMatch;
import com.aseubel.yusi.repository.MatchFeedbackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 匹配反馈服务。
 * 收集用户的匹配行为反馈，并将其转化为 Agent 精排时可用的偏好上下文。
 *
 * @author Aseubel
 * @date 2026/06/03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchFeedbackService {

    private final MatchFeedbackRepository feedbackRepository;

    /**
     * 记录用户对匹配的反馈。
     */
    @Transactional
    public void recordFeedback(Long matchId, String userId, String action) {
        recordFeedback(matchId, userId, action, null);
    }

    /** 记录匹配后的互动深度。 */
    @Transactional
    public void recordInteraction(Long matchId, String userId, int interactionDepth) {
        if (interactionDepth < 0) {
            return;
        }
        recordFeedback(matchId, userId, MatchFeedbackAction.INTERACT.code(), interactionDepth);
    }

    /** 记录举报信号，供后续精排排除高风险匹配。 */
    @Transactional
    public void recordReport(Long matchId, String userId) {
        recordFeedback(matchId, userId, MatchFeedbackAction.REPORT.code());
    }

    /**
     * 记录用户对匹配的反馈（含互动深度）。
     */
    @Transactional
    public void recordFeedback(Long matchId, String userId, String action, Integer interactionDepth) {
        recordConnectionFeedback(null, matchId, userId, action, interactionDepth);
    }

    /** 记录带独立连接 ID 的连接反馈。 */
    @Transactional
    public void recordConnectionFeedback(Long connectionId, Long matchId, String userId, String action) {
        recordConnectionFeedback(connectionId, matchId, userId, action, null);
    }

    @Transactional
    public void recordConnectionFeedback(Long connectionId, Long matchId, String userId,
            String action, Integer interactionDepth) {
        if (matchId == null || StrUtil.isBlank(userId) || StrUtil.isBlank(action)) {
            return;
        }
        try {
            feedbackRepository.save(MatchFeedback.builder()
                    .connectionId(connectionId)
                    .matchId(matchId)
                    .userId(userId)
                    .action(action)
                    .interactionDepth(interactionDepth)
                    .build());
        } catch (Exception e) {
            log.warn("记录匹配反馈失败: matchId={}, userId={}, action={}", matchId, userId, action, e);
        }
    }

    /**
     * 构建用户匹配偏好摘要，供精排 prompt 使用。
     * 返回 null 表示无足够反馈数据。
     */
    public String buildPreferenceContext(String userId) {
        List<MatchFeedback> recentFeedback = feedbackRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId);
        if (recentFeedback.isEmpty()) {
            return null;
        }

        long acceptCount = recentFeedback.stream().filter(f -> MatchFeedbackAction.ACCEPT.code().equals(f.getAction())).count();
        long skipCount = recentFeedback.stream().filter(f -> MatchFeedbackAction.SKIP.code().equals(f.getAction())).count();
        long reportCount = recentFeedback.stream().filter(f -> MatchFeedbackAction.REPORT.code().equals(f.getAction())).count();
        int interactionDepth = recentFeedback.stream()
                .filter(f -> MatchFeedbackAction.INTERACT.code().equals(f.getAction()))
                .map(MatchFeedback::getInteractionDepth)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        // 聚合行为信号，作为精排上下文的轻量偏好摘要
        StringBuilder ctx = new StringBuilder();
        ctx.append("用户历史匹配偏好（最近").append(recentFeedback.size()).append("次）：");
        ctx.append("接受").append(acceptCount).append("次，");
        ctx.append("跳过").append(skipCount).append("次");
        if (reportCount > 0) {
            ctx.append("，举报").append(reportCount).append("次（请严格避免类似匹配）");
        }
        if (interactionDepth > 0) {
            ctx.append("，累计互动").append(interactionDepth).append("条消息");
        }
        ctx.append("。");

        if (acceptCount > skipCount) {
            ctx.append("用户倾向接受匹配，可适当放宽共鸣阈值。");
        } else if (skipCount > acceptCount) {
            ctx.append("用户较挑剔匹配，请提高共鸣判断的严格度。");
        }

        return ctx.toString();
    }

    /**
     * 检查用户是否有强负面信号（如举报），精排时应排除类似候选人。
     */
    public boolean hasStrongNegativeSignal(String userId) {
        return feedbackRepository.countByUserIdAndAction(userId, MatchFeedbackAction.REPORT.code()) > 0;
    }

    /** 举报、拉黑或明确不继续时，后续不得重新推荐同一对象。 */
    public boolean hasStrongNegativeSignal(Long matchId) {
        return matchId != null && feedbackRepository.countByMatchIdAndActionIn(matchId,
                List.of(MatchFeedbackAction.REPORT.code(), MatchFeedbackAction.UNSAFE.code(),
                        MatchFeedbackAction.BLOCK.code(), MatchFeedbackAction.DO_NOT_CONTINUE.code())) > 0;
    }

    /** 只有双方都明确反馈互动很深，才进入双向共鸣状态。 */
    public boolean hasMutualDeepInteraction(SoulConnection connection, SoulMatch match) {
        if (connection == null || connection.getId() == null || match == null) {
            return false;
        }
        List<String> deepInteraction = List.of(MatchFeedbackAction.DEEP_INTERACTION.code());
        return feedbackRepository.existsByConnectionIdAndUserIdAndActionIn(
                        connection.getId(), match.getUserAId(), deepInteraction)
                && feedbackRepository.existsByConnectionIdAndUserIdAndActionIn(
                        connection.getId(), match.getUserBId(), deepInteraction);
    }
}
