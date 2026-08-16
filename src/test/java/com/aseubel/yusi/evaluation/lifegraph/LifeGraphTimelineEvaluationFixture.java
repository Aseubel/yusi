package com.aseubel.yusi.evaluation.lifegraph;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/** Typed, low-sensitivity input contract for the offline LifeGraph replay. */
public final class LifeGraphTimelineEvaluationFixture {

    private LifeGraphTimelineEvaluationFixture() {
    }

    public record Suite(int schemaVersion, String suiteId, List<EvaluationCase> cases) {
    }

    public record EvaluationCase(String caseId, List<Scenario> scenarios) {
    }

    public record Scenario(String scenarioId, String userId, List<Source> sources, JsonNode expected) {
    }

    public record Source(String sourceType, String sourceId, List<Event> events) {
    }

    public record Event(String operation, long sourceRevision, String entryDate, JsonNode extraction) {
    }
}
