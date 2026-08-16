package com.aseubel.yusi.evaluation.lifegraph;

import com.aseubel.yusi.evaluation.EvaluationFixtureRedLineValidator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;

import static com.aseubel.yusi.evaluation.lifegraph.LifeGraphTimelineEvaluationFixture.EvaluationCase;
import static com.aseubel.yusi.evaluation.lifegraph.LifeGraphTimelineEvaluationFixture.Event;
import static com.aseubel.yusi.evaluation.lifegraph.LifeGraphTimelineEvaluationFixture.Scenario;
import static com.aseubel.yusi.evaluation.lifegraph.LifeGraphTimelineEvaluationFixture.Source;
import static com.aseubel.yusi.evaluation.lifegraph.LifeGraphTimelineEvaluationFixture.Suite;

/** Loads only the fixed, sanitized replay schema. */
public final class LifeGraphTimelineFixtureLoader {

    public static final String DEFAULT_RESOURCE =
            "evaluation/lifegraph-timeline-v1-fixtures.json";
    private static final String INVALID_CODE = "FIXTURE_INVALID";
    private static final Pattern CASE_ID = Pattern.compile("EVAL-[A-Z]+-\\d{3}");
    private static final Pattern SCENARIO_ID = Pattern.compile("EVAL-[A-Z]+-\\d{3}-[A-Z]");
    private static final Pattern USER_ID = Pattern.compile("fixture-user-[a-z0-9-]+");
    private static final Pattern SOURCE_ID = Pattern.compile("fixture-(diary|plaza)-[a-z0-9-]+");

    private final ObjectMapper objectMapper;

    public LifeGraphTimelineFixtureLoader(ObjectMapper objectMapper) {
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
        if (suite == null || suite.schemaVersion() != 1
                || !"lifegraph-timeline-v1".equals(suite.suiteId())
                || suite.cases() == null || suite.cases().isEmpty()) {
            throw invalid();
        }
        for (EvaluationCase evaluationCase : suite.cases()) {
            if (evaluationCase == null || !matches(CASE_ID, evaluationCase.caseId())
                    || evaluationCase.scenarios() == null || evaluationCase.scenarios().isEmpty()) {
                throw invalid();
            }
            for (Scenario scenario : evaluationCase.scenarios()) {
                validateScenario(scenario);
            }
        }
    }

    private void validateScenario(Scenario scenario) {
        if (scenario == null || !matches(SCENARIO_ID, scenario.scenarioId())
                || !matches(USER_ID, scenario.userId())
                || scenario.sources() == null || scenario.sources().isEmpty()) {
            throw invalid();
        }
        for (Source source : scenario.sources()) {
            if (source == null || source.sourceType() == null
                    || !("DIARY".equals(source.sourceType()) || "PLAZA".equals(source.sourceType()))
                    || !matches(SOURCE_ID, source.sourceId())
                    || source.events() == null || source.events().isEmpty()) {
                throw invalid();
            }
            for (Event event : source.events()) {
                if (event == null || event.sourceRevision() < 1
                        || event.extraction() == null
                        || !("UPSERT".equals(event.operation()) || "DELETE".equals(event.operation()))) {
                    throw invalid();
                }
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
