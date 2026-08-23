package com.aseubel.yusi.evaluation.lifegraph;

import com.aseubel.yusi.TestInfrastructureConfig;
import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.common.constant.SourceType;
import com.aseubel.yusi.evaluation.OfflineEvaluationReportWriter;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import com.aseubel.yusi.pojo.entity.LifeGraphEntityEvidence;
import com.aseubel.yusi.pojo.entity.LifeGraphMention;
import com.aseubel.yusi.pojo.entity.LifeGraphRelation;
import com.aseubel.yusi.pojo.entity.LifeGraphRelationEvidence;
import com.aseubel.yusi.repository.LifeGraphEntityEvidenceRepository;
import com.aseubel.yusi.repository.LifeGraphEntityRepository;
import com.aseubel.yusi.repository.LifeGraphMentionRepository;
import com.aseubel.yusi.repository.LifeGraphRelationEvidenceRepository;
import com.aseubel.yusi.repository.LifeGraphRelationRepository;
import com.aseubel.yusi.service.ai.prompt.PromptManager;
import com.aseubel.yusi.service.ai.prompt.PromptSnapshot;
import com.aseubel.yusi.service.lifegraph.LifeGraphBuildService;
import com.aseubel.yusi.service.lifegraph.LifeGraphPromotionPolicy;
import com.aseubel.yusi.service.lifegraph.ai.LifeGraphExtractor;
import com.aseubel.yusi.service.lifegraph.constant.LifeGraphConstants;
import com.aseubel.yusi.service.lifegraph.dto.LifeGraphExtractionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.aseubel.yusi.evaluation.lifegraph.LifeGraphPromotionEvaluationFixture.EvaluationCase;
import static com.aseubel.yusi.evaluation.lifegraph.LifeGraphPromotionEvaluationFixture.Expected;
import static com.aseubel.yusi.evaluation.lifegraph.LifeGraphPromotionEvaluationFixture.Scenario;
import static com.aseubel.yusi.evaluation.lifegraph.LifeGraphPromotionEvaluationFixture.Suite;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:yusi_promotion_evaluation;MODE=MySQL;"
                + "DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
@ActiveProfiles("test")
@Import(TestInfrastructureConfig.class)
class LifeGraphPromotionEvaluationTest {

    private static final String FIXTURE_VERSION = "fixture-v1";
    private static final String EXPECTATION_VERSION = "expectation-v1";
    private static final String DIARY_SOURCE_TYPE = SourceType.DIARY.code();
    private static final Path REPORT_PATH = Path.of(
            "target", "evaluation", "lifegraph-promotion-v1-report.json");

    private final LifeGraphPromotionPolicy promotionPolicy = new LifeGraphPromotionPolicy();

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LifeGraphBuildService lifeGraphBuildService;

    @Autowired
    private LifeGraphEntityRepository entityRepository;

    @Autowired
    private LifeGraphRelationRepository relationRepository;

    @Autowired
    private LifeGraphEntityEvidenceRepository entityEvidenceRepository;

    @Autowired
    private LifeGraphRelationEvidenceRepository relationEvidenceRepository;

    @Autowired
    private LifeGraphMentionRepository mentionRepository;

    @MockBean
    private PromptManager promptManager;

    @MockBean
    private LifeGraphExtractor extractor;

    @BeforeEach
    void resetExtractorBoundary() {
        reset(promptManager, extractor);
        when(promptManager.getSnapshot(PromptKey.GRAPHRAG_EXTRACT))
                .thenReturn(new PromptSnapshot("graphrag-extract", FIXTURE_VERSION, "zh-CN", "fixture"));
    }

