package com.aseubel.yusi.evaluation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QualityGatePolicyTest {

    private static final String SUITE_ID = "chat-quality-v1";
    private static final Set<String> CASE_IDS = Set.of("EVAL-CHAT-001");
    private static final QualityGatePolicy.SuiteContract CONTRACT =
            new QualityGatePolicy.SuiteContract(SUITE_ID, CASE_IDS, 2);

    @Test
    void acceptsExactPassingSuiteWithCompleteVersionsAndSafeSummary() {
        assertDoesNotThrow(() -> QualityGatePolicy.requirePass(
                SUITE_ID, List.of(validCase()), CONTRACT));
    }

    @Test
    void rejectsSuiteMismatchWithFixedCode() {
        assertViolation("QUALITY_GATE_SUITE_MISMATCH", () -> QualityGatePolicy.requirePass(
                "wrong-suite", List.of(validCase()), CONTRACT));
    }

    @Test
    void rejectsMissingCaseWithFixedCode() {
        QualityGatePolicy.SuiteContract twoCases = new QualityGatePolicy.SuiteContract(
                SUITE_ID, Set.of("EVAL-CHAT-001", "EVAL-CHAT-002"), 2);
        assertViolation("QUALITY_GATE_CASE_COUNT", () -> QualityGatePolicy.requirePass(
                SUITE_ID, List.of(validCase()), twoCases));
    }

    @Test
    void rejectsFailedCaseAndReducedAssertionsWithFixedCodes() {
        assertViolation("QUALITY_GATE_CASE_STATUS", () -> QualityGatePolicy.requirePass(
                SUITE_ID, List.of(caseResult("FAIL", 2, 0, validCase().versions(), validCase().actualSummary())),
                CONTRACT));
        assertViolation("QUALITY_GATE_ASSERTION_COUNT", () -> QualityGatePolicy.requirePass(
                SUITE_ID, List.of(caseResult("PASS", 1, 1, validCase().versions(), validCase().actualSummary())),
                CONTRACT));
    }

    @Test
    void rejectsMissingOrWrongPromptVersionWithFixedCodes() {
        OfflineEvaluationReportWriter.Versions missingPrompt = new OfflineEvaluationReportWriter.Versions(
                validCase().versions().model(), null,
                validCase().versions().retrieval(), validCase().versions().ranking());
        assertViolation("QUALITY_GATE_VERSION_MISSING", () -> QualityGatePolicy.requirePass(
                SUITE_ID, List.of(caseResult("PASS", 2, 2, missingPrompt, validCase().actualSummary())),
                CONTRACT));

        OfflineEvaluationReportWriter.Versions wrongPrompt = new OfflineEvaluationReportWriter.Versions(
                validCase().versions().model(),
                new OfflineEvaluationReportWriter.PromptVersion("fixture", "wrong-v1", "zh-CN"),
                validCase().versions().retrieval(), validCase().versions().ranking());
        assertViolation("QUALITY_GATE_PROMPT_VERSION", () -> QualityGatePolicy.requirePass(
                SUITE_ID, List.of(caseResult("PASS", 2, 2, wrongPrompt, validCase().actualSummary())),
                CONTRACT));
    }

    @Test
    void rejectsSensitiveSummaryValueWithFixedCode() {
        assertViolation("QUALITY_GATE_SUMMARY", () -> QualityGatePolicy.requirePass(
                SUITE_ID,
                List.of(caseResult("PASS", 2, 2, validCase().versions(),
                        Map.of("status", "contains query text"))),
                CONTRACT));
    }

    @Test
    void rejectsNegativeAndNonFiniteIntegerMetrics() {
        assertViolation("QUALITY_GATE_METRIC_INVALID",
                () -> QualityGatePolicy.intMetric(Map.of("count", -1), "count"));
        assertViolation("QUALITY_GATE_METRIC_INVALID",
                () -> QualityGatePolicy.intMetric(Map.of("count", Double.NaN), "count"));
    }

    @Test
    void rejectsMetricMismatchAndWrongBooleanTypeWithFixedCodes() {
        assertViolation("MATCH_METRIC_MISMATCH", () -> QualityGatePolicy.requireMetricEquals(
                Map.of("count", 1), "count", 2, "MATCH_METRIC_MISMATCH"));
        assertViolation("QUALITY_GATE_METRIC_INVALID",
                () -> QualityGatePolicy.booleanMetric(Map.of("available", 1), "available"));
    }

    private OfflineEvaluationReportWriter.CaseResult validCase() {
        return caseResult("PASS", 2, 2,
                OfflineEvaluationReportWriter.Versions.fixtureBaseline(),
                Map.of("policyPassCount", 1, "semanticModelScoreAvailable", false));
    }

    private OfflineEvaluationReportWriter.CaseResult caseResult(
            String status, int assertionCount, int passedAssertionCount,
            OfflineEvaluationReportWriter.Versions versions, Map<String, Object> summary) {
        return new OfflineEvaluationReportWriter.CaseResult(
                "EVAL-CHAT-001", "EVAL-CHAT-001-A", status,
                "fixture-v1", "expectation-v1", versions,
                assertionCount, passedAssertionCount, List.of(), summary);
    }

    private void assertViolation(String code, org.junit.jupiter.api.function.Executable executable) {
        QualityGatePolicy.GateViolation violation = assertThrows(
                QualityGatePolicy.GateViolation.class, executable);
        assertEquals(code, violation.getMessage());
    }
}
