package com.aseubel.yusi.evaluation.lifegraph;

import com.aseubel.yusi.evaluation.OfflineEvaluationReportWriter;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stable, low-sensitivity output contract for the revision rebuild replay. */
public final class LifeGraphTimelineRebuildEvaluationReport {

    public static final int SCHEMA_VERSION = 1;
    public static final String SUITE_ID = "lifegraph-timeline-rebuild-v1";
    public static final String RUNNER_VERSION = "v1";

    private LifeGraphTimelineRebuildEvaluationReport() {
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

    public record ActualSummary(int beforeRevisionNodeCount,
                                int afterRevisionOldResidualCount,
                                int afterRevisionNewNodeCount,
                                int afterDeleteTimelineNodeCount,
                                int sourceResidualCount) {
    }

    public record CaseResult(String caseId, String scenarioId, String status,
                             String inputVersion, String expectedVersion, Versions versions,
                             int assertionCount, int passedAssertionCount,
                             List<String> violationCodes, ActualSummary actualSummary) {
        public CaseResult {
            violationCodes = violationCodes == null
                    ? List.of()
                    : violationCodes.stream().sorted().toList();
        }
    }

    public record Summary(int caseCount, int passedCaseCount, int failedCaseCount,
                          int assertionCount, int passedAssertionCount, String status) {
    }

    public record Report(int schemaVersion, String suiteId, String runnerVersion,
                         Instant generatedAt, List<CaseResult> cases, Summary summary) {
    }

    public static void write(Path path, List<CaseResult> inputCases) throws IOException {
        List<OfflineEvaluationReportWriter.CaseResult> cases = inputCases == null ? List.of()
                : inputCases.stream().map(LifeGraphTimelineRebuildEvaluationReport::toGenericCase).toList();
        OfflineEvaluationReportWriter.write(path, SUITE_ID, cases);
    }

    public static OfflineEvaluationReportWriter.CaseResult toGenericCase(CaseResult result) {
        return new OfflineEvaluationReportWriter.CaseResult(
                result.caseId(),
                result.scenarioId(),
                result.status(),
                result.inputVersion(),
                result.expectedVersion(),
                toGenericVersions(result.versions()),
                result.assertionCount(),
                result.passedAssertionCount(),
                result.violationCodes(),
                toSummaryMap(result.actualSummary()));
    }

    private static OfflineEvaluationReportWriter.Versions toGenericVersions(Versions versions) {
        if (versions == null) {
            return OfflineEvaluationReportWriter.Versions.fixtureBaseline();
        }
        return new OfflineEvaluationReportWriter.Versions(
                new OfflineEvaluationReportWriter.ModelVersion(
                        versions.model().provider(), versions.model().name(), versions.model().version()),
                new OfflineEvaluationReportWriter.PromptVersion(
                        versions.prompt().key(), versions.prompt().version(), versions.prompt().locale()),
                new OfflineEvaluationReportWriter.StrategyVersion(
                        versions.retrieval().strategy(), versions.retrieval().version()),
                new OfflineEvaluationReportWriter.StrategyVersion(
                        versions.ranking().strategy(), versions.ranking().version()));
    }

    private static Map<String, Object> toSummaryMap(ActualSummary summary) {
        if (summary == null) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("beforeRevisionNodeCount", summary.beforeRevisionNodeCount());
        values.put("afterRevisionOldResidualCount", summary.afterRevisionOldResidualCount());
        values.put("afterRevisionNewNodeCount", summary.afterRevisionNewNodeCount());
        values.put("afterDeleteTimelineNodeCount", summary.afterDeleteTimelineNodeCount());
        values.put("sourceResidualCount", summary.sourceResidualCount());
        return values;
    }
}
