package com.aseubel.yusi.evaluation.lifegraph;

import com.aseubel.yusi.evaluation.EvaluationFixtureRedLineValidator;
import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import com.aseubel.yusi.service.lifegraph.LifeGraphPromotionPolicy;
import com.aseubel.yusi.service.lifegraph.constant.LifeGraphRelationType;
import com.aseubel.yusi.service.lifegraph.dto.LifeGraphExtractionResult;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static com.aseubel.yusi.evaluation.lifegraph.LifeGraphPromotionEvaluationFixture.EvaluationCase;
import static com.aseubel.yusi.evaluation.lifegraph.LifeGraphPromotionEvaluationFixture.Expected;
import static com.aseubel.yusi.evaluation.lifegraph.LifeGraphPromotionEvaluationFixture.Scenario;
import static com.aseubel.yusi.evaluation.lifegraph.LifeGraphPromotionEvaluationFixture.Suite;

/** Loads only the fixed, sanitized LifeGraph promotion replay schema. */
public final class LifeGraphPromotionFixtureLoader {

    public static final String DEFAULT_RESOURCE =
            "evaluation/lifegraph-promotion-v1-fixtures.json";
    private static final String INVALID_CODE = "FIXTURE_INVALID";
    private static final String SUITE_ID = "lifegraph-promotion-v1";
    private static final String CASE_ID = "EVAL-MEM-003";
    private static final Pattern SCENARIO_ID = Pattern.compile("EVAL-MEM-003-[A-C]");
    private static final Pattern USER_ID = Pattern.compile("fixture-user-[a-z0-9-]+");
    private static final Pattern SOURCE_ID = Pattern.compile("fixture-diary-promotion-[a-z0-9-]+");
    private static final Pattern FIXTURE_KEY = Pattern.compile("fixture-[a-z0-9-]+");
    private static final Pattern EVIDENCE_TOKEN = Pattern.compile("evidence-token-[a-z0-9-]+");

    private final ObjectMapper objectMapper;
    private final LifeGraphPromotionPolicy promotionPolicy = new LifeGraphPromotionPolicy();

    public LifeGraphPromotionFixtureLoader(ObjectMapper objectMapper) {
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
        if (suite == null || suite.schemaVersion() != 1
                || !SUITE_ID.equals(suite.suiteId())
                || suite.cases() == null || suite.cases().size() != 1) {
            throw invalid();
        }

        EvaluationCase evaluationCase = suite.cases().get(0);
        if (evaluationCase == null || !CASE_ID.equals(evaluationCase.caseId())
                || evaluationCase.scenarios() == null || evaluationCase.scenarios().size() != 3) {
            throw invalid();
        }

        Set<String> scenarioIds = new HashSet<>();
        Set<String> userIds = new HashSet<>();
        Set<String> sourceIds = new HashSet<>();
        for (Scenario scenario : evaluationCase.scenarios()) {
            validateScenario(scenario);
            if (!scenarioIds.add(scenario.scenarioId())
                    || !userIds.add(scenario.userId())
                    || !sourceIds.add(scenario.sourceId())) {
                throw invalid();
            }
        }
        if (!scenarioIds.equals(Set.of(
                "EVAL-MEM-003-A", "EVAL-MEM-003-B", "EVAL-MEM-003-C"))) {
            throw invalid();
        }
    }

    private void validateScenario(Scenario scenario) {
        if (scenario == null || !SCENARIO_ID.matcher(scenario.scenarioId()).matches()
                || !USER_ID.matcher(scenario.userId()).matches()
                || !SOURCE_ID.matcher(scenario.sourceId()).matches()
                || scenario.confirmedImportantPersonKeys() == null
                || scenario.extraction() == null
                || scenario.expected() == null) {
            throw invalid();
        }

        Map<String, LifeGraphExtractionResult.ExtractedEntity> entities =
                validateEntities(scenario.extraction().getEntities());
        for (String confirmedKey : scenario.confirmedImportantPersonKeys()) {
            requireFixtureOrUserKey(confirmedKey);
            LifeGraphExtractionResult.ExtractedEntity entity = entities.get(
                    promotionPolicy.normalizeKey(confirmedKey, null));
            if (!isPerson(entity)) {
                throw invalid();
            }
        }

        Set<String> relationKeys = validateRelations(scenario.extraction().getRelations(), entities.keySet());
        Set<String> mentionKeys = validateMentions(scenario.extraction().getMentions(), entities.keySet());
        validateExpected(scenario.expected(), relationKeys, entities.keySet(), mentionKeys);
    }

