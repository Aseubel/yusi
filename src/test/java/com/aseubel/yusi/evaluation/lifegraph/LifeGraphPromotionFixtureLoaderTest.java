package com.aseubel.yusi.evaluation.lifegraph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LifeGraphPromotionFixtureLoaderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LifeGraphPromotionFixtureLoader loader =
            new LifeGraphPromotionFixtureLoader(objectMapper);

    @Test
    void loadsTheSanitizedPromotionSuite() throws IOException {
        LifeGraphPromotionEvaluationFixture.Suite suite = loader.load(validFixture());

        assertEquals("lifegraph-promotion-v1", suite.suiteId());
        assertEquals(1, suite.cases().size());
        assertEquals("EVAL-MEM-003", suite.cases().get(0).caseId());
        assertEquals(3, suite.cases().get(0).scenarios().size());
    }

    @Test
    void rejectsForbiddenFixtureFieldsWithoutEchoingTheirValue() throws IOException {
        assertInvalid(jsonWithField("rawText", "synthetic-sensitive-value"));
    }

    @Test
    void rejectsNonFixtureIdsAndNonTokenEvidence() throws IOException {
        assertInvalid(jsonWithField("userId", "real-user-value"));
        assertInvalid(jsonWithField("evidenceSnippet", "not-an-evidence-token"));
    }

    @Test
    void rejectsUnknownFieldsAndInvalidPromotionShape() throws IOException {
        assertInvalid(jsonWithField("unknownField", "fixture-value"));
        assertInvalid(jsonWithMissingRequiredRelationEndpoint());
    }

    private JsonNode validFixture() throws IOException {
        try (InputStream input = new ClassPathResource(
                LifeGraphPromotionFixtureLoader.DEFAULT_RESOURCE).getInputStream()) {
            return objectMapper.readTree(input);
        }
    }

    private void assertInvalid(JsonNode root) {
        LifeGraphPromotionFixtureLoader.FixtureValidationException failure =
                assertThrows(LifeGraphPromotionFixtureLoader.FixtureValidationException.class,
                        () -> loader.load(root));
        assertEquals("FIXTURE_INVALID", failure.code());
    }

    private JsonNode jsonWithField(String field, String value) throws IOException {
        ObjectNode root = (ObjectNode) validFixture().deepCopy();
        switch (field) {
            case "userId" -> ((ObjectNode) root.at(
                    "/cases/0/scenarios/0")).put(field, value);
            case "evidenceSnippet" -> ((ObjectNode) root.at(
                    "/cases/0/scenarios/0/extraction/relations/0")).put(field, value);
            default -> root.put(field, value);
        }
        return root;
    }

    private JsonNode jsonWithMissingRequiredRelationEndpoint() throws IOException {
        ObjectNode root = (ObjectNode) validFixture().deepCopy();
        ((ObjectNode) root.at(
                "/cases/0/scenarios/0/extraction/relations/0")).remove("target");
        return root;
    }
}
