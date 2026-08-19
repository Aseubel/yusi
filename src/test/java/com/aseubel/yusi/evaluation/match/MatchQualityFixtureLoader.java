package com.aseubel.yusi.evaluation.match;

import com.aseubel.yusi.evaluation.EvaluationFixtureRedLineValidator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static com.aseubel.yusi.evaluation.match.MatchQualityEvaluationFixture.EvaluationCase;
import static com.aseubel.yusi.evaluation.match.MatchQualityEvaluationFixture.Scenario;
import static com.aseubel.yusi.evaluation.match.MatchQualityEvaluationFixture.Suite;

/** Loads only the fixed, sanitized matching quality replay schema. */
public final class MatchQualityFixtureLoader {

    public static final String DEFAULT_RESOURCE = "evaluation/match-quality-v1-fixtures.json";
    private static final String INVALID_CODE = "FIXTURE_INVALID";
    private static final String SUITE_ID = "match-quality-v1";
    private static final String CASE_ID = "EVAL-MATCH-001";
    private static final Set<String> SCENARIO_IDS = Set.of(
            "EVAL-MATCH-001-A", "EVAL-MATCH-001-B", "EVAL-MATCH-001-C");
    private static final Pattern USER_ID = Pattern.compile("fixture-user-match-[a-z0-9-]+");
    private static final Set<String> ACTIONS = Set.of(
            "ACCEPT", "DEEP_INTERACTION", "REPORT");

    private final ObjectMapper objectMapper;

    public MatchQualityFixtureLoader(ObjectMapper objectMapper) {
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
            throw exception;
        } catch (Exception exception) {
            throw invalid();
        }
    }

    private void validateTypedSuite(Suite suite) {
        if (suite == null || suite.schemaVersion() != 1 || !SUITE_ID.equals(suite.suiteId())
                || suite.cases() == null || suite.cases().size() != 1) {
            throw invalid();
        }
        EvaluationCase evaluationCase = suite.cases().get(0);
        if (evaluationCase == null || !CASE_ID.equals(evaluationCase.caseId())
                || evaluationCase.scenarios() == null || evaluationCase.scenarios().size() != 3) {
            throw invalid();
        }
        Set<String> scenarioIds = new HashSet<>();
        Set<String> scenarioUsers = new HashSet<>();
        for (Scenario scenario : evaluationCase.scenarios()) {
            if (scenario == null || !scenarioIds.add(scenario.scenarioId())
                    || !SCENARIO_IDS.contains(scenario.scenarioId())
                    || !scenarioUsers.add(scenario.userId())) {
                throw invalid();
            }
            validateScenario(scenario);
        }
        if (!scenarioIds.equals(SCENARIO_IDS)) {
            throw invalid();
        }
    }

    private void validateScenario(Scenario scenario) {
        if (!matches(USER_ID, scenario.userId()) || scenario.participantUserIds() == null
                || scenario.recallCandidates() == null || scenario.actions() == null
                || scenario.expectedReasonCount() < 0
                || scenario.expectedSubsequentRecommendationCount() < 0
                || scenario.acceptanceRateAvailable()) {
            throw invalid();
        }
        validateUserIds(scenario.participantUserIds());
        validateUserIds(scenario.recallCandidates());
        if (scenario.targetProfileKey() != null && !matches(USER_ID, scenario.targetProfileKey())) {
            throw invalid();
        }
        for (String action : scenario.actions()) {
            if (!ACTIONS.contains(action)) {
                throw invalid();
            }
        }
        if (scenario.negativeFeedbackAction() != null
                && !"REPORT".equals(scenario.negativeFeedbackAction())) {
            throw invalid();
        }

        switch (scenario.scenarioId()) {
            case "EVAL-MATCH-001-A" -> validateRecallScenario(scenario);
            case "EVAL-MATCH-001-B" -> validateLifecycleScenario(scenario);
            case "EVAL-MATCH-001-C" -> validateNegativeScenario(scenario);
            default -> throw invalid();
        }
    }

    private void validateRecallScenario(Scenario scenario) {
        if (!scenario.participantUserIds().isEmpty()
                || scenario.recallCandidates().size() != 2
                || scenario.targetProfileKey() == null
                || !scenario.recallCandidates().contains(scenario.targetProfileKey())
                || scenario.expectedReasonCount() != 3
                || !scenario.actions().isEmpty()
                || scenario.negativeFeedbackAction() != null
                || scenario.expectedSubsequentRecommendationCount() != 0) {
            throw invalid();
        }
    }

    private void validateLifecycleScenario(Scenario scenario) {
        if (scenario.participantUserIds().size() != 2
                || scenario.participantUserIds().get(0).equals(scenario.participantUserIds().get(1))
                || !scenario.recallCandidates().isEmpty()
                || scenario.targetProfileKey() != null
                || scenario.expectedReasonCount() != 0
                || !scenario.actions().equals(List.of(
                        "ACCEPT", "ACCEPT", "DEEP_INTERACTION", "DEEP_INTERACTION"))
                || scenario.negativeFeedbackAction() != null
                || scenario.expectedSubsequentRecommendationCount() != 0) {
            throw invalid();
        }
    }

    private void validateNegativeScenario(Scenario scenario) {
        if (scenario.participantUserIds().size() != 2
                || scenario.participantUserIds().get(0).equals(scenario.participantUserIds().get(1))
                || !scenario.recallCandidates().isEmpty()
                || scenario.targetProfileKey() != null
                || scenario.expectedReasonCount() != 0
                || !scenario.actions().isEmpty()
                || !"REPORT".equals(scenario.negativeFeedbackAction())
                || scenario.expectedSubsequentRecommendationCount() != 0) {
            throw invalid();
        }
    }

    private void validateUserIds(List<String> userIds) {
        Set<String> unique = new HashSet<>();
        for (String userId : userIds) {
            if (!matches(USER_ID, userId) || !unique.add(userId)) {
                throw invalid();
            }
        }
    }

    private boolean matches(Pattern pattern, String value) {
        return value != null && pattern.matcher(value).matches();
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
