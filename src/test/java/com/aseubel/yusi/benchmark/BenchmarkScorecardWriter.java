package com.aseubel.yusi.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

/**
 * 记分卡落盘：JSON（机器可 diff）+ Markdown（人读摘要）到 target/benchmark/。
 * 写文件前先过敏感信息校验，任何违禁字符串直接抛出而非脱敏输出。
 */
public final class BenchmarkScorecardWriter {

    public record WrittenFiles(Path jsonPath, Path markdownPath) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final BenchmarkReportSensitivityValidator sensitivityValidator =
            new BenchmarkReportSensitivityValidator();

    public WrittenFiles write(BenchmarkScorecard card, Path targetDir) throws IOException {
        if (card.getGeneratedAt() == null) {
            card.markGeneratedAt(Instant.now());
        }
        String baseName = "benchmark-scorecard-" + card.getEnv() + "-"
                + card.getGeneratedAt().toString().replace(":", "").substring(0, 13);
        Files.createDirectories(targetDir);

        String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(card);
        // 敏感校验：API key 形态 / 超长 base64 等不允许进入记分卡
        java.util.List<String> violations = sensitivityValidator.validate(json);
        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                    "scorecard failed sensitivity validation: " + String.join("; ", violations));
        }

        Path jsonPath = targetDir.resolve(baseName + ".json");
        Files.writeString(jsonPath, json);
        Path markdownPath = targetDir.resolve(baseName + ".md");
        Files.writeString(markdownPath, renderMarkdown(card, json));

        return new WrittenFiles(jsonPath, markdownPath);
    }

    /** 人读摘要：头部关键指标表 + 分项 JSON 缩进文本。 */
    private String renderMarkdown(BenchmarkScorecard card, String json) {
        StringBuilder md = new StringBuilder();
        md.append("# Yusi Benchmark Scorecard\n\n");
        md.append("- benchmarkId: ").append(card.getBenchmarkId()).append('\n');
        md.append("- runId: ").append(card.getRunId())
                .append("  env: ").append(card.getEnv()).append('\n');
        md.append("- startedAt: ").append(card.getStartedAt())
                .append("  generatedAt: ").append(card.getGeneratedAt()).append('\n');
        if (card.getVersions() != null) {
            md.append("- versions: ");
            card.getVersions().forEach((k, v) -> md.append(k).append('=').append(v).append(' '));
            md.append('\n');
        }
        if (card.getOverallScore() != null) {
            md.append("\n## Overall: ").append(card.getOverallScore()).append("\n");
        } else if (card.getOverallScoreNote() != null) {
            md.append("\n## Overall: N/A (").append(card.getOverallScoreNote()).append(")\n");
        }
        md.append("\n## 分项聚合分\n\n| 层 | 分数 |\n| --- | --- |\n");
        card.getAggregateScores()
                .forEach((layer, score) -> md.append("| ").append(layer).append(" | ")
                        .append(score).append(" |\n"));
        md.append("\n## 异常记录（不可静默）\n\n");
        if (card.getAnomalies().isEmpty()) {
            md.append("(none)\n");
        } else {
            md.append("| step | type | message | at |\n| --- | --- | --- | --- |\n");
            for (BenchmarkFailureRecorder.Failure failure : card.getAnomalies()) {
                md.append("| ").append(escape(failure.step()))
                        .append(" | ").append(escape(failure.failureType()))
                        .append(" | ").append(escape(failure.message()))
                        .append(" | ").append(failure.occurredAt())
                        .append(" |\n");
            }
        }
        md.append("\n## 分项详情\n\n```json\n").append(json).append("\n```\n");
        return md.toString();
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace('|', '/').replace('\n', ' ');
    }

    /** 供测试断言的只读视图。 */
    public Map<String, Object> toTree(BenchmarkScorecard card) {
        return MAPPER.convertValue(card, Map.class);
    }
}
