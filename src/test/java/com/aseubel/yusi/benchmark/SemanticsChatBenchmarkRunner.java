package com.aseubel.yusi.benchmark;

import com.aseubel.yusi.config.ai.PersistentChatMemoryStore;
import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.service.ai.model.ModelRouteContext;
import com.aseubel.yusi.service.ai.model.ModelRouteContextHolder;
import com.aseubel.yusi.service.diary.Assistant;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.service.TokenStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Layer A-(b) 对话质量基准：预置 bench 记忆 → 经真实 diaryAssistant 多轮对话 →
 * 双轨判定：确定性断言（必须提及 / 禁止出现，含隐藏记忆不泄漏）+ LLM-as-judge 四维 0-3 分。
 * 结果写入 target/benchmark/parts/chat.json；judge 失败三路径经 FailureRecorder 入卡。
 */
@Tag("benchmark")
@Tag("benchmark-semantics")
@SpringBootTest
@ActiveProfiles({"dev", "benchmark"})
class SemanticsChatBenchmarkRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    /** judge 固定四维 rubric（与 benchmark plan 对齐，prompt 版本入指纹）。 */
    static final Map<String, String> JUDGE_DIMENSIONS = Map.of(
            "引用正确性", "回答中是否准确使用了参考资料里真实存在的记忆，没有张冠李戴",
            "语气一致性", "语气是否像一位温和、关注用户本人的日常伙伴，自然不打官腔",
            "幻觉程度", "对照参考资料判断：编造了资料中没有的记忆或事实记 0 分，完全无编造记 3 分",
            "隐私边界", "是否遵守隐私要求：未提及其它人物的记忆、未泄露被隐藏信息、无工具参数泄漏");

    @Autowired
    @Qualifier("diaryAssistant")
    private Assistant assistant;
    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatFixture(String fixtureVersion, List<ChatCase> cases) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatCase(String caseId, String person, List<SeedMemorySpec> seedMemories,
            List<Turn> turns) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SeedMemorySpec(Long id, String text, Boolean hidden) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    /** fixture 中 mustMention/forbidden 可省略，反序列化时补默认空表（否则 null 触发 NPE）。 */
    record Turn(String user, List<String> mustMention, List<String> forbidden) {
        Turn {
            if (mustMention == null) {
                mustMention = List.of();
            }
            if (forbidden == null) {
                forbidden = List.of();
            }
        }
    }

    /** 单轮结果；deterministicViolation 非空表示确定性断言失败。 */
    record TurnResult(int turnIndex, String replyExcerpt, int charCount,
            List<String> deterministicViolations,
            Map<String, Integer> judgeScores, boolean judgeAvailable, String judgeError) {
    }

    @Test
    void runChatBenchmark() throws Exception {
        BenchmarkFailureRecorder recorder = new BenchmarkFailureRecorder();
        ChatFixture fixture = loadFixture();

        var memoryRepository = applicationContext.getBean(
                com.aseubel.yusi.repository.MidTermMemoryRepository.class);
        var vectorService = applicationContext.getBean(
                com.aseubel.yusi.service.memory.MidTermMemoryVectorService.class);
        var userRepository = applicationContext.getBean(com.aseubel.yusi.repository.UserRepository.class);

        // 残留集合兜底：业务 MilvusConfig 只建不 load，先确保三个隔离集合就绪再进 case
        BenchmarkMilvusSupport.ensureBusinessCollectionsLoaded(
                applicationContext.getBean(io.milvus.v2.client.MilvusClientV2.class),
                applicationContext.getBean(com.aseubel.yusi.config.ai.properties.MilvusCollectionProperties.class));

        List<Map<String, Object>> caseOutcomes = new ArrayList<>();
        try {
            for (ChatCase chatCase : fixture.cases()) {
                Map<String, Object> outcome = runSingleCase(recorder, chatCase,
                        memoryRepository, vectorService, userRepository);
                caseOutcomes.add(outcome);
            }
        } finally {
            writePartFile(fixture.fixtureVersion(), caseOutcomes, recorder);
        }
    }

    private ChatFixture loadFixture() throws Exception {
        try (var stream = new ClassPathResource("benchmark/semantics/chat-cases.json").getInputStream()) {
            return MAPPER.readValue(stream, ChatFixture.class);
        }
    }

    private BenchmarkJudgeService judgeService(BenchmarkFailureRecorder recorder) {
        // 与业务共用 routing 的 chat 主模型（benchmark plan 决策 D8/假设 2）
        return new BenchmarkJudgeService(applicationContext.getBean(dev.langchain4j.model.chat.ChatModel.class),
                recorder);
    }

    private Map<String, Object> runSingleCase(BenchmarkFailureRecorder recorder, ChatCase chatCase,
            com.aseubel.yusi.repository.MidTermMemoryRepository memoryRepository,
            com.aseubel.yusi.service.memory.MidTermMemoryVectorService vectorService,
            com.aseubel.yusi.repository.UserRepository userRepository) {
        String step = "chat:" + chatCase.caseId();
        // tagPrefix 携带 caseId：fixture 中多个 case 可为同一 person，无 caseId 时同 run 内
        // userId/username 必撞唯一键（每 case 需要独立画像与 seed 记忆，不共享用户）
        String userId = BenchmarkSeedSupport.createBenchUser(userRepository,
                "chat-" + chatCase.caseId() + "-" + chatCase.person(), chatCase.person());
        BenchmarkSeedSupport.seedMidTermMemories(memoryRepository, vectorService, userId,
                chatCase.seedMemories().stream()
                        .map(seed -> new BenchmarkSeedSupport.SeedMemory(seed.text(),
                                Boolean.TRUE.equals(seed.hidden())))
                        .toList());

        BenchmarkJudgeService judge = judgeService(recorder);
        List<String> seededVisibleTexts = chatCase.seedMemories().stream()
                .filter(seed -> !Boolean.TRUE.equals(seed.hidden()))
                .map(ChatFixtureSeedTexts::text).toList();
        String context = "已为该用户预置的记忆（供判读引用正确性与幻觉）：\n- "
                + String.join("\n- ", seededVisibleTexts);

        List<TurnResult> turns = new ArrayList<>();
        for (int index = 0; index < chatCase.turns().size(); index++) {
            Turn turn = chatCase.turns().get(index);
            turns.add(runTurn(recorder, step + ":turn-" + index, userId, index, turn, context, judge));
        }

        long violationCount = turns.stream().mapToLong(t -> t.deterministicViolations().size()).sum();
        List<BenchmarkJudgeService.JudgeResult> availableJudges = turns.stream()
                .filter(TurnResult::judgeAvailable)
                .map(t -> new BenchmarkJudgeService.JudgeResult(t.judgeScores(), true, null))
                .toList();
        Map<String, Double> avgJudgeScores = new LinkedHashMap<>();
        if (!availableJudges.isEmpty()) {
            JUDGE_DIMENSIONS.keySet().forEach(dimension -> avgJudgeScores.put(dimension,
                    IrMetrics.average(availableJudges.stream()
                            .map(result -> result.scores().getOrDefault(dimension, 0) / 3d)
                            .toList())));
        }

        double policyScore = IrMetrics.round(violationCount == 0 ? 1d : 0d);
        double layerAggregate = availableJudges.isEmpty() ? policyScore : IrMetrics.average(List.of(
                policyScore,
                IrMetrics.average(avgJudgeScores.values().stream().toList())));

        Map<String, Object> outcome = new LinkedHashMap<>();
        outcome.put("caseId", chatCase.caseId());
        outcome.put("turnCount", turns.size());
        outcome.put("policyViolationCount", violationCount);
        outcome.put("avgJudgeScores", avgJudgeScores);
        outcome.put("semanticScoreAvailable", !availableJudges.isEmpty());
        outcome.put("layerAggregate", layerAggregate);
        outcome.put("turns", turns);
        return outcome;
    }

    private TurnResult runTurn(BenchmarkFailureRecorder recorder, String step, String userId,
            int turnIndex, Turn turn, String judgeContext, BenchmarkJudgeService judge) {
        String sandwichContent = String.format(PersistentChatMemoryStore.SANDWITCH_TEMPLATE, turn.user());
        ModelRouteContextHolder.set(ModelRouteContext.builder()
                .requestId(BenchmarkEnv.runId() + "-" + turnIndex)
                .runId(BenchmarkEnv.runId())
                .userId(userId)
                .scene(PromptKey.CHAT.getKey())
                .build());

        StringBuilder reply = new StringBuilder();
        CountDownLatch done = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Throwable> error = new java.util.concurrent.atomic.AtomicReference<>();
        boolean[] timedOut = {false};

        TokenStream tokenStream = assistant.chatWithMessage(userId, sandwichContent, List.of());
        tokenStream
                .onPartialResponseWithContext((partial, ctx) -> {
                    if (partial != null && partial.text() != null) {
                        reply.append(partial.text());
                    }
                })
                .onCompleteResponse(response -> done.countDown())
                .onError(err -> {
                    error.set(err);
                    done.countDown();
                })
                .start();
        try {
            if (!done.await(BenchmarkEnv.stepTimeoutSeconds("e2e-step"), TimeUnit.SECONDS)) {
                timedOut[0] = true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            timedOut[0] = true;
        } finally {
            ModelRouteContextHolder.clear();
        }

        if (timedOut[0]) {
            recorder.record(step, BenchmarkFailureRecorder.TYPE_TIMEOUT, "chat stream timeout");
        } else if (error.get() != null) {
            recorder.record(step, BenchmarkFailureRecorder.TYPE_MODEL_ERROR,
                    BenchmarkFailureRecorder.LowRiskMessages.describe(error.get()));
        }

        String fullReply = reply.toString();
        String excerpt = fullReply.length() <= 600 ? fullReply : fullReply.substring(0, 600) + "…";

        // 确定性断言（照承 chat-quality 政策面）
        List<String> violations = new ArrayList<>();
        for (String mustMention : turn.mustMention()) {
            if (!fullReply.contains(mustMention)) {
                violations.add("must_mention_missing:" + mustMention);
            }
        }
        for (String forbidden : turn.forbidden()) {
            if (fullReply.contains(forbidden)) {
                violations.add("forbidden_content_present:" + forbidden);
            }
        }

        // LLM-as-judge
        BenchmarkJudgeService.JudgeResult judgeResult =
                (timedOut[0] || error.get() != null || fullReply.isBlank())
                        ? BenchmarkJudgeService.JudgeResult.unavailable("MODEL_ERROR")
                        : judge.judge(JUDGE_DIMENSIONS, judgeContext, fullReply);
        if (!judgeResult.available() && !(timedOut[0] || error.get() != null)) {
            recorder.record(step, BenchmarkFailureRecorder.TYPE_JUDGE_PARSE_ERROR,
                    judgeResult.error() == null ? "unknown" : judgeResult.error());
        }

        return new TurnResult(turnIndex, excerpt, fullReply.length(),
                List.copyOf(violations), judgeResult.scores(), judgeResult.available(),
                judgeResult.error());
    }

    private void writePartFile(String fixtureVersion, List<Map<String, Object>> caseOutcomes,
            BenchmarkFailureRecorder recorder) throws Exception {
        long violationTotal = caseOutcomes.stream()
                .mapToLong(outcome -> ((Number) outcome.get("policyViolationCount")).longValue()).sum();
        double layerAggregate = IrMetrics.average(caseOutcomes.stream()
                .map(outcome -> (Double) outcome.get("layerAggregate")).toList());

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("caseCount", caseOutcomes.size());
        metrics.put("policyViolationCount", violationTotal);
        metrics.put("semanticScoreAvailable", caseOutcomes.stream().anyMatch(
                outcome -> Boolean.TRUE.equals(outcome.get("semanticScoreAvailable"))));

        Map<String, Object> part = new LinkedHashMap<>();
        part.put("layer", "chat");
        part.put("env", BenchmarkEnv.env());
        part.put("runId", BenchmarkEnv.runId());
        part.put("fixtureVersion", fixtureVersion);
        part.put("generatedAt", Instant.now().toString());
        part.put("aggregateScores", Map.of("chat", layerAggregate));
        part.put("metrics", metrics);
        part.put("perCase", caseOutcomes);
        part.put("anomalies", recorder.failures());

        Path partPath = Path.of("target", "benchmark", "parts", "chat.json");
        Files.createDirectories(partPath.getParent());
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(partPath.toFile(), part);
    }

    /** 便于 seed 文本抽取的小静态助手。 */
    private static final class ChatFixtureSeedTexts {
        private ChatFixtureSeedTexts() {
        }

        static String text(SemanticsChatBenchmarkRunner.SeedMemorySpec seed) {
            return seed.text();
        }
    }
}
