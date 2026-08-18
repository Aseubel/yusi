package com.aseubel.yusi.evaluation;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Test-only executable gates shared by deterministic evaluation suites. */
public final class QualityGatePolicy {

    private static final String EXPECTED_PROMPT_KEY = "fixture";
    private static final String EXPECTED_PROMPT_VERSION = "fixture-v1";
    private static final String EXPECTED_PROMPT_LOCALE = "zh-CN";
    private static final Set<String> FORBIDDEN_SUMMARY_TOKENS = Set.of(
            "query", "rawtext", "plaincontent", "prompt", "toolarguments", "toolresult",
            "secret", "password", "response");

    private QualityGatePolicy() {
    }

    public static final class GateViolation extends AssertionError {
        public GateViolation(String code) {
            super(code);
        }
    }

    public record SuiteContract(String expectedSuiteId,
                                Set<String> expectedCaseIds,
                                int minimumAssertionCount) {
        public SuiteContract {
            expectedCaseIds = expectedCaseIds == null ? Set.of() : Set.copyOf(expectedCaseIds);
        }
    }

    public static void requirePass(
            String actualSuiteId,
            List<OfflineEvaluationReportWriter.CaseResult> cases,
            SuiteContract contract) {
        if (contract == null || contract.expectedSuiteId() == null
                || contract.expectedSuiteId().isBlank()) {
            fail("QUALITY_GATE_CONTRACT");
        }
        if (!Objects.equals(actualSuiteId, contract.expectedSuiteId())) {
            fail("QUALITY_GATE_SUITE_MISMATCH");
        }
        if (cases == null || cases.size() != contract.expectedCaseIds().size()) {
            fail("QUALITY_GATE_CASE_COUNT");
        }
        if (contract.minimumAssertionCount() < 0) {
            fail("QUALITY_GATE_ASSERTION_COUNT");
        }

        Set<String> actualCaseIds = new HashSet<>();
        int assertionCount = 0;
        for (OfflineEvaluationReportWriter.CaseResult result : cases) {
            if (result == null || result.caseId() == null || !actualCaseIds.add(result.caseId())) {
                fail("QUALITY_GATE_CASE_IDS");
            }
            if (!"PASS".equals(result.status())) {
                fail("QUALITY_GATE_CASE_STATUS");
            }
            if (result.assertionCount() < 0 || result.passedAssertionCount() < 0
                    || result.passedAssertionCount() > result.assertionCount()
                    || result.passedAssertionCount() != result.assertionCount()) {
                fail("QUALITY_GATE_ASSERTION_COUNT");
            }
            assertionCount += result.assertionCount();
            validateVersions(result.versions());
            validateSummary(result.actualSummary());
        }
        if (!actualCaseIds.equals(contract.expectedCaseIds())) {
            fail("QUALITY_GATE_CASE_IDS");
        }
        if (assertionCount < contract.minimumAssertionCount()) {
            fail("QUALITY_GATE_ASSERTION_COUNT");
        }
    }

    public static void requireMetricAtLeast(
            Map<String, Object> summary, String metric, int expected, String violationCode) {
        if (expected < 0) {
            fail("QUALITY_GATE_METRIC_EXPECTATION");
        }
        if (intMetric(summary, metric) < expected) {
            fail(violationCode);
        }
    }

    public static void requireMetricEquals(
            Map<String, Object> summary, String metric, int expected, String violationCode) {
        if (expected < 0) {
            fail("QUALITY_GATE_METRIC_EXPECTATION");
        }
        if (intMetric(summary, metric) != expected) {
            fail(violationCode);
        }
    }

    public static int intMetric(Map<String, Object> summary, String metric) {
        if (summary == null || metric == null || !(summary.get(metric) instanceof Number)) {
            fail("QUALITY_GATE_METRIC_INVALID");
        }
        Number number = (Number) summary.get(metric);
        double value = number.doubleValue();
        if (!Double.isFinite(value) || value < 0 || value > Integer.MAX_VALUE
                || value != Math.rint(value)) {
            fail("QUALITY_GATE_METRIC_INVALID");
        }
        return (int) value;
    }

    public static boolean booleanMetric(Map<String, Object> summary, String metric) {
        if (summary == null || metric == null || !(summary.get(metric) instanceof Boolean)) {
            fail("QUALITY_GATE_METRIC_INVALID");
        }
        Boolean value = (Boolean) summary.get(metric);
        return value;
    }

    private static void validateVersions(OfflineEvaluationReportWriter.Versions versions) {
        if (versions == null || versions.model() == null || versions.prompt() == null
                || versions.retrieval() == null || versions.ranking() == null) {
            fail("QUALITY_GATE_VERSION_MISSING");
        }
        OfflineEvaluationReportWriter.PromptVersion prompt = versions.prompt();
        if (!EXPECTED_PROMPT_KEY.equals(prompt.key())
                || !EXPECTED_PROMPT_VERSION.equals(prompt.version())
                || !EXPECTED_PROMPT_LOCALE.equals(prompt.locale())) {
            fail("QUALITY_GATE_PROMPT_VERSION");
        }
    }

    private static void validateSummary(Map<String, Object> summary) {
        if (summary == null) {
            fail("QUALITY_GATE_SUMMARY");
        }
        for (Object value : summary.values()) {
            if (value instanceof Number number) {
                double numericValue = number.doubleValue();
                if (!Double.isFinite(numericValue) || numericValue < 0) {
                    fail("QUALITY_GATE_SUMMARY");
                }
                continue;
            }
            if (value instanceof Boolean) {
                continue;
            }
            if (!(value instanceof String)) {
                fail("QUALITY_GATE_SUMMARY");
            }
            String text = (String) value;
            if (text.isBlank() || containsForbiddenToken(text)) {
                fail("QUALITY_GATE_SUMMARY");
            }
            if (!text.matches("[A-Z][A-Z0-9_]*")) {
                fail("QUALITY_GATE_SUMMARY");
            }
        }
    }

    private static boolean containsForbiddenToken(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return FORBIDDEN_SUMMARY_TOKENS.stream().anyMatch(normalized::contains);
    }

    private static void fail(String code) {
        throw new GateViolation(code);
    }
}
