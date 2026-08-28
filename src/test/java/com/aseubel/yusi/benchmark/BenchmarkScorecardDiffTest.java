package com.aseubel.yusi.benchmark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** BenchmarkScorecardDiff 纯逻辑单测：delta 方向、缺失层、版本指纹差异与口径警告。 */
class BenchmarkScorecardDiffTest {

    @TempDir
    Path tempDir;

    @Test
    void compareReportsLayerDeltasAndVersionDiffs() throws IOException {
        Path baseline = writeCard("baseline.json", """
                {
                  "runId": "run00001",
                  "env": "local",
                  "versions": {"gitSha": "abc123", "model": "qwen-main", "fixtures": "v1"},
                  "aggregateScores": {"retrieval": 0.8, "extraction": 0.5},
                  "overallScore": 0.65,
                  "anomalies": [{"step": "s", "failureType": "TIMEOUT", "message": "", "occurredAt": "2026-08-27T00:00:00Z"}]
                }
                """);
        Path current = writeCard("current.json", """
                {
                  "runId": "run00002",
                  "env": "local",
                  "versions": {"gitSha": "def456", "model": "qwen-main", "fixtures": "v1"},
                  "aggregateScores": {"retrieval": 0.9, "extraction": 0.5, "chat": 0.7},
                  "overallScore": 0.72,
                  "anomalies": []
                }
                """);

        String report = BenchmarkScorecardDiff.compare(baseline, current);

        // 分项 delta：升层带 ↑，持平无符号
        assertThat(report).contains("| retrieval | 0.8 | 0.9 | +0.1 ↑ |");
        assertThat(report).contains("| extraction | 0.5 | 0.5 | +0");
        // current 新增层在 baseline 中缺数 → n/a
        assertThat(report).contains("| chat | - | 0.7 | n/a |");
        // overall delta
        assertThat(report).contains("| overallScore | 0.65 | 0.72 | +0.07 ↑ |");
        // anomaly 计数变化
        assertThat(report).contains("- baseline: 1\n- current: 0");
        // 版本指纹差异必须点名 gitSha；一致的键（model/fixtures）不列出不制造噪音
        assertThat(report).contains("gitSha: abc123 → def456");
        assertThat(report).doesNotContain("model:");
        // runId 变化属预期（箭头标注），env 一致不告警
        assertThat(report).contains("runId: run00001 → run00002");
        assertThat(report).doesNotContain("⚠ 口径不同");
    }

    @Test
    void compareWarnsWhenEnvsDifferAndOverallMissing() throws IOException {
        Path baseline = writeCard("baseline2.json", """
                {
                  "runId": "aaaa",
                  "env": "local",
                  "versions": {},
                  "aggregateScores": {"retrieval": 0.8},
                  "overallScoreNote": "weights_not_configured"
                }
                """);
        Path current = writeCard("current2.json", """
                {
                  "runId": "bbbb",
                  "env": "server",
                  "versions": {},
                  "aggregateScores": {}
                }
                """);

        String report = BenchmarkScorecardDiff.compare(baseline, current);

        assertThat(report).contains("⚠ 口径不同: server");
        // 两卡都没有 overallScore 时明确说明只能比分项
        assertThat(report).contains("仅可对比分项");
        // baseline 层在 current 缺失 → n/a
        assertThat(report).contains("| retrieval | 0.8 | - | n/a |");
    }

    private Path writeCard(String fileName, String json) throws IOException {
        Path path = tempDir.resolve(fileName);
        Files.writeString(path, json);
        return path;
    }
}
