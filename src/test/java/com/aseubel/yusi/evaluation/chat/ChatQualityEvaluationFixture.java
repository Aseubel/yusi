package com.aseubel.yusi.evaluation.chat;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Set;

/** Typed, low-sensitivity input for the deterministic chat quality replay. */
public final class ChatQualityEvaluationFixture {

    private ChatQualityEvaluationFixture() {
    }

    public record Suite(int schemaVersion, String suiteId, List<EvaluationCase> cases) {
    }

    public record EvaluationCase(String caseId, List<Scenario> scenarios) {
    }

    public record Scenario(String scenarioId, String userId, String inputKind,
                           Set<String> availableMemoryKeys, Set<String> restrictedMemoryKeys,
                           Set<String> allowedTools, Set<String> expectedPolicyCodes,
                           JsonNode expected) {
    }
}
