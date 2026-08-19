package com.aseubel.yusi.evaluation.lifegraph;

import com.aseubel.yusi.evaluation.EvaluationFixtureRedLineValidator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static com.aseubel.yusi.evaluation.lifegraph.LifeGraphTimelineRebuildEvaluationFixture.EvaluationCase;
import static com.aseubel.yusi.evaluation.lifegraph.LifeGraphTimelineRebuildEvaluationFixture.Event;
import static com.aseubel.yusi.evaluation.lifegraph.LifeGraphTimelineRebuildEvaluationFixture.ExtractedEntity;
import static com.aseubel.yusi.evaluation.lifegraph.LifeGraphTimelineRebuildEvaluationFixture.ExtractedMention;
import static com.aseubel.yusi.evaluation.lifegraph.LifeGraphTimelineRebuildEvaluationFixture.ExtractedRelation;
import static com.aseubel.yusi.evaluation.lifegraph.LifeGraphTimelineRebuildEvaluationFixture.Extraction;
import static com.aseubel.yusi.evaluation.lifegraph.LifeGraphTimelineRebuildEvaluationFixture.Expected;
import static com.aseubel.yusi.evaluation.lifegraph.LifeGraphTimelineRebuildEvaluationFixture.Scenario;
import static com.aseubel.yusi.evaluation.lifegraph.LifeGraphTimelineRebuildEvaluationFixture.Source;
import static com.aseubel.yusi.evaluation.lifegraph.LifeGraphTimelineRebuildEvaluationFixture.Suite;

/** Loads only the fixed, sanitized source revision replay schema. */
public final class LifeGraphTimelineRebuildFixtureLoader {

    public static final String DEFAULT_RESOURCE =
            "evaluation/lifegraph-timeline-rebuild-v1-fixtures.json";
    private static final String INVALID_CODE = "FIXTURE_INVALID";
    private static final String SUITE_ID = "lifegraph-timeline-rebuild-v1";
    private static final String CASE_ID = "EVAL-TIMELINE-002";
    private static final String SCENARIO_ID = "EVAL-TIMELINE-002-A";
    private static final String USER_ID = "fixture-user-timeline-rebuild";
    private static final String SOURCE_ID = "fixture-diary-timeline-rebuild";
    private static final String OLD_EVENT_KEY = "fixture-rebuild-event-old";
    private static final String NEW_EVENT_KEY = "fixture-rebuild-event-new";
    private static final Pattern EVENT_KEY = Pattern.compile("fixture-rebuild-event-(old|new)");
    private static final Pattern EVIDENCE_TOKEN = Pattern.compile("evidence-token-rebuild-(old|new)");

    private final ObjectMapper objectMapper;

    public LifeGraphTimelineRebuildFixtureLoader(ObjectMapper objectMapper) {
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
        Set<String> caseIds = new HashSet<>();
        for (EvaluationCase evaluationCase : suite.cases()) {
            if (evaluationCase == null || !CASE_ID.equals(evaluationCase.caseId())
                    || !caseIds.add(evaluationCase.caseId())
                    || evaluationCase.scenarios() == null || evaluationCase.scenarios().size() != 1) {
                throw invalid();
            }
            validateScenario(evaluationCase.scenarios().get(0));
        }
        if (!caseIds.equals(Set.of(CASE_ID))) {
            throw invalid();
        }
    }

