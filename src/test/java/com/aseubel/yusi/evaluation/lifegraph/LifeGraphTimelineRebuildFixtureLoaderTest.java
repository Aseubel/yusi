package com.aseubel.yusi.evaluation.lifegraph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LifeGraphTimelineRebuildFixtureLoaderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LifeGraphTimelineRebuildFixtureLoader loader =
            new LifeGraphTimelineRebuildFixtureLoader(objectMapper);

    @Test
    void loadsTheVersionedSanitizedRevisionFixture() {
        LifeGraphTimelineRebuildEvaluationFixture.Suite suite = loader.load();

        assertEquals(1, suite.schemaVersion());
        assertEquals("lifegraph-timeline-rebuild-v1", suite.suiteId());
        assertEquals(Set.of("EVAL-TIMELINE-002"),
                suite.cases().stream()
                        .map(LifeGraphTimelineRebuildEvaluationFixture.EvaluationCase::caseId)
                        .collect(java.util.stream.Collectors.toSet()));

        LifeGraphTimelineRebuildEvaluationFixture.Scenario scenario = suite.cases().get(0).scenarios().get(0);
        assertEquals("EVAL-TIMELINE-002-A", scenario.scenarioId());
        assertEquals("DIARY", scenario.sources().get(0).sourceType());
        assertEquals(3, scenario.sources().get(0).events().size());
        assertEquals(java.util.List.of("UPSERT", "UPSERT", "DELETE"),
                scenario.sources().get(0).events().stream()
                        .map(LifeGraphTimelineRebuildEvaluationFixture.Event::operation)
                        .toList());
        assertEquals(java.util.List.of(1L, 2L, 2L),
                scenario.sources().get(0).events().stream()
                        .map(LifeGraphTimelineRebuildEvaluationFixture.Event::sourceRevision)
                        .toList());
        assertEquals(2, scenario.expected().afterRevisionEntityCount());
        assertEquals(1, scenario.expected().afterRevisionRelationCount());
        assertEquals(1, scenario.expected().afterRevisionEntityEvidenceCount());
        assertEquals(1, scenario.expected().afterRevisionRelationEvidenceCount());
        assertEquals(1, scenario.expected().afterRevisionMentionCount());
    }

    @Test
    void rejectsUnknownFixtureFieldsWithoutEchoingSensitiveValues() {
        ObjectNode invalid = validFixture();
        invalid.put("unexpectedField", "evidence-token-unknown-field");

        LifeGraphTimelineRebuildFixtureLoader.FixtureValidationException failure =
                assertThrows(LifeGraphTimelineRebuildFixtureLoader.FixtureValidationException.class,
                        () -> loader.load(invalid));

        assertEquals("FIXTURE_INVALID", failure.code());
        assertFalse(failure.getMessage().contains("evidence-token-unknown-field"));
    }

    @Test
    void rejectsNonFixtureIdsAndMissingEventDates() {
        ObjectNode invalidId = validFixture();
        scenario(invalidId).put("userId", "real-user-id");

        LifeGraphTimelineRebuildFixtureLoader.FixtureValidationException idFailure =
                assertThrows(LifeGraphTimelineRebuildFixtureLoader.FixtureValidationException.class,
                        () -> loader.load(invalidId));
        assertEquals("FIXTURE_INVALID", idFailure.code());

        ObjectNode invalidDate = validFixture();
        ((ObjectNode) events(invalidDate).get(0)).putNull("entryDate");

        LifeGraphTimelineRebuildFixtureLoader.FixtureValidationException dateFailure =
                assertThrows(LifeGraphTimelineRebuildFixtureLoader.FixtureValidationException.class,
                        () -> loader.load(invalidDate));
        assertEquals("FIXTURE_INVALID", dateFailure.code());
    }

    @Test
    void rejectsEvidenceProseAndUnexpectedExtractionFields() {
        ObjectNode invalidEvidence = validFixture();
        ObjectNode mention = (ObjectNode) ((ArrayNode) extraction(invalidEvidence).get("mentions")).get(0);
        mention.put("snippet", "synthetic evidence prose");

        LifeGraphTimelineRebuildFixtureLoader.FixtureValidationException evidenceFailure =
                assertThrows(LifeGraphTimelineRebuildFixtureLoader.FixtureValidationException.class,
                        () -> loader.load(invalidEvidence));
        assertEquals("FIXTURE_INVALID", evidenceFailure.code());

        ObjectNode invalidExtraction = validFixture();
        extraction(invalidExtraction).put("unexpectedExtractionField", "synthetic-token");

        LifeGraphTimelineRebuildFixtureLoader.FixtureValidationException extractionFailure =
                assertThrows(LifeGraphTimelineRebuildFixtureLoader.FixtureValidationException.class,
                        () -> loader.load(invalidExtraction));
        assertEquals("FIXTURE_INVALID", extractionFailure.code());
    }

    private ObjectNode validFixture() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", 1);
        root.put("suiteId", "lifegraph-timeline-rebuild-v1");

        ObjectNode evaluationCase = objectMapper.createObjectNode();
        evaluationCase.put("caseId", "EVAL-TIMELINE-002");
        ObjectNode scenario = objectMapper.createObjectNode();
        scenario.put("scenarioId", "EVAL-TIMELINE-002-A");
        scenario.put("userId", "fixture-user-timeline-rebuild");

        ObjectNode source = objectMapper.createObjectNode();
        source.put("sourceType", "DIARY");
        source.put("sourceId", "fixture-diary-timeline-rebuild");
        ArrayNode eventNodes = objectMapper.createArrayNode();
        eventNodes.add(event("UPSERT", 1, "2026-07-01", "fixture-rebuild-event-old",
                "evidence-token-rebuild-old"));
        eventNodes.add(event("UPSERT", 2, "2026-08-11", "fixture-rebuild-event-new",
                "evidence-token-rebuild-new"));
        ObjectNode delete = objectMapper.createObjectNode();
        delete.put("operation", "DELETE");
        delete.put("sourceRevision", 2);
        delete.put("entryDate", "2026-08-11");
        delete.set("extraction", objectMapper.createObjectNode()
                .set("entities", objectMapper.createArrayNode()));
        ((ObjectNode) delete.get("extraction")).set("relations", objectMapper.createArrayNode());
        ((ObjectNode) delete.get("extraction")).set("mentions", objectMapper.createArrayNode());
        eventNodes.add(delete);
        source.set("events", eventNodes);
        scenario.set("sources", objectMapper.createArrayNode().add(source));

        ObjectNode expected = objectMapper.createObjectNode();
        expected.put("beforeRevisionNodeCount", 1);
        expected.put("afterRevisionOldResidualCount", 0);
        expected.put("afterRevisionNewNodeCount", 1);
        expected.put("afterDeleteTimelineNodeCount", 0);
        expected.put("sourceResidualCount", 0);
        expected.put("afterRevisionEntityCount", 2);
        expected.put("afterRevisionRelationCount", 1);
        expected.put("afterRevisionEntityEvidenceCount", 1);
        expected.put("afterRevisionRelationEvidenceCount", 1);
        expected.put("afterRevisionMentionCount", 1);
        expected.put("oldEventKey", "fixture-rebuild-event-old");
        expected.put("newEventKey", "fixture-rebuild-event-new");
        scenario.set("expected", expected);

        evaluationCase.set("scenarios", objectMapper.createArrayNode().add(scenario));
        root.set("cases", objectMapper.createArrayNode().add(evaluationCase));
        return root;
    }

    private ObjectNode event(String operation, long revision, String entryDate,
                             String eventKey, String evidenceToken) {
        ObjectNode event = objectMapper.createObjectNode();
        event.put("operation", operation);
        event.put("sourceRevision", revision);
        event.put("entryDate", entryDate);

        ObjectNode extraction = objectMapper.createObjectNode();
        ArrayNode entities = objectMapper.createArrayNode();
        entities.add(objectMapper.createObjectNode()
                .put("type", "User")
                .put("displayName", "__USER__")
                .put("nameNorm", "__USER__"));
        entities.add(objectMapper.createObjectNode()
                .put("type", "Event")
                .put("displayName", eventKey)
                .put("nameNorm", eventKey)
                .put("importance", 0.9)
                .put("confidence", 0.9));
        extraction.set("entities", entities);
        extraction.set("relations", objectMapper.createArrayNode().add(objectMapper.createObjectNode()
                .put("source", "__USER__")
                .put("target", eventKey)
                .put("type", "PARTICIPATED_IN")
                .put("confidence", 0.9)
                .put("evidenceSnippet", evidenceToken)));
        extraction.set("mentions", objectMapper.createArrayNode().add(objectMapper.createObjectNode()
                .put("entity", eventKey)
                .put("snippet", evidenceToken)));
        event.set("extraction", extraction);
        return event;
    }

    private ObjectNode scenario(ObjectNode root) {
        return (ObjectNode) ((ArrayNode) root.withArray("cases")).get(0)
                .withArray("scenarios").get(0);
    }

    private ArrayNode events(ObjectNode root) {
        return ((ObjectNode) scenario(root).withArray("sources").get(0)).withArray("events");
    }

    private ObjectNode extraction(ObjectNode root) {
        return (ObjectNode) events(root).get(0).get("extraction");
    }
}