    @Test
    void writesTheH2PromotionEvaluationReport() throws Exception {
        List<OfflineEvaluationReportWriter.CaseResult> results = new ArrayList<>();
        Files.deleteIfExists(REPORT_PATH);
        try {
            Suite suite = new LifeGraphPromotionFixtureLoader(objectMapper).load();
            for (EvaluationCase evaluationCase : suite.cases()) {
                for (Scenario scenario : evaluationCase.scenarios()) {
                    results.add(replayScenario(evaluationCase.caseId(), scenario));
                }
            }
        } catch (Exception exception) {
            String code = exception instanceof LifeGraphPromotionFixtureLoader.FixtureValidationException validation
                    ? validation.code() : "REPLAY_EXECUTION";
            results.add(failedCase("EVAL-MEM-003", "EVAL-MEM-003-A", code));
        } finally {
            OfflineEvaluationReportWriter.write(REPORT_PATH, "lifegraph-promotion-v1", results);
        }

        assertFalse(results.isEmpty());
        assertTrue(results.stream().allMatch(result -> "PASS".equals(result.status())),
                () -> results.stream().flatMap(result -> result.violationCodes().stream()).toList().toString());
        assertTrue(Files.exists(REPORT_PATH));

        JsonNode report = objectMapper.readTree(REPORT_PATH.toFile());
        JsonNode expectedPrompt = objectMapper.readTree(
                "{\"key\":\"fixture\",\"version\":\"fixture-v1\",\"locale\":\"zh-CN\"}");
        for (JsonNode result : report.path("cases")) {
            assertEquals(expectedPrompt, result.at("/versions/prompt"));
        }

        ObjectNode reportWithoutPrompt = report.deepCopy();
        for (JsonNode result : reportWithoutPrompt.path("cases")) {
            if (result.isObject() && result.path("versions").isObject()) {
                ((ObjectNode) result.path("versions")).remove("prompt");
            }
        }
        String reportText = reportWithoutPrompt.toString().toLowerCase(Locale.ROOT);
        for (String forbidden : List.of(
                "evidence-token-", "rawtext", "plaincontent", "toolarguments",
                "toolresult", "secret", "password")) {
            assertFalse(reportText.contains(forbidden), () -> "forbidden report token: " + forbidden);
        }
    }

    private OfflineEvaluationReportWriter.CaseResult replayScenario(String caseId, Scenario scenario) {
        Checks checks = new Checks();
        try {
            LifeGraphPromotionPolicy.PromotionResult policyResult = promotionPolicy.promote(
                    scenario.extraction(), Set.copyOf(scenario.confirmedImportantPersonKeys()));
            evaluatePolicy(scenario, policyResult, checks);

            if ("EVAL-MEM-003-B".equals(scenario.scenarioId())) {
                seedConfirmedPerson(scenario);
                Set<String> confirmedFromH2 = findConfirmedImportantPersonKeysFromH2(scenario.userId());
                checks.check("CONFIRMED_PERSON_POSITIVE_CONTROL",
                        !confirmedFromH2.isEmpty() && confirmedFromH2.contains("fixture-person-b"));
                if (confirmedFromH2.isEmpty() || !confirmedFromH2.contains("fixture-person-b")) {
                    return caseResult(caseId, scenario, checks, snapshot(scenario));
                }
            }

            configureExtractor(scenario.extraction());
            lifeGraphBuildService.upsertFromDiary(
                    Diary.builder()
                            .diaryId(scenario.sourceId())
                            .userId(scenario.userId())
                            .title("fixture-title")
                            .entryDate(LocalDate.of(2026, 8, 17))
                            .build(),
                    "evidence-token-diary-input");
            flushGraph();
            evaluateH2(scenario, checks);
        } catch (Exception exception) {
            checks.violate("REPLAY_EXECUTION");
        }
        return caseResult(caseId, scenario, checks, snapshot(scenario));
    }

    private void evaluatePolicy(Scenario scenario,
                                LifeGraphPromotionPolicy.PromotionResult result,
                                Checks checks) {
        Expected expected = scenario.expected();
        Set<String> actualEntityKeys = result.acceptedEntityKeys().stream()
                .map(key -> promotionPolicy.normalizeKey(key, null))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> actualRelationKeys = result.relations().stream()
                .map(this::relationKey)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        checks.check("POLICY_ENTITY_BOUNDARY", actualEntityKeys.equals(expected.acceptedEntityKeys()));
        checks.check("POLICY_RELATION_BOUNDARY", actualRelationKeys.equals(expected.acceptedRelationKeys()));
        checks.check("POLICY_REJECTED_RELATIONS",
                expected.rejectedRelationKeys().stream().noneMatch(actualRelationKeys::contains));

        boolean occurrenceCountsMatch = expected.acceptedRelationKeys().stream().allMatch(key -> {
            int expectedCount = "EVAL-MEM-003-A".equals(scenario.scenarioId())
                    && "__user__|fixture-person-a|PARTNER_OF".equals(key) ? 2 : 1;
            return result.relationOccurrences().getOrDefault(key, 0) == expectedCount;
        });
        checks.check("POLICY_OCCURRENCE_BOUNDARY", occurrenceCountsMatch);
    }

