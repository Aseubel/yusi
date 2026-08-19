package com.aseubel.yusi.evaluation.match;

import java.util.List;

/** Typed, low-sensitivity input contract for the offline matching replay. */
public final class MatchQualityEvaluationFixture {

    private MatchQualityEvaluationFixture() {
    }

    public record Suite(int schemaVersion, String suiteId, List<EvaluationCase> cases) {
    }

    public record EvaluationCase(String caseId, List<Scenario> scenarios) {
    }

    public record Scenario(String scenarioId,
                           String userId,
                           List<String> participantUserIds,
                           List<String> recallCandidates,
                           String targetProfileKey,
                           int expectedReasonCount,
                           List<String> actions,
                           String negativeFeedbackAction,
                           int expectedSubsequentRecommendationCount,
                           boolean acceptanceRateAvailable) {
    }
}
