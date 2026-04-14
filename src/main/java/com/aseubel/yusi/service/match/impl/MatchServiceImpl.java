package com.aseubel.yusi.service.match.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.common.utils.ModelUtils;
import com.aseubel.yusi.pojo.dto.match.MatchRecommendationResponse;
import com.aseubel.yusi.pojo.dto.match.MatchRerankResult;
import com.aseubel.yusi.pojo.dto.match.MatchStatusResponse;
import com.aseubel.yusi.pojo.entity.MatchProfile;
import com.aseubel.yusi.pojo.entity.SoulMatch;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.redis.annotation.QueryCache;
import com.aseubel.yusi.redis.annotation.UpdateCache;
import com.aseubel.yusi.repository.DiaryRepository;
import com.aseubel.yusi.repository.SoulMatchRepository;
import com.aseubel.yusi.service.ai.model.ModelRouteContext;
import com.aseubel.yusi.service.ai.model.ModelRouteContextHolder;
import com.aseubel.yusi.service.match.MatchAssistant;
import com.aseubel.yusi.service.match.MatchProfileAssembler;
import com.aseubel.yusi.service.match.MatchService;
import com.aseubel.yusi.service.user.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.AnnSearchReq;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.request.data.EmbeddedText;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.ranker.RRFRanker;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * @author Aseubel
 * @date 2025/12/21
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchServiceImpl implements MatchService {

    private static final String MATCH_PROFILE_COLLECTION = "yusi_match_profile";
    private static final int RECALL_TOP_K = 8;
    private static final int MIN_RERANK_SCORE = 70;
    private static final int MIN_WEAK_SIGNAL_SCORE = 78;
    private static final int STRONG_SIGNAL_SECTION_COUNT = 3;
    private static final int MEDIUM_SIGNAL_SECTION_COUNT = 2;
    private static final int RECENT_EXPOSURE_COOLDOWN_DAYS = 14;
    private static final int PENDING_RESPONSE_COOLDOWN_DAYS = 7;
    private static final int SKIP_COOLDOWN_DAYS = 30;
    private static final int MAX_HISTORY_PENALTY = 18;

    private final UserService userService;
    private final SoulMatchRepository soulMatchRepository;
    private final DiaryRepository diaryRepository;
    private final MatchAssistant matchAssistant;
    private final MatchProfileAssembler matchProfileAssembler;
    private final MilvusClientV2 milvusClientV2;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;

    @Override
    @Scheduled(cron = "0 0 2 * * ?") // Every day at 2 AM
    public void runDailyMatching() {
        log.info("Starting daily matching process...");
        ModelRouteContextHolder.set(ModelRouteContext.builder()
                .language(ModelUtils.normalizeLanguage("zh"))
                .scene(PromptKey.SOUL_MATCH.getKey())
                .build());
        try {
            List<User> candidates = userService.getMatchEnabledUsers();
            if (CollUtil.isEmpty(candidates) || candidates.size() < 2) {
                log.info("Not enough candidates for matching.");
                return;
            }

            Collections.shuffle(candidates);
            Map<String, User> candidateMap = new HashMap<>();
            candidates.forEach(user -> candidateMap.put(user.getUserId(), user));
            Map<String, MatchProfile> profileCache = new HashMap<>();
            Map<String, Long> diaryCountCache = new HashMap<>();
            Map<String, List<SoulMatch>> pairHistoryCache = new HashMap<>();

            LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
            Set<String> processedUserIds = new HashSet<>();

            for (User userA : candidates) {
                if (processedUserIds.contains(userA.getUserId())) {
                    continue;
                }
                if (soulMatchRepository.countMatchesSince(userA.getUserId(), startOfDay) >= 1) {
                    processedUserIds.add(userA.getUserId());
                    continue;
                }

                MatchProfile profileA = getOrLoadProfile(profileCache, userA.getUserId());
                if (profileA == null || StrUtil.isBlank(profileA.getProfileText())) {
                    continue;
                }

                User partner = null;
                MatchRerankResult bestResult = null;
                int bestScore = -1;

                for (String candidateUserId : recallCandidateIds(userA, profileA, candidates, profileCache,
                        diaryCountCache)) {
                    User potential = candidateMap.get(candidateUserId);
                    if (potential == null) {
                        continue;
                    }
                    if (processedUserIds.contains(candidateUserId)) {
                        continue;
                    }
                    PairHistoryFeedback pairHistoryFeedback = evaluatePairHistory(userA.getUserId(), candidateUserId,
                            pairHistoryCache);
                    if (pairHistoryFeedback.exclude()) {
                        continue;
                    }
                    if (!isIntentCompatible(userA, potential)) {
                        continue;
                    }
                    if (soulMatchRepository.countMatchesSince(candidateUserId, startOfDay) >= 1) {
                        continue;
                    }

                    MatchProfile profileB = getOrLoadProfile(profileCache, candidateUserId);
                    if (profileB == null || StrUtil.isBlank(profileB.getProfileText())) {
                        continue;
                    }

                    MatchRerankResult rerankResult = rerank(profileA, profileB);
                    if (rerankResult == null || !Boolean.TRUE.equals(rerankResult.getResonance())) {
                        continue;
                    }

                    int score = rerankResult.getScore() != null ? rerankResult.getScore() : 0;
                    score = applyHistoryPenalty(score, pairHistoryFeedback);
                    if (!passesScoreThreshold(profileA, profileB, score)) {
                        continue;
                    }
                    if (score > bestScore) {
                        bestScore = score;
                        rerankResult.setScore(score);
                        bestResult = rerankResult;
                        partner = potential;
                    }
                }

                if (partner != null && bestResult != null) {
                    processedUserIds.add(userA.getUserId());
                    processedUserIds.add(partner.getUserId());
                    createMatch(userA, partner, profileA,
                            getOrLoadProfile(profileCache, partner.getUserId()), bestResult);
                }
            }
        } finally {
            ModelRouteContextHolder.clear();
        }
        log.info("Daily matching process completed.");
    }

    private void createMatch(User userA, User userB, MatchProfile profileA, MatchProfile profileB,
            MatchRerankResult rerankResult) {
        log.info("Creating match for {} and {}", userA.getUserName(), userB.getUserName());
        CompletableFuture<String> futureA = generateLetter(userA.getUserId(),
                buildStructuredProfileForMatching(profileA),
                buildStructuredProfileForMatching(profileB),
                rerankResult);
        CompletableFuture<String> futureB = generateLetter(userB.getUserId(),
                buildStructuredProfileForMatching(profileB),
                buildStructuredProfileForMatching(profileA),
                rerankResult);

        try {
            CompletableFuture.allOf(futureA, futureB).join();

            SoulMatch match = SoulMatch.builder()
                    .userAId(userA.getUserId())
                    .userBId(userB.getUserId())
                    .letterAtoB(futureA.get())
                    .letterBtoA(futureB.get())
                    .reason(rerankResult.getReason())
                    .timingReason(rerankResult.getTimingReason())
                    .iceBreaker(rerankResult.getIceBreaker())
                    .score(rerankResult.getScore())
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

    private CompletableFuture<String> generateLetter(String userId, String myProfile, String partnerProfile,
            MatchRerankResult rerankResult) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return matchAssistant.generateRecommendationLetterFromMatchDecision(
                        myProfile,
                        partnerProfile,
                        rerankResult != null ? rerankResult.getReason() : "",
                        rerankResult != null ? rerankResult.getTimingReason() : "",
                        rerankResult != null ? rerankResult.getIceBreaker() : "");
            } catch (Exception e) {
                log.error("Failed to generate recommendation letter for user {}", userId, e);
                return buildFallbackLetter(rerankResult);
            }
        });
    }

    private String buildFallbackLetter(MatchRerankResult rerankResult) {
        if (rerankResult != null && StrUtil.isNotBlank(rerankResult.getIceBreaker())) {
            return "向你推荐一位'灵魂伙伴'。\n" + rerankResult.getIceBreaker();
        }
        return "向你推荐一位'灵魂伙伴'。你们之间存在某种值得慢慢靠近的共鸣。";
    }

    private List<String> recallCandidateIds(User currentUser, MatchProfile profile, List<User> allCandidates,
            Map<String, MatchProfile> profileCache, Map<String, Long> diaryCountCache) {
        List<String> recalled = new ArrayList<>();
        if (profile != null && StrUtil.isNotBlank(profile.getProfileText())) {
            recalled.addAll(recallByMilvus(currentUser.getUserId(), profile.getProfileText()));
        }
        Map<String, Integer> recallRankMap = new HashMap<>();
        for (int i = 0; i < recalled.size(); i++) {
            recallRankMap.putIfAbsent(recalled.get(i), i);
        }

        List<String> prioritized = new ArrayList<>();
        List<CandidatePriority> recalledCandidates = recalled.stream()
                .distinct()
                .filter(candidateUserId -> !currentUser.getUserId().equals(candidateUserId))
                .map(candidateUserId -> findCandidate(allCandidates, candidateUserId))
                .filter(candidate -> candidate != null && isIntentCompatible(currentUser, candidate))
                .map(candidate -> buildCandidatePriority(currentUser, candidate,
                        recallRankMap.getOrDefault(candidate.getUserId(), RECALL_TOP_K),
                        profileCache, diaryCountCache))
                .sorted(candidatePriorityComparator())
                .toList();
        recalledCandidates.stream()
                .map(priority -> priority.user().getUserId())
                .forEach(prioritized::add);

        if (prioritized.size() < RECALL_TOP_K) {
            Set<String> excludedUserIds = new LinkedHashSet<>(prioritized);
            excludedUserIds.add(currentUser.getUserId());
            for (User fallback : buildFallbackCandidates(currentUser, allCandidates, excludedUserIds, profileCache,
                    diaryCountCache)) {
                prioritized.add(fallback.getUserId());
                if (prioritized.size() >= RECALL_TOP_K) {
                    break;
                }
            }
        }
        return prioritized;
    }

    private List<String> recallByMilvus(String userId, String profileText) {
        if (StrUtil.isBlank(profileText)) {
            return List.of();
        }
        try {
            String expr = String.format("metadata[\"userId\"] != '%s'", userId);
            Embedding queryEmbedding = embeddingModel.embed(profileText).content();

            AnnSearchReq denseReq = AnnSearchReq.builder()
                    .vectorFieldName("vector")
                    .vectors(List.of(new FloatVec(queryEmbedding.vector())))
                    .params("{\"metric_type\": \"COSINE\"}")
                    .limit(RECALL_TOP_K * 2)
                    .filter(expr)
                    .build();

            AnnSearchReq sparseReq = AnnSearchReq.builder()
                    .vectorFieldName("text_sparse")
                    .vectors(List.of(new EmbeddedText(profileText)))
                    .params("{\"metric_type\": \"BM25\"}")
                    .limit(RECALL_TOP_K * 2)
                    .filter(expr)
                    .build();

            HybridSearchReq hybridSearchReq = HybridSearchReq.builder()
                    .collectionName(MATCH_PROFILE_COLLECTION)
                    .searchRequests(Arrays.asList(denseReq, sparseReq))
                    .ranker(RRFRanker.builder().k(60).build())
                    .limit(RECALL_TOP_K)
                    .outFields(List.of("metadata"))
                    .build();

            SearchResp searchResp = milvusClientV2.hybridSearch(hybridSearchReq);
            List<List<SearchResp.SearchResult>> searchResults = searchResp.getSearchResults();
            if (searchResults == null || searchResults.isEmpty() || searchResults.get(0).isEmpty()) {
                return List.of();
            }

            List<String> recalled = new ArrayList<>();
            for (SearchResp.SearchResult result : searchResults.get(0)) {
                String candidateUserId = extractUserId(result);
                if (StrUtil.isNotBlank(candidateUserId) && !userId.equals(candidateUserId)) {
                    recalled.add(candidateUserId);
                }
            }
            return recalled;
        } catch (Exception e) {
            log.warn("Milvus 召回匹配候选失败, userId={}", userId, e);
            return List.of();
        }
    }

    private List<User> buildFallbackCandidates(User currentUser, List<User> allCandidates, Set<String> excludedUserIds,
            Map<String, MatchProfile> profileCache, Map<String, Long> diaryCountCache) {
        return allCandidates.stream()
                .filter(candidate -> !candidate.getUserId().equals(currentUser.getUserId()))
                .filter(candidate -> !excludedUserIds.contains(candidate.getUserId()))
                .filter(candidate -> isIntentCompatible(currentUser, candidate))
                .map(candidate -> buildCandidatePriority(currentUser, candidate, RECALL_TOP_K + 1,
                        profileCache, diaryCountCache))
                .sorted(candidatePriorityComparator())
                .map(CandidatePriority::user)
                .limit(RECALL_TOP_K)
                .toList();
    }

    private User findCandidate(List<User> allCandidates, String userId) {
        for (User candidate : allCandidates) {
            if (candidate != null && StrUtil.equals(candidate.getUserId(), userId)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isIntentCompatible(User userA, User userB) {
        String intentA = normalizeIntent(userA != null ? userA.getMatchIntent() : null);
        String intentB = normalizeIntent(userB != null ? userB.getMatchIntent() : null);
        if (StrUtil.isBlank(intentA) || StrUtil.isBlank(intentB)) {
            return true;
        }
        return intentA.equals(intentB);
    }

    private String normalizeIntent(String intent) {
        return StrUtil.blankToDefault(intent, "").trim().toLowerCase();
    }

    private boolean passesScoreThreshold(MatchProfile profileA, MatchProfile profileB, int score) {
        boolean weakSignal = isWeakSignal(profileA) || isWeakSignal(profileB);
        return score >= (weakSignal ? MIN_WEAK_SIGNAL_SCORE : MIN_RERANK_SCORE);
    }

    private boolean isWeakSignal(MatchProfile profile) {
        return profileSignalSectionCount(profile) < MEDIUM_SIGNAL_SECTION_COUNT;
    }

    private int profileSignalSectionCount(MatchProfile profile) {
        if (profile == null) {
            return 0;
        }
        int count = 0;
        if (hasMeaningfulText(profile.getLifeGraphSummary(), "长期结构信息较少。")) {
            count++;
        }
        if (hasMeaningfulText(profile.getPersonaSummary(), "稳定偏好信息较少。")) {
            count++;
        }
        if (hasMeaningfulText(profile.getMidMemorySummary(), "近期状态信息较少。")) {
            count++;
        }
        return count;
    }

    private boolean hasMeaningfulText(String text, String emptyMarker) {
        return StrUtil.isNotBlank(text) && !emptyMarker.equals(text.trim());
    }

    private MatchProfile getOrLoadProfile(Map<String, MatchProfile> profileCache, String userId) {
        if (StrUtil.isBlank(userId)) {
            return null;
        }
        return profileCache.computeIfAbsent(userId, matchProfileAssembler::ensureProfile);
    }

    private long getOrLoadDiaryCount(Map<String, Long> diaryCountCache, String userId) {
        if (StrUtil.isBlank(userId)) {
            return 0L;
        }
        return diaryCountCache.computeIfAbsent(userId, diaryRepository::countByUserId);
    }

    private CandidatePriority buildCandidatePriority(User currentUser, User candidate, int recallRank,
            Map<String, MatchProfile> profileCache, Map<String, Long> diaryCountCache) {
        MatchProfile candidateProfile = getOrLoadProfile(profileCache, candidate.getUserId());
        int signalSections = profileSignalSectionCount(candidateProfile);
        long diaryCount = getOrLoadDiaryCount(diaryCountCache, candidate.getUserId());
        boolean explicitSharedIntent = hasExplicitSharedIntent(currentUser, candidate);
        return new CandidatePriority(candidate, signalSections, diaryCount, explicitSharedIntent, recallRank);
    }

    private Comparator<CandidatePriority> candidatePriorityComparator() {
        return Comparator.comparingInt((CandidatePriority priority) -> candidateSignalTier(priority.signalSections()))
                .thenComparing(CandidatePriority::explicitSharedIntent, Comparator.reverseOrder())
                .thenComparing(Comparator.comparingInt(CandidatePriority::signalSections).reversed())
                .thenComparing(Comparator.comparingLong(CandidatePriority::diaryCount).reversed())
                .thenComparingInt(CandidatePriority::recallRank)
                .thenComparing(priority -> priority.user().getUserId(), Comparator.nullsLast(String::compareTo));
    }

    private int candidateSignalTier(int signalSections) {
        if (signalSections >= STRONG_SIGNAL_SECTION_COUNT) {
            return 0;
        }
        if (signalSections >= MEDIUM_SIGNAL_SECTION_COUNT) {
            return 1;
        }
        return 2;
    }

    private boolean hasExplicitSharedIntent(User userA, User userB) {
        String intentA = normalizeIntent(userA != null ? userA.getMatchIntent() : null);
        String intentB = normalizeIntent(userB != null ? userB.getMatchIntent() : null);
        return StrUtil.isNotBlank(intentA) && intentA.equals(intentB);
    }

    private PairHistoryFeedback evaluatePairHistory(String userAId, String userBId,
            Map<String, List<SoulMatch>> pairHistoryCache) {
        List<SoulMatch> history = getPairHistory(userAId, userBId, pairHistoryCache);
        if (CollUtil.isEmpty(history)) {
            return PairHistoryFeedback.allow(0);
        }

        SoulMatch latest = history.get(0);
        LocalDateTime now = LocalDateTime.now();
        if (history.stream().anyMatch(match -> Boolean.TRUE.equals(match.getIsMatched()))) {
            return PairHistoryFeedback.exclude("already_matched", MAX_HISTORY_PENALTY);
        }
        if (latest.getCreateTime() != null
                && latest.getCreateTime().isAfter(now.minusDays(RECENT_EXPOSURE_COOLDOWN_DAYS))) {
            return PairHistoryFeedback.exclude("recently_exposed", MAX_HISTORY_PENALTY);
        }
        if (isPendingPair(latest) && latest.getCreateTime() != null
                && latest.getCreateTime().isAfter(now.minusDays(PENDING_RESPONSE_COOLDOWN_DAYS))) {
            return PairHistoryFeedback.exclude("pending_response", MAX_HISTORY_PENALTY);
        }

        long skipCount = history.stream().filter(this::hasAnySkip).count();
        if (skipCount > 0) {
            LocalDateTime lastSkipTime = history.stream()
                    .filter(this::hasAnySkip)
                    .map(SoulMatch::getUpdateTime)
                    .filter(time -> time != null)
                    .max(LocalDateTime::compareTo)
                    .orElseGet(() -> history.stream()
                            .filter(this::hasAnySkip)
                            .map(SoulMatch::getCreateTime)
                            .filter(time -> time != null)
                            .max(LocalDateTime::compareTo)
                            .orElse(null));
            if (lastSkipTime != null && lastSkipTime.isAfter(now.minusDays(SKIP_COOLDOWN_DAYS))) {
                return PairHistoryFeedback.exclude("recent_skip", MAX_HISTORY_PENALTY);
            }
        }

        int penalty = calculateHistoryPenalty(history);
        return PairHistoryFeedback.allow(penalty);
    }

    private List<SoulMatch> getPairHistory(String userAId, String userBId,
            Map<String, List<SoulMatch>> pairHistoryCache) {
        String cacheKey = buildPairCacheKey(userAId, userBId);
        return pairHistoryCache.computeIfAbsent(cacheKey, key -> soulMatchRepository.findPairHistory(userAId, userBId));
    }

    private String buildPairCacheKey(String userAId, String userBId) {
        if (StrUtil.compare(userAId, userBId, false) <= 0) {
            return userAId + "::" + userBId;
        }
        return userBId + "::" + userAId;
    }

    private boolean isPendingPair(SoulMatch match) {
        if (match == null) {
            return false;
        }
        Integer statusA = match.getStatusA();
        Integer statusB = match.getStatusB();
        return Integer.valueOf(0).equals(statusA)
                || Integer.valueOf(0).equals(statusB)
                || (Integer.valueOf(1).equals(statusA) && Integer.valueOf(0).equals(statusB))
                || (Integer.valueOf(0).equals(statusA) && Integer.valueOf(1).equals(statusB));
    }

    private boolean hasAnySkip(SoulMatch match) {
        return match != null
                && (Integer.valueOf(2).equals(match.getStatusA()) || Integer.valueOf(2).equals(match.getStatusB()));
    }

    private int calculateHistoryPenalty(List<SoulMatch> history) {
        if (CollUtil.isEmpty(history)) {
            return 0;
        }
        int penalty = 0;
        long skipCount = history.stream().filter(this::hasAnySkip).count();
        penalty += (int) Math.min(skipCount * 6, 12);

        int exposureCount = history.size();
        if (exposureCount > 1) {
            penalty += Math.min((exposureCount - 1) * 2, 6);
        }
        return Math.min(penalty, MAX_HISTORY_PENALTY);
    }

    private int applyHistoryPenalty(int score, PairHistoryFeedback feedback) {
        if (feedback == null || feedback.penaltyScore() <= 0) {
            return score;
        }
        return Math.max(0, score - feedback.penaltyScore());
    }

    private record CandidatePriority(User user, int signalSections, long diaryCount,
            boolean explicitSharedIntent, int recallRank) {
    }

    private record PairHistoryFeedback(boolean exclude, String reason, int penaltyScore) {
        private static PairHistoryFeedback allow(int penaltyScore) {
            return new PairHistoryFeedback(false, "allow", penaltyScore);
        }

        private static PairHistoryFeedback exclude(String reason, int penaltyScore) {
            return new PairHistoryFeedback(true, reason, penaltyScore);
        }
    }

    private String extractUserId(SearchResp.SearchResult result) {
        if (result == null || result.getEntity() == null) {
            return null;
        }
        Object metadataObj = result.getEntity().get("metadata");
        if (metadataObj instanceof Map<?, ?> metadataMap) {
            Object userId = metadataMap.get("userId");
            return userId != null ? userId.toString() : null;
        }
        if (metadataObj != null) {
            String raw = metadataObj.toString();
            int idx = raw.indexOf("userId");
            if (idx >= 0) {
                int colon = raw.indexOf(':', idx);
                if (colon >= 0) {
                    String value = raw.substring(colon + 1).replaceAll("[\"'{} ]", "");
                    int comma = value.indexOf(',');
                    return comma >= 0 ? value.substring(0, comma) : value;
                }
            }
        }
        return null;
    }

    private MatchRerankResult rerank(MatchProfile targetProfile, MatchProfile candidateProfile) {
        try {
            String raw = matchAssistant.rerankMatch(
                    buildStructuredProfileForMatching(targetProfile),
                    buildStructuredProfileForMatching(candidateProfile));
            return objectMapper.readValue(extractJsonObject(raw), MatchRerankResult.class);
        } catch (Exception e) {
            log.warn("匹配精排失败: targetUserId={}, candidateUserId={}",
                    targetProfile.getUserId(), candidateProfile.getUserId(), e);
            return null;
        }
    }

    private String buildStructuredProfileForMatching(MatchProfile profile) {
        if (profile == null) {
            return "匹配画像缺失。";
        }
        return """
                长期结构：
                %s

                稳定偏好：
                %s

                当前阶段：
                %s
                """.formatted(
                StrUtil.blankToDefault(profile.getLifeGraphSummary(), "长期结构信息较少。"),
                StrUtil.blankToDefault(profile.getPersonaSummary(), "稳定偏好信息较少。"),
                StrUtil.blankToDefault(profile.getMidMemorySummary(), "近期状态信息较少。")).trim();
    }

    private String extractJsonObject(String raw) {
        if (raw == null) {
            return "{}";
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return "{}";
        }
        return raw.substring(start, end + 1);
    }

    @Override
    @QueryCache(key = "'match:list:' + #userId", ttl = 60)
    public List<MatchRecommendationResponse> getMatches(String userId) {
        List<SoulMatch> matchesA = soulMatchRepository.findByUserAId(userId);
        List<SoulMatch> matchesB = soulMatchRepository.findByUserBId(userId);
        List<SoulMatch> all = new ArrayList<>();
        all.addAll(matchesA);
        all.addAll(matchesB);
        return all.stream()
                .map(match -> toRecommendationResponse(userId, match))
                .toList();
    }

    @Override
    @UpdateCache(key = "'match:list:' + #userId + ':*'", evictOnly = true)
    @UpdateCache(key = "'match:status:' + #userId", evictOnly = true)
    public MatchRecommendationResponse handleMatchAction(String userId, Long matchId, Integer action) {
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
        SoulMatch saved = soulMatchRepository.save(match);
        return toRecommendationResponse(userId, saved);
    }

    @Override
    public MatchStatusResponse getMatchStatus(String userId) {
        User user = userService.getUserByUserId(userId);
        long diaryCount = diaryRepository.countByUserId(userId);
        List<MatchRecommendationResponse> matches = getMatches(userId);

        // Calculate pending matches (user hasn't acted yet)
        long pendingMatches = matches.stream()
                .filter(m -> Integer.valueOf(0).equals(m.getMyStatus()))
                .count();

        // Calculate completed matches (both interested)
        long completedMatches = matches.stream()
                .filter(MatchRecommendationResponse::getMatched)
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

    private MatchRecommendationResponse toRecommendationResponse(String currentUserId, SoulMatch match) {
        boolean isUserA = currentUserId.equals(match.getUserAId());
        String counterpartUserId = isUserA ? match.getUserBId() : match.getUserAId();
        User counterpart = userService.getUserByUserId(counterpartUserId);
        return MatchRecommendationResponse.builder()
                .matchId(match.getId())
                .counterpartUserId(counterpartUserId)
                .counterpartUserName(counterpart != null ? counterpart.getUserName() : null)
                .recommendationLetter(isUserA ? match.getLetterAtoB() : match.getLetterBtoA())
                .counterpartLetter(isUserA ? match.getLetterBtoA() : match.getLetterAtoB())
                .reason(match.getReason())
                .timingReason(match.getTimingReason())
                .iceBreaker(match.getIceBreaker())
                .score(match.getScore())
                .myStatus(isUserA ? match.getStatusA() : match.getStatusB())
                .counterpartStatus(isUserA ? match.getStatusB() : match.getStatusA())
                .matched(match.getIsMatched())
                .createTime(match.getCreateTime())
                .updateTime(match.getUpdateTime())
                .build();
    }
}
