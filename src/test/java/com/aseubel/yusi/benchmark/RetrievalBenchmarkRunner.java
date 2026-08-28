package com.aseubel.yusi.benchmark;

import com.aseubel.yusi.config.ai.properties.MilvusCollectionProperties;
import com.aseubel.yusi.pojo.entity.MidTermMemory;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.MidTermMemoryRepository;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.service.ai.tool.DiarySearchTool;
import com.aseubel.yusi.service.memory.MidTermMemorySearchService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.milvus.v2.client.MilvusClientV2;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Layer B 检索质量基准 benchmark-retrieval-v1。
 * 真实依赖：Milvus（yusi_benchmark_* 隔离集合）+ 真实 Embedding + 真实 MySQL 中期记忆行。
 * 走产品真实检索方法：MidTermMemorySearchService / DiarySearchTool，输出 recall@5 / MRR / nDCG@10，
 * 结果写入 target/benchmark/parts/retrieval.json 供统一记分卡聚合；任何失败经 FailureRecorder 入卡。
 */
@Tag("benchmark")
@Tag("benchmark-retrieval")
@SpringBootTest
@ActiveProfiles({"dev", "benchmark"})
class RetrievalBenchmarkRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private MilvusClientV2 milvusClientV2;
    @Autowired
    private EmbeddingModel embeddingModel;
    @Autowired
    private MilvusCollectionProperties collectionProperties;
    @Autowired
    private MidTermMemorySearchService midTermMemorySearchService;
    @Autowired
    private DiarySearchTool diarySearchTool;
    @Autowired
    private MidTermMemoryRepository midTermMemoryRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private com.aseubel.yusi.config.ai.properties.EmbeddingModelConfigProperties embeddingModelConfigProperties;

    /** 语料文本到 docId 的映射（同一 JVM 进程内在插入与查询阶段间传递）。 */
    private Map<String, String> midTextToId = Map.of();
    private Map<String, String> diaryTextToId = Map.of();

    @JsonIgnoreProperties(ignoreUnknown = true)
    static record Corpus(String fixtureVersion, List<MemoryDoc> midTermMemories,
            List<DiaryDoc> diaries, List<QueryDoc> queries) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static record MemoryDoc(String id, String person, String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static record DiaryDoc(String diaryId, String person, String entryDate,
            String header, String body) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static record QueryDoc(String queryId, String route, String person, String text,
            Map<String, Integer> relevant) {
    }

    /** 单条查询的原始得分明细，写入分项详情供 diff 工具逐条对比。 */
    public record QueryScore(String queryId, String route, List<String> retrievedIds,
            double recallAt5, double mrr, double ndcgAt10, boolean failed) {
    }

    @Test
    void runRetrievalBenchmark() throws Exception {
        BenchmarkFailureRecorder recorder = new BenchmarkFailureRecorder();
        Corpus corpus = loadCorpus();

        // 1. 每个 persona 一个独立 bench 用户，同时验证检索的 userId 隔离过滤
        TreeSet<String> persons = new TreeSet<>();
        corpus.midTermMemories().forEach(doc -> persons.add(doc.person()));
        Map<String, String> userIdByPerson = createBenchUsers(persons);

        // 2. 重置隔离集合并入库（真实 embedding）
        BenchmarkMilvusSupport.resetCollections(milvusClientV2, collectionProperties,
                resolveDimension());
        Map<String, String> midTextToId = insertMidMemories(corpus, userIdByPerson);
        Map<String, String> diaryTextToId = insertDiaries(corpus, userIdByPerson);
        this.midTextToId = midTextToId;
        this.diaryTextToId = diaryTextToId;

        // 3. 按路由执行查询并计算 IR 指标
        List<QueryScore> scores = new ArrayList<>();
        for (QueryDoc query : corpus.queries()) {
            String step = "retrieval:" + query.queryId();
            List<String> retrievedIds;
            try {
                retrievedIds = recorder.withinTimeout(
                        BenchmarkEnv.stepTimeoutSeconds("retrieval-query"),
                        () -> executeQuery(query, userIdByPerson));
            } catch (java.util.concurrent.TimeoutException e) {
                recorder.record(step, BenchmarkFailureRecorder.TYPE_TIMEOUT, "retrieval timeout");
                scores.add(new QueryScore(query.queryId(), query.route(), List.of(), 0d, 0d, 0d, true));
                continue;
            }
            if (retrievedIds == null || retrievedIds.isEmpty()) {
                recorder.record(step, BenchmarkFailureRecorder.TYPE_RETRIEVAL_ERROR,
                        "empty_result_on_populated_collection");
            }
            List<String> relevantIds = new ArrayList<>(query.relevant().keySet());
            scores.add(new QueryScore(query.queryId(), query.route(),
                    retrievedIds,
                    IrMetrics.recallAtK(relevantIds, retrievedIds, 5),
                    IrMetrics.reciprocalRank(relevantIds, retrievedIds, 10),
                    IrMetrics.ndcgAtK(query.relevant(), retrievedIds, 10),
                    false));
        }

        // 4. 分路由聚合 + 层级聚合分
        Map<String, Object> routeMetrics = aggregateByRoute(scores);
        Map<String, Double> recallSummaries = new LinkedHashMap<>();
        routeMetrics.forEach((route, value) -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> metrics = (Map<String, Object>) value;
            recallSummaries.put(route, (Double) metrics.get("recallAt5"));
        });

        double layerAggregate = IrMetrics.average(scores.stream()
                .map(score -> IrMetrics.average(List.of(score.recallAt5(), score.mrr(), score.ndcgAt10())))
                .toList());

        writePartFile(corpus.fixtureVersion(), routeMetrics, scores, layerAggregate, recorder);

        // 5. gate 模式：显式开启时 recall@5 低于阈值判定失败（默认 record-only 只出分）
        if (BenchmarkEnv.gateEnabled()) {
            double minRecall = Math.min(
                    ((Number) recallSummaries.getOrDefault("MID_MEMORY", 0d)).doubleValue(),
                    ((Number) recallSummaries.getOrDefault("DIARY", 0d)).doubleValue());
            org.junit.jupiter.api.Assertions.assertTrue(minRecall >= 0.6d,
                    () -> "benchmark-retrieval gate: min route recall@5=" + minRecall + " < 0.6");
        }
    }

    private Corpus loadCorpus() throws Exception {
        try (var stream = new ClassPathResource("benchmark/retrieval/corpus-v1.json").getInputStream()) {
            return MAPPER.readValue(stream, Corpus.class);
        }
    }

    private int resolveDimension() {
        return embeddingModelConfigProperties.getDimension();
    }

    private Map<String, String> createBenchUsers(TreeSet<String> persons) {
        Map<String, String> userIds = new HashMap<>();
        for (String person : persons) {
            User user = new User();
            user.setUserId(BenchmarkEnv.userId("ret-" + person));
            user.setUserName(BenchmarkSeedSupport.benchUserName("ret-" + person, person));
            user.setPassword("bench-only-no-login-" + BenchmarkEnv.runId());
            user.setEmail("bench-" + person + "-" + BenchmarkEnv.runId() + "@benchmark.invalid");
            user.setKeyMode(com.aseubel.yusi.pojo.constant.KeyMode.DEFAULT.code());
            userRepository.save(user);
            userIds.put(person, user.getUserId());
        }
        return userIds;
    }

    private Map<String, String> insertMidMemories(Corpus corpus, Map<String, String> userIdByPerson) {
        Map<String, String> docs = new LinkedHashMap<>();
        Map<String, Map<String, String>> metadata = new HashMap<>();
        // 先落库拿自增主键：Milvus metadata.memoryId 必须是数字型 MySQL 主键，
        // 检索链路 isAvailable 会 Long.valueOf 后回查 MySQL（与生产 MidTermMemoryVectorService 对齐）
        corpus.midTermMemories().forEach(doc -> {
            MidTermMemory memory = new MidTermMemory();
            memory.setUserId(userIdByPerson.get(doc.person()));
            memory.setSummary(doc.text());
            memory.setImportance(3.5d);
            memory.setHidden(false);
            memory.setMatchAllowed(true);
            memory.setCreatedAt(LocalDateTime.now());
            memory.setUpdatedAt(LocalDateTime.now());
            midTermMemoryRepository.save(memory);
            docs.put(doc.id(), doc.text());
            metadata.put(doc.id(), Map.of(
                    "userId", userIdByPerson.get(doc.person()),
                    "memoryId", String.valueOf(memory.getId())));
        });
        Map<String, String> textToId = BenchmarkMilvusSupport.insertDocs(milvusClientV2,
                embeddingModel, collectionProperties.getMidTermMemory(), docs, metadata);

        return textToId;
    }

    private Map<String, String> insertDiaries(Corpus corpus, Map<String, String> userIdByPerson) {
        Map<String, String> docs = new LinkedHashMap<>();
        Map<String, Map<String, String>> metadata = new HashMap<>();
        corpus.diaries().forEach(doc -> {
            String text = doc.header() + "\n\n" + doc.body();
            docs.put(doc.diaryId(), text);
            metadata.put(doc.diaryId(), Map.of(
                    "userId", userIdByPerson.get(doc.person()),
                    "diaryId", doc.diaryId(),
                    "chunkIndex", "0",
                    "chunkCount", "1",
                    "entryDate", doc.entryDate()));
        });
        return BenchmarkMilvusSupport.insertDocs(milvusClientV2, embeddingModel,
                collectionProperties.getEmbedding(), docs, metadata);
    }

    private List<String> executeQuery(QueryDoc query, Map<String, String> userIdByPerson) {
        Map<String, String> textToId = "MID_MEMORY".equals(query.route())
                ? midTextToId : diaryTextToId;
        String userId = userIdByPerson.get(query.person());
        List<String> texts = "MID_MEMORY".equals(query.route())
                ? midTermMemorySearchService.searchMidTermMemory(userId, query.text(), 10)
                : diarySearchTool.searchDiary(userId, query.text(), null, null);
        return texts.stream()
                .map(textToId::get)
                .filter(id -> id != null)
                .toList();
    }

    private Map<String, Object> aggregateByRoute(List<QueryScore> scores) {
        Map<String, List<QueryScore>> byRoute = new LinkedHashMap<>();
        scores.forEach(score -> byRoute.computeIfAbsent(score.route(), k -> new ArrayList<>()).add(score));
        Map<String, Object> routeMetrics = new LinkedHashMap<>();
        byRoute.forEach((route, routeScores) -> routeMetrics.put(route, Map.of(
                "queryCount", routeScores.size(),
                "recallAt5", IrMetrics.average(routeScores.stream().map(QueryScore::recallAt5).toList()),
                "mrr", IrMetrics.average(routeScores.stream().map(QueryScore::mrr).toList()),
                "ndcgAt10", IrMetrics.average(routeScores.stream().map(QueryScore::ndcgAt10).toList()),
                "failedCount", routeScores.stream().filter(QueryScore::failed).count())));
        return routeMetrics;
    }

    private void writePartFile(String fixtureVersion, Map<String, Object> routeMetrics,
            List<QueryScore> scores, double layerAggregate, BenchmarkFailureRecorder recorder)
            throws Exception {
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("layer", "retrieval");
        part.put("layerLabel", "retrieval");
        part.put("env", BenchmarkEnv.env());
        part.put("runId", BenchmarkEnv.runId());
        part.put("fixtureVersion", fixtureVersion);
        part.put("generatedAt", Instant.now().toString());
        part.put("aggregateScores", Map.of("retrieval", layerAggregate));
        part.put("metrics", routeMetrics);
        part.put("perQuery", scores);
        part.put("anomalies", recorder.failures());
        Path partPath = Path.of("target", "benchmark", "parts", "retrieval.json");
        Files.createDirectories(partPath.getParent());
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(partPath.toFile(), part);
    }
}
