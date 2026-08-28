package com.aseubel.yusi.benchmark;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一记分卡的内存模型：各层 runner 向其中填充分项数据，最后由 ScorecardWriter 落盘。
 */
public final class BenchmarkScorecard {

    public static final int SCHEMA_VERSION = 1;
    public static final String BENCHMARK_ID = "yusi-benchmark-v1";

    private final int schemaVersion = SCHEMA_VERSION;
    private final String benchmarkId = BENCHMARK_ID;
    private final String runId;
    private final String env;
    private final Instant startedAt;
    private Instant generatedAt;

    /** 版本指纹（BenchmarkVersionFingerprint.collect 的输出）。 */
    private Map<String, String> versions;
    /** 失败事件全量列表（不可静默）。 */
    private List<BenchmarkFailureRecorder.Failure> anomalies = new ArrayList<>();
    /** 各层分项结果：键如 retrieval / semantics / e2e。 */
    private final Map<String, Object> sections = new LinkedHashMap<>();
    /** 层级聚合分：overallScore 合成输入；缺层时不得合成总分。 */
    private final Map<String, Double> aggregateScores = new LinkedHashMap<>();
    /** 权重配置（yusi.benchmark.scorecard.weights），可为 null 表示未配置。 */
    private Map<String, Double> weights;
    /** 合成总分；权重缺失或不归一时为 null，并记录原因。 */
    private Double overallScore;
    private String overallScoreNote;
    /** 清理摘要，由 BenchmarkDataGuard 填充。 */
    private Object cleanup;
    /** 收尾阶段发现的新失败（写入清理后的最终版）。 */
    private boolean gateMode;

    public BenchmarkScorecard() {
        this.runId = BenchmarkEnv.runId();
        this.env = BenchmarkEnv.env();
        this.startedAt = BenchmarkEnv.startedAt();
    }

    public void fillVersions(Map<String, String> versions) {
        this.versions = Collections.unmodifiableMap(new LinkedHashMap<>(versions));
    }

    public void fillAnomalies(BenchmarkFailureRecorder recorder) {
        this.anomalies = List.copyOf(recorder.failures());
    }

    public void putSection(String name, Object data) {
        sections.put(name, data);
    }

    public void putAggregate(String layerName, double score) {
        aggregateScores.put(layerName, IrMetrics.round(score));
    }

    public Object section(String name) {
        return sections.get(name);
    }

    public void applyWeights(Map<String, Double> configuredWeights) {
        this.weights = configuredWeights == null ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(configuredWeights));
        synthesizeOverall();
    }

    /** 权重存在、覆盖所有已聚合层级且和为 1 时才合成 overallScore，否则保持 null 并说明原因。 */
    private void synthesizeOverall() {
        if (weights == null || weights.isEmpty()) {
            overallScore = null;
            overallScoreNote = "weights_not_configured";
            return;
        }
        double sum = 0d;
        for (Double weight : weights.values()) {
            sum += weight == null ? 0d : weight;
        }
        if (Math.abs(sum - 1d) > 1e-6) {
            overallScore = null;
            overallScoreNote = "weights_sum_not_1:" + IrMetrics.round(sum);
            return;
        }
        double total = 0d;
        for (Map.Entry<String, Double> entry : aggregateScores.entrySet()) {
            Double weight = weights.get(entry.getKey());
            if (weight == null) {
                overallScore = null;
                overallScoreNote = "missing_weight_for_layer:" + entry.getKey();
                return;
            }
            total += weight * entry.getValue();
        }
        // 权重里配置了但该层没有分数时同样拒绝合成
        for (String layer : weights.keySet()) {
            if (!aggregateScores.containsKey(layer)) {
                overallScore = null;
                overallScoreNote = "missing_aggregate_for_layer:" + layer;
                return;
            }
        }
        overallScore = IrMetrics.round(total);
        overallScoreNote = null;
    }

    public void markGeneratedAt(Instant instant) {
        this.generatedAt = instant;
    }

    // ---- getters 供 Jackson 序列化 ----
    public int getSchemaVersion() { return schemaVersion; }
    public String getBenchmarkId() { return benchmarkId; }
    public String getRunId() { return runId; }
    public String getEnv() { return env; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getGeneratedAt() { return generatedAt; }
    public Map<String, String> getVersions() { return versions; }
    public List<BenchmarkFailureRecorder.Failure> getAnomalies() { return anomalies; }
    public Map<String, Object> getSections() { return sections; }
    public Map<String, Double> getAggregateScores() { return aggregateScores; }
    public Map<String, Double> getWeights() { return weights; }
    public Double getOverallScore() { return overallScore; }
    public String getOverallScoreNote() { return overallScoreNote; }
    public Object getCleanup() { return cleanup; }
    public boolean isGateMode() { return gateMode; }

    public void setCleanup(Object cleanup) { this.cleanup = cleanup; }
    public void setGateMode(boolean gateMode) { this.gateMode = gateMode; }
}
