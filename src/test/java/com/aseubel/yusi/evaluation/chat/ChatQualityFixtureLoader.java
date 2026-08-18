package com.aseubel.yusi.evaluation.chat;

import com.aseubel.yusi.evaluation.EvaluationFixtureRedLineValidator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static com.aseubel.yusi.evaluation.chat.ChatQualityEvaluationFixture.EvaluationCase;
import static com.aseubel.yusi.evaluation.chat.ChatQualityEvaluationFixture.Scenario;
import static com.aseubel.yusi.evaluation.chat.ChatQualityEvaluationFixture.Suite;

/** Loads only the fixed, sanitized chat quality replay schema. */
public final class ChatQualityFixtureLoader {

    public static final String DEFAULT_RESOURCE = "evaluation/chat-quality-v1-fixtures.json";
    private static final String INVALID_CODE = "FIXTURE_INVALID";
    private static final String SUITE_ID = "chat-quality-v1";
    private static final Pattern USER_ID = Pattern.compile("fixture-user-chat-[a-z0-9-]+");
    private static final Pattern MEMORY_KEY = Pattern.compile("fixture-memory-[a-z0-9-]+");
    private static final Set<String> CASE_IDS = Set.of(
            "EVAL-CHAT-001", "EVAL-CHAT-002", "EVAL-CHAT-003", "EVAL-TOOL-001");
    private static final Map<String, String> SCENARIO_IDS = Map.of(
            "EVAL-CHAT-001", "EVAL-CHAT-001-A",
            "EVAL-CHAT-002", "EVAL-CHAT-002-A",
            "EVAL-CHAT-003", "EVAL-CHAT-003-A",
            "EVAL-TOOL-001", "EVAL-TOOL-001-A");
    private static final Set<String> INPUT_KINDS = Set.of(
            "NO_HISTORY", "SUPPORTED_AND_UNSUPPORTED_MEMORY", "UNRESOLVED_CONFLICT", "TOOL_FAILURE");
    private static final Set<String> ALLOWED_TOOLS = Set.of("searchMemories");
    private static final Set<String> EXPECTED_CODES = Set.of(
            "NO_STABLE_CLAIM_WITHOUT_CONTEXT", "VISIBLE_MEMORY_ONLY",
            "CONFLICT_REQUIRES_ATTENTION", "TOOL_EVENT_LOW_SENSITIVITY");
    private static final Set<String> EXPECTED_FIELDS = Set.of(
            "semanticModelScoreAvailable", "restrictedMemoryCount", "visibleMemoryCount",
            "conflictAttentionRequired", "responseDeltaCount", "terminalEventRequired");

    private final ObjectMapper objectMapper;

    public ChatQualityFixtureLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    public Suite load() {
        try (InputStream input = new ClassPathResource(DEFAULT_RESOURCE).getInputStream()) {
            return load(objectMapper.readTree(input));
        } catch (IOException exception) {
            throw invalid();
        }
    }

    public Suite load(JsonNode root) {
        try {
            EvaluationFixtureRedLineValidator.validateTree(root);
            Suite suite = objectMapper.readerFor(Suite.class).readValue(root.toString());
            validateTypedSuite(suite);
            return suite;
        } catch (EvaluationFixtureRedLineValidator.FixtureValidationException exception) {
            throw invalid();
        } catch (FixtureValidationException exception) {
            throw invalid();
        } catch (Exception exception) {
            throw invalid();
        }
    }

    private void validateTypedSuite(Suite suite) {
        if (suite == null || suite.schemaVersion() != 1 || !SUITE_ID.equals(suite.suiteId())
                || suite.cases() == null || suite.cases().size() != CASE_IDS.size()) {
            throw invalid();
        }

        Set<String> caseIds = new HashSet<>();
        Set<String> scenarioIds = new HashSet<>();
        Set<String> userIds = new HashSet<>();
        for (EvaluationCase evaluationCase : suite.cases()) {
            if (evaluationCase == null || evaluationCase.scenarios() == null
                    || evaluationCase.scenarios().size() != 1
                    || !CASE_IDS.contains(evaluationCase.caseId())
                    || !caseIds.add(evaluationCase.caseId())) {
                throw invalid();
            }
            Scenario scenario = evaluationCase.scenarios().get(0);
            validateScenario(evaluationCase.caseId(), scenario);
            if (!scenarioIds.add(scenario.scenarioId()) || !userIds.add(scenario.userId())) {
                throw invalid();
            }
        }
        if (!caseIds.equals(CASE_IDS)) {
            throw invalid();
        }
    }

    private void validateScenario(String caseId, Scenario scenario) {
        if (scenario == null || !SCENARIO_IDS.get(caseId).equals(scenario.scenarioId())
                || !USER_ID.matcher(String.valueOf(scenario.userId())).matches()
                || !INPUT_KINDS.contains(scenario.inputKind())
                || scenario.availableMemoryKeys() == null
                || scenario.restrictedMemoryKeys() == null
                || scenario.allowedTools() == null
                || scenario.expectedPolicyCodes() == null
                || scenario.expected() == null) {
            throw invalid();
        }

        validateMemoryKeys(scenario.availableMemoryKeys());
        validateMemoryKeys(scenario.restrictedMemoryKeys());
        Set<String> overlap = new HashSet<>(scenario.availableMemoryKeys());
        overlap.retainAll(scenario.restrictedMemoryKeys());
        if (!overlap.isEmpty()) {
            throw invalid();
        }
        if (!ALLOWED_TOOLS.containsAll(scenario.allowedTools())
                || !EXPECTED_CODES.containsAll(scenario.expectedPolicyCodes())
                || scenario.expectedPolicyCodes().isEmpty()) {
            throw invalid();
        }
        validateExpected(scenario.expected());
    }

    private void validateMemoryKeys(Set<String> keys) {
        for (String key : keys) {
            if (key == null || !MEMORY_KEY.matcher(key).matches()) {
                throw invalid();
            }
        }
    }

    private void validateExpected(JsonNode expected) {
        if (!expected.isObject()) {
            throw invalid();
        }
        var fields = expected.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            if (!EXPECTED_FIELDS.contains(field.getKey()) || !isSafeExpectedValue(field.getValue())) {
                throw invalid();
            }
        }
        if (!expected.path("semanticModelScoreAvailable").isBoolean()
                || expected.path("semanticModelScoreAvailable").booleanValue()) {
            throw invalid();
        }
    }

    private boolean isSafeExpectedValue(JsonNode value) {
        if (value.isBoolean()) {
            return true;
        }
        if (value.isIntegralNumber()) {
            return value.longValue() >= 0;
        }
        return value.isTextual()
                && value.textValue().matches("[A-Z][A-Z0-9_]*");
    }

    private FixtureValidationException invalid() {
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
