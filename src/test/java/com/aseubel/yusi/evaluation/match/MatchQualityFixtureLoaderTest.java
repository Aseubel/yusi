package com.aseubel.yusi.evaluation.match;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MatchQualityFixtureLoaderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MatchQualityFixtureLoader loader = new MatchQualityFixtureLoader(objectMapper);

    @Test
    void loadsTheVersionedSanitizedMatchingFixture() {
        MatchQualityEvaluationFixture.Suite suite = loader.load();

        assertEquals(1, suite.schemaVersion());
        assertEquals("match-quality-v1", suite.suiteId());
        assertEquals(Set.of("EVAL-MATCH-001"), suite.cases().stream()
                .map(MatchQualityEvaluationFixture.EvaluationCase::caseId)
                .collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of("EVAL-MATCH-001-A", "EVAL-MATCH-001-B", "EVAL-MATCH-001-C"),
                suite.cases().get(0).scenarios().stream()
                        .map(MatchQualityEvaluationFixture.Scenario::scenarioId)
                        .collect(java.util.stream.Collectors.toSet()));

        MatchQualityEvaluationFixture.Scenario recall = scenario(suite, "EVAL-MATCH-001-A");
        assertEquals(List.of("fixture-user-match-target", "fixture-user-match-secondary"),
                recall.recallCandidates());
        assertEquals("fixture-user-match-target", recall.targetProfileKey());
        assertEquals(3, recall.expectedReasonCount());
        assertFalse(recall.acceptanceRateAvailable());

        MatchQualityEvaluationFixture.Scenario lifecycle = scenario(suite, "EVAL-MATCH-001-B");
        assertEquals(List.of("ACCEPT", "ACCEPT", "DEEP_INTERACTION", "DEEP_INTERACTION"),
                lifecycle.actions());

        MatchQualityEvaluationFixture.Scenario negative = scenario(suite, "EVAL-MATCH-001-C");
        assertEquals("REPORT", negative.negativeFeedbackAction());
        assertEquals(0, negative.expectedSubsequentRecommendationCount());
    }

    @Test
    void rejectsUnknownAndSensitiveFixtureFieldsWithoutEchoingValues() {
        for (String field : List.of(
                "profileText", "reason", "letter", "query", "prompt", "tool", "password")) {
            ObjectNode invalid = validFixture();
            scenario(invalid).put(field, "synthetic-forbidden-value");

            MatchQualityFixtureLoader.FixtureValidationException failure =
                    assertThrows(MatchQualityFixtureLoader.FixtureValidationException.class,
                            () -> loader.load(invalid));

            assertEquals("FIXTURE_INVALID", failure.code());
            assertFalse(failure.getMessage().contains("synthetic-forbidden-value"));
        }
    }

    @Test
    void rejectsNonFixtureIdsAndInvalidLifecycleShape() {
        ObjectNode invalidUser = validFixture();
        scenario(invalidUser).put("userId", "real-user-id");
        MatchQualityFixtureLoader.FixtureValidationException userFailure =
                assertThrows(MatchQualityFixtureLoader.FixtureValidationException.class,
                        () -> loader.load(invalidUser));
        assertEquals("FIXTURE_INVALID", userFailure.code());

        ObjectNode invalidActions = validFixture();
        ArrayNode actions = (ArrayNode) invalidActions.withArray("cases").get(0)
                .withArray("scenarios").get(1).get("actions");
        actions.remove(2);
        MatchQualityFixtureLoader.FixtureValidationException actionFailure =
                assertThrows(MatchQualityFixtureLoader.FixtureValidationException.class,
                        () -> loader.load(invalidActions));
        assertEquals("FIXTURE_INVALID", actionFailure.code());
    }

    private MatchQualityEvaluationFixture.Scenario scenario(
            MatchQualityEvaluationFixture.Suite suite, String scenarioId) {
        return suite.cases().get(0).scenarios().stream()
                .filter(item -> scenarioId.equals(item.scenarioId()))
                .findFirst()
                .orElseThrow();
    }

    private ObjectNode scenario(ObjectNode root) {
        return (ObjectNode) root.withArray("cases").get(0).withArray("scenarios").get(0);
    }

    private ObjectNode validFixture() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", 1);
        root.put("suiteId", "match-quality-v1");
        ObjectNode evaluationCase = objectMapper.createObjectNode();
        evaluationCase.put("caseId", "EVAL-MATCH-001");
        ArrayNode scenarios = objectMapper.createArrayNode();
        scenarios.add(scenarioNode("EVAL-MATCH-001-A", "fixture-user-match-a",
                List.of("fixture-user-match-b", "fixture-user-match-c"),
                "fixture-user-match-b", 3, List.of(), null, 0));
        scenarios.add(scenarioNode("EVAL-MATCH-001-B", "fixture-user-match-a",
                List.of(), null, 0,
                List.of("ACCEPT", "ACCEPT", "DEEP_INTERACTION", "DEEP_INTERACTION"), null, 0));
        scenarios.add(scenarioNode("EVAL-MATCH-001-C", "fixture-user-match-negative",
                List.of(), null, 0, List.of(), "REPORT", 0));
        evaluationCase.set("scenarios", scenarios);
        root.set("cases", objectMapper.createArrayNode().add(evaluationCase));
        return root;
    }

    private ObjectNode scenarioNode(String scenarioId, String userId, List<String> recallCandidates,
                                    String targetProfileKey, int expectedReasonCount, List<String> actions,
                                    String negativeFeedbackAction, int expectedSubsequentRecommendationCount) {
        ObjectNode scenario = objectMapper.createObjectNode();
        scenario.put("scenarioId", scenarioId);
        scenario.put("userId", userId);
        scenario.set("recallCandidates", objectMapper.valueToTree(recallCandidates));
        if (targetProfileKey == null) {
            scenario.putNull("targetProfileKey");
        } else {
            scenario.put("targetProfileKey", targetProfileKey);
        }
        scenario.put("expectedReasonCount", expectedReasonCount);
        scenario.set("actions", objectMapper.valueToTree(actions));
        if (negativeFeedbackAction == null) {
            scenario.putNull("negativeFeedbackAction");
        } else {
            scenario.put("negativeFeedbackAction", negativeFeedbackAction);
        }
        scenario.put("expectedSubsequentRecommendationCount", expectedSubsequentRecommendationCount);
        scenario.put("acceptanceRateAvailable", false);
        return scenario;
    }
}
