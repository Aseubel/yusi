package com.aseubel.yusi.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * 两份记分卡 JSON 的分项对比工具：baseline vs 当前，输出各层聚合分 delta、overallScore delta、
 * anomaly 数量变化与版本指纹差异。用法：
 *
 * <pre>
 *   main 参数：java ... BenchmarkScorecardDiff baseline.json current.json
 * </pre>
 */
public final class BenchmarkScorecardDiff {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    /** 与记分卡合成逻辑一致的浮点容差。 */
    private static final double EPSILON = 1e-6;

    private BenchmarkScorecardDiff() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("usage: BenchmarkScorecardDiff <baseline-scorecard.json> <current-scorecard.json>");
            System.exit(2);
        }
        System.out.println(compare(Path.of(args[0]), Path.of(args[1])));
    }

    /** 输出 Markdown 格式的对比表；两卡 runId/env 一并展示便于确认是同口径比较。 */
    public static String compare(Path baselinePath, Path currentPath) throws IOException {
        JsonNode base = MAPPER.readTree(baselinePath.toFile());
        JsonNode current = MAPPER.readTree(currentPath.toFile());

        StringBuilder md = new StringBuilder();
        md.append("# Benchmark Scorecard Diff\n\n");
        md.append("- baseline: `").append(baselinePath).append("`\n");
        md.append("- current:  `").append(currentPath).append("`\n");
        appendRunIdentity(md, "runId", base.path("runId").asText("?"), current.path("runId").asText("?"));
        appendRunIdentity(md, "env", base.path("env").asText("?"), current.path("env").asText("?"));

        md.append("\n## 分项聚合分\n\n| 层 | baseline | current | delta |\n| --- | --- | --- | --- |\n");
        Set<String> layers = unionOf(base.path("aggregateScores"), current.path("aggregateScores"));
        for (String layer : layers) {
            Double before = doubleOrNull(base.path("aggregateScores").path(layer));
            Double after = doubleOrNull(current.path("aggregateScores").path(layer));
            appendDeltaRow(md, layer, before, after);
        }

        md.append("\n## Overall\n\n");
        Double overallBefore = doubleOrNull(base.path("overallScore"));
        Double overallAfter = doubleOrNull(current.path("overallScore"));
        if (overallBefore == null && overallAfter == null) {
            md.append("两次运行均未合成 overallScore（权重缺失/不归一或层缺失），仅可对比分项。\n");
        } else {
            appendDeltaRow(md, "overallScore", overallBefore, overallAfter);
            appendNoteIfPresent(md, "baseline note", base.path("overallScoreNote"));
            appendNoteIfPresent(md, "current note", current.path("overallScoreNote"));
        }

        long baseAnomalies = countAnomalies(base);
        long currentAnomalies = countAnomalies(current);
        md.append("\n## 异常事件\n\n- baseline: ").append(baseAnomalies)
                .append("\n- current: ").append(currentAnomalies).append('\n');

        md.append("\n## 版本指纹差异\n\n");
        appendVersionDiffs(md, base.path("versions"), current.path("versions"));
        return md.toString();
    }

    private static void appendDeltaRow(StringBuilder md, String label, Double before, Double after) {
        String deltaText;
        if (before == null || after == null) {
            deltaText = "n/a";
        } else {
            double delta = after - before;
            deltaText = (delta >= 0 ? "+" : "") + IrMetrics.round(delta)
                    + (delta > EPSILON ? " ↑" : delta < -EPSILON ? " ↓" : "");
        }
        md.append("| ").append(label).append(" | ").append(before == null ? "-" : before)
                .append(" | ").append(after == null ? "-" : after)
                .append(" | ").append(deltaText).append(" |\n");
    }

    private static void appendVersionDiffs(StringBuilder md, JsonNode baseVersions, JsonNode currentVersions) {
        Set<String> keys = new TreeSet<>(unionOf(baseVersions, currentVersions));
        boolean anyDiff = false;
        for (String key : keys) {
            String before = baseVersions.path(key).asText(null);
            String after = currentVersions.path(key).asText(null);
            if (before == null || !before.equals(after)) {
                anyDiff = true;
                md.append("- ").append(key).append(": ").append(before == null ? "(absent)" : before)
                        .append(" → ").append(after == null ? "(absent)" : after).append('\n');
            }
        }
        if (!anyDiff) {
            md.append("(无差异，指纹一致，分数可直接归因于质量变化)\n");
        }
    }

    /** 并集按插入序（以 baseline 为基准，先出现的排前面），新增键保持 current 中顺序并排在末尾。 */
    private static Set<String> unionOf(JsonNode objectNode, JsonNode otherObjectNode) {
        Set<String> union = new LinkedHashSet<>();
        objectNode.fieldNames().forEachRemaining(union::add);
        otherObjectNode.fieldNames().forEachRemaining(union::add);
        return union;
    }

    private static Double doubleOrNull(JsonNode node) {
        return node.isNumber() ? node.asDouble() : null;
    }

    private static long countAnomalies(JsonNode card) {
        return card.path("anomalies").size();
    }

    private static void appendNoteIfPresent(StringBuilder md, String label, JsonNode note) {
        if (note.isTextual() && !note.asText().isBlank()) {
            md.append("- ").append(label).append(": ").append(note.asText()).append('\n');
        }
    }

    /** runId 属预期必然不同，展示但注明；其余口径（如 env）不同时给出显式警告。 */
    private static void appendRunIdentity(StringBuilder md, String label, String baseValue, String currentValue) {
        md.append("- ").append(label).append(": ").append(baseValue);
        if (!baseValue.equals(currentValue)) {
            md.append(valueChangeSuffix(label)).append(currentValue);
        }
        md.append('\n');
    }

    private static String valueChangeSuffix(String label) {
        return "runId".equals(label) ? " → " : " ⚠ 口径不同: ";
    }
}
