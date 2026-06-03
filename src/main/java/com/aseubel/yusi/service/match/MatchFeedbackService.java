package com.aseubel.yusi.service.match;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.pojo.entity.MatchFeedback;
import com.aseubel.yusi.repository.MatchFeedbackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

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

    /**
     * 记录用户对匹配的反馈（含互动深度）。
     */
    @Transactional
    public void recordFeedback(Long matchId, String userId, String action, Integer interactionDepth) {
        if (matchId == null || StrUtil.isBlank(userId) || StrUtil.isBlank(action)) {
            return;
        }
        try {
            feedbackRepository.save(MatchFeedback.builder()
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
    // TODO Phase 3 (F11.3): 提取被接受匹配的 reason 关键词，构建更细粒度的偏好画像；在精排时排除与 REPORT 候选人类似的用户
    public String buildPreferenceContext(String userId) {
        List<MatchFeedback> recentFeedback = feedbackRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId);
        if (recentFeedback.isEmpty()) {
            return null;
        }

        long acceptCount = recentFeedback.stream().filter(f -> "ACCEPT".equals(f.getAction())).count();
        long skipCount = recentFeedback.stream().filter(f -> "SKIP".equals(f.getAction())).count();
        long reportCount = recentFeedback.stream().filter(f -> "REPORT".equals(f.getAction())).count();

        // 提取被接受的 match 的 reason 中的常见关键词
        StringBuilder ctx = new StringBuilder();
        ctx.append("用户历史匹配偏好（最近").append(recentFeedback.size()).append("次）：");
        ctx.append("接受").append(acceptCount).append("次，");
        ctx.append("跳过").append(skipCount).append("次");
        if (reportCount > 0) {
            ctx.append("，举报").append(reportCount).append("次（请严格避免类似匹配）");
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
        return feedbackRepository.countByUserIdAndAction(userId, "REPORT") > 0;
    }
}
