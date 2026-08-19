package com.aseubel.yusi.evaluation.lifegraph;

import java.util.List;

/** Typed, low-sensitivity input contract for the revision rebuild replay. */
public final class LifeGraphTimelineRebuildEvaluationFixture {

    private LifeGraphTimelineRebuildEvaluationFixture() {
    }

    public record Suite(int schemaVersion, String suiteId, List<EvaluationCase> cases) {
    }

    public record EvaluationCase(String caseId, List<Scenario> scenarios) {
    }

    public record Scenario(String scenarioId, String userId, List<Source> sources, Expected expected) {
    }

    public record Source(String sourceType, String sourceId, List<Event> events) {
    }

    public record Event(String operation, long sourceRevision, String entryDate, Extraction extraction) {
    }

    public record Extraction(List<ExtractedEntity> entities,
                             List<ExtractedRelation> relations,
                             List<ExtractedMention> mentions) {
    }

    public record ExtractedEntity(String type, String displayName, String nameNorm,
                                  Double importance, Double confidence) {
    }

    public record ExtractedRelation(String source, String target, String type,
                                    Double confidence, String evidenceSnippet) {
    }

    public record ExtractedMention(String entity, String snippet) {
    }

    public record Expected(int beforeRevisionNodeCount,
                           int afterRevisionOldResidualCount,
                           int afterRevisionNewNodeCount,
                           int afterDeleteTimelineNodeCount,
                           int sourceResidualCount,
                           int afterRevisionEntityCount,
                           int afterRevisionRelationCount,
                           int afterRevisionEntityEvidenceCount,
                           int afterRevisionRelationEvidenceCount,
                           int afterRevisionMentionCount,
                           String oldEventKey,
                           String newEventKey) {
    }
}