    private void evaluateH2(Scenario scenario, Checks checks) {
        Expected expected = scenario.expected();
        List<LifeGraphEntityEvidence> entityEvidence = entityEvidenceRepository
                .findByUserIdAndSourceTypeAndSourceId(
                        scenario.userId(), DIARY_SOURCE_TYPE, scenario.sourceId());
        List<LifeGraphRelationEvidence> relationEvidence = relationEvidenceRepository
                .findByUserIdAndSourceTypeAndSourceId(
                        scenario.userId(), DIARY_SOURCE_TYPE, scenario.sourceId());
        List<LifeGraphMention> mentions = mentionRepository
                .findByUserIdAndDiaryId(scenario.userId(), scenario.sourceId());

        checks.check("EVIDENCE_BOUNDARY",
                entityEvidence.size() == expected.sourceEntityEvidenceCount()
                        && relationEvidence.size() == expected.sourceRelationEvidenceCount()
                        && mentions.size() == expected.sourceMentionCount());

        List<LifeGraphEntity> entities = entityRepository.findByUserId(scenario.userId());
        checks.check("H2_ENTITY_BOUNDARY",
                expected.acceptedEntityKeys().stream().allMatch(key -> entityExists(scenario.userId(), key))
                        && expected.acceptedEntityKeys().stream().noneMatch(key ->
                        "fixture-person-c".equals(key) && entityExists(scenario.userId(), key))
                        && ("EVAL-MEM-003-C".equals(scenario.scenarioId())
                        ? entities.size() == 1 && entityExists(scenario.userId(), LifeGraphConstants.USER_ENTITY_NORM)
                        : true));

        List<LifeGraphRelation> relations = relationRepository.findByUserId(scenario.userId());
        boolean relationBoundary = switch (scenario.scenarioId()) {
            case "EVAL-MEM-003-A" -> relationExists(scenario.userId(),
                    "__user__", "fixture-person-a", "PARTNER_OF", 2)
                    && relationExists(scenario.userId(),
                    "fixture-person-a", "fixture-item-a", "LIKES", 1)
                    && relationExists(scenario.userId(),
                    "fixture-person-a", "fixture-event-a", "PARTICIPATED_IN", 1)
                    && relationTypesAbsent(relations, Set.of(
                    "DISLIKES", "HAPPENED_AT", "FRIEND_OF", "MENTIONED",
                    "MENTIONED_IN", "SAID", "RELATED_TO"));
            case "EVAL-MEM-003-B" -> relations.size() == 3
                    && relations.stream().filter(item -> item.getOrigin() == LifeGraphRelation.Origin.MANUAL).count() == 1
                    && relations.stream().filter(item -> item.getOrigin() == LifeGraphRelation.Origin.AUTO).count() == 2
                    && relationExists(scenario.userId(),
                    "fixture-person-b", "fixture-item-b", "LIKES", 1)
                    && relationExists(scenario.userId(),
                    "fixture-person-b", "fixture-event-b", "PARTICIPATED_IN", 1)
                    && relationTypesAbsent(relations, Set.of("FRIEND_OF"));
            case "EVAL-MEM-003-C" -> relations.isEmpty();
            default -> false;
        };
        checks.check("H2_RELATION_BOUNDARY", relationBoundary);
    }

    private void seedConfirmedPerson(Scenario scenario) {
        LifeGraphEntity user = entityRepository.save(LifeGraphEntity.builder()
                .userId(scenario.userId())
                .type(LifeGraphEntity.EntityType.User)
                .nameNorm(LifeGraphConstants.USER_ENTITY_NORM)
                .displayName("我")
                .mentionCount(0)
                .relationCount(0)
                .confidence(1.0)
                .importance(1.0)
                .matchAllowed(false)
                .hidden(false)
                .origin(LifeGraphEntity.Origin.MANUAL)
                .build());
        LifeGraphEntity person = entityRepository.save(LifeGraphEntity.builder()
                .userId(scenario.userId())
                .type(LifeGraphEntity.EntityType.Person)
                .nameNorm("fixture-person-b")
                .displayName("fixture-person-b")
                .mentionCount(0)
                .relationCount(0)
                .confidence(0.9)
                .importance(0.8)
                .matchAllowed(false)
                .hidden(false)
                .origin(LifeGraphEntity.Origin.MANUAL)
                .build());
        entityRepository.flush();

        relationRepository.saveAndFlush(LifeGraphRelation.builder()
                .userId(scenario.userId())
                .sourceId(Math.min(user.getId(), person.getId()))
                .targetId(Math.max(user.getId(), person.getId()))
                .semanticSourceId(user.getId())
                .semanticTargetId(person.getId())
                .type("PARTNER_OF")
                .confidence(BigDecimal.valueOf(0.9))
                .weight(1)
                .manualWeight(1)
                .origin(LifeGraphRelation.Origin.MANUAL)
                .build());
    }

