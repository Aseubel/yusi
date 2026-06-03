package com.aseubel.yusi.service.cognition;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.pojo.entity.CognitiveConflict;
import com.aseubel.yusi.pojo.entity.UserPersona;
import com.aseubel.yusi.repository.CognitiveConflictRepository;
import com.aseubel.yusi.service.user.UserPersonaService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 认知冲突检测器（F11.3）。
 * 当新增 mid-memory 洞察时，与已有 persona 做语义一致性对比，发现矛盾则标记。
 *
 * @author Aseubel
 * @date 2026/06/03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CognitiveConflictDetector {

    private final CognitiveConflictAssistant conflictAssistant;
    private final CognitiveConflictRepository conflictRepository;
    private final UserPersonaService userPersonaService;
    private final ObjectMapper objectMapper;

    /** 同一天内对同一用户最多创建3条冲突，避免过度打扰 */
    private static final int MAX_CONFLICTS_PER_DAY = 3;

    /**
     * 检测新洞察是否与已有认知冲突。如发现冲突则持久化记录。
     * 由 MidMemoryUpdateServiceImpl 在写入 mid-memory 后调用。
     *
     * @param userId    用户ID
     * @param newInsight 新写入的中期记忆摘要
     */
    public void checkAndRecord(String userId, String newInsight) {
        if (StrUtil.isBlank(userId) || StrUtil.isBlank(newInsight)) {
            return;
        }

        UserPersona persona = userPersonaService.getUserPersona(userId);
        if (persona == null) {
            return; // 画像不足，无法对比
        }

        String existingBelief = buildExistingBelief(persona);
        if (StrUtil.isBlank(existingBelief)) {
            return; // 无足够的已有认知信息
        }

        // 频率控制
        if (tooManyConflictsToday(userId)) {
            return;
        }

        try {
            String raw = conflictAssistant.detect(existingBelief, newInsight);
            JsonNode result = objectMapper.readTree(extractJson(raw));
            if (result.has("hasConflict") && result.get("hasConflict").asBoolean()) {
                String description = result.has("description")
                        ? result.get("description").asText()
                        : "你的想法似乎和之前有些不同";

                conflictRepository.save(CognitiveConflict.builder()
                        .userId(userId)
                        .description(description)
                        .existingBelief(existingBelief)
                        .newObservation(newInsight)
                        .source("PERSONA")
                        .resolved(false)
                        .build());

                log.info("检测到认知冲突: userId={}, desc={}", userId, description);
            }
        } catch (Exception e) {
            log.warn("认知冲突检测失败: userId={}", userId, e);
        }
    }

    /**
     * 获取用户未解决的冲突列表。
     */
    public List<CognitiveConflict> getUnresolved(String userId) {
        return conflictRepository.findByUserIdAndResolvedFalseOrderByCreatedAtDesc(userId);
    }

    /**
     * 获取未解决冲突的摘要文本，供 ContextBuilderService 注入 system prompt。
     * 返回 null 表示无未解决冲突。
     */
    public String getUnresolvedContext(String userId) {
        List<CognitiveConflict> unresolved = getUnresolved(userId);
        if (unresolved.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("你注意到用户最近的表达与之前的认知有些不一致的地方，可以在对话中自然地问一问：\n");
        for (CognitiveConflict conflict : unresolved) {
            sb.append("- ").append(conflict.getDescription()).append("\n");
        }
        return sb.toString();
    }

    private String buildExistingBelief(UserPersona persona) {
        StringBuilder sb = new StringBuilder();
        if (StrUtil.isNotBlank(persona.getInterests())) {
            sb.append("兴趣: ").append(persona.getInterests()).append("。");
        }
        if (StrUtil.isNotBlank(persona.getTone())) {
            sb.append("偏好语气: ").append(persona.getTone()).append("。");
        }
        if (StrUtil.isNotBlank(persona.getCustomInstructions())) {
            sb.append("相处方式: ").append(persona.getCustomInstructions()).append("。");
        }
        return sb.toString();
    }

    private boolean tooManyConflictsToday(String userId) {
        List<CognitiveConflict> recent = conflictRepository.findTop3ByUserIdOrderByCreatedAtDesc(userId);
        if (recent.size() < MAX_CONFLICTS_PER_DAY) {
            return false;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        long todayCount = recent.stream()
                .filter(c -> c.getCreatedAt() != null && c.getCreatedAt().isAfter(cutoff))
                .count();
        return todayCount >= MAX_CONFLICTS_PER_DAY;
    }

    private String extractJson(String raw) {
        if (raw == null) { return "{}"; }
        int s = raw.indexOf('{'), e = raw.lastIndexOf('}');
        return (s >= 0 && e > s) ? raw.substring(s, e + 1) : "{}";
    }
}
