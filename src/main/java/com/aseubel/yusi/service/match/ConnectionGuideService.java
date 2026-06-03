package com.aseubel.yusi.service.match;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.pojo.dto.match.MatchRerankResult;
import com.aseubel.yusi.pojo.entity.MatchProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 匹配后引导服务。
 * 匹配成功后，为双方生成破冰话题和情景建议，帮助用户自然开启对话。
 *
 * @author Aseubel
 * @date 2026/06/03
 */
@Slf4j
@Service
public class ConnectionGuideService {

    /**
     * 基于匹配精排结果和双方画像，生成破冰话题列表。
     */
    public List<String> generateIceBreakers(MatchProfile profileA, MatchProfile profileB,
            MatchRerankResult rerankResult) {
        List<String> topics = new ArrayList<>();

        // 从精排结果中提取破冰线索
        if (rerankResult != null && StrUtil.isNotBlank(rerankResult.getIceBreaker())) {
            topics.add("💡 " + rerankResult.getIceBreaker());
        }
        if (rerankResult != null && StrUtil.isNotBlank(rerankResult.getReason())) {
            topics.add("你们可能都感兴趣的话题：" + rerankResult.getReason());
        }
        if (rerankResult != null && StrUtil.isNotBlank(rerankResult.getTimingReason())) {
            topics.add("为什么是现在：" + rerankResult.getTimingReason());
        }

        // 从画像中挖掘共同点
        if (profileA != null && profileB != null) {
            topics.addAll(extractCommonGroundTopics(profileA, profileB));
        }

        // 兜底话题
        if (topics.isEmpty()) {
            topics.add("聊聊最近让你感到平静的一个时刻");
            topics.add("如果可以给过去的自己一个建议，会是什么？");
        }

        return topics;
    }

    /**
     * 从双方画像中提取共同话题线索。
     */
    private List<String> extractCommonGroundTopics(MatchProfile profileA, MatchProfile profileB) {
        List<String> topics = new ArrayList<>();

        // 从 persona summary 中提取共同兴趣
        String personaA = profileA.getPersonaSummary();
        String personaB = profileB.getPersonaSummary();
        if (StrUtil.isNotBlank(personaA) && StrUtil.isNotBlank(personaB)
                && !"稳定偏好信息较少。".equals(personaA)
                && !"稳定偏好信息较少。".equals(personaB)) {
            // 寻找可能共同的关键词
            String[] interestKeywords = {"摄影", "旅行", "电影", "音乐", "阅读", "写作", "运动", "美食",
                    "哲学", "心理学", "艺术", "科技", "自然", "猫", "狗", "独处"};
            for (String keyword : interestKeywords) {
                if (personaA.contains(keyword) && personaB.contains(keyword)) {
                    topics.add("你们似乎都提到了「" + keyword + "」，这是个不错的起点");
                }
            }
        }

        // 从 mid-memory 中提取当前阶段相似性
        String midA = profileA.getMidMemorySummary();
        String midB = profileB.getMidMemorySummary();
        if (StrUtil.isNotBlank(midA) && StrUtil.isNotBlank(midB)
                && !"近期状态信息较少。".equals(midA)
                && !"近期状态信息较少。".equals(midB)) {
            topics.add("你们似乎正处在相似的阶段，可以聊聊彼此最近的感受");
        }

        return topics;
    }

    /**
     * 生成情景室推荐信息。若双方画像信息不足以推荐，返回 null。
     */
    public String suggestScenario(MatchProfile profileA, MatchProfile profileB) {
        // 在 Phase 2 中，返回简单推荐文案
        // 后续可接入 ScenarioRepository 做智能匹配
        String midA = profileA != null ? StrUtil.blankToDefault(profileA.getMidMemorySummary(), "") : "";
        String midB = profileB != null ? StrUtil.blankToDefault(profileB.getMidMemorySummary(), "") : "";

        if (StrUtil.isNotBlank(midA) && StrUtil.isNotBlank(midB)
                && !"近期状态信息较少。".equals(midA)
                && !"近期状态信息较少。".equals(midB)) {
            return "推荐开启一个情景室，让场景引导你们自然地互相了解";
        }
        return null;
    }
}
