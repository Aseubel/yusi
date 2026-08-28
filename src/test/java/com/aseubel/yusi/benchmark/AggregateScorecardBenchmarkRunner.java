package com.aseubel.yusi.benchmark;

import com.aseubel.yusi.config.ai.properties.EmbeddingModelConfigProperties;
import com.aseubel.yusi.config.ai.properties.MilvusCollectionProperties;
import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.service.ai.model.ModelCapability;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.milvus.v2.client.MilvusClientV2;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 统一记分卡收口：读取各层写入的 target/benchmark/parts/*.json，聚合为单一记分卡并落盘。
 *
 * <pre>
 * 1. 逐个 part 文件 → putSection + putAggregate（part 缺 aggregateScores 记 FIXTURE_ERROR，不静默）
 * 2. part 自带 anomalies 全量并入最终 anomalies（禁止任何失败被吞）
 * 3. 权重来自 yusi.benchmark.scorecard.weights（可配置，不写死）；不满足合成条件时只报分项
 * 4. 所有层结束后执行 BenchmarkDataGuard 清理，清理结果与清理期新失败一并入卡
 * </pre>
 *
 * 类名以 A 开头：surefire runOrder=reversealphabetial 下字典序最小者最后执行，
 * 保证在五个 layer runner 之后运行；即使顺序被打乱，缺层的部分也会如实记录为异常，
 * 并因权重覆盖不全而拒绝合成 overallScore。
 */
@Slf4j
@Tag("benchmark")
@Tag("benchmark-aggregate")
@SpringBootTest
@ActiveProfiles({"dev", "benchmark"})
@EnableConfigurationProperties(BenchmarkScorecardProperties.class)
class AggregateScorecardBenchmarkRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    /** 各层 part 文件按此清单聚合；文件缺失记为异常而不是让总分悄悄变少。 */
    private static final List<String> EXPECTED_LAYERS = List.of(
            "retrieval", "extraction", "chat", "match", "e2e");

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MilvusClientV2 milvusClientV2;
    @Autowired
    private MilvusCollectionProperties collectionProperties;
    @Autowired
    private BenchmarkScorecardProperties scorecardProperties;
    @Autowired
    private ModelRoutingProperties modelRoutingProperties;
    @Autowired
    private EmbeddingModelConfigProperties embeddingModelConfigProperties;

    @Test
    void finishUnifiedScorecard() throws Exception {
        BenchmarkFailureRecorder recorder = new BenchmarkFailureRecorder();
        BenchmarkScorecard card = new BenchmarkScorecard();
        card.fillVersions(BenchmarkVersionFingerprint.collect(
                String.valueOf(modelRoutingProperties.getSchemaVersion()),
                embeddingModelConfigProperties.getModel(),
                resolveChatPrimaryModelName(),
                BenchmarkJudgeService.JUDGE_TEMPERATURE));

        Path partsDir = Path.of("target", "benchmark", "parts");
        List<Path> partFiles = listPartFiles(partsDir);
        if (partFiles.isEmpty()) {
            recorder.record("aggregate:parts", BenchmarkFailureRecorder.TYPE_FIXTURE_ERROR,
                    "no part files found under target/benchmark/parts");
        }

        for (Path partFile : partFiles) {
            ingestPart(card, recorder, partFile);
        }
        // 预期层缺失时显式入卡：overallScore 会因此拒绝合成，但原因必须可见
        for (String layer : EXPECTED_LAYERS) {
            if (!card.getAggregateScores().containsKey(layer)) {
                recorder.record("aggregate:" + layer, BenchmarkFailureRecorder.TYPE_FIXTURE_ERROR,
                        "missing layer part: " + layer);
            }
        }

        card.applyWeights(scorecardProperties.getWeights());

        // 所有层跑完后执行收尾清理；cleanup 内部失败已由 guard 经 recorder 记 CLEANUP_ERROR
        BenchmarkDataGuard dataGuard = new BenchmarkDataGuard(
                jdbcTemplate, milvusClientV2, collectionProperties, recorder);
        card.setCleanup(dataGuard.cleanup());

        // 清理后的最终失败视图（含清理期新事件）统一入卡
        card.fillAnomalies(recorder);
        card.setGateMode(BenchmarkEnv.gateEnabled());

        BenchmarkScorecardWriter.WrittenFiles written =
                new BenchmarkScorecardWriter().write(card, Path.of("target", "benchmark"));
        log.info("unified benchmark scorecard written: {} / {}", written.jsonPath(), written.markdownPath());
        log.info("aggregate scores: {}; overallScore={} ({})", card.getAggregateScores(),
                card.getOverallScore(), card.getOverallScoreNote() == null ? "ok" : card.getOverallScoreNote());

        if (BenchmarkEnv.gateEnabled()) {
            Assertions.assertNotNull(card.getOverallScore(),
                    "benchmark gate: overallScore must be synthesized in gate mode (note="
                            + card.getOverallScoreNote() + ")");
            double threshold = Double.parseDouble(System.getProperty("yusi.benchmark.gate.min-overall", "0"));
            Assertions.assertTrue(card.getOverallScore() >= threshold,
                    () -> "benchmark gate: overall=" + card.getOverallScore() + " < " + threshold);
        }
    }

    /** 单个 part → section/aggregate/anomalies 三并入卡。 */
    private void ingestPart(BenchmarkScorecard card, BenchmarkFailureRecorder recorder, Path partFile)
            throws IOException {
        JsonNode node = MAPPER.readTree(partFile.toFile());
        String layer = node.path("layer").asText("");
        if (layer.isBlank()) {
            layer = stripExtension(partFile.getFileName().toString());
            recorder.record("aggregate:" + layer, BenchmarkFailureRecorder.TYPE_FIXTURE_ERROR,
                    "part file missing 'layer' field: " + partFile.getFileName());
        }
        card.putSection(layer, MAPPER.convertValue(node, Map.class));

        JsonNode aggregate = node.path("aggregateScores").path(layer);
        if (aggregate.isNumber()) {
            card.putAggregate(layer, aggregate.asDouble());
        } else {
            recorder.record("aggregate:" + layer, BenchmarkFailureRecorder.TYPE_FIXTURE_ERROR,
                    "part has no numeric aggregateScores." + layer);
        }

        for (JsonNode failure : node.path("anomalies")) {
            recorder.record(failure.path("step").asText(layer),
                    failure.path("failureType").asText("UNKNOWN"),
                    "[part:" + layer + "] " + failure.path("message").asText(""));
        }
    }

    private static List<Path> listPartFiles(Path partsDir) throws IOException {
        if (!Files.isDirectory(partsDir)) {
            return List.of();
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(partsDir)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .forEach(files::add);
        }
        return List.copyOf(files);
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 ? fileName : fileName.substring(0, dot);
    }

    /** 与业务共用 routing 的 chat 主模型名（benchmark plan 决策 D8）：第一个启用且支持 CHAT 的路由模型。 */
    private String resolveChatPrimaryModelName() {
        return modelRoutingProperties.getModels().stream()
                .filter(ModelRoutingProperties.ModelDefinition::isEnabled)
                .filter(model -> model.supports(ModelCapability.CHAT))
                .map(ModelRoutingProperties.ModelDefinition::getModel)
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElse("unknown");
    }
}
