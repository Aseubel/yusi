package com.aseubel.yusi.service.match.impl;

import cn.hutool.core.collection.CollUtil;

import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.common.utils.ModelUtils;
import com.aseubel.yusi.pojo.dto.match.MatchStatusResponse;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.pojo.entity.SoulMatch;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.pojo.entity.UserPersona;
import com.aseubel.yusi.service.user.UserPersonaService;
import com.aseubel.yusi.repository.DiaryRepository;
import com.aseubel.yusi.repository.SoulMatchRepository;
import com.aseubel.yusi.service.ai.model.ModelRouteContext;
import com.aseubel.yusi.service.ai.model.ModelRouteContextHolder;
import com.aseubel.yusi.service.diary.Assistant;
import com.aseubel.yusi.service.match.MatchAssistant;
import com.aseubel.yusi.service.match.MatchService;
import com.aseubel.yusi.service.user.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * @author Aseubel
 * @date 2025/12/21
 */
@Slf4j
@Service
public class MatchServiceImpl implements MatchService {

    @Autowired
    private UserService userService;

    @Autowired
    private SoulMatchRepository soulMatchRepository;

    @Autowired
    private DiaryRepository diaryRepository;

    @Autowired
    private UserPersonaService userPersonaService;

    @Autowired
    private MatchAssistant matchAssistant;

    @Override
    @Scheduled(cron = "0 0 2 * * ?") // Every day at 2 AM
    public void runDailyMatching() {
        log.info("Starting daily matching process...");
        // 设置模型路由上下文
        ModelRouteContextHolder.set(ModelRouteContext.builder()
                .language(ModelUtils.normalizeLanguage("zh"))
                .scene(PromptKey.SOUL_MATCH.getKey())
                .build());
        List<User> candidates = userService.getMatchEnabledUsers();
        if (CollUtil.isEmpty(candidates) || candidates.size() < 2) {
            log.info("Not enough candidates for matching.");
            return;
        }

        // Shuffle to randomize
        Collections.shuffle(candidates);

        /*
         * 当前策略: 基于规则的智能启发式匹配
         * 
         * 规则：
         * 1. 过滤近期已配对的用户
         * 2. 每天每个用户最多匹配 1 次 (控制AI成本)
         * 3. 意图(intent)相同优先匹配
         * 4. 基于用户日记数量决定活跃度权重
         */
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        List<String> processedUserIds = new ArrayList<>();

        for (User userA : candidates) {
            if (processedUserIds.contains(userA.getUserId()))
                continue;

            // 检查今天是否已经匹配过
            long todayMatches = soulMatchRepository.countMatchesSince(userA.getUserId(), startOfDay);
            if (todayMatches >= 1) {
                processedUserIds.add(userA.getUserId());
                continue;
            }

            // 获取 userA 的所有历史匹配，避免重复匹配
            List<SoulMatch> userAMatchesA = soulMatchRepository.findByUserAId(userA.getUserId());
            List<SoulMatch> userAMatchesB = soulMatchRepository.findByUserBId(userA.getUserId());
            List<String> historyPartnerIds = new ArrayList<>();
            userAMatchesA.forEach(m -> historyPartnerIds.add(m.getUserBId()));
            userAMatchesB.forEach(m -> historyPartnerIds.add(m.getUserAId()));

            // Find a partner
            User partner = null;
            int bestScore = -1;

            for (User potential : candidates) {
                if (potential.getUserId().equals(userA.getUserId()))
                    continue;
                if (processedUserIds.contains(potential.getUserId()))
                    continue;

                // 检查对方今天是否已匹配
                long potentialTodayMatches = soulMatchRepository.countMatchesSince(potential.getUserId(), startOfDay);
                if (potentialTodayMatches >= 1) {
                    continue;
                }

                // 避免重复匹配
                if (historyPartnerIds.contains(potential.getUserId())) {
                    continue;
                }

                // 计算匹配分数
                int score = 0;

                // 1. 意图相同加分 (50分)
                if (userA.getMatchIntent() != null && userA.getMatchIntent().equals(potential.getMatchIntent())) {
                    score += 50;
                }

                // 2. 活跃度相近加分 (这里用日记数量模拟)
                long diariesA = diaryRepository.countByUserId(userA.getUserId());
                long diariesB = diaryRepository.countByUserId(potential.getUserId());
                if (Math.abs(diariesA - diariesB) < 10) {
                    score += 20;
                }

                // 3. 用户画像(Persona)加分
                UserPersona personaA = userPersonaService.getUserPersona(userA.getUserId());
                UserPersona personaB = userPersonaService.getUserPersona(potential.getUserId());

                // 4. 让 AI Agent 综合评估给分 (权重更高)
                int aiScore = 0;
                try {
                    String profileA = getProfileSummaryWithPersona(userA.getUserId(), personaA);
                    String profileB = getProfileSummaryWithPersona(potential.getUserId(), personaB);
                    String scoreStr = matchAssistant.evaluateMatchScore(profileA, profileB);
                    aiScore = Integer.parseInt(scoreStr.trim());
                    log.debug("AI 匹配打分 - UserA: {}, UserB: {}, 分数: {}", userA.getUserId(), potential.getUserId(),
                            aiScore);
                } catch (Exception e) {
                    log.warn("AI 匹配打分失败, 回退到基础规则打分", e);
                    if (personaA != null && personaB != null) {
                        // 如果都在同一个城市
                        if (personaA.getLocation() != null && personaB.getLocation() != null
                                && !personaA.getLocation().isEmpty()
                                && personaA.getLocation().equals(personaB.getLocation())) {
                            score += 30;
                        }
                        // 兴趣爱好有重叠（简单的字符串包含检查）
                        if (personaA.getInterests() != null && personaB.getInterests() != null) {
                            String[] interestsA = personaA.getInterests().split("[,，、 ]+");
                            for (String interest : interestsA) {
                                if (!interest.trim().isEmpty() && personaB.getInterests().contains(interest.trim())) {
                                    score += 10;
                                }
                            }
                        }
                    }
                }

                score += aiScore;

                if (score > bestScore) {
                    bestScore = score;
                    partner = potential;
                }
            }

            if (partner != null) {
                processedUserIds.add(userA.getUserId());
                processedUserIds.add(partner.getUserId());
                createMatch(userA, partner);
            }
        }
        log.info("Daily matching process completed.");
    }

