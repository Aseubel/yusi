package com.aseubel.yusi.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EvaluationFixtureRedLineValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsSanitizedFixtureTree() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("summary", "memory-summary-active-a");
        root.put("evidenceSnippet", "evidence-token-active-a");

        EvaluationFixtureRedLineValidator.validateTree(root);
    }

    @Test
    void rejectsForbiddenFieldWithoutEchoingItsValue() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("rawText", "sensitive-fixture-value");

        EvaluationFixtureRedLineValidator.FixtureValidationException failure =
                assertThrows(EvaluationFixtureRedLineValidator.FixtureValidationException.class,
                        () -> EvaluationFixtureRedLineValidator.validateTree(root));

        assertEquals("FIXTURE_INVALID", failure.code());
        assertFalse(failure.getMessage().contains("sensitive-fixture-value"));
    }

    @Test
    void rejectsOverlongFixtureString() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("summary", "x".repeat(257));

        EvaluationFixtureRedLineValidator.FixtureValidationException failure =
                assertThrows(EvaluationFixtureRedLineValidator.FixtureValidationException.class,
                        () -> EvaluationFixtureRedLineValidator.validateTree(root));

        assertEquals("FIXTURE_INVALID", failure.code());
    }

    @Test
    void rejectsNonTokenEvidence() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("evidenceSnippet", "evidence that contains fixture prose");

        EvaluationFixtureRedLineValidator.FixtureValidationException failure =
                assertThrows(EvaluationFixtureRedLineValidator.FixtureValidationException.class,
                        () -> EvaluationFixtureRedLineValidator.validateTree(root));

        assertEquals("FIXTURE_INVALID", failure.code());
    }
}
