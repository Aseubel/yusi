package com.aseubel.yusi.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualityGateAggregateReportTest {

    @Test
    void mapsEightSuiteSummaryRowsToLowSensitivityReport(@TempDir Path tempDir) throws Exception {
        Path reportPath = tempDir.resolve("quality-gates-aggregate-v1-report.json");
        QualityGateAggregateReport.write(reportPath, List.of(
                new QualityGateAggregateReport.SuiteSummary(
                        "chat-quality-v1", "PASS", 4, 10, 10, 0, true, List.of())));

        JsonNode report = new ObjectMapper().readTree(reportPath.toFile());
        assertEquals("quality-gates-aggregate-v1", report.path("suiteId").asText());
        assertEquals("chat-quality-v1", report.at("/cases/0/caseId").asText());
        assertEquals("PASS", report.at("/cases/0/status").asText());
        assertEquals(10, report.at("/cases/0/assertionCount").asInt());
        assertEquals(10, report.at("/cases/0/passedAssertionCount").asInt());
        assertEquals(4, report.at("/cases/0/actualSummary/suiteCaseCount").asInt());
        assertEquals(10, report.at("/cases/0/actualSummary/suiteAssertionCount").asInt());
        assertEquals(0, report.at("/cases/0/actualSummary/violationCount").asInt());
        assertTrue(report.at("/cases/0/actualSummary/semanticStable").asBoolean());
        assertEquals("fixture", report.at("/cases/0/versions/prompt/key").asText());
        assertEquals("fixture-v1", report.at("/cases/0/versions/prompt/version").asText());
        assertEquals("zh-CN", report.at("/cases/0/versions/prompt/locale").asText());
        assertEquals("PASS", report.at("/summary/status").asText());
    }
}