    private Set<String> findConfirmedImportantPersonKeysFromH2(String userId) {
        Set<String> result = new LinkedHashSet<>();
        for (LifeGraphRelation relation : relationRepository.findByUserId(userId)) {
            String type = relation.getType() == null
                    ? "" : relation.getType().trim().toUpperCase(Locale.ROOT);
            if (!promotionPolicy.personRelations().contains(type)) {
                continue;
            }
            Long sourceId = relation.getSemanticSourceId() == null
                    ? relation.getSourceId() : relation.getSemanticSourceId();
            Long targetId = relation.getSemanticTargetId() == null
                    ? relation.getTargetId() : relation.getSemanticTargetId();
            LifeGraphEntity source = entityRepository.findByIdAndUserId(sourceId, userId).orElse(null);
            LifeGraphEntity target = entityRepository.findByIdAndUserId(targetId, userId).orElse(null);
            if (source != null && target != null
                    && source.getType() == LifeGraphEntity.EntityType.User
                    && target.getType() == LifeGraphEntity.EntityType.Person) {
                result.add(target.getNameNorm());
            } else if (source != null && target != null
                    && target.getType() == LifeGraphEntity.EntityType.User
                    && source.getType() == LifeGraphEntity.EntityType.Person) {
                result.add(source.getNameNorm());
            }
        }
        return result;
    }

    private void configureExtractor(LifeGraphExtractionResult extraction) throws IOException {
        when(extractor.extract(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString())).thenReturn(objectMapper.writeValueAsString(extraction));
    }

    private void flushGraph() {
        entityEvidenceRepository.flush();
        relationEvidenceRepository.flush();
        mentionRepository.flush();
        relationRepository.flush();
        entityRepository.flush();
    }

    private Snapshot snapshot(Scenario scenario) {
        List<LifeGraphEntity> entities = entityRepository.findByUserId(scenario.userId());
        List<LifeGraphRelation> relations = relationRepository.findByUserId(scenario.userId());
        int entityEvidenceCount = entityEvidenceRepository
                .findByUserIdAndSourceTypeAndSourceId(
                        scenario.userId(), DIARY_SOURCE_TYPE, scenario.sourceId()).size();
        int relationEvidenceCount = relationEvidenceRepository
                .findByUserIdAndSourceTypeAndSourceId(
                        scenario.userId(), DIARY_SOURCE_TYPE, scenario.sourceId()).size();
        int mentionCount = mentionRepository
                .findByUserIdAndDiaryId(scenario.userId(), scenario.sourceId()).size();
        return new Snapshot(
                entities.size(),
                (int) entities.stream().filter(item -> item.getType() == LifeGraphEntity.EntityType.User).count(),
                (int) entities.stream().filter(item -> item.getType() == LifeGraphEntity.EntityType.Person).count(),
                (int) entities.stream().filter(item -> item.getOrigin() == LifeGraphEntity.Origin.AUTO).count(),
                relations.size(),
                (int) relations.stream().filter(item -> item.getOrigin() == LifeGraphRelation.Origin.AUTO).count(),
                countEntityEvidence(scenario.userId()),
                countRelationEvidence(scenario.userId()),
                countMentions(scenario.userId()),
                entityEvidenceCount,
                relationEvidenceCount,
                mentionCount);
    }

    private int countEntityEvidence(String userId) {
        return entityRepository.findByUserId(userId).stream()
                .filter(entity -> entity.getId() != null)
                .mapToInt(entity -> entityEvidenceRepository
                        .findByUserIdAndEntityId(userId, entity.getId()).size())
                .sum();
    }

    private int countRelationEvidence(String userId) {
        return relationRepository.findByUserId(userId).stream()
                .filter(relation -> relation.getId() != null)
                .mapToInt(relation -> relationEvidenceRepository
                        .findByUserIdAndRelationId(userId, relation.getId()).size())
                .sum();
    }

    private int countMentions(String userId) {
        return entityRepository.findByUserId(userId).stream()
                .filter(entity -> entity.getId() != null)
                .mapToInt(entity -> mentionRepository
                        .findByUserIdAndEntityId(userId, entity.getId()).size())
                .sum();
    }

    private boolean entityExists(String userId, String key) {
        return entityRepository.findByUserIdAndNameNorm(userId,
                        promotionPolicy.normalizeKey(key, null)).stream()
                .findAny().isPresent();
    }