    private Map<String, LifeGraphExtractionResult.ExtractedEntity> validateEntities(
            List<LifeGraphExtractionResult.ExtractedEntity> extractedEntities) {
        if (extractedEntities == null || extractedEntities.isEmpty()) {
            throw invalid();
        }
        Map<String, LifeGraphExtractionResult.ExtractedEntity> entities = new LinkedHashMap<>();
        for (LifeGraphExtractionResult.ExtractedEntity entity : extractedEntities) {
            if (entity == null || !promotionPolicy.isSupportedType(entity.getType())
                    || entity.getProps() != null && !entity.getProps().isEmpty()) {
                throw invalid();
            }
            String key = promotionPolicy.normalizeKey(entity.getNameNorm(), entity.getDisplayName());
            requireFixtureOrUserKey(key);
            if (entities.putIfAbsent(key, entity) != null
                    || entity.getConfidence() != null && !isConfidence(entity.getConfidence())
                    || !isExpectedEntityKeyForType(key, entity.getType())) {
                throw invalid();
            }
        }
        long userCount = entities.entrySet().stream()
                .filter(entry -> isUser(entry.getValue()))
                .count();
        if (userCount != 1 || !entities.containsKey("__user__")
                || !isUser(entities.get("__user__"))) {
            throw invalid();
        }
        return entities;
    }

    private Set<String> validateRelations(
            List<LifeGraphExtractionResult.ExtractedRelation> relations,
            Set<String> entityKeys) {
        if (relations == null || relations.isEmpty()) {
            throw invalid();
        }
        Set<String> relationKeys = new LinkedHashSet<>();
        for (LifeGraphExtractionResult.ExtractedRelation relation : relations) {
            if (relation == null || relation.getProps() != null && !relation.getProps().isEmpty()
                    || relation.getConfidence() == null
                    || !isConfidence(relation.getConfidence())) {
                throw invalid();
            }
            String source = normalizeEndpoint(relation.getSource());
            String target = normalizeEndpoint(relation.getTarget());
            LifeGraphRelationType relationType = LifeGraphRelationType.fromCode(relation.getType());
            if (source == null || target == null || !entityKeys.contains(source)
                    || !entityKeys.contains(target) || relationType == null) {
                throw invalid();
            }
            if (relation.getEvidenceSnippet() != null
                    && !EVIDENCE_TOKEN.matcher(relation.getEvidenceSnippet()).matches()) {
                throw invalid();
            }
            relationKeys.add(relationKey(source, target, relationType.code()));
        }
        return relationKeys;
    }

    private Set<String> validateMentions(
            List<LifeGraphExtractionResult.ExtractedMention> mentions,
            Set<String> entityKeys) {
        if (mentions == null) {
            return Set.of();
        }
        Set<String> mentionKeys = new LinkedHashSet<>();
        for (LifeGraphExtractionResult.ExtractedMention mention : mentions) {
            if (mention == null || mention.getProps() != null && !mention.getProps().isEmpty()
                    || mention.getSnippet() == null
                    || !EVIDENCE_TOKEN.matcher(mention.getSnippet()).matches()) {
                throw invalid();
            }
            String key = normalizeEndpoint(mention.getEntity());
            if (key == null || !entityKeys.contains(key)) {
                throw invalid();
            }
            mentionKeys.add(key);
        }
        return mentionKeys;
    }

