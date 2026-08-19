package com.aseubel.yusi.evaluation.lifegraph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LifeGraphTimelineRebuildEvaluationReportTest {

    @Test
    void mapsRebuildCountsAndFixturePromptVersion(@TempDir Path tempDir) throws Exception {
        Path reportPath = tempDir.resolve("lifegraph-timeline-rebuild-v1-report.json");
        LifeGraphTimelineRebuildEvaluationReport.ActualSummary actualSummary =
                new LifeGraphTimelineRebuildEvaluationReport.ActualSummary(1, 0, 1, 0, 0);
        LifeGraphTimelineRebuildEvaluationReport.CaseResult result =
                new LifeGraphTimelineRebuildEvaluationReport.CaseResult(
                        "EVAL-TIMELINE-002", "EVAL-TIMELINE-002-A", "PASS",
                        "fixture-v1", "expectation-v1",
                        LifeGraphTimelineRebuildEvaluationReport.Versions.fixtureBaseline(),
                        5, 5, List.of(), actualSummary);

        LifeGraphTimelineRebuildEvaluationReport.write(reportPath, List.of(result));

        JsonNode report = new ObjectMapper().readTree(reportPath.toFile());
        assertEquals("lifegraph-timeline-rebuild-v1", report.path("suiteId").asText());
        assertEquals("fixture", report.at("/cases/0/versions/prompt/key").asText());
        assertEquals("fixture-v1", report.at("/cases/0/versions/prompt/version").asText());
        assertEquals("zh-CN", report.at("/cases/0/versions/prompt/locale").asText());
        assertEquals(1, report.at("/cases/0/actualSummary/beforeRevisionNodeCount").asInt());
        assertEquals(0, report.at("/cases/0/actualSummary/afterRevisionOldResidualCount").asInt());
        assertEquals(1, report.at("/cases/0/actualSummary/afterRevisionNewNodeCount").asInt());
        assertEquals(0, report.at("/cases/0/actualSummary/afterDeleteTimelineNodeCount").asInt());
        assertEquals(0, report.at("/cases/0/actualSummary/sourceResidualCount").asInt());
        assertFalse(report.toString().contains("evidence-token-"));
    }
}