    private boolean relationExists(String userId, String sourceKey, String targetKey,
                                   String type, int expectedWeight) {
        LifeGraphEntity source = findEntity(userId, sourceKey);
        LifeGraphEntity target = findEntity(userId, targetKey);
        if (source == null || target == null) {
            return false;
        }
        return relationRepository.findByUserId(userId).stream().anyMatch(relation ->
                relation.getSemanticSourceId() != null
                        && relation.getSemanticTargetId() != null
                        && relation.getSemanticSourceId().equals(source.getId())
                        && relation.getSemanticTargetId().equals(target.getId())
                        && type.equals(relation.getType())
                        && expectedWeight == relation.getWeight());
    }

    private LifeGraphEntity findEntity(String userId, String key) {
        return entityRepository.findByUserIdAndNameNorm(
                        userId, promotionPolicy.normalizeKey(key, null)).stream()
                .findFirst().orElse(null);
    }

    private boolean relationTypesAbsent(List<LifeGraphRelation> relations, Set<String> types) {
        return relations.stream().noneMatch(relation -> types.contains(relation.getType()));
    }

    private String relationKey(LifeGraphExtractionResult.ExtractedRelation relation) {
        return promotionPolicy.normalizeKey(relation.getSource(), null)
                + "|" + promotionPolicy.normalizeKey(relation.getTarget(), null)
                + "|" + relation.getType().trim().toUpperCase(Locale.ROOT);
    }

    private OfflineEvaluationReportWriter.CaseResult caseResult(
            String caseId, Scenario scenario, Checks checks, Snapshot snapshot) {
        return new OfflineEvaluationReportWriter.CaseResult(
                caseId,
                scenario.scenarioId(),
                checks.hasFailures() ? "FAIL" : "PASS",
                FIXTURE_VERSION,
                EXPECTATION_VERSION,
                OfflineEvaluationReportWriter.Versions.fixtureBaseline(),
                checks.assertionCount,
                checks.passedAssertionCount,
                checks.violationCodes,
                Map.ofEntries(
                        Map.entry("entityCount", snapshot.entityCount),
                        Map.entry("userEntityCount", snapshot.userEntityCount),
                        Map.entry("personEntityCount", snapshot.personEntityCount),
                        Map.entry("autoEntityCount", snapshot.autoEntityCount),
                        Map.entry("relationCount", snapshot.relationCount),
                        Map.entry("autoRelationCount", snapshot.autoRelationCount),
                        Map.entry("entityEvidenceCount", snapshot.entityEvidenceCount),
                        Map.entry("relationEvidenceCount", snapshot.relationEvidenceCount),
                        Map.entry("mentionCount", snapshot.mentionCount),
                        Map.entry("sourceEntityEvidenceCount", snapshot.sourceEntityEvidenceCount),
                        Map.entry("sourceRelationEvidenceCount", snapshot.sourceRelationEvidenceCount),
                        Map.entry("sourceMentionCount", snapshot.sourceMentionCount)));
    }

    private OfflineEvaluationReportWriter.CaseResult failedCase(
            String caseId, String scenarioId, String code) {
        return new OfflineEvaluationReportWriter.CaseResult(
                caseId, scenarioId, "FAIL", FIXTURE_VERSION, EXPECTATION_VERSION,
                OfflineEvaluationReportWriter.Versions.fixtureBaseline(), 1, 0, List.of(code),
                Map.ofEntries(
                        Map.entry("entityCount", 0),
                        Map.entry("userEntityCount", 0),
                        Map.entry("personEntityCount", 0),
                        Map.entry("autoEntityCount", 0),
                        Map.entry("relationCount", 0),
                        Map.entry("autoRelationCount", 0),
                        Map.entry("entityEvidenceCount", 0),
                        Map.entry("relationEvidenceCount", 0),
                        Map.entry("mentionCount", 0),
                        Map.entry("sourceEntityEvidenceCount", 0),
                        Map.entry("sourceRelationEvidenceCount", 0),
                        Map.entry("sourceMentionCount", 0)));
    }

    private record Snapshot(
            int entityCount,
            int userEntityCount,
            int personEntityCount,
            int autoEntityCount,
            int relationCount,
            int autoRelationCount,
            int entityEvidenceCount,
            int relationEvidenceCount,
            int mentionCount,
            int sourceEntityEvidenceCount,
            int sourceRelationEvidenceCount,
            int sourceMentionCount) {
    }

    private static final class Checks {
        private final List<String> violationCodes = new ArrayList<>();
        private int assertionCount;
        private int passedAssertionCount;

        private void check(String code, boolean condition) {
            assertionCount++;
            if (condition) {
                passedAssertionCount++;
            } else {
                violationCodes.add(code);
            }
        }

        private void violate(String code) {
            assertionCount++;
            violationCodes.add(code);
        }

        private boolean hasFailures() {
            return !violationCodes.isEmpty();
        }
    }
}
