package com.aseubel.yusi.evaluation.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryLifecycleFixtureLoaderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MemoryLifecycleFixtureLoader loader =
            new MemoryLifecycleFixtureLoader(objectMapper);

    @Test
    void loadsThreeScenariosWithPositiveAndRestrictedMemoryKeys() {
        MemoryLifecycleEvaluationFixture.Suite suite = loader.load();

        assertEquals(1, suite.schemaVersion());
        assertEquals("memory-lifecycle-v1", suite.suiteId());
        assertEquals(1, suite.cases().size());
        assertEquals(3, suite.cases().getFirst().scenarios().size());
        assertTrue(suite.cases().getFirst().scenarios().stream()
                .allMatch(scenario -> !scenario.positiveMemoryKey().isBlank()
                        && !scenario.expected().restrictedKeys().isEmpty()));
    }

    @Test
    void rejectsNonTokenSummaryWithoutEchoingItsValue() throws Exception {
        ObjectNode invalid = objectMapper.readTree("""
                {
                  "schemaVersion": 1,
                  "suiteId": "memory-lifecycle-v1",
                  "cases": [{
                    "caseId": "EVAL-MEM-001",
                    "scenarios": [{
                      "scenarioId": "EVAL-MEM-001-A",
                      "userId": "fixture-user-test",
                      "memories": [{
                        "memoryKey": "fixture-memory-test",
                        "ownerUserId": "fixture-user-test",
                        "summaryToken": "this-is-not-a-memory-token",
                        "lifecycle": "ACTIVE",
                        "matchAllowed": true
                      }],
                      "vectorCandidates": [{
                        "memoryKey": "fixture-memory-test",
                        "ownerUserId": "fixture-user-test",
                        "summaryToken": "this-is-not-a-memory-token"
                      }],
                      "positiveMemoryKey": "fixture-memory-test",
                      "vectorDeleteFails": false,
                      "expected": {
                        "availableKeys": ["fixture-memory-test"],
                        "matchableKeys": ["fixture-memory-test"],
                        "retrievedKeys": ["fixture-memory-test"],
                        "restrictedKeys": ["fixture-memory-test"],
                        "retainedUserMemoryKey": "fixture-memory-test"
                      }
                    }]
                  }]
                }
                """).deepCopy();

        MemoryLifecycleFixtureLoader.FixtureValidationException failure =
                assertThrows(MemoryLifecycleFixtureLoader.FixtureValidationException.class,
                        () -> loader.load(invalid));

        assertEquals("FIXTURE_INVALID", failure.code());
        assertFalse(failure.getMessage().contains("this-is-not-a-memory-token"));
    }

    @Test
    void rejectsNonFixtureMemoryId() throws Exception {
        ObjectNode invalid = (ObjectNode) objectMapper.readTree(
                getClass().getResourceAsStream("/evaluation/memory-lifecycle-v1-fixtures.json"));
        ObjectNode firstMemory = (ObjectNode) invalid.withArray("cases").get(0)
                .withArray("scenarios").get(0).withArray("memories").get(0);
        firstMemory.put("memoryKey", "real-memory-id");

        MemoryLifecycleFixtureLoader.FixtureValidationException failure =
                assertThrows(MemoryLifecycleFixtureLoader.FixtureValidationException.class,
                        () -> loader.load(invalid));

        assertEquals("FIXTURE_INVALID", failure.code());
    }

    @Test
    void rejectsUnknownLifecycle() throws Exception {
        ObjectNode invalid = (ObjectNode) objectMapper.readTree(
                getClass().getResourceAsStream("/evaluation/memory-lifecycle-v1-fixtures.json"));
        ObjectNode firstMemory = (ObjectNode) invalid.withArray("cases").get(0)
                .withArray("scenarios").get(0).withArray("memories").get(0);
        firstMemory.put("lifecycle", "REMOVED");

        MemoryLifecycleFixtureLoader.FixtureValidationException failure =
                assertThrows(MemoryLifecycleFixtureLoader.FixtureValidationException.class,
                        () -> loader.load(invalid));

        assertEquals("FIXTURE_INVALID", failure.code());
    }

    @Test
    void rejectsDeleteTargetThatIsNotInMemoryRecords() throws Exception {
        ObjectNode invalid = (ObjectNode) objectMapper.readTree(
                getClass().getResourceAsStream("/evaluation/memory-lifecycle-v1-fixtures.json"));
        ObjectNode scenario = (ObjectNode) invalid.withArray("cases").get(0)
                .withArray("scenarios").get(0);
        scenario.put("deleteMemoryKey", "fixture-memory-missing");

        MemoryLifecycleFixtureLoader.FixtureValidationException failure =
                assertThrows(MemoryLifecycleFixtureLoader.FixtureValidationException.class,
                        () -> loader.load(invalid));

        assertEquals("FIXTURE_INVALID", failure.code());
    }
}
