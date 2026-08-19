package com.aseubel.yusi.evaluation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Low-sensitivity report mapper for the cross-suite quality gate. */
public final class QualityGateAggregateReport {

    public static final String SUITE_ID = "quality-gates-aggregate-v1";

    private QualityGateAggregateReport() {
    }

    public record SuiteSummary(String suiteId, String status, int caseCount,
                               int assertionCount, int passedAssertionCount,
                               int violationCount, boolean semanticStable,
                               List<String> violationCodes) {
        public SuiteSummary {
            violationCodes = violationCodes == null ? List.of() : violationCodes.stream().sorted().toList();
        }
    }

    public static void write(Path path, List<SuiteSummary> summaries) throws IOException {
        List<OfflineEvaluationReportWriter.CaseResult> cases = summaries == null ? List.of()
                : summaries.stream().map(QualityGateAggregateReport::toCaseResult).toList();
        OfflineEvaluationReportWriter.write(path, SUITE_ID, cases);
    }

    private static OfflineEvaluationReportWriter.CaseResult toCaseResult(SuiteSummary summary) {
        Map<String, Object> actualSummary = new LinkedHashMap<>();
        actualSummary.put("suiteCaseCount", summary.caseCount());
        actualSummary.put("suiteAssertionCount", summary.assertionCount());
        actualSummary.put("suitePassedAssertionCount", summary.passedAssertionCount());
        actualSummary.put("violationCount", summary.violationCount());
        actualSummary.put("semanticStable", summary.semanticStable());
        return new OfflineEvaluationReportWriter.CaseResult(
                summary.suiteId(), summary.suiteId() + "-SUMMARY", summary.status(),
                "aggregate-v1", "aggregate-v1",
                OfflineEvaluationReportWriter.Versions.fixtureBaseline(),
                summary.assertionCount(), summary.passedAssertionCount(),
                summary.violationCodes(), actualSummary);
    }
}