    private void createMatch(User userA, User userB) {
        log.info("Creating match for {} and {}", userA.getUserName(), userB.getUserName());

        UserPersona personaA = userPersonaService.getUserPersona(userA.getUserId());
        UserPersona personaB = userPersonaService.getUserPersona(userB.getUserId());

        String profileA = getProfileSummaryWithPersona(userA.getUserId(), personaA);
        String profileB = getProfileSummaryWithPersona(userB.getUserId(), personaB);

        // Generate Letter A to B (User A sees this recommending B)
        // Wait, prompt says: "Recommend User B to User A".
        // So letterAtoB = generate(profileA, profileB)

        // Asynchronously generate letters
        CompletableFuture<String> futureA = generateLetter(userA.getUserId(), profileA, profileB);
        CompletableFuture<String> futureB = generateLetter(userB.getUserId(), profileB, profileA); // Recommend A to B

        try {
            CompletableFuture.allOf(futureA, futureB).join();

            SoulMatch match = SoulMatch.builder()
                    .userAId(userA.getUserId())
                    .userBId(userB.getUserId())
                    .letterAtoB(futureA.get())
                    .letterBtoA(futureB.get())
                    .statusA(0) // Pending
                    .statusB(0) // Pending
                    .isMatched(false)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();

            soulMatchRepository.save(match);
        } catch (Exception e) {
            log.error("Failed to create match", e);
        }
    }