    private void validateExpected(Expected expected, Set<String> relationKeys,
                                  Set<String> entityKeys, Set<String> mentionKeys) {
        if (expected.acceptedEntityKeys() == null
                || expected.acceptedRelationKeys() == null
                || expected.rejectedRelationKeys() == null
                || expected.sourceEntityEvidenceCount() < 0
                || expected.sourceRelationEvidenceCount() < 0
                || expected.sourceMentionCount() < 0) {
            throw invalid();
        }

        Set<String> acceptedEntities = validateEntityExpectationKeys(
                expected.acceptedEntityKeys(), entityKeys);
        Set<String> acceptedRelations = validateRelationExpectationKeys(
                expected.acceptedRelationKeys(), relationKeys);
        Set<String> rejectedRelations = validateRelationExpectationKeys(
                expected.rejectedRelationKeys(), relationKeys);
        Set<String> overlap = new HashSet<>(acceptedRelations);
        overlap.retainAll(rejectedRelations);
        Set<String> classifiedRelations = new LinkedHashSet<>(acceptedRelations);
        classifiedRelations.addAll(rejectedRelations);
        if (!overlap.isEmpty() || !classifiedRelations.equals(relationKeys)) {
            throw invalid();
        }

        Set<String> relationEntityKeys = new LinkedHashSet<>();
        for (String relation : acceptedRelations) {
            String[] endpoints = relation.split("\\|", -1);
            if (!"__user__".equals(endpoints[0])) {
                relationEntityKeys.add(endpoints[0]);
            }
            if (!"__user__".equals(endpoints[1])) {
                relationEntityKeys.add(endpoints[1]);
            }
        }
        if (!acceptedEntities.equals(relationEntityKeys)
                || !acceptedEntities.stream().allMatch(mentionKeys::contains)
                || expected.sourceEntityEvidenceCount() != acceptedEntities.size()
                || expected.sourceRelationEvidenceCount() != acceptedRelations.size()
                || expected.sourceMentionCount() != acceptedEntities.size()) {
            throw invalid();
        }
    }

    private Set<String> validateEntityExpectationKeys(Set<String> keys, Set<String> entityKeys) {
        Set<String> result = new LinkedHashSet<>();
        for (String key : keys) {
            requireFixtureOrUserKey(key);
            if ("__user__".equals(key) || !entityKeys.contains(key) || !result.add(key)) {
                throw invalid();
            }
        }
        return result;
    }

    private Set<String> validateRelationExpectationKeys(Set<String> keys, Set<String> relationKeys) {
        Set<String> result = new LinkedHashSet<>();
        for (String key : keys) {
            String[] parts = key == null ? new String[0] : key.split("\\|", -1);
            if (parts.length != 3 || normalizeEndpoint(parts[0]) == null
                    || normalizeEndpoint(parts[1]) == null
                    || LifeGraphRelationType.fromCode(parts[2]) == null) {
                throw invalid();
            }
            String normalized = relationKey(normalizeEndpoint(parts[0]), normalizeEndpoint(parts[1]),
                    LifeGraphRelationType.fromCode(parts[2]).code());
            if (!relationKeys.contains(normalized) || !result.add(normalized)) {
                throw invalid();
            }
        }
        return result;
    }

    private boolean isExpectedEntityKeyForType(String key, String type) {
        boolean user = "__user__".equals(key);
        return user == "User".equalsIgnoreCase(type);
    }

    private boolean isUser(LifeGraphExtractionResult.ExtractedEntity entity) {
        return entity != null && "User".equalsIgnoreCase(entity.getType());
    }

    private boolean isPerson(LifeGraphExtractionResult.ExtractedEntity entity) {
        return entity != null && "Person".equalsIgnoreCase(entity.getType());
    }

    private boolean isConfidence(Double value) {
        return value >= 0.0 && value <= 1.0;
    }

    private String normalizeEndpoint(String value) {
        String key = promotionPolicy.normalizeKey(value, null);
        if (key == null) {
            return null;
        }
        return isFixtureOrUserKey(key) ? key : null;
    }

    private void requireFixtureOrUserKey(String value) {
        if (!isFixtureOrUserKey(value)) {
            throw invalid();
        }
    }

    private boolean isFixtureOrUserKey(String value) {
        return "__user__".equals(value) || value != null && FIXTURE_KEY.matcher(value).matches();
    }

    private String relationKey(String source, String target, String type) {
        return source + "|" + target + "|" + type.toUpperCase(Locale.ROOT);
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
