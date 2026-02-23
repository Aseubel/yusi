package com.aseubel.yusi.service.lifegraph;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import com.aseubel.yusi.pojo.entity.LifeGraphMergeJudgment;
import com.aseubel.yusi.repository.LifeGraphEntityRepository;
import com.aseubel.yusi.repository.LifeGraphMergeJudgmentRepository;
import com.aseubel.yusi.service.ai.PromptService;
import com.aseubel.yusi.service.lifegraph.ai.LifeGraphAssistant;
import com.aseubel.yusi.service.lifegraph.dto.LifeGraphMergeSuggestion;
import com.aseubel.yusi.service.notification.NotificationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class LifeGraphMergeSuggestionService {

    private final LifeGraphEntityRepository entityRepository;
    private final LifeGraphMergeJudgmentRepository judgmentRepository;
    private final LifeGraphAssistant assistant;
    private final PromptService promptService;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    private static final int SUGGESTION_LIMIT = 10;

    /**
     * 定时任务：每天凌晨3点执行，为所有用户生成合并建议
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void scheduledMergeSuggestion() {
        log.info("开始执行定时合并建议任务");
        List<LifeGraphEntity> allEntities = entityRepository.findAll();
        Set<String> processedUsers = new HashSet<>();
        
        for (LifeGraphEntity entity : allEntities) {
            String userId = entity.getUserId();
            if (processedUsers.contains(userId)) {
                continue;
            }
            processedUsers.add(userId);
            
            try {
                int count = suggestForUser(userId);
                if (count > 0) {
                    log.info("用户 {} 生成了 {} 条合并建议", userId, count);
                }
            } catch (Exception e) {
                log.error("用户 {} 合并建议生成失败", userId, e);
            }
        }
        log.info("定时合并建议任务完成，处理了 {} 个用户", processedUsers.size());
    }

    /**
     * 为单个用户生成合并建议
     */
    public int suggestForUser(String userId) {
        List<LifeGraphMergeSuggestion> suggestions = suggest(userId, SUGGESTION_LIMIT);
        return suggestions.size();
    }

    /**
     * 获取合并建议（排除已判断的候选对）
     */
    public List<LifeGraphMergeSuggestion> suggest(String userId, int limit) {
        if (StrUtil.isBlank(userId) || limit <= 0) {
            return List.of();
        }
        
        // 1. 获取已判断的实体对，用于排除
        Set<String> judgedPairs = judgmentRepository.findJudgedPairs(userId);
        log.debug("User {} has {} judged pairs", userId, judgedPairs.size());

        // 2. 生成新候选对（排除已判断的）
        List<CandidatePair> candidates = findCandidates(userId, judgedPairs);
        if (candidates.isEmpty()) {
            log.debug("No new candidates for user {}", userId);
            return List.of();
        }

        // 3. 准备 JSON 给 LLM
        String candidatesJson = toJson(candidates);
        if (candidatesJson == null) {
            return List.of();
        }

        // 4. 调用 LLM
        String prompt = promptService.getPrompt(PromptKey.GRAPHRAG_MERGE_SUGGEST.getKey(), "zh-CN");
        if (prompt == null) {
            // Fallback prompt if not in DB
            prompt = "你将获得若干“疑似重复实体”的候选对。请为每一对给出：是否建议合并（YES/NO）、原因、推荐保留的规范名。只输出严格 JSON 数组。";
        }

        String response = assistant.analyzeMergeCandidates(prompt, candidatesJson);
        
        // 5. 解析响应
        List<LlmMergeJudgment> judgments = parseJudgment(response);
        if (judgments == null) {
            return List.of();
        }

        // 6. 保存判断结果并构建返回列表
        List<LifeGraphMergeSuggestion> results = new ArrayList<>();
        List<LifeGraphMergeJudgment> toSave = new ArrayList<>();
        
        for (int i = 0; i < Math.min(candidates.size(), judgments.size()); i++) {
            LlmMergeJudgment llmJudgment = judgments.get(i);
            CandidatePair pair = candidates.get(i);
            
            // 构建判断记录
            LifeGraphMergeJudgment record = LifeGraphMergeJudgment.builder()
                    .userId(userId)
                    .entityIdA(pair.getIdA())
                    .entityIdB(pair.getIdB())
                    .nameA(pair.getNameA())
                    .nameB(pair.getNameB())
                    .type(pair.getType())
                    .simScore(pair.getSimScore())
                    .mergeDecision(llmJudgment.getMerge())
                    .reason(llmJudgment.getReason())
                    .recommendedMasterName(llmJudgment.getRecommendedMasterName())
                    .status("PENDING")
                    .build();
            toSave.add(record);
            
            // 只返回建议合并的
            if ("YES".equalsIgnoreCase(llmJudgment.getMerge())) {
                results.add(LifeGraphMergeSuggestion.builder()
                        .entityIdA(pair.getIdA())
                        .entityIdB(pair.getIdB())
                        .nameA(pair.getNameA())
                        .nameB(pair.getNameB())
                        .type(pair.getType())
                        .score(pair.getSimScore())
                        .reason(llmJudgment.getReason())
                        .recommendedMasterName(llmJudgment.getRecommendedMasterName())
                        .build());
            }
        }
        
        // 7. 批量保存判断记录
        if (!toSave.isEmpty()) {
            judgmentRepository.saveAll(toSave);
            log.info("Saved {} merge judgments for user {}", toSave.size(), userId);
            
            // 8. 为建议合并的候选对创建通知
            for (LifeGraphMergeJudgment judgment : toSave) {
                if ("YES".equalsIgnoreCase(judgment.getMergeDecision())) {
                    notificationService.createMergeSuggestionNotification(
                            userId, 
                            judgment.getId(), 
                            judgment.getNameA(), 
                            judgment.getNameB(), 
                            judgment.getType()
                    );
                }
            }
        }
        
        return results.size() > limit ? results.subList(0, limit) : results;
    }

    /**
     * 获取待处理的合并建议（从数据库读取，不调用LLM）
     */
    public List<LifeGraphMergeSuggestion> getPendingSuggestions(String userId, int limit) {
        List<LifeGraphMergeJudgment> pending = judgmentRepository
                .findByUserIdAndStatusOrderByCreatedAtDesc(userId, "PENDING");
        
        return pending.stream()
                .filter(j -> "YES".equalsIgnoreCase(j.getMergeDecision()))
                .limit(limit)
                .map(j -> LifeGraphMergeSuggestion.builder()
                        .judgmentId(j.getId())
                        .entityIdA(j.getEntityIdA())
                        .entityIdB(j.getEntityIdB())
                        .nameA(j.getNameA())
                        .nameB(j.getNameB())
                        .type(j.getType())
                        .score(j.getSimScore())
                        .reason(j.getReason())
                        .recommendedMasterName(j.getRecommendedMasterName())
                        .build())
                .toList();
    }

    /**
     * 接受合并建议
     */
    @Transactional
    public void acceptMerge(Long judgmentId) {
        judgmentRepository.findById(judgmentId).ifPresent(j -> {
            j.setStatus("ACCEPTED");
            judgmentRepository.save(j);
            // TODO: 执行实际的合并逻辑
        });
    }

    /**
     * 拒绝合并建议
     */
    @Transactional
    public void rejectMerge(Long judgmentId) {
        judgmentRepository.findById(judgmentId).ifPresent(j -> {
            j.setStatus("REJECTED");
            judgmentRepository.save(j);
        });
    }

    private List<CandidatePair> findCandidates(String userId, Set<String> judgedPairs) {
        List<LifeGraphEntity> entities = entityRepository.findTop50ByUserIdOrderByMentionCountDesc(userId);
        List<CandidatePair> candidates = new ArrayList<>();

        for (int i = 0; i < entities.size(); i++) {
            LifeGraphEntity a = entities.get(i);
            if (a.getType() == LifeGraphEntity.EntityType.User) continue;
            
            for (int j = i + 1; j < entities.size(); j++) {
                LifeGraphEntity b = entities.get(j);
                if (b.getType() != a.getType()) continue;
                
                // 检查是否已判断过
                String pairKey = Math.min(a.getId(), b.getId()) + "-" + Math.max(a.getId(), b.getId());
                if (judgedPairs.contains(pairKey)) {
                    continue;
                }
                
                double score = similarity(a.getNameNorm(), b.getNameNorm());
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
