package com.aseubel.yusi.evaluation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Locale;
import java.util.Set;

/** Shared red-line validation for low-sensitivity offline evaluation fixtures. */
public final class EvaluationFixtureRedLineValidator {

    public static final String INVALID_CODE = "FIXTURE_INVALID";
    private static final int MAX_STRING_LENGTH = 256;
    private static final Set<String> FORBIDDEN_FIELDS = Set.of(
            "plaincontent", "rawtext", "prompt", "toolarguments", "toolresult", "secret", "password",
            "content");

    private EvaluationFixtureRedLineValidator() {
    }

    public static void validateTree(JsonNode root) {
        if (root == null || root.isNull()) {
            throw invalid();
        }
        validateNode(root, null);
    }

    private static void validateNode(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isTextual() && node.textValue().length() > MAX_STRING_LENGTH) {
            throw invalid();
        }
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                if (FORBIDDEN_FIELDS.contains(field.getKey().toLowerCase(Locale.ROOT))) {
                    throw invalid();
                }
                validateNode(field.getValue(), field.getKey());
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                validateNode(child, fieldName);
            }
            return;
        }
        if (node.isTextual() && isEvidenceField(fieldName)
                && !node.textValue().matches("evidence-token-[a-z0-9-]+")) {
            throw invalid();
        }
    }

    private static boolean isEvidenceField(String fieldName) {
        return "evidenceSnippet".equals(fieldName) || "snippet".equals(fieldName);
    }

    private static FixtureValidationException invalid() {
        return new FixtureValidationException(INVALID_CODE);
    }

    public static final class FixtureValidationException extends RuntimeException {
        private final String code;

        public FixtureValidationException(String code) {
            super(code);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
