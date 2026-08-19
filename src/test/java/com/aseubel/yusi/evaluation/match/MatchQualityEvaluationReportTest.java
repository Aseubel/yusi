package com.aseubel.yusi.evaluation.match;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MatchQualityEvaluationReportTest {

    @Test
    void mapsMatchingMetricsAndFixturePromptVersion(@TempDir Path tempDir) throws Exception {
        Path reportPath = tempDir.resolve("match-quality-v1-report.json");
        MatchQualityEvaluationReport.ActualSummary summary =
                new MatchQualityEvaluationReport.ActualSummary(
                        2, 2, 1, 3, 1, 1, 1, 1, 2, 0, false);
        MatchQualityEvaluationReport.CaseResult result =
                new MatchQualityEvaluationReport.CaseResult(
                        "EVAL-MATCH-001", "EVAL-MATCH-001-A", "PASS",
                        "fixture-v1", "expectation-v1",
                        MatchQualityEvaluationReport.Versions.fixtureBaseline(),
                        11, 11, List.of(), summary);

        MatchQualityEvaluationReport.write(reportPath, List.of(result));

        JsonNode report = new ObjectMapper().readTree(reportPath.toFile());
        assertEquals("match-quality-v1", report.path("suiteId").asText());
        assertEquals("fixture", report.at("/cases/0/versions/prompt/key").asText());
        assertEquals("fixture-v1", report.at("/cases/0/versions/prompt/version").asText());
        assertEquals("zh-CN", report.at("/cases/0/versions/prompt/locale").asText());
        assertEquals(2, report.at("/cases/0/actualSummary/recallExpectedCount").asInt());
        assertEquals(2, report.at("/cases/0/actualSummary/recallMatchedCount").asInt());
        assertEquals(1, report.at("/cases/0/actualSummary/recommendationCount").asInt());
        assertEquals(3, report.at("/cases/0/actualSummary/reasonCoveragePassCount").asInt());
        assertEquals(1, report.at("/cases/0/actualSummary/startedInteractionPassCount").asInt());
        assertEquals(1, report.at("/cases/0/actualSummary/mutualResonancePassCount").asInt());
        assertEquals(1, report.at("/cases/0/actualSummary/strongNegativeExcludedCount").asInt());
        assertEquals(1, report.at("/cases/0/actualSummary/recommendedCount").asInt());
        assertEquals(2, report.at("/cases/0/actualSummary/acceptedCount").asInt());
        assertEquals(0, report.at("/cases/0/actualSummary/viewedCount").asInt());
        assertFalse(report.at("/cases/0/actualSummary/acceptanceRateAvailable").asBoolean());
        assertFalse(report.toString().contains("synthetic-forbidden-value"));
    }
}
