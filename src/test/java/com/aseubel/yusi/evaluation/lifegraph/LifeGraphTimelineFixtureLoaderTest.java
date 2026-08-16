package com.aseubel.yusi.evaluation.lifegraph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LifeGraphTimelineFixtureLoaderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LifeGraphTimelineFixtureLoader loader =
            new LifeGraphTimelineFixtureLoader(objectMapper);

    @Test
    void loadsTheVersionedSanitizedFixture() {
        LifeGraphTimelineEvaluationFixture.Suite suite = loader.load();

        assertEquals(1, suite.schemaVersion());
        assertEquals("lifegraph-timeline-v1", suite.suiteId());
    }

    @Test
    void rejectsForbiddenFixtureFieldsWithoutEchoingSensitiveValues() throws Exception {
        ObjectNode invalid = objectMapper.createObjectNode();
        invalid.put("schemaVersion", 1);
        invalid.put("suiteId", "lifegraph-timeline-v1");
        invalid.put("rawText", "synthetic-but-forbidden");

        LifeGraphTimelineFixtureLoader.FixtureValidationException failure =
                assertThrows(LifeGraphTimelineFixtureLoader.FixtureValidationException.class,
                        () -> loader.load(invalid));

        assertEquals("FIXTURE_INVALID", failure.code());
    }

    @Test
    void rejectsNonFixtureUserIds() throws Exception {
        ObjectNode invalid = minimalFixture();
        ObjectNode scenario = (ObjectNode) invalid.withArray("cases").get(0)
                .withArray("scenarios").get(0);
        scenario.put("userId", "real-user-id");

        LifeGraphTimelineFixtureLoader.FixtureValidationException failure =
                assertThrows(LifeGraphTimelineFixtureLoader.FixtureValidationException.class,
                        () -> loader.load(invalid));

        assertEquals("FIXTURE_INVALID", failure.code());
    }

    private ObjectNode minimalFixture() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", 1);
        root.put("suiteId", "lifegraph-timeline-v1");
        ObjectNode evaluationCase = objectMapper.createObjectNode();
        evaluationCase.put("caseId", "EVAL-MEM-002");
        ObjectNode scenario = objectMapper.createObjectNode();
        scenario.put("scenarioId", "EVAL-MEM-002-A");
        scenario.put("userId", "fixture-user-test");
        ObjectNode source = objectMapper.createObjectNode();
        source.put("sourceType", "DIARY");
        source.put("sourceId", "fixture-diary-test");
        ObjectNode event = objectMapper.createObjectNode();
        event.put("operation", "UPSERT");
        event.put("sourceRevision", 1);
        event.put("entryDate", "2026-08-16");
        event.set("extraction", objectMapper.createObjectNode());
        source.set("events", objectMapper.createArrayNode().add(event));
        scenario.set("sources", objectMapper.createArrayNode().add(source));
        scenario.set("expected", objectMapper.createObjectNode());
        evaluationCase.set("scenarios", objectMapper.createArrayNode().add(scenario));
        root.set("cases", objectMapper.createArrayNode().add(evaluationCase));
        return root;
    }
}
