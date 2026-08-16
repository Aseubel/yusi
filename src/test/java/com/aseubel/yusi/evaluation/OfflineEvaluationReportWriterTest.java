package com.aseubel.yusi.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineEvaluationReportWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void writesGenericVersionSlotsAndLowSensitivitySummary(@TempDir Path tempDir) throws Exception {
        OfflineEvaluationReportWriter.CaseResult result = new OfflineEvaluationReportWriter.CaseResult(
                "EVAL-MEM-001", "EVAL-MEM-001-A", "PASS", "fixture-v1", "expectation-v1",
                OfflineEvaluationReportWriter.Versions.fixtureBaseline(),
                2, 2, List.of(), Map.of("profileLeakCount", 0));
        Path output = tempDir.resolve("memory-report.json");

        OfflineEvaluationReportWriter.write(output, "memory-lifecycle-v1", List.of(result));

        JsonNode root = objectMapper.readTree(output.toFile());
        assertEquals(1, root.path("schemaVersion").asInt());
        assertEquals("memory-lifecycle-v1", root.path("suiteId").asText());
        assertTrue(root.at("/cases/0/versions/model").isObject());
        assertTrue(root.at("/cases/0/versions/prompt").isObject());
        assertTrue(root.at("/cases/0/versions/retrieval").isObject());
        assertTrue(root.at("/cases/0/versions/ranking").isObject());
        assertEquals(0, root.at("/cases/0/actualSummary/profileLeakCount").asInt());
        assertEquals("PASS", root.at("/summary/status").asText());
        assertNotNull(root.get("generatedAt"));
        assertFalse(Files.readString(output).contains("evidence-token-"));
    }

    @Test
    void marksSummaryFailedWhenAnyCaseFails(@TempDir Path tempDir) throws Exception {
        OfflineEvaluationReportWriter.CaseResult result = new OfflineEvaluationReportWriter.CaseResult(
                "EVAL-MEM-001", "EVAL-MEM-001-B", "FAIL", "fixture-v1", "expectation-v1",
                OfflineEvaluationReportWriter.Versions.fixtureBaseline(),
                2, 1, List.of("PROFILE_LEAK_THRESHOLD"), Map.of("profileLeakCount", 1));
        Path output = tempDir.resolve("failed-memory-report.json");

        OfflineEvaluationReportWriter.write(output, "memory-lifecycle-v1", List.of(result));

        JsonNode root = objectMapper.readTree(output.toFile());
        assertEquals("FAIL", root.at("/summary/status").asText());
        assertEquals(List.of("PROFILE_LEAK_THRESHOLD"),
                objectMapper.convertValue(root.at("/cases/0/violationCodes"), List.class));
    }
}
