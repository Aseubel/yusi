package com.aseubel.yusi.evaluation.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatQualityFixtureLoaderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatQualityFixtureLoader loader = new ChatQualityFixtureLoader(objectMapper);

    @Test
    void loadsTheFourSanitizedChatQualityCases() throws IOException {
        ChatQualityEvaluationFixture.Suite suite = loader.load(validFixture());

        assertEquals("chat-quality-v1", suite.suiteId());
        assertEquals(1, suite.schemaVersion());
        assertEquals(4, suite.cases().size());
        assertEquals("EVAL-CHAT-001", suite.cases().get(0).caseId());
        assertEquals("EVAL-CHAT-002", suite.cases().get(1).caseId());
        assertEquals("EVAL-CHAT-003", suite.cases().get(2).caseId());
        assertEquals("EVAL-TOOL-001", suite.cases().get(3).caseId());
    }

    @Test
    void rejectsForbiddenFixtureFieldsWithoutEchoingTheirValue() throws IOException {
        assertInvalid(jsonWithField("rawText", "synthetic-sensitive-value"));
        assertInvalid(jsonWithField("prompt", "synthetic-sensitive-prompt"));
    }

    @Test
    void rejectsUnknownFieldsAndNonFixtureIds() throws IOException {
        assertInvalid(jsonWithField("unknownField", "fixture-value"));
        assertInvalid(jsonWithField("userId", "real-user-value"));
    }

    @Test
    void rejectsMissingCaseAndInvalidInputKind() throws IOException {
        ObjectNode missingCase = (ObjectNode) validFixture().deepCopy();
        missingCase.withArray("cases").remove(0);
        assertInvalid(missingCase);

        ObjectNode invalidInputKind = (ObjectNode) validFixture().deepCopy();
        ((ObjectNode) invalidInputKind.at("/cases/0/scenarios/0"))
                .put("inputKind", "RAW_QUERY");
        assertInvalid(invalidInputKind);
    }

    private JsonNode validFixture() throws IOException {
        try (InputStream input = new ClassPathResource(
                ChatQualityFixtureLoader.DEFAULT_RESOURCE).getInputStream()) {
            return objectMapper.readTree(input);
        }
    }

    private void assertInvalid(JsonNode root) {
        ChatQualityFixtureLoader.FixtureValidationException failure =
                assertThrows(ChatQualityFixtureLoader.FixtureValidationException.class,
                        () -> loader.load(root));
        assertEquals("FIXTURE_INVALID", failure.code());
        assertEquals("FIXTURE_INVALID", failure.getMessage());
    }

    private JsonNode jsonWithField(String field, String value) throws IOException {
        ObjectNode root = (ObjectNode) validFixture().deepCopy();
        if ("userId".equals(field)) {
            ((ObjectNode) root.at("/cases/0/scenarios/0")).put(field, value);
        } else {
            root.put(field, value);
        }
        return root;
    }
}
