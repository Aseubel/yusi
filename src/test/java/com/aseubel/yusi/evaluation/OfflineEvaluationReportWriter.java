package com.aseubel.yusi.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared low-sensitivity envelope for deterministic offline evaluation suites. */
public final class OfflineEvaluationReportWriter {

    public static final int SCHEMA_VERSION = 1;
    public static final String RUNNER_VERSION = "v1";

    private OfflineEvaluationReportWriter() {
    }

    public record ModelVersion(String provider, String name, String version) {
    }

    public record PromptVersion(String key, String version, String locale) {
    }

    public record StrategyVersion(String strategy, String version) {
    }

    public record Versions(ModelVersion model, PromptVersion prompt,
                           StrategyVersion retrieval, StrategyVersion ranking) {
        public static Versions fixtureBaseline() {
            return new Versions(
                    new ModelVersion("fixture", "none", "fixture-v1"),
                    new PromptVersion("fixture", "fixture-v1", "zh-CN"),
                    new StrategyVersion("not_applicable", "fixture-v1"),
                    new StrategyVersion("not_applicable", "fixture-v1"));
        }
    }

    public record CaseResult(String caseId, String scenarioId, String status,
                             String inputVersion, String expectedVersion, Versions versions,
                             int assertionCount, int passedAssertionCount,
                             List<String> violationCodes,
                             Map<String, Object> actualSummary) {
        public CaseResult {
            violationCodes = violationCodes == null
                    ? List.of()
                    : violationCodes.stream().sorted().toList();
            actualSummary = actualSummary == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(actualSummary));
        }
    }

    public record Summary(int caseCount, int passedCaseCount, int failedCaseCount,
                          int assertionCount, int passedAssertionCount, String status) {
    }

    public record Report(int schemaVersion, String suiteId, String runnerVersion,
                         Instant generatedAt, List<CaseResult> cases, Summary summary) {
    }

    public static void write(Path path, String suiteId, List<CaseResult> inputCases)
            throws IOException {
        List<CaseResult> cases = inputCases == null ? List.of() : inputCases.stream()
                .sorted(Comparator.comparing(CaseResult::caseId,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(CaseResult::scenarioId,
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
        int passedCaseCount = (int) cases.stream()
                .filter(item -> "PASS".equals(item.status()))
                .count();
        int assertionCount = cases.stream().mapToInt(CaseResult::assertionCount).sum();
        int passedAssertionCount = cases.stream().mapToInt(CaseResult::passedAssertionCount).sum();
        Summary summary = new Summary(
                cases.size(),
                passedCaseCount,
                cases.size() - passedCaseCount,
                assertionCount,
                passedAssertionCount,
                !cases.isEmpty() && passedCaseCount == cases.size() ? "PASS" : "FAIL");
        Report report = new Report(SCHEMA_VERSION, suiteId, RUNNER_VERSION,
                Instant.now(), cases, summary);

        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), report);
    }
}
