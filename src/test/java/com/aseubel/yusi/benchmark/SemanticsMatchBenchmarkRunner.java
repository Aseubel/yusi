package com.aseubel.yusi.benchmark;

import com.aseubel.yusi.pojo.dto.match.MatchRerankResult;
import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import com.aseubel.yusi.pojo.entity.MatchProfile;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.pojo.entity.UserPersona;
import com.aseubel.yusi.repository.MidTermMemoryRepository;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.repository.UserPersonaRepository;
import com.aseubel.yusi.service.match.MatchProfileAssembler;
import com.aseubel.yusi.service.match.impl.MatchServiceImpl;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Layer A-(c) 匹配排序基准：虚构人物群组 → 真实 MatchProfile 装配 → 真实 Dense+Sparse+RRF 召回 →
 * 真实 soul-match 精排 → 与 gold 分级序比 recall@5 / MRR / top-1 命中率 / nDCG@5；
 * gold 最佳搭档对生成真实推荐信并走 LLM-as-judge 三维分。
 * 失败（超时/模型错误/judge 解析失败）经 FailureRecorder 入卡；推荐结果为「无共鸣」属模型正常输出，
 * 记入 rejectedCount 而非异常。
 */
@Tag("benchmark")
@Tag("benchmark-match")
@SpringBootTest
@ActiveProfiles({"dev", "benchmark"})
class SemanticsMatchBenchmarkRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    /** 金标准分级权重：tier0 → gain 2，tier1 → gain 1，tier2 干扰项 → 0。 */
    private static final int NDCG_K = 5;

    /** judge 固定三维 rubric：仅评推荐信文本本身（prompt 版本入版本指纹）。 */
    static final Map<String, String> JUDGE_DIMENSIONS = Map.of(
            "个性化", "推荐信是否体现双方画像里的具体特征，而不是可套用于任意两人的通用模板",
            "具体性", "是否引用了双方真实的兴趣、经历细节，而不是空泛形容词堆砌",
            "分寸感", "语气是否尊重边界、克制自然，无过度承诺、无轻浮或催促式表达");

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MatchFixture(String fixtureVersion, List<Group> groups) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Group(String groupId, String intent, Person target, List<Person> candidates) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Person(String tag, Integer tier, String name, String interests, String tone,
            String customInstructions, List<String> memories, List<EntitySeed> entities) {

        boolean isGoldTop() {
            return tier != null && tier == 0;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EntitySeed(String type, String name, String summary, Double importance) {
    }

    /** 单候选精排明细：modelError 表示调用链路异常，resonance=false 表示模型判定无共鸣（score=-1）。 */
    record RerankEntry(String tag, Integer tier, Integer rerankScore, boolean resonance,
            boolean modelError) {
    }

    /** 推荐信 judge 结果；分值已归一到 0..1。 */
    record LetterBlock(double personalization, double specificity, double tactfulness,
            boolean available, String error) {

        static LetterBlock unavailable(String error) {
            return new LetterBlock(0d, 0d, 0d, false, error);
        }
    }

    record GroupOutcome(String groupId, boolean failed,
            double goldRecallAt5, double goldTopMrr,
            double rerankTop1Hit, double rerankNdcgAt5,
            int rejectedCount, List<RerankEntry> rankedEntries, LetterBlock letter) {
    }

    @Test
    void runMatchBenchmark() throws Exception {
        BenchmarkFailureRecorder recorder = new BenchmarkFailureRecorder();
        MatchFixture fixture = loadFixture();

        MatchProfileAssembler assembler = applicationContext.getBean(MatchProfileAssembler.class);
        MatchServiceImpl matchService = applicationContext.getBean(MatchServiceImpl.class);
        UserRepository userRepository = applicationContext.getBean(UserRepository.class);
        UserPersonaRepository personaRepository = applicationContext.getBean(UserPersonaRepository.class);
        MidTermMemoryRepository memoryRepository = applicationContext.getBean(MidTermMemoryRepository.class);
        var vectorService = applicationContext.getBean(
                com.aseubel.yusi.service.memory.MidTermMemoryVectorService.class);

        // 残留集合兜底：业务 MilvusConfig 只建不 load，recallByMilvus 前必须确保三个隔离集合就绪
        BenchmarkMilvusSupport.ensureBusinessCollectionsLoaded(
                applicationContext.getBean(io.milvus.v2.client.MilvusClientV2.class),
                applicationContext.getBean(com.aseubel.yusi.config.ai.properties.MilvusCollectionProperties.class));

        List<GroupOutcome> outcomes = new ArrayList<>();
        try {
            for (Group group : fixture.groups()) {
                Map<String, String> userIdByTag = new LinkedHashMap<>();
                Map<String, Person> personByTag = new HashMap<>();
                seedPersons(group, userRepository, personaRepository, memoryRepository,
                        vectorService, userIdByTag, personByTag);

                outcomes.add(runGroup(recorder, group, assembler, matchService,
                        userIdByTag, personByTag));
            }
        } finally {
            writePartFile(fixture.fixtureVersion(), outcomes, recorder);
        }

        // gate 模式：显式开启时 top-1 命中率过低判定失败（默认 record-only 只出分）
        if (BenchmarkEnv.gateEnabled() && !outcomes.isEmpty()) {
            double top1HitRate = IrMetrics.average(outcomes.stream()
                    .map(GroupOutcome::rerankTop1Hit).toList());
            org.junit.jupiter.api.Assertions.assertTrue(top1HitRate >= 0.34d,
                    () -> "benchmark-match gate: top1HitRate=" + top1HitRate + " < 0.34");
        }
    }

    private MatchFixture loadFixture() throws Exception {
        try (var stream = new ClassPathResource("benchmark/semantics/match-cases.json").getInputStream()) {
            return MAPPER.readValue(stream, MatchFixture.class);
        }
    }

    /** 种子目标 + 全部候选人（记忆 id 由数据库自增分配）。 */
    private void seedPersons(Group group, UserRepository userRepository,
            UserPersonaRepository personaRepository, MidTermMemoryRepository memoryRepository,
            com.aseubel.yusi.service.memory.MidTermMemoryVectorService vectorService,
            Map<String, String> userIdByTag, Map<String, Person> personByTag) {
        List<Person> everyone = new ArrayList<>();
        everyone.add(group.target());
        everyone.addAll(group.candidates());
        for (Person person : everyone) {
            String userId = BenchmarkSeedSupport.createBenchUser(userRepository,
                    "mt-" + person.tag(), person.name());
            User user = userRepository.findByUserId(userId);
            if (user != null && group.intent() != null) {
                user.setMatchIntent(group.intent());
                userRepository.save(user);
            }
            UserPersona persona = UserPersona.builder()
                    .userId(userId)
                    .interests(person.interests())
                    .tone(person.tone())
                    .customInstructions(person.customInstructions())
                    .matchAllowed(Boolean.TRUE)
                    .hidden(Boolean.FALSE)
                    .confidence(0.9d)
                    .build();
            persona.setUpdatedAt(LocalDateTime.now());
            personaRepository.save(persona);

            for (EntitySeed seed : person.entities()) {
                LifeGraphEntity entity = LifeGraphEntity.builder()
                        .userId(userId)
                        .type(LifeGraphEntity.EntityType.valueOf(seed.type()))
                        .nameNorm(seed.name().toLowerCase(Locale.ROOT))
                        .displayName(seed.name())
                        .summary(seed.summary())
                        .mentionCount(3)
                        .relationCount(1)
                        .firstMentionDate(LocalDate.now().minusDays(30))
                        .lastMentionAt(LocalDateTime.now().minusDays(2))
                        .confidence(0.9d)
                        .importance(seed.importance() == null ? 0.7d : seed.importance())
                        .matchAllowed(Boolean.TRUE)
                        .hidden(Boolean.FALSE)
                        .build();
                entity.setCreatedAt(LocalDateTime.now());
                entity.setUpdatedAt(LocalDateTime.now());
                applicationContext.getBean(
                        com.aseubel.yusi.repository.LifeGraphEntityRepository.class).save(entity);
            }

            if (person.memories() != null && !person.memories().isEmpty()) {
                BenchmarkSeedSupport.seedMidTermMemories(memoryRepository, vectorService, userId,
                        person.memories().stream().map(BenchmarkSeedSupport.SeedMemory::visible).toList());
            }

            userIdByTag.put(person.tag(), userId);
            personByTag.put(person.tag(), person);
        }
    }

    private GroupOutcome runGroup(BenchmarkFailureRecorder recorder, Group group,
            MatchProfileAssembler assembler, MatchServiceImpl matchService,
            Map<String, String> userIdByTag, Map<String, Person> personByTag) {
        String groupStep = "match:" + group.groupId();
        String targetTag = group.target().tag();
        String targetUserId = userIdByTag.get(targetTag);

        // 1. 真实画像装配（写库 + 同步 benchmark Milvus 集合）
        Map<String, MatchProfile> profileByTag = new LinkedHashMap<>();
        MatchProfile targetProfile = refreshProfileSafely(recorder, groupStep + ":profile",
                assembler, targetTag, targetUserId);
        if (targetProfile == null) {
            return new GroupOutcome(group.groupId(), true, 0d, 0d, 0d, 0d, 0, List.of(),
                    LetterBlock.unavailable("TARGET_PROFILE_MISSING"));
        }
        profileByTag.put(targetTag, targetProfile);
        for (Person candidate : group.candidates()) {
            MatchProfile profile = refreshProfileSafely(recorder, groupStep + ":profile",
                    assembler, candidate.tag(), userIdByTag.get(candidate.tag()));
            if (profile == null) {
                return new GroupOutcome(group.groupId(), true, 0d, 0d, 0d, 0d, 0, List.of(),
                        LetterBlock.unavailable("CANDIDATE_PROFILE_MISSING"));
            }
            profileByTag.put(candidate.tag(), profile);
        }

        // 2. 真实召回（Dense+Sparse+RRF），统计金标准命中与位置
        List<String> recalledTags;
        try {
            List<String> recalledUserIds = recorder.withinTimeout(
                    BenchmarkEnv.stepTimeoutSeconds("retrieval-query"),
                    () -> matchService.recallByMilvus(targetUserId, targetProfile.getProfileText()));
            recalledTags = mapToTagsInGroup(recalledUserIds, userIdByTag, group);
        } catch (Exception e) {
            recorder.record(groupStep + ":recall",
                    e instanceof java.util.concurrent.TimeoutException
                            ? BenchmarkFailureRecorder.TYPE_TIMEOUT
                            : BenchmarkFailureRecorder.TYPE_RETRIEVAL_ERROR,
                    BenchmarkFailureRecorder.LowRiskMessages.describe(e));
            recalledTags = List.of();
        }
        List<String> relevantTierTags = group.candidates().stream()
                .filter(candidate -> candidate.tier() <= 1)
                .map(candidate -> userIdByTag.get(candidate.tag())).toList();
        double goldRecallAt5 = relevantTierTags.isEmpty() ? 0d
                : IrMetrics.recallAtK(relevantTierTags, recalledUserIds(recalledTags, userIdByTag), 5);
        double goldTopMrr = IrMetrics.reciprocalRank(relevantTierTags,
                recalledUserIds(recalledTags, userIdByTag), 10);

        // 3. 真实精排：按召回顺序 + 未召回补位，逐个打分
        List<RerankEntry> reranked = new ArrayList<>();
        Map<String, MatchRerankResult> resultByTag = new HashMap<>();
        int rejectCount = 0;
        List<String> orderedTags = new ArrayList<>(recalledTags);
        group.candidates().stream().map(Person::tag)
                .filter(tag -> !orderedTags.contains(tag))
                .forEach(orderedTags::add);
        int sequence = 0;
        for (String tag : orderedTags) {
            String step = groupStep + ":rerank-" + sequence++;
            Person candidate = personByTag.get(tag);
            MatchRerankResult result;
            try {
                result = recorder.withinTimeout(BenchmarkEnv.stepTimeoutSeconds("match-rerank"),
                        () -> matchService.rerank(targetProfile, profileByTag.get(tag)));
            } catch (java.util.concurrent.TimeoutException e) {
                recorder.record(step, BenchmarkFailureRecorder.TYPE_TIMEOUT, "match rerank timeout");
                reranked.add(new RerankEntry(tag, candidate.tier(), -1, false, true));
                continue;
            } catch (RuntimeException e) {
                recorder.record(step, BenchmarkFailureRecorder.TYPE_MODEL_ERROR,
                        BenchmarkFailureRecorder.LowRiskMessages.describe(e));
                reranked.add(new RerankEntry(tag, candidate.tier(), -1, false, true));
                continue;
            }
            if (result == null) {
                // rerank 内部吞掉的异常以 null 返回，同样不允许静默
                recorder.record(step, BenchmarkFailureRecorder.TYPE_MODEL_ERROR,
                        "rerank returned null");
                reranked.add(new RerankEntry(tag, candidate.tier(), -1, false, true));
                continue;
            }
            resultByTag.put(tag, result);
            if (!Boolean.TRUE.equals(result.getResonance())) {
                rejectCount++;
                reranked.add(new RerankEntry(tag, candidate.tier(), -1, false, false));
                continue;
            }
            reranked.add(new RerankEntry(tag, candidate.tier(),
                    result.getScore() == null ? 0 : result.getScore(), true, false));
        }

        List<RerankEntry> ranked = reranked.stream()
                .sorted(Comparator.comparingInt(RerankEntry::rerankScore).reversed())
                .toList();
        Person goldTop = group.candidates().stream()
                .filter(Person::isGoldTop).findFirst().orElse(null);
        double top1Hit = goldTop != null && !ranked.isEmpty()
                && ranked.get(0).tag().equals(goldTop.tag()) && ranked.get(0).rerankScore() >= 0 ? 1d : 0d;

        Map<String, Integer> gains = new LinkedHashMap<>();
        group.candidates().forEach(candidate ->
                gains.put(candidate.tag(), Math.max(0, 2 - candidate.tier())));
        List<String> rankedTagList = ranked.stream().map(RerankEntry::tag).toList();
        double ndcgAt5 = IrMetrics.ndcgAtK(gains, rankedTagList, Math.min(NDCG_K, rankedTagList.size()));

        // 4. gold 最佳搭档的真实推荐信 + judge 三维分
        LetterBlock letter = evaluateLetter(recorder, groupStep, matchService,
                personByTag.get(goldTop == null ? null : goldTop.tag()),
                userIdByTag.get(goldTop == null ? null : goldTop.tag()),
                matchService.buildStructuredProfileForMatching(targetProfile),
                goldTop == null ? null : matchService
                        .buildStructuredProfileForMatching(profileByTag.get(goldTop.tag())),
                resultByTag.get(goldTop == null ? null : goldTop.tag()), targetUserId);

        return new GroupOutcome(group.groupId(), false, goldRecallAt5, goldTopMrr,
                top1Hit, ndcgAt5, rejectCount, ranked, letter);
    }

    /** 以目标用户视角向 gold 搭档生成真实推荐信，judge 三维归一分。 */
    private LetterBlock evaluateLetter(BenchmarkFailureRecorder recorder, String groupStep,
            MatchServiceImpl matchService, Person goldTop, String goldTopUserId,
            String targetStructuredProfile, String partnerStructuredProfile,
            MatchRerankResult rerankResult, String targetUserId) {
        if (goldTop == null || goldTopUserId == null || rerankResult == null) {
            return LetterBlock.unavailable("NO_RERANK_RESULT_FOR_GOLD_TOP");
        }
        String letter;
        try {
            var future = matchService.generateLetter(targetUserId, targetStructuredProfile,
                    partnerStructuredProfile, rerankResult);
            letter = future.get(BenchmarkEnv.stepTimeoutSeconds("match-letter"), TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            recorder.record(groupStep + ":letter", BenchmarkFailureRecorder.TYPE_TIMEOUT,
                    "letter generation timeout");
            return LetterBlock.unavailable(BenchmarkFailureRecorder.TYPE_TIMEOUT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            recorder.record(groupStep + ":letter", BenchmarkFailureRecorder.TYPE_E2E_STEP_ERROR,
                    "interrupted while waiting letter");
            return LetterBlock.unavailable("INTERRUPTED");
        } catch (Exception e) {
            recorder.record(groupStep + ":letter", BenchmarkFailureRecorder.TYPE_MODEL_ERROR,
                    BenchmarkFailureRecorder.LowRiskMessages.describe(e));
            return LetterBlock.unavailable(BenchmarkFailureRecorder.TYPE_MODEL_ERROR);
        }
        if (letter == null || letter.isBlank()) {
            recorder.record(groupStep + ":letter", BenchmarkFailureRecorder.TYPE_MODEL_ERROR,
                    "empty letter content");
            return LetterBlock.unavailable(BenchmarkFailureRecorder.TYPE_MODEL_ERROR);
        }

        BenchmarkJudgeService judge = new BenchmarkJudgeService(
                applicationContext.getBean(dev.langchain4j.model.chat.ChatModel.class), recorder);
        String context = "收信人画像：\n" + targetStructuredProfile
                + "\n\n发信对象画像：\n" + partnerStructuredProfile;
        BenchmarkJudgeService.JudgeResult judgeResult =
                recorder.guard(groupStep + ":judge", BenchmarkFailureRecorder.TYPE_JUDGE_PARSE_ERROR,
                        () -> judge.judge(JUDGE_DIMENSIONS, context, letter),
                        () -> BenchmarkJudgeService.JudgeResult.unavailable("JUDGE_FAILED"));
        if (judgeResult == null || !judgeResult.available() || judgeResult.scores() == null
                || judgeResult.scores().size() < JUDGE_DIMENSIONS.size()) {
            return LetterBlock.unavailable(judgeResult == null || judgeResult.error() == null
                    ? "JUDGE_INCOMPLETE" : judgeResult.error());
        }
        return new LetterBlock(
                normalize(judgeResult.scores().get("个性化")),
                normalize(judgeResult.scores().get("具体性")),
                normalize(judgeResult.scores().get("分寸感")),
                true, null);
    }

    private static double normalize(Integer score) {
        return score == null ? 0d : IrMetrics.round(score / 3d);
    }

    private MatchProfile refreshProfileSafely(BenchmarkFailureRecorder recorder, String step,
            MatchProfileAssembler assembler, String tag, String userId) {
        try {
            return assembler.refreshProfile(userId);
        } catch (Exception e) {
            recorder.record(step, BenchmarkFailureRecorder.TYPE_MODEL_ERROR,
                    "refresh profile failed for " + tag + ": "
                            + BenchmarkFailureRecorder.LowRiskMessages.describe(e));
            return null;
        }
    }

    private static List<String> recalledUserIds(List<String> recalledTags, Map<String, String> userIdByTag) {
        return recalledTags.stream().map(userIdByTag::get).filter(java.util.Objects::nonNull).toList();
    }

    private static List<String> mapToTagsInGroup(List<String> recalledUserIds,
            Map<String, String> userIdByTag, Group group) {
        List<String> tags = new ArrayList<>();
        if (recalledUserIds == null) {
            return tags;
        }
        List<String> knownCandidateIds = group.candidates().stream()
                .map(person -> userIdByTag.get(person.tag())).toList();
        for (String userId : recalledUserIds) {
            if (userId.equals(userIdByTag.get(group.target().tag()))) {
                continue;
            }
            if (knownCandidateIds.contains(userId)) {
                userIdByTag.forEach((tag, uid) -> {
                    if (uid.equals(userId) && !tags.contains(tag)) {
                        tags.add(tag);
                    }
                });
            }
        }
        return tags;
    }

    private void writePartFile(String fixtureVersion, List<GroupOutcome> outcomes,
            BenchmarkFailureRecorder recorder) throws Exception {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("groupCount", outcomes.size());
        metrics.put("failedGroupCount", outcomes.stream().filter(GroupOutcome::failed).count());
        metrics.put("goldRecallAt5",
                IrMetrics.average(outcomes.stream().map(GroupOutcome::goldRecallAt5).toList()));
        metrics.put("goldTopMrr",
                IrMetrics.average(outcomes.stream().map(GroupOutcome::goldTopMrr).toList()));
        metrics.put("rerankTop1HitRate",
                IrMetrics.average(outcomes.stream().map(GroupOutcome::rerankTop1Hit).toList()));
        metrics.put("rerankNdcgAt5",
                IrMetrics.average(outcomes.stream().map(GroupOutcome::rerankNdcgAt5).toList()));
        metrics.put("rejectedCount", outcomes.stream().mapToInt(GroupOutcome::rejectedCount).sum());

        List<LetterBlock> availableLetters = outcomes.stream()
                .filter(outcome -> outcome.letter() != null && outcome.letter().available())
                .map(GroupOutcome::letter)
                .toList();
        metrics.put("letterJudgeCount", availableLetters.size());
        if (!availableLetters.isEmpty()) {
            metrics.put("letterPersonalization",
                    IrMetrics.average(availableLetters.stream().map(LetterBlock::personalization).toList()));
            metrics.put("letterSpecificity",
                    IrMetrics.average(availableLetters.stream().map(LetterBlock::specificity).toList()));
            metrics.put("letterTactfulness",
                    IrMetrics.average(availableLetters.stream().map(LetterBlock::tactfulness).toList()));
        }

        // 层聚合只含排序质量四指标；letter 三维单列汇报，不参与聚合
        double layerAggregate = IrMetrics.average(List.of(
                (Double) metrics.get("goldRecallAt5"),
                (Double) metrics.get("goldTopMrr"),
                (Double) metrics.get("rerankTop1HitRate"),
                (Double) metrics.get("rerankNdcgAt5")));

        Map<String, Object> part = new LinkedHashMap<>();
        part.put("layer", "match");
        part.put("layerLabel", "match");
        part.put("env", BenchmarkEnv.env());
        part.put("runId", BenchmarkEnv.runId());
        part.put("fixtureVersion", fixtureVersion);
        part.put("generatedAt", Instant.now().toString());
        part.put("aggregateScores", Map.of("match", layerAggregate));
        part.put("metrics", metrics);
        part.put("perGroup", outcomes);
        part.put("anomalies", recorder.failures());

        Path partPath = Path.of("target", "benchmark", "parts", "match.json");
        Files.createDirectories(partPath.getParent());
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(partPath.toFile(), part);
    }
}
