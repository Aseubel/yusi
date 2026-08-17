package com.aseubel.yusi.evaluation.lifegraph;

import com.aseubel.yusi.service.lifegraph.dto.LifeGraphExtractionResult;

import java.util.List;
import java.util.Set;

/** Typed, low-sensitivity input contract for the LifeGraph promotion replay. */
public final class LifeGraphPromotionEvaluationFixture {

    private LifeGraphPromotionEvaluationFixture() {
    }

    public record Suite(int schemaVersion, String suiteId, List<EvaluationCase> cases) {
    }

    public record EvaluationCase(String caseId, List<Scenario> scenarios) {
    }

    public record Scenario(
            String scenarioId,
            String userId,
            String sourceId,
            List<String> confirmedImportantPersonKeys,
            LifeGraphExtractionResult extraction,
            Expected expected) {
    }

    public record Expected(
            Set<String> acceptedEntityKeys,
            Set<String> acceptedRelationKeys,
            Set<String> rejectedRelationKeys,
            int sourceEntityEvidenceCount,
            int sourceRelationEvidenceCount,
            int sourceMentionCount) {
    }
}
