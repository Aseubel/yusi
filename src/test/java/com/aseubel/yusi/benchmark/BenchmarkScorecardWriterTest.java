package com.aseubel.yusi.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 记分卡落盘验证：JSON+MD 双产物、权重可配合成总分、异常不静默、敏感违禁拒绝落盘。 */
class BenchmarkScorecardWriterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final BenchmarkScorecardWriter writer = new BenchmarkScorecardWriter();

    @TempDir
    Path tempDir;

    @Test
    void writesJsonAndMarkdownWithWeightedOverallScore() throws Exception {
        BenchmarkScorecard card = sampleCard();
        card.putAggregate("retrieval", 0.80);
        card.putAggregate("chat", 0.60);
        card.applyWeights(Map.of("retrieval", 0.6, "chat", 0.4));

        BenchmarkScorecardWriter.WrittenFiles files = writer.write(card, tempDir);

        JsonNode json = MAPPER.readTree(Files.readString(files.jsonPath()));
        assertThat(json.get("overallScore").asDouble()).isEqualTo(0.72);
        assertThat(json.get("weights").get("retrieval").asDouble()).isEqualTo(0.6);
        assertThat(json.get("anomalies")).hasSize(1);
        assertThat(files.markdownPath()).exists();
        String markdown = Files.readString(files.markdownPath());
        assertThat(markdown).contains("## Overall: 0.72");
        assertThat(markdown).contains("MODEL_ERROR");
    }

    @Test
    void missingWeightsRefuseToSynthesizeOverall() {
        BenchmarkScorecard card = sampleCard();
        card.putAggregate("retrieval", 0.5);
        card.applyWeights(null);

        assertThat(card.getOverallScore()).isNull();
        assertThat(card.getOverallScoreNote()).isEqualTo("weights_not_configured");

        BenchmarkScorecard unbalanced = sampleCard();
        unbalanced.putAggregate("retrieval", 0.5);
        unbalanced.applyWeights(Map.of("retrieval", 0.3, "chat", 0.3));
        assertThat(unbalanced.getOverallScore()).isNull();
        assertThat(unbalanced.getOverallScoreNote()).startsWith("weights_sum_not_1");

        BenchmarkScorecard missingLayer = sampleCard();
        missingLayer.putAggregate("retrieval", 0.5);
        missingLayer.applyWeights(Map.of("retrieval", 0.5, "e2e", 0.5));
        assertThat(missingLayer.getOverallScore()).isNull();
        assertThat(missingLayer.getOverallScoreNote())
                .isEqualTo("missing_aggregate_for_layer:e2e");
    }

    @Test
    void sensitiveContentIsRejectedBeforeWriting() {
        BenchmarkScorecard card = sampleCard();
        card.putSection("leaky", Map.of("note", "sk-abcdef1234567890abcd"));

        assertThatThrownBy(() -> writer.write(card, tempDir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("api_key_like");
        assertThat(tempDir).isEmptyDirectory();
    }

    private BenchmarkScorecard sampleCard() {
        BenchmarkScorecard card = new BenchmarkScorecard();
        card.fillVersions(BenchmarkVersionFingerprint.collect("2", "emb-v1", "qwen-main",
                BenchmarkJudgeService.JUDGE_TEMPERATURE));
        BenchmarkFailureRecorder recorder = new BenchmarkFailureRecorder();
        recorder.record("retrieval:query-1", BenchmarkFailureRecorder.TYPE_MODEL_ERROR,
                "sample failure");
        card.fillAnomalies(recorder);
        card.setCleanup(new BenchmarkDataGuard.CleanupResult(12,
                List.of("diary"), List.of(), true, List.of()));
        card.markGeneratedAt(Instant.parse("2026-08-27T10:00:00Z"));
        return card;
    }
}
