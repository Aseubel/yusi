package com.aseubel.yusi.service.lifegraph;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import com.aseubel.yusi.repository.LifeGraphEntityRepository;
import com.aseubel.yusi.service.ai.PromptService;
import com.aseubel.yusi.service.lifegraph.ai.LifeGraphAssistant;
import com.aseubel.yusi.service.lifegraph.dto.LifeGraphMergeSuggestion;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LifeGraphMergeSuggestionService {

    private final LifeGraphEntityRepository entityRepository;
    private final LifeGraphAssistant assistant;
    private final PromptService promptService;
    private final ObjectMapper objectMapper;

    public List<LifeGraphMergeSuggestion> suggest(String userId, int limit) {
        if (StrUtil.isBlank(userId) || limit <= 0) {
            return List.of();
        }
        
        // 1. Generate Candidates using loose string similarity
        List<CandidatePair> candidates = findCandidates(userId);
        if (candidates.isEmpty()) {
            return List.of();
        }

        // 2. Prepare JSON for LLM
        String candidatesJson = toJson(candidates);
        if (candidatesJson == null) {
            return List.of();
        }

        // 3. Call LLM
        String prompt = promptService.getPrompt(PromptKey.GRAPHRAG_MERGE_SUGGEST.getKey(), "zh-CN");
        if (prompt == null) {
            // Fallback prompt if not in DB
            prompt = "你将获得若干“疑似重复实体”的候选对。请为每一对给出：是否建议合并（YES/NO）、原因、推荐保留的规范名。只输出严格 JSON 数组。";
        }

        String response = assistant.analyzeMergeCandidates(prompt, candidatesJson);
        
        // 4. Parse Response and Filter
        List<LlmMergeJudgment> judgments = parseJudgment(response);
        if (judgments == null) {
            return List.of();
        }

        List<LifeGraphMergeSuggestion> results = new ArrayList<>();
        for (int i = 0; i < Math.min(candidates.size(), judgments.size()); i++) {
            LlmMergeJudgment judgment = judgments.get(i);
            if ("YES".equalsIgnoreCase(judgment.getMerge())) {
                CandidatePair pair = candidates.get(i);
                results.add(LifeGraphMergeSuggestion.builder()
                        .entityIdA(pair.getIdA())
                        .entityIdB(pair.getIdB())
                        .nameA(pair.getNameA())
                        .nameB(pair.getNameB())
                        .type(pair.getType())
                        .score(pair.getSimScore())
                        .reason(judgment.getReason())
                        .recommendedMasterName(judgment.getRecommendedMasterName())
                        .build());
            }
        }
        
        return results.size() > limit ? results.subList(0, limit) : results;
    }

    private List<CandidatePair> findCandidates(String userId) {
        List<LifeGraphEntity> entities = entityRepository.findTop50ByUserIdOrderByMentionCountDesc(userId);
        List<CandidatePair> candidates = new ArrayList<>();

        for (int i = 0; i < entities.size(); i++) {
            LifeGraphEntity a = entities.get(i);
            if (a.getType() == LifeGraphEntity.EntityType.User) continue;
            
            for (int j = i + 1; j < entities.size(); j++) {
                LifeGraphEntity b = entities.get(j);
                if (b.getType() != a.getType()) continue;
                
                double score = similarity(a.getNameNorm(), b.getNameNorm());
                // Lower threshold to 0.7 to let LLM decide
                if (score >= 0.7) {
                    candidates.add(new CandidatePair(a.getId(), b.getId(), a.getNameNorm(), b.getNameNorm(), a.getType().name(), score));
                }
            }
        }
        return candidates;
    }
    
    @Data
    private static class CandidatePair {
        private final Long idA;
        private final Long idB;
        private final String nameA;
        private final String nameB;
        private final String type;
        private final double simScore;
    }

    @Data
    private static class LlmMergeJudgment {
        private String merge; // YES/NO
        private String reason;
        private String recommendedMasterName;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("JSON serialization failed", e);
            return null;
        }
    }

    private List<LlmMergeJudgment> parseJudgment(String raw) {
        if (StrUtil.isBlank(raw)) return null;
        String json = extractJsonArray(raw);
        if (json == null) return null;
        
        try {
            return objectMapper.readValue(json, new TypeReference<List<LlmMergeJudgment>>() {});
        } catch (Exception e) {
            log.error("Failed to parse LLM merge judgment", e);
            return null;
        }
    }

    private String extractJsonArray(String raw) {
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        if (start < 0 || end <= start) return null;
        return raw.substring(start, end + 1);
    }

    private double similarity(String a, String b) {
        if (StrUtil.isBlank(a) || StrUtil.isBlank(b)) {
            return 0.0;
        }
        String x = a.trim();
        String y = b.trim();
        if (x.equalsIgnoreCase(y)) {
            return 1.0;
        }
        if (x.contains(y) || y.contains(x)) {
            int min = Math.min(x.length(), y.length());
            int max = Math.max(x.length(), y.length());
            return 0.85 + 0.15 * ((double) min / (double) max);
        }
        int d = levenshtein(x, y);
        int maxLen = Math.max(x.length(), y.length());
        if (maxLen == 0) {
            return 1.0;
        }
        return 1.0 - ((double) d / (double) maxLen);
    }

    private int levenshtein(String a, String b) {
        int n = a.length();
        int m = b.length();
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[m];
    }
}
