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
        ObjectNode invalid = objectMapper.createObjectNode();
        invalid.put("schemaVersion", 1);
        invalid.put("suiteId", "lifegraph-timeline-v1");
        invalid.put("userId", "real-user-id");

        LifeGraphTimelineFixtureLoader.FixtureValidationException failure =
                assertThrows(LifeGraphTimelineFixtureLoader.FixtureValidationException.class,
                        () -> loader.load(invalid));

        assertEquals("FIXTURE_INVALID", failure.code());
    }
}
