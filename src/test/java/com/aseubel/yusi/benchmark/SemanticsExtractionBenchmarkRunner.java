package com.aseubel.yusi.benchmark;

import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import com.aseubel.yusi.pojo.entity.LifeGraphRelation;
import com.aseubel.yusi.pojo.entity.LifeGraphTask;
import com.aseubel.yusi.repository.LifeGraphEntityRepository;
import com.aseubel.yusi.repository.LifeGraphRelationRepository;
import com.aseubel.yusi.repository.LifeGraphTaskRepository;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.service.diary.impl.DiaryServiceImpl;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Layer A-(a) 认知抽取基准：虚构日记 → 真实 life-graph 摄取管线（真实 LLM）→
 * 与 gold 实体（名字+类型）/关系（端点名+方向）比对，输出 precision / recall / F1 与任务失败率。
 * 结果写入 target/benchmark/parts/extraction.json；任务失败/超时经 FailureRecorder 入卡，不静默。
 */
@Tag("benchmark")
@Tag("benchmark-semantics")
@SpringBootTest
@ActiveProfiles({"dev", "benchmark"})
class SemanticsExtractionBenchmarkRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final long POLL_INTERVAL_MILLIS = 2000L;

    @Autowired
    private DiaryServiceImpl diaryService;
    @Autowired
    private LifeGraphTaskRepository lifeGraphTaskRepository;
    @Autowired
    private LifeGraphEntityRepository entityRepository;
    @Autowired
    private LifeGraphRelationRepository relationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExtractionFixture(String fixtureVersion, List<ExtractionCase> cases) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExtractionCase(String caseId, String person, String userName, String entryDate,
            String title, String body, List<GoldEntity> goldEntities,
            List<GoldRelation> goldRelations) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GoldEntity(String name, String type) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GoldRelation(String subject, String object, String type) {
    }

    /** 单 case 结果：P/R 为 0 时若并非金标准为空，需结合 anomalies 判读。 */
    record CaseOutcome(String caseId, double entityPrecision, double entityRecall,
            double relationPrecision, double relationRecall,
            int taskCount, int failedTaskCount, boolean failed,
            List<PredictedEntity> predictedEntities, List<PredictedRelation> predictedRelations) {

        double entityF1() {
            return f1(entityPrecision, entityRecall);
        }

        double relationF1() {
            return f1(relationPrecision, relationRecall);
        }
    }

    /** 预测明细：用于离线 diff 假阳性/假阴性，无需重跑 LLM。 */
    record PredictedEntity(String name, String type, boolean matched) {
    }

    record PredictedRelation(String source, String target, String type, boolean matched) {
    }

    static double f1(double precision, double recall) {
        if (precision + recall == 0d) {
            return 0d;
        }
        return IrMetrics.round(2d * precision * recall / (precision + recall));
    }

    @Test
    void runExtractionBenchmark() throws Exception {
        BenchmarkFailureRecorder recorder = new BenchmarkFailureRecorder();
        ExtractionFixture fixture = loadFixture();

        Map<String, String> userIdByPerson = new LinkedHashMap<>();
        List<CaseOutcome> outcomes = new ArrayList<>();

        // 残留集合兜底：记忆抽取的真实链路会向 mid_term_memory 集合 upsert 向量
        BenchmarkMilvusSupport.ensureBusinessCollectionsLoaded(
                applicationContext.getBean(io.milvus.v2.client.MilvusClientV2.class),
                applicationContext.getBean(com.aseubel.yusi.config.ai.properties.MilvusCollectionProperties.class));

        try {
            for (ExtractionCase extractionCase : fixture.cases()) {
                String userId = userIdByPerson.computeIfAbsent(extractionCase.person(),
                        person -> BenchmarkSeedSupport.createBenchUser(userRepository,
                                "ext-" + person, extractionCase.userName()));
                outcomes.add(runSingleCase(recorder, fixture.fixtureVersion(), extractionCase, userId));
            }
        } finally {
            writePartFile(fixture.fixtureVersion(), outcomes, recorder);
        }

        // gate 模式：显式开启时实体 F1 低于阈值判定失败
        if (BenchmarkEnv.gateEnabled() && !outcomes.isEmpty()) {
            double avgEntityF1 = IrMetrics.average(outcomes.stream()
                    .map(CaseOutcome::entityF1).toList());
            org.junit.jupiter.api.Assertions.assertTrue(avgEntityF1 >= 0.5d,
                    () -> "benchmark-extraction gate: avg entity F1=" + avgEntityF1 + " < 0.5");
        }
    }

    private ExtractionFixture loadFixture() throws Exception {
        try (var stream = new ClassPathResource("benchmark/semantics/extraction-cases.json")
                .getInputStream()) {
            return MAPPER.readValue(stream, ExtractionFixture.class);
        }
    }

    private CaseOutcome runSingleCase(BenchmarkFailureRecorder recorder, String fixtureVersion,
            ExtractionCase extractionCase, String userId) {
        String step = "extraction:" + extractionCase.caseId();

        Set<Long> entityIdsBefore = entityIdSet(userId);
        Set<Long> relationIdsBefore = relationIdSet(userId);

        // 1. 通过产品入口写日记（真实加密、事件发布、任务创建）
        Diary diary = new Diary();
        diary.setUserId(userId);
        diary.setTitle(extractionCase.title());
        diary.setContent(extractionCase.body());
        diary.setEntryDate(LocalDate.parse(extractionCase.entryDate()));
        try {
            diaryService.addDiary(diary);
        } catch (Exception e) {
            recorder.record(step, BenchmarkFailureRecorder.TYPE_EXTRACTION_ERROR,
                    BenchmarkFailureRecorder.LowRiskMessages.describe(e));
            return new CaseOutcome(extractionCase.caseId(), 0d, 0d, 0d, 0d, 0, 1, true, List.of(), List.of());
        }

        // 2. 轮询等待该日记的摄取任务到达终态。
        // 注意：任务由 @Async 监听器创建，存在可见的创建窗口；必须等到"出现终态任务"
        // 才能判定完成，不能以"PENDING/PROCESSING 短暂为空"作为结束依据。
        boolean timedOut = false;
        long deadlineNanos = System.nanoTime()
                + BenchmarkEnv.stepTimeoutSeconds("extraction-task") * 1_000_000_000L;
        List<LifeGraphTask> completed = List.of();
        List<LifeGraphTask> failed = List.of();
        while (System.nanoTime() < deadlineNanos) {
            completed = lifeGraphTaskRepository
                    .findByUserIdAndDiaryIdAndStatusIn(userId, diary.getDiaryId(),
                            List.of(LifeGraphTask.TaskStatus.COMPLETED));
            failed = lifeGraphTaskRepository
                    .findByUserIdAndDiaryIdAndStatusIn(userId, diary.getDiaryId(),
                            List.of(LifeGraphTask.TaskStatus.FAILED));
            if (!completed.isEmpty() || !failed.isEmpty()) {
                break;
            }
            try {
                Thread.sleep(POLL_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                timedOut = true;
                break;
            }
        }
        // 结束时统计终态分布
        List<LifeGraphTask> stillActive = lifeGraphTaskRepository.findByUserIdAndDiaryIdAndStatusIn(
                userId, diary.getDiaryId(), List.of(LifeGraphTask.TaskStatus.PENDING,
                        LifeGraphTask.TaskStatus.PROCESSING));
        if (!stillActive.isEmpty()) {
            timedOut = true;
        }
        int totalTasks = completed.size() + failed.size() + stillActive.size();
        if (timedOut) {
            recorder.record(step, BenchmarkFailureRecorder.TYPE_TIMEOUT,
                    "extraction task did not reach terminal state");
        }
        if (!failed.isEmpty()) {
            recorder.record(step, BenchmarkFailureRecorder.TYPE_EXTRACTION_ERROR,
                    "task_failed_count=" + failed.size());
        }
        boolean taskFailed = !failed.isEmpty() || !stillActive.isEmpty();
        if (totalTasks == 0) {
            recorder.record(step, BenchmarkFailureRecorder.TYPE_EXTRACTION_ERROR,
                    "no_life_graph_task_created");
        }

        // 3. 读回本 case 新产生的实体/关系并与 gold 比对
        List<LifeGraphEntity> entities = entityRepository.findByUserId(userId).stream()
                .filter(entity -> !entityIdsBefore.contains(entity.getId())).toList();
        List<LifeGraphRelation> relations = relationRepository.findByUserId(userId).stream()
                .filter(relation -> !relationIdsBefore.contains(relation.getId())).toList();
        Map<Long, LifeGraphEntity> entityById = new HashMap<>();
        entities.forEach(entity -> entityById.put(entity.getId(), entity));

        int matchedEntities = 0;
        List<PredictedEntity> predictedEntities = new ArrayList<>();
        for (LifeGraphEntity entity : entities) {
            boolean matched = matchesAnyGoldEntity(extractionCase.goldEntities(), entity);
            if (matched) {
                matchedEntities++;
            }
            predictedEntities.add(new PredictedEntity(entity.getDisplayName(),
                    entity.getType() == null ? null : entity.getType().name(), matched));
        }
        double entityPrecision = entities.isEmpty() ? 0d
                : IrMetrics.round((double) matchedEntities / entities.size());
        int goldCount = extractionCase.goldEntities().size();
        double entityRecall = goldCount == 0 ? 0d : IrMetrics.round((double) matchedEntities / goldCount);

        int matchedRelations = 0;
        List<PredictedRelation> predictedRelations = new ArrayList<>();
        for (LifeGraphRelation relation : relations) {
            boolean matched = matchesGoldRelation(extractionCase, relation, entityById);
            if (matched) {
                matchedRelations++;
            }
            LifeGraphEntity source = entityById.get(relation.getSemanticSourceId());
            LifeGraphEntity target = entityById.get(relation.getSemanticTargetId());
            predictedRelations.add(new PredictedRelation(
                    source == null ? null : source.getDisplayName(),
                    target == null ? null : target.getDisplayName(),
                    relation.getType(), matched));
        }
        double relationPrecision = relations.isEmpty() ? 0d
                : IrMetrics.round((double) matchedRelations / relations.size());
        int goldRelationCount = extractionCase.goldRelations().size();
        double relationRecall = goldRelationCount == 0 ? 1d
                : IrMetrics.round((double) matchedRelations / goldRelationCount);

        return new CaseOutcome(extractionCase.caseId(), entityPrecision, entityRecall,
                relationPrecision, relationRecall, totalTasks, failed.size(), taskFailed,
                predictedEntities, predictedRelations);
    }

    private Set<Long> entityIdSet(String userId) {
        Set<Long> ids = new HashSet<>();
        entityRepository.findByUserId(userId).forEach(entity -> ids.add(entity.getId()));
        return ids;
    }

    private Set<Long> relationIdSet(String userId) {
        Set<Long> ids = new HashSet<>();
        relationRepository.findByUserId(userId).forEach(relation -> ids.add(relation.getId()));
        return ids;
    }

    /** 实体命中：displayName 或 nameNorm 与 gold 相等（忽略大小写），且类型一致。 */
    private boolean matchesAnyGoldEntity(List<GoldEntity> golds, LifeGraphEntity entity) {
        String displayName = lower(entity.getDisplayName());
        String nameNorm = lower(entity.getNameNorm());
        for (GoldEntity gold : golds) {
            boolean nameMatches = lower(gold.name()).equals(displayName)
                    || lower(gold.name()).equals(nameNorm);
            boolean typeMatches = entity.getType() != null && entity.getType().name()
                    .equalsIgnoreCase(gold.type());
            if (nameMatches && typeMatches) {
                return true;
            }
        }
        return false;
    }

    /**
     * 关系命中：两端点解析出的实体名与 gold 一致（self 允许 userName / 我两种写法），忽略 type 大小写。
     * 注意：必须用 semanticSourceId/semanticTargetId —— sourceId/targetId 是按实体 ID 排序的物理端点，方向无语义。
     */
    private boolean matchesGoldRelation(ExtractionCase extractionCase, LifeGraphRelation relation,
            Map<Long, LifeGraphEntity> entityById) {
        LifeGraphEntity source = entityById.get(relation.getSemanticSourceId());
        LifeGraphEntity target = entityById.get(relation.getSemanticTargetId());
        if (source == null || target == null) {
            return false;
        }
        for (GoldRelation gold : extractionCase.goldRelations()) {
            boolean sourceMatches = endpointMatches(extractionCase, gold.subject(),
                    source.getDisplayName());
            boolean targetMatches = endpointMatches(extractionCase, gold.object(),
                    target.getDisplayName());
            boolean typeMatches = relation.getType() != null && gold.type() != null
                    && relation.getType().toLowerCase(Locale.ROOT)
                            .contains(gold.type().toLowerCase(Locale.ROOT));
            if (sourceMatches && targetMatches && typeMatches) {
                return true;
            }
        }
        return false;
    }

    private boolean endpointMatches(ExtractionCase extractionCase, String goldEndpoint,
            String actualName) {
        String actual = lower(actualName);
        if ("self".equalsIgnoreCase(goldEndpoint)) {
            return actual.equals(lower(extractionCase.userName()))
                    || actual.equals(lower(BenchmarkSeedSupport.benchUserName(
                            "ext-" + extractionCase.person(), extractionCase.userName())))
                    || actual.equals("我")
                    || actual.equals(lower(extractionCase.person()));
        }
        return actual.equals(lower(goldEndpoint));
    }

    private static String lower(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private void writePartFile(String fixtureVersion, List<CaseOutcome> outcomes,
            BenchmarkFailureRecorder recorder) throws Exception {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("caseCount", outcomes.size());
        metrics.put("failedCaseCount", outcomes.stream().filter(CaseOutcome::failed).count());
        metrics.put("totalTaskCount", outcomes.stream().mapToInt(CaseOutcome::taskCount).sum());
        metrics.put("failedTaskCount", outcomes.stream().mapToInt(CaseOutcome::failedTaskCount).sum());
        metrics.put("entityPrecision",
                IrMetrics.average(outcomes.stream().map(CaseOutcome::entityPrecision).toList()));
        metrics.put("entityRecall",
                IrMetrics.average(outcomes.stream().map(CaseOutcome::entityRecall).toList()));
        metrics.put("entityF1", IrMetrics.average(outcomes.stream().map(CaseOutcome::entityF1).toList()));
        metrics.put("relationPrecision",
                IrMetrics.average(outcomes.stream().map(CaseOutcome::relationPrecision).toList()));
        metrics.put("relationRecall",
                IrMetrics.average(outcomes.stream().map(CaseOutcome::relationRecall).toList()));
        metrics.put("relationF1",
                IrMetrics.average(outcomes.stream().map(CaseOutcome::relationF1).toList()));

        double layerAggregate = IrMetrics.average(List.of(
                (Double) metrics.get("entityF1"), (Double) metrics.get("relationF1")));

        Map<String, Object> part = new LinkedHashMap<>();
        part.put("layer", "extraction");
        part.put("layerLabel", "extraction");
        part.put("env", BenchmarkEnv.env());
        part.put("runId", BenchmarkEnv.runId());
        part.put("fixtureVersion", fixtureVersion);
        part.put("generatedAt", Instant.now().toString());
        part.put("aggregateScores", Map.of("extraction", layerAggregate));
        part.put("metrics", metrics);
        part.put("perCase", outcomes);
        part.put("anomalies", recorder.failures());

        Path partPath = Path.of("target", "benchmark", "parts", "extraction.json");
        Files.createDirectories(partPath.getParent());
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(partPath.toFile(), part);
    }
}