    private String getProfileSummaryWithPersona(String userId, UserPersona persona) {
        StringBuilder summary = new StringBuilder();
        if (persona != null) {
            if (persona.getInterests() != null && !persona.getInterests().isEmpty()) {
                summary.append("兴趣爱好: ").append(persona.getInterests()).append("\n");
            }
            // UserPersona doesn't have getPersonality(), let's use getTone() or
            // customInstructions
            if (persona.getTone() != null && !persona.getTone().isEmpty()) {
                summary.append("偏好语气: ").append(persona.getTone()).append("\n");
            }
            if (persona.getLocation() != null && !persona.getLocation().isEmpty()) {
                summary.append("所在城市: ").append(persona.getLocation()).append("\n");
            }
        }
        summary.append("近期日记摘要:\n");
        summary.append(getProfileSummary(userId));
        return summary.toString();
    }

    private String getProfileSummary(String userId) {
        List<Diary> diaries = diaryRepository.findTop3ByUserIdOrderByCreateTimeDesc(userId);
        if (CollUtil.isEmpty(diaries)) {
            return "该用户比较神秘，还没有留下太多日记。";
        }
        return diaries.stream()
                .map(d -> "日期: " + d.getEntryDate() + "\n内容: " + d.getContent())
                .collect(Collectors.joining("\n\n"));
    }

    private CompletableFuture<String> generateLetter(String userId, String myProfile, String partnerProfile) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return matchAssistant.generateRecommendationLetter(myProfile, partnerProfile);
            } catch (Exception e) {
                log.error("Failed to generate recommendation letter for user {}", userId, e);
                throw new RuntimeException("Failed to generate recommendation letter", e);
            }
        });
    }

    @Override
    public List<SoulMatch> getMatches(String userId) {
        List<SoulMatch> matchesA = soulMatchRepository.findByUserAId(userId);
        List<SoulMatch> matchesB = soulMatchRepository.findByUserBId(userId);
        List<SoulMatch> all = new ArrayList<>();
        all.addAll(matchesA);
        all.addAll(matchesB);
        return all;
    }

    @Override
    public SoulMatch handleMatchAction(String userId, Long matchId, Integer action) {
        SoulMatch match = soulMatchRepository.findById(matchId).orElse(null);
        if (match == null)
            return null;

        if (userId.equals(match.getUserAId())) {
            match.setStatusA(action);
        } else if (userId.equals(match.getUserBId())) {
            match.setStatusB(action);
        } else {
            return null; // Not involved
        }

        // Check for match
        if (Integer.valueOf(1).equals(match.getStatusA()) && Integer.valueOf(1).equals(match.getStatusB())) {
            match.setIsMatched(true);
        }

        match.setUpdateTime(LocalDateTime.now());
        return soulMatchRepository.save(match);
    }

    @Override
    public MatchStatusResponse getMatchStatus(String userId) {
        User user = userService.getUserByUserId(userId);
        long diaryCount = diaryRepository.countByUserId(userId);
        List<SoulMatch> matches = getMatches(userId);

        // Calculate pending matches (user hasn't acted yet)
        long pendingMatches = matches.stream()
                .filter(m -> {
                    if (userId.equals(m.getUserAId())) {
                        return m.getStatusA() == 0;
                    } else {
                        return m.getStatusB() == 0;
                    }
                })
                .count();

        // Calculate completed matches (both interested)
        long completedMatches = matches.stream()
                .filter(SoulMatch::getIsMatched)
                .count();

        // Check if user can enable matching
        boolean canEnable = diaryCount >= 3;
        String enableHint = canEnable ? null : "需要至少3篇日记才能开启灵魂匹配，让AI更了解你";

        return MatchStatusResponse.builder()
                .enabled(user.getIsMatchEnabled())
                .intent(user.getMatchIntent())
                .diaryCount(diaryCount)
                .pendingMatches(pendingMatches)
                .completedMatches(completedMatches)
                .nextMatchTime("每日凌晨 2:00")
                .canEnable(canEnable)
                .enableHint(enableHint)
                .build();
    }
}
