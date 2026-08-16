package com.aseubel.yusi.evaluation.lifegraph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LifeGraphTimelineEvaluationReportTest {

    @Test
    void writesVersionSlotsAndOnlyLowSensitivitySummary(@TempDir Path tempDir) throws Exception {
        LifeGraphTimelineEvaluationReport.CaseResult result =
                new LifeGraphTimelineEvaluationReport.CaseResult(
                        "EVAL-MEM-002", "EVAL-MEM-002-A", "PASS", "fixture-v1", "expectation-v1",
                        LifeGraphTimelineEvaluationReport.Versions.fixtureBaseline(),
                        4, 4, List.of(),
                        new LifeGraphTimelineEvaluationReport.ActualSummary(1, 1, 1, 1, 1, 0));
        Path output = tempDir.resolve("report.json");

        LifeGraphTimelineEvaluationReport.write(output, List.of(result));

        JsonNode root = new ObjectMapper().readTree(Files.readString(output));
        assertEquals(1, root.get("schemaVersion").asInt());
        assertFalse(root.at("/cases/0/versions/model").isMissingNode());
        assertFalse(root.at("/cases/0/versions/prompt").isMissingNode());
        assertFalse(root.at("/cases/0/versions/retrieval").isMissingNode());
        assertFalse(root.at("/cases/0/versions/ranking").isMissingNode());
        assertEquals("PASS", root.at("/summary/status").asText());
        assertNotNull(root.get("generatedAt"));
        assertFalse(Files.readString(output).contains("evidence-token-"));
    }
}
