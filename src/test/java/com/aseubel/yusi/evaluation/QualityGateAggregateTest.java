package com.aseubel.yusi.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualityGateAggregateTest {

    private static final Path EVALUATION_DIR = Path.of("target", "evaluation");
    private static final Path REPORT_PATH = EVALUATION_DIR.resolve(
            "quality-gates-aggregate-v1-report.json");
    private static final String CASE_SEPARATOR = "\u0000";
    private static final JsonNode EXPECTED_PROMPT = new ObjectMapper().createObjectNode()
            .put("key", "fixture")
            .put("version", "fixture-v1")
            .put("locale", "zh-CN");

    private static final List<SuiteContractSpec> CONTRACTS = List.of(
            spec("chat-quality-v1", "chat-quality-v1-report.json", 8,
                    List.of(row("EVAL-CHAT-001", "EVAL-CHAT-001-A"),
                            row("EVAL-CHAT-002", "EVAL-CHAT-002-A"),
                            row("EVAL-CHAT-003", "EVAL-CHAT-003-A"),
                            row("EVAL-TOOL-001", "EVAL-TOOL-001-A")),
                    "CHAT_SEMANTIC_SCORE"),
            spec("lifegraph-timeline-rebuild-v1", "lifegraph-timeline-rebuild-v1-report.json", 16,
                    List.of(row("EVAL-TIMELINE-002", "EVAL-TIMELINE-002-A")), "NONE"),
            spec("match-quality-v1", "match-quality-v1-report.json", 20,
                    List.of(row("EVAL-MATCH-001", "EVAL-MATCH-001")), "MATCH_ACCEPTANCE_RATE"),
            spec("lifegraph-promotion-v1", "lifegraph-promotion-v1-report.json", 22,
                    List.of(row("EVAL-MEM-003", "EVAL-MEM-003-A"),
                            row("EVAL-MEM-003", "EVAL-MEM-003-B"),
                            row("EVAL-MEM-003", "EVAL-MEM-003-C")), "NONE"),
            spec("lifegraph-timeline-v1", "lifegraph-timeline-v1-report.json", 13,
                    List.of(row("EVAL-MEM-002", "EVAL-MEM-002-A"),
                            row("EVAL-MEM-002", "EVAL-MEM-002-B"),
                            row("EVAL-MEM-002", "EVAL-MEM-002-C"),
                            row("EVAL-TIMELINE-001", "EVAL-TIMELINE-001-A")), "NONE"),
            spec("lifegraph-importance-v1", "lifegraph-importance-v1-report.json", 11,
                    List.of(row("EVAL-MEM-003", "EVAL-MEM-003-B")), "NONE"),
            spec("lifegraph-memory-relation-v1", "lifegraph-memory-relation-v1-report.json", 7,
                    List.of(row("EVAL-MEM-003", "EVAL-MEM-003-B")), "NONE"),
            spec("memory-lifecycle-v1", "memory-lifecycle-v1-report.json", 25,
                    List.of(row("EVAL-MEM-001", "EVAL-MEM-001-A"),
                            row("EVAL-MEM-001", "EVAL-MEM-001-B"),
                            row("EVAL-MEM-001", "EVAL-MEM-001-C")), "NONE"));

    private static final Map<String, String> EXPECTED_SEMANTIC_HASHES = Map.ofEntries(
            Map.entry("chat-quality-v1", "03601e1f2df091316d6531fe420117612f94a63d4a0c7840018cbc6973855b41"),
            Map.entry("lifegraph-timeline-rebuild-v1", "4b91d52526258cc982a19743bb8e7046a9691de7306abf41fe5de75a6c44ca3e"),
            Map.entry("match-quality-v1", "6298f1a467002bb88f37ca571cf575eb481516c54c7c7c23127a78b16f3214c7"),
            Map.entry("lifegraph-promotion-v1", "2293869aa50c085b81615bf6dba40baa13cde7a6fc2f812f947d21f14bdc7487"),
            Map.entry("lifegraph-timeline-v1", "f25bb6ed1570179424c8812907695cd487b85fc699c20a91260eba70b3b648d4"),
            Map.entry("lifegraph-importance-v1", "a102c1bcd7b1c9723706cfa4df38720d79d87642927e01477572595a51eb447c"),
            Map.entry("lifegraph-memory-relation-v1", "20111b26094b7cb3e477b2234dc47eb41a64c8bcba2c76529746a13e1ef5dc95"),
            Map.entry("memory-lifecycle-v1", "d0554c2476219489fc09e9993441e0d9ff9726cc239d648c5c8163c1fc227429"));

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void validatesAllEightReportsAndWritesTheUnifiedQualityGateReport() throws Exception {
        List<QualityGateAggregateReport.SuiteSummary> summaries = new ArrayList<>();
        for (SuiteContractSpec contract : CONTRACTS) {
            summaries.add(validateReport(contract));
        }

        QualityGateAggregateReport.write(REPORT_PATH, summaries);
        assertTrue(Files.exists(REPORT_PATH), "AGGREGATE_REPORT_MISSING");
        assertEquals(8, summaries.size(), "AGGREGATE_SUITE_COUNT");
        JsonNode aggregate = objectMapper.readTree(REPORT_PATH.toFile());
        int totalAssertions = summaries.stream()
                .mapToInt(QualityGateAggregateReport.SuiteSummary::assertionCount)
                .sum();
        assertEquals(8, aggregate.at("/summary/caseCount").asInt(), "AGGREGATE_REPORT_CASE_COUNT");
        assertEquals(8, aggregate.at("/summary/passedCaseCount").asInt(),
                "AGGREGATE_REPORT_PASSED_CASE_COUNT");
        assertEquals(0, aggregate.at("/summary/failedCaseCount").asInt(),
                "AGGREGATE_REPORT_FAILED_CASE_COUNT");
        assertEquals(totalAssertions, aggregate.at("/summary/assertionCount").asInt(),
                "AGGREGATE_REPORT_ASSERTION_COUNT");
        assertEquals(totalAssertions, aggregate.at("/summary/passedAssertionCount").asInt(),
                "AGGREGATE_REPORT_PASSED_ASSERTION_COUNT");
        assertEquals("PASS", aggregate.at("/summary/status").asText(), "AGGREGATE_REPORT_STATUS");
        assertTrue(summaries.stream().allMatch(summary -> "PASS".equals(summary.status())),
                "AGGREGATE_SUITE_STATUS");
        assertTrue(summaries.stream().allMatch(summary -> summary.violationCodes().isEmpty()),
                "AGGREGATE_VIOLATIONS");
        assertLowSensitivityReport();
    }

    private QualityGateAggregateReport.SuiteSummary validateReport(SuiteContractSpec contract)
            throws IOException {
        Path reportPath = EVALUATION_DIR.resolve(contract.reportFile());
        if (!Files.isRegularFile(reportPath)) {
            throw gate("AGGREGATE_REPORT_MISSING");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(reportPath.toFile());
        } catch (IOException exception) {
            throw gate("AGGREGATE_REPORT_INVALID");
        }

        List<ReportCase> reportCases = parseCases(root);
        validateReportRows(reportCases, contract);
        String actualSuiteId = root.path("suiteId").asText(null);
        for (ReportCase reportCase : reportCases) {
            QualityGatePolicy.requirePass(
                    actualSuiteId,
                    List.of(reportCase.asPolicyCase()),
                    new QualityGatePolicy.SuiteContract(
                            contract.suiteId(), Set.of(reportCase.caseId()), 0));
        }
        List<OfflineEvaluationReportWriter.CaseResult> policyCases = groupedPolicyCases(reportCases);
        QualityGatePolicy.requirePass(
                actualSuiteId,
                policyCases,
                new QualityGatePolicy.SuiteContract(
                        contract.suiteId(), contract.caseIds(),
                        contract.minimumAssertionCount()));
        validateRootSummary(root, reportCases);
        validateHonestBoundary(reportCases, contract.boundaryMetric());
        String semanticHash = semanticHash(root);
        if (!semanticHash.equals(EXPECTED_SEMANTIC_HASHES.get(contract.suiteId()))) {
            throw gate("AGGREGATE_SEMANTIC_CHANGED");
        }

        int assertionCount = reportCases.stream().mapToInt(ReportCase::assertionCount).sum();
        int passedAssertionCount = reportCases.stream().mapToInt(ReportCase::passedAssertionCount).sum();
        return new QualityGateAggregateReport.SuiteSummary(
                contract.suiteId(), "PASS", reportCases.size(), assertionCount,
                passedAssertionCount, 0, true, List.of());
    }

    private List<ReportCase> parseCases(JsonNode root) {
        if (root == null || !root.path("cases").isArray() || root.path("cases").isEmpty()) {
            throw gate("AGGREGATE_REPORT_SHAPE");
        }
        List<ReportCase> cases = new ArrayList<>();
        for (JsonNode item : root.path("cases")) {
            if (!item.isObject()) {
                throw gate("AGGREGATE_REPORT_SHAPE");
            }
            String caseId = requiredText(item, "caseId");
            String scenarioId = requiredText(item, "scenarioId");
            String status = requiredText(item, "status");
            String inputVersion = requiredText(item, "inputVersion");
            String expectedVersion = requiredText(item, "expectedVersion");
            int assertionCount = requiredNonNegativeInt(item, "assertionCount");
            int passedAssertionCount = requiredNonNegativeInt(item, "passedAssertionCount");
            List<String> violationCodes = parseViolationCodes(item.path("violationCodes"));
            Map<String, Object> actualSummary = objectMapper.convertValue(
                    item.path("actualSummary"), new TypeReference<Map<String, Object>>() { });
            if (actualSummary == null) {
                throw gate("AGGREGATE_REPORT_SHAPE");
            }
            OfflineEvaluationReportWriter.Versions versions = returnVersions(item);
            cases.add(new ReportCase(caseId, scenarioId, status, inputVersion, expectedVersion,
                    versions, assertionCount, passedAssertionCount,
                    violationCodes, actualSummary));
        }
        return cases;
    }

    private void validateReportRows(List<ReportCase> reportCases, SuiteContractSpec contract) {
        Set<String> expectedRows = contract.rows().stream()
                .map(row -> contractRow(row.caseId(), row.scenarioId()))
                .collect(Collectors.toUnmodifiableSet());
        Set<String> actualRows = reportCases.stream()
                .map(item -> contractRow(item.caseId(), item.scenarioId()))
                .collect(Collectors.toSet());
        Set<String> expectedCaseIds = contract.rows().stream()
                .map(ExpectedRow::caseId)
                .collect(Collectors.toUnmodifiableSet());
        Set<String> actualCaseIds = reportCases.stream()
                .map(ReportCase::caseId)
                .collect(Collectors.toSet());
        Set<String> actualScenarioIds = reportCases.stream()
                .map(ReportCase::scenarioId)
                .collect(Collectors.toSet());
        if (reportCases.size() != expectedRows.size() || !actualRows.equals(expectedRows)) {
            throw gate("AGGREGATE_CASE_ROWS");
        }
        if (!actualCaseIds.equals(expectedCaseIds)) {
            throw gate("AGGREGATE_CASE_IDS");
        }
        Set<String> expectedScenarioIds = contract.rows().stream()
                .map(ExpectedRow::scenarioId)
                .collect(Collectors.toUnmodifiableSet());
        if (!actualScenarioIds.equals(expectedScenarioIds)) {
            throw gate("AGGREGATE_SCENARIO_IDS");
        }
        if (reportCases.stream().anyMatch(item -> !item.violationCodes().isEmpty())) {
            throw gate("AGGREGATE_VIOLATIONS");
        }
        if (!reportCases.stream().allMatch(item -> "PASS".equals(item.status()))) {
            throw gate("AGGREGATE_CASE_STATUS");
        }
    }

    private void validateRootSummary(JsonNode root, List<ReportCase> reportCases) {
        JsonNode summary = root.path("summary");
        int assertionCount = reportCases.stream().mapToInt(ReportCase::assertionCount).sum();
        int passedAssertionCount = reportCases.stream().mapToInt(ReportCase::passedAssertionCount).sum();
        if (!summary.isObject()
                || !"PASS".equals(summary.path("status").asText())
                || summary.path("caseCount").asInt(-1) != reportCases.size()
                || summary.path("passedCaseCount").asInt(-1) != reportCases.size()
                || summary.path("failedCaseCount").asInt(-1) != 0
                || summary.path("assertionCount").asInt(-1) != assertionCount
                || summary.path("passedAssertionCount").asInt(-1) != passedAssertionCount) {
            throw gate("AGGREGATE_REPORT_SUMMARY");
        }
    }

    private List<OfflineEvaluationReportWriter.CaseResult> groupedPolicyCases(
            List<ReportCase> reportCases) {
        Map<String, List<ReportCase>> byCaseId = reportCases.stream()
                .collect(Collectors.groupingBy(ReportCase::caseId, LinkedHashMap::new,
                        Collectors.toList()));
        return byCaseId.values().stream().map(rows -> {
            ReportCase first = rows.get(0);
            int assertionCount = rows.stream().mapToInt(ReportCase::assertionCount).sum();
            int passedAssertionCount = rows.stream().mapToInt(ReportCase::passedAssertionCount).sum();
            List<String> violationCodes = rows.stream()
                    .flatMap(row -> row.violationCodes().stream())
                    .distinct()
                    .toList();
            return new OfflineEvaluationReportWriter.CaseResult(
                    first.caseId(), first.caseId() + "-SUMMARY", first.status(),
                    first.inputVersion(), first.expectedVersion(), first.versions(),
                    assertionCount, passedAssertionCount, violationCodes,
                    Map.of("scenarioCount", rows.size()));
        }).toList();
    }

    private void validateHonestBoundary(List<ReportCase> reportCases, String boundaryMetric) {
        if ("NONE".equals(boundaryMetric)) {
            return;
        }
        for (ReportCase reportCase : reportCases) {
            String metric = "CHAT_SEMANTIC_SCORE".equals(boundaryMetric)
                    ? "semanticModelScoreAvailable" : "acceptanceRateAvailable";
            if (QualityGatePolicy.booleanMetric(reportCase.actualSummary(), metric)) {
                throw gate("AGGREGATE_HONEST_BOUNDARY");
            }
        }
    }

    private void assertLowSensitivityReport() throws IOException {
        JsonNode report = objectMapper.readTree(REPORT_PATH.toFile());
        for (JsonNode result : report.path("cases")) {
            assertEquals(EXPECTED_PROMPT, result.at("/versions/prompt"),
                    "AGGREGATE_PROMPT_VERSION");
        }
        JsonNode reportWithoutPrompt = report.deepCopy();
        for (JsonNode result : reportWithoutPrompt.path("cases")) {
            if (result.isObject() && result.path("versions").isObject()) {
                ((ObjectNode) result.path("versions")).remove("prompt");
            }
        }
        String reportText = reportWithoutPrompt.toString().toLowerCase(Locale.ROOT);
        for (String forbidden : List.of(
                "evidence-token-", "rawtext", "plaincontent", "toolarguments", "toolresult",
                "secret", "password", "query", "response")) {
            assertFalse(reportText.contains(forbidden), "AGGREGATE_LOW_SENSITIVITY");
        }
    }

    private String semanticHash(JsonNode root) {
        JsonNode canonical = canonicalize(root, true);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                output.append(String.format(Locale.ROOT, "%02x", value));
            }
            return output.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw gate("AGGREGATE_HASH_UNAVAILABLE");
        }
    }

    private JsonNode canonicalize(JsonNode node, boolean root) {
        if (node == null || node.isValueNode()) {
            return node == null ? null : node.deepCopy();
        }
        if (node.isArray()) {
            ArrayNode array = JsonNodeFactory.instance.arrayNode();
            for (JsonNode child : node) {
                array.add(canonicalize(child, false));
            }
            return array;
        }
        ObjectNode object = JsonNodeFactory.instance.objectNode();
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        names.sort(Comparator.naturalOrder());
        for (String name : names) {
            if (root && "generatedAt".equals(name)) {
                continue;
            }
            object.set(name, canonicalize(node.get(name), false));
        }
        return object;
    }

    private List<String> parseViolationCodes(JsonNode node) {
        if (!node.isArray()) {
            throw gate("AGGREGATE_REPORT_SHAPE");
        }
        List<String> codes = new ArrayList<>();
        for (JsonNode code : node) {
            if (!code.isTextual() || code.asText().isBlank()
                    || !code.asText().matches("[A-Z][A-Z0-9_]*")) {
                throw gate("AGGREGATE_REPORT_SHAPE");
            }
            codes.add(code.asText());
        }
        return codes;
    }

    private OfflineEvaluationReportWriter.Versions returnVersions(JsonNode item) {
        JsonNode versions = item.path("versions");
        if (!versions.isObject()) {
            throw gate("AGGREGATE_REPORT_SHAPE");
        }
        return new OfflineEvaluationReportWriter.Versions(
                modelVersion(versions.path("model")),
                promptVersion(versions.path("prompt")),
                strategyVersion(versions.path("retrieval")),
                strategyVersion(versions.path("ranking")));
    }

    private OfflineEvaluationReportWriter.ModelVersion modelVersion(JsonNode node) {
        return new OfflineEvaluationReportWriter.ModelVersion(
                requiredText(node, "provider"), requiredText(node, "name"), requiredText(node, "version"));
    }

    private OfflineEvaluationReportWriter.PromptVersion promptVersion(JsonNode node) {
        return new OfflineEvaluationReportWriter.PromptVersion(
                requiredText(node, "key"), requiredText(node, "version"), requiredText(node, "locale"));
    }

    private OfflineEvaluationReportWriter.StrategyVersion strategyVersion(JsonNode node) {
        return new OfflineEvaluationReportWriter.StrategyVersion(
                requiredText(node, "strategy"), requiredText(node, "version"));
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw gate("AGGREGATE_REPORT_SHAPE");
        }
        return value.asText();
    }

    private int requiredNonNegativeInt(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber() || value.intValue() < 0) {
            throw gate("AGGREGATE_REPORT_SHAPE");
        }
        return value.intValue();
    }

    private String contractRow(String caseId, String scenarioId) {
        return caseId + CASE_SEPARATOR + scenarioId;
    }

    private static SuiteContractSpec spec(String suiteId, String reportFile, int minimumAssertionCount,
                                          List<ExpectedRow> rows, String boundaryMetric) {
        return new SuiteContractSpec(suiteId, reportFile, minimumAssertionCount,
                List.copyOf(rows), boundaryMetric);
    }

    private static ExpectedRow row(String caseId, String scenarioId) {
        return new ExpectedRow(caseId, scenarioId);
    }

    private QualityGatePolicy.GateViolation gate(String code) {
        return new QualityGatePolicy.GateViolation(code);
    }

    private record SuiteContractSpec(String suiteId, String reportFile, int minimumAssertionCount,
                                     List<ExpectedRow> rows, String boundaryMetric) {
        private Set<String> caseIds() {
            return rows.stream().map(ExpectedRow::caseId).collect(Collectors.toUnmodifiableSet());
        }

    }

    private record ExpectedRow(String caseId, String scenarioId) {
    }

    private record ReportCase(String caseId, String scenarioId, String status,
                              String inputVersion, String expectedVersion,
                              OfflineEvaluationReportWriter.Versions versions,
                              int assertionCount, int passedAssertionCount,
                              List<String> violationCodes, Map<String, Object> actualSummary) {
        private OfflineEvaluationReportWriter.CaseResult asPolicyCase() {
            return new OfflineEvaluationReportWriter.CaseResult(
                    caseId, scenarioId, status, inputVersion, expectedVersion, versions,
                    assertionCount, passedAssertionCount, violationCodes, actualSummary);
        }
    }
}
