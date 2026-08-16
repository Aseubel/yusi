package com.aseubel.yusi.evaluation.memory;

import java.util.List;
import java.util.Set;

/** Typed, low-sensitivity input contract for the memory lifecycle replay. */
public final class MemoryLifecycleEvaluationFixture {

    private MemoryLifecycleEvaluationFixture() {
    }

    public record Suite(int schemaVersion, String suiteId, List<EvaluationCase> cases) {
    }

    public record EvaluationCase(String caseId, List<Scenario> scenarios) {
    }

    public record Scenario(String scenarioId, String userId, List<MemoryRecord> memories,
                           List<VectorCandidate> vectorCandidates, String positiveMemoryKey,
                           String deleteMemoryKey, boolean vectorDeleteFails, Expected expected) {
    }

    public record MemoryRecord(String memoryKey, String ownerUserId, String summaryToken,
                               String lifecycle, boolean matchAllowed, String mergedIntoKey) {
    }

    public record VectorCandidate(String memoryKey, String ownerUserId, String summaryToken) {
    }

    public record Expected(Set<String> availableKeys, Set<String> matchableKeys,
                           Set<String> retrievedKeys, Set<String> restrictedKeys,
                           String retainedUserMemoryKey, String otherUserId,
                           String otherUserMemoryKey) {
    }
}