    private void validateScenario(Scenario scenario) {
        if (scenario == null || !SCENARIO_ID.equals(scenario.scenarioId())
                || !USER_ID.equals(scenario.userId()) || scenario.sources() == null
                || scenario.sources().size() != 1 || scenario.expected() == null) {
            throw invalid();
        }
        Expected expected = scenario.expected();
        if (expected.beforeRevisionNodeCount() != 1
                || expected.afterRevisionOldResidualCount() != 0
                || expected.afterRevisionNewNodeCount() != 1
                || expected.afterDeleteTimelineNodeCount() != 0
                || expected.sourceResidualCount() != 0
                || expected.afterRevisionEntityCount() != 2
                || expected.afterRevisionRelationCount() != 1
                || expected.afterRevisionEntityEvidenceCount() != 1
                || expected.afterRevisionRelationEvidenceCount() != 1
                || expected.afterRevisionMentionCount() != 1
                || !OLD_EVENT_KEY.equals(expected.oldEventKey())
                || !NEW_EVENT_KEY.equals(expected.newEventKey())) {
            throw invalid();
        }

        Source source = scenario.sources().get(0);
        if (source == null || !"DIARY".equals(source.sourceType())
                || !SOURCE_ID.equals(source.sourceId()) || source.events() == null
                || source.events().size() != 3) {
            throw invalid();
        }
        validateEvent(source.events().get(0), "UPSERT", 1L, "2026-07-01", OLD_EVENT_KEY,
                "evidence-token-rebuild-old", false);
        validateEvent(source.events().get(1), "UPSERT", 2L, "2026-08-11", NEW_EVENT_KEY,
                "evidence-token-rebuild-new", false);
        validateEvent(source.events().get(2), "DELETE", 2L, "2026-08-11", NEW_EVENT_KEY,
                "evidence-token-rebuild-new", true);
    }

    private void validateEvent(Event event, String operation, long revision, String entryDate,
                               String expectedEventKey, String expectedEvidenceToken,
                               boolean delete) {
        if (event == null || !operation.equals(event.operation()) || event.sourceRevision() != revision
                || !entryDate.equals(event.entryDate()) || event.extraction() == null) {
            throw invalid();
        }
        try {
            LocalDate.parse(event.entryDate());
        } catch (DateTimeParseException exception) {
            throw invalid();
        }
        Extraction extraction = event.extraction();
        if (extraction.entities() == null || extraction.relations() == null
                || extraction.mentions() == null) {
            throw invalid();
        }
        if (delete) {
            if (!extraction.entities().isEmpty() || !extraction.mentions().isEmpty()) {
                throw invalid();
            }
            return;
        }
        if (extraction.entities().size() != 2 || extraction.relations().size() != 1
                || extraction.mentions().size() != 1) {
            throw invalid();
        }
        validateUserEntity(extraction.entities().get(0));
        validateEntity(extraction.entities().get(1), expectedEventKey);
        validateRelation(extraction.relations().get(0), expectedEventKey, expectedEvidenceToken);
        validateMention(extraction.mentions().get(0), expectedEventKey, expectedEvidenceToken);
    }

    private void validateUserEntity(ExtractedEntity entity) {
        if (entity == null || !"User".equals(entity.type()) || !"__USER__".equals(entity.displayName())
                || !"__USER__".equals(entity.nameNorm())) {
            throw invalid();
        }
    }

    private void validateEntity(ExtractedEntity entity, String expectedEventKey) {
        if (entity == null || !"Event".equals(entity.type())
                || !expectedEventKey.equals(entity.displayName())
                || !expectedEventKey.equals(entity.nameNorm())
                || entity.importance() == null || entity.confidence() == null
                || !isUnitInterval(entity.importance()) || !isUnitInterval(entity.confidence())
                || !EVENT_KEY.matcher(entity.displayName()).matches()) {
            throw invalid();
        }
    }

    private void validateMention(ExtractedMention mention, String expectedEventKey,
                                 String expectedEvidenceToken) {
        if (mention == null || !expectedEventKey.equals(mention.entity())
                || !expectedEvidenceToken.equals(mention.snippet())
                || !EVIDENCE_TOKEN.matcher(mention.snippet()).matches()) {
            throw invalid();
        }
    }

    private void validateRelation(ExtractedRelation relation, String expectedEventKey,
                                  String expectedEvidenceToken) {
        if (relation == null || !"__USER__".equals(relation.source())
                || !expectedEventKey.equals(relation.target())
                || !"PARTICIPATED_IN".equals(relation.type())
                || relation.confidence() == null || !isUnitInterval(relation.confidence())
                || !expectedEvidenceToken.equals(relation.evidenceSnippet())
                || !EVIDENCE_TOKEN.matcher(relation.evidenceSnippet()).matches()) {
            throw invalid();
        }
    }

    private boolean isUnitInterval(Double value) {
        return value != null && Double.isFinite(value) && value >= 0.0 && value <= 1.0;
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
