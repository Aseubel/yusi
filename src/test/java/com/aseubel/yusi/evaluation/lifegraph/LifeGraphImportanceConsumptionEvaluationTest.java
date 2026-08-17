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
import com.aseubel.yusi.pojo.entity.MatchProfile;
import com.aseubel.yusi.pojo.entity.MidTermMemory;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.LifeGraphEntityEvidenceRepository;
import com.aseubel.yusi.repository.LifeGraphEntityRepository;
import com.aseubel.yusi.repository.LifeGraphMentionRepository;
import com.aseubel.yusi.repository.LifeGraphRelationEvidenceRepository;
import com.aseubel.yusi.repository.LifeGraphRelationRepository;
import com.aseubel.yusi.repository.MatchProfileRepository;
import com.aseubel.yusi.repository.MidTermMemoryRepository;
import com.aseubel.yusi.repository.UserRepository;
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
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.milvus.v2.client.MilvusClientV2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.aseubel.yusi.evaluation.lifegraph.LifeGraphPromotionEvaluationFixture.Scenario;
import static com.aseubel.yusi.evaluation.lifegraph.LifeGraphPromotionEvaluationFixture.Suite;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestInfrastructureConfig.class)
class LifeGraphImportanceConsumptionEvaluationTest {

    private static final String FIXTURE_VERSION = "fixture-v1";
    private static final String EXPECTATION_VERSION = "importance-lexicographic-v1";
    private static final String ISOLATED_USER_ID = "fixture-user-importance-b";
    private static final String ISOLATED_SOURCE_ID = "fixture-diary-importance-b";
    private static final String DIARY_SOURCE_TYPE = SourceType.DIARY.code();
    private static final Path REPORT_PATH = Path.of(
            "target", "evaluation", "lifegraph-importance-v1-report.json");

    private final LifeGraphPromotionPolicy promotionPolicy = new LifeGraphPromotionPolicy();

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

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

    @Autowired
    private MatchProfileRepository matchProfileRepository;

    @Autowired
    private MidTermMemoryRepository midTermMemoryRepository;

    @Autowired
    private com.aseubel.yusi.service.match.MatchProfileAssembler matchProfileAssembler;

    @MockBean
    private PromptManager promptManager;

    @MockBean
    private LifeGraphExtractor extractor;

    @MockBean(name = "embeddingModel")
    private EmbeddingModel embeddingModel;

    @MockBean(name = "milvusClientV2")
    private MilvusClientV2 milvusClientV2;

    @BeforeEach
    void resetExternalBoundaries() {
        reset(promptManager, extractor);
        when(promptManager.getSnapshot(PromptKey.GRAPHRAG_EXTRACT))
                .thenReturn(new PromptSnapshot("graphrag-extract", FIXTURE_VERSION, "zh-CN", "fixture"));
        when(embeddingModel.embed(anyString()))
                .thenReturn(Response.from(Embedding.from(new float[] {0.1f})));
    }

    @Test
    void writesTheLifeGraphImportanceEvaluationReport() throws Exception {
        List<OfflineEvaluationReportWriter.CaseResult> results = new ArrayList<>();
        Files.deleteIfExists(REPORT_PATH);
        try {
            Suite suite = new LifeGraphPromotionFixtureLoader(objectMapper).load();
            Scenario sourceScenario = findScenario(suite);
            results.add(replayScenario(isolatedScenario(sourceScenario)));
        } catch (Exception exception) {
            String code = exception instanceof LifeGraphPromotionFixtureLoader.FixtureValidationException
                    ? "FIXTURE_INVALID" : "REPLAY_EXECUTION";
            results.add(failedCase("EVAL-MEM-003", "EVAL-MEM-003-B", code));
        } finally {
            OfflineEvaluationReportWriter.write(REPORT_PATH, "lifegraph-importance-v1", results);
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

    private Scenario findScenario(Suite suite) {
        return suite.cases().stream()
                .filter(item -> "EVAL-MEM-003".equals(item.caseId()))
                .flatMap(item -> item.scenarios().stream())
                .filter(item -> "EVAL-MEM-003-B".equals(item.scenarioId()))
                .findFirst()
                .orElseThrow();
    }

    private Scenario isolatedScenario(Scenario source) {
        return new Scenario(
                source.scenarioId(),
                ISOLATED_USER_ID,
                ISOLATED_SOURCE_ID,
                source.confirmedImportantPersonKeys(),
                source.extraction(),
                source.expected());
    }

    private OfflineEvaluationReportWriter.CaseResult replayScenario(Scenario scenario) {
        Checks checks = new Checks();
        try {
            LifeGraphPromotionPolicy.PromotionResult policyResult = promotionPolicy.promote(
                    scenario.extraction(), Set.copyOf(scenario.confirmedImportantPersonKeys()));
            evaluatePolicy(scenario, policyResult, checks);

            seedConfirmedPerson(scenario.userId());
            Set<String> confirmedFromH2 = findConfirmedImportantPersonKeysFromH2(scenario.userId());
            checks.check("CONFIRMED_PERSON_POSITIVE_CONTROL",
                    !confirmedFromH2.isEmpty() && confirmedFromH2.contains("fixture-person-b"));
            if (confirmedFromH2.isEmpty() || !confirmedFromH2.contains("fixture-person-b")) {
                return caseResult(scenario, checks);
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
            evaluatePromotionH2(scenario, checks);

            userRepository.saveAndFlush(User.builder().userId(scenario.userId()).build());
            prepareMatchableProjection(scenario.userId());

            List<LifeGraphEntity> candidates = entityRepository.findMatchableTopByUserId(
                    scenario.userId(), LocalDateTime.now(), PageRequest.of(0, 1));
            checks.check("MATCHABLE_IMPORTANCE_ORDER",
                    candidates.size() == 1
                            && "fixture-person-b".equals(candidates.get(0).getNameNorm()));

            persistMidTermMemoryControl(scenario.userId());
            MatchProfile profile = matchProfileAssembler.refreshProfile(scenario.userId());
            checks.check("PROFILE_IMPORTANCE_ORDER",
                    ordered(profile == null ? null : profile.getLifeGraphSummary(),
                            "fixture-person-b", "fixture-event-b"));
            checks.check("MID_MEMORY_DECAY_CONTROL",
                    ordered(profile == null ? null : profile.getMidMemorySummary(),
                            "fixture-mid-recent", "fixture-mid-old"));
            checks.check("PROFILE_PERSISTED_FROM_REAL_H2",
                    matchProfileRepository.findByUserId(scenario.userId()).isPresent());
        } catch (Exception exception) {
            checks.violate("REPLAY_EXECUTION");
        }
        return caseResult(scenario, checks);
    }

    private void evaluatePolicy(Scenario scenario,
                                LifeGraphPromotionPolicy.PromotionResult result,
                                Checks checks) {
        Set<String> actualEntityKeys = result.acceptedEntityKeys().stream()
                .map(key -> promotionPolicy.normalizeKey(key, null))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> actualRelationKeys = result.relations().stream()
                .map(this::relationKey)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        checks.check("PROMOTION_POLICY_ENTITY_BOUNDARY",
                actualEntityKeys.equals(scenario.expected().acceptedEntityKeys()));
        checks.check("PROMOTION_POLICY_RELATION_BOUNDARY",
                actualRelationKeys.equals(scenario.expected().acceptedRelationKeys()));
        checks.check("PROMOTION_POLICY_REJECTED_RELATIONS",
                scenario.expected().rejectedRelationKeys().stream().noneMatch(actualRelationKeys::contains));
    }

    private void evaluatePromotionH2(Scenario scenario, Checks checks) {
        List<LifeGraphEntityEvidence> entityEvidence = entityEvidenceRepository
                .findByUserIdAndSourceTypeAndSourceId(
                        scenario.userId(), DIARY_SOURCE_TYPE, scenario.sourceId());
        List<LifeGraphRelationEvidence> relationEvidence = relationEvidenceRepository
                .findByUserIdAndSourceTypeAndSourceId(
                        scenario.userId(), DIARY_SOURCE_TYPE, scenario.sourceId());
        List<LifeGraphMention> mentions = mentionRepository
                .findByUserIdAndDiaryId(scenario.userId(), scenario.sourceId());
        checks.check("PROMOTION_H2_EVIDENCE_BOUNDARY",
                entityEvidence.size() == scenario.expected().sourceEntityEvidenceCount()
                        && relationEvidence.size() == scenario.expected().sourceRelationEvidenceCount()
                        && mentions.size() == scenario.expected().sourceMentionCount());

        List<LifeGraphEntity> entities = entityRepository.findByUserId(scenario.userId());
        boolean entityBoundary = entities.size() == 4
                && scenario.expected().acceptedEntityKeys().stream()
                .allMatch(key -> entityExists(scenario.userId(), key))
                && entities.stream().noneMatch(entity -> "fixture-person-c".equals(entity.getNameNorm()));
        checks.check("PROMOTION_H2_ENTITY_BOUNDARY", entityBoundary);

        List<LifeGraphRelation> relations = relationRepository.findByUserId(scenario.userId());
        boolean relationBoundary = relations.size() == 3
                && relations.stream().filter(item -> item.getOrigin() == LifeGraphRelation.Origin.MANUAL).count() == 1
                && relations.stream().filter(item -> item.getOrigin() == LifeGraphRelation.Origin.AUTO).count() == 2
                && relationExists(scenario.userId(),
                "fixture-person-b", "fixture-item-b", "LIKES", 1)
                && relationExists(scenario.userId(),
                "fixture-person-b", "fixture-event-b", "PARTICIPATED_IN", 1)
                && relationTypesAbsent(relations, Set.of("FRIEND_OF"));
        checks.check("PROMOTION_H2_RELATION_BOUNDARY", relationBoundary);
    }

    private void seedConfirmedPerson(String userId) {
        LifeGraphEntity user = entityRepository.save(LifeGraphEntity.builder()
                .userId(userId)
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
                .userId(userId)
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
                .userId(userId)
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

    private void prepareMatchableProjection(String userId) {
        LifeGraphEntity person = findEntity(userId, "fixture-person-b");
        LifeGraphEntity event = findEntity(userId, "fixture-event-b");
        LifeGraphEntity item = findEntity(userId, "fixture-item-b");

        person.setMatchAllowed(true);
        person.setImportance(0.8);
        person.setMentionCount(1);

        event.setMatchAllowed(true);
        event.setImportance(0.5);
        event.setMentionCount(9);

        item.setMatchAllowed(true);
        item.setImportance(0.5);
        item.setMentionCount(2);

        entityRepository.saveAllAndFlush(List.of(person, event, item));
    }

    private void persistMidTermMemoryControl(String userId) {
        LocalDateTime now = LocalDateTime.now();
        midTermMemoryRepository.saveAllAndFlush(List.of(
                memory(userId, "fixture-mid-recent", 0.7, now.minusHours(1)),
                memory(userId, "fixture-mid-old", 1.0, now.minusDays(365))));
    }

    private MidTermMemory memory(String userId, String summary, double importance, LocalDateTime createdAt) {
        return MidTermMemory.builder()
                .userId(userId)
                .sourceType("FIXTURE")
                .sourceId("fixture-memory-" + summary)
                .summary(summary)
                .importance(importance)
                .confidence(0.9)
                .matchAllowed(true)
                .hidden(false)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
    }

    private void flushGraph() {
        entityEvidenceRepository.flush();
        relationEvidenceRepository.flush();
        mentionRepository.flush();
        relationRepository.flush();
        entityRepository.flush();
    }

    private boolean entityExists(String userId, String key) {
        return entityRepository.findByUserIdAndNameNorm(userId,
                        promotionPolicy.normalizeKey(key, null)).stream()
                .findAny().isPresent();
    }

    private LifeGraphEntity findEntity(String userId, String key) {
        return entityRepository.findByUserIdAndNameNorm(
                        userId, promotionPolicy.normalizeKey(key, null)).stream()
                .findFirst().orElseThrow();
    }

    private boolean relationExists(String userId, String sourceKey, String targetKey,
                                   String type, int expectedWeight) {
        LifeGraphEntity source = findEntity(userId, sourceKey);
        LifeGraphEntity target = findEntity(userId, targetKey);
        return relationRepository.findByUserId(userId).stream().anyMatch(relation ->
                relation.getSemanticSourceId() != null
                        && relation.getSemanticTargetId() != null
                        && relation.getSemanticSourceId().equals(source.getId())
                        && relation.getSemanticTargetId().equals(target.getId())
                        && type.equals(relation.getType())
                        && expectedWeight == relation.getWeight());
    }

    private boolean relationTypesAbsent(List<LifeGraphRelation> relations, Set<String> types) {
        return relations.stream().noneMatch(relation -> types.contains(relation.getType()));
    }

    private String relationKey(LifeGraphExtractionResult.ExtractedRelation relation) {
        return promotionPolicy.normalizeKey(relation.getSource(), null)
                + "|" + promotionPolicy.normalizeKey(relation.getTarget(), null)
                + "|" + relation.getType().trim().toUpperCase(Locale.ROOT);
    }

    private boolean ordered(String value, String first, String second) {
        return value != null
                && value.contains(first)
                && value.contains(second)
                && value.indexOf(first) < value.indexOf(second);
    }

    private OfflineEvaluationReportWriter.CaseResult caseResult(
            Scenario scenario, Checks checks) {
        ActualSummary actual = actualSummary(scenario.userId(), scenario.sourceId());
        return new OfflineEvaluationReportWriter.CaseResult(
                "EVAL-MEM-003",
                scenario.scenarioId(),
                checks.hasFailures() ? "FAIL" : "PASS",
                FIXTURE_VERSION,
                EXPECTATION_VERSION,
                importanceVersions(),
                checks.assertionCount,
                checks.passedAssertionCount,
                checks.violationCodes,
                Map.ofEntries(
                        Map.entry("entityCount", actual.entityCount),
                        Map.entry("relationCount", actual.relationCount),
                        Map.entry("entityEvidenceCount", actual.entityEvidenceCount),
                        Map.entry("relationEvidenceCount", actual.relationEvidenceCount),
                        Map.entry("mentionCount", actual.mentionCount),
                        Map.entry("promotionH2BoundaryPassCount",
                                checks.passCount("PROMOTION_H2_ENTITY_BOUNDARY")
                                        + checks.passCount("PROMOTION_H2_RELATION_BOUNDARY")
                                        + checks.passCount("PROMOTION_H2_EVIDENCE_BOUNDARY")),
                        Map.entry("matchableCandidateImportancePassCount",
                                checks.passCount("MATCHABLE_IMPORTANCE_ORDER")),
                        Map.entry("matchProfileImportancePassCount",
                                checks.passCount("PROFILE_IMPORTANCE_ORDER")),
                        Map.entry("midMemoryDecayControlPassCount",
                                checks.passCount("MID_MEMORY_DECAY_CONTROL"))));
    }

    private OfflineEvaluationReportWriter.Versions importanceVersions() {
        return new OfflineEvaluationReportWriter.Versions(
                new OfflineEvaluationReportWriter.ModelVersion("fixture", "none", FIXTURE_VERSION),
                new OfflineEvaluationReportWriter.PromptVersion("fixture", FIXTURE_VERSION, "zh-CN"),
                new OfflineEvaluationReportWriter.StrategyVersion("not_applicable", FIXTURE_VERSION),
                new OfflineEvaluationReportWriter.StrategyVersion(
                        "lifegraph-importance-lexicographic", "v1"));
    }

    private OfflineEvaluationReportWriter.CaseResult failedCase(
            String caseId, String scenarioId, String code) {
        return new OfflineEvaluationReportWriter.CaseResult(
                caseId,
                scenarioId,
                "FAIL",
                FIXTURE_VERSION,
                EXPECTATION_VERSION,
                importanceVersions(),
                1,
                0,
                List.of(code),
                Map.ofEntries(
                        Map.entry("entityCount", 0),
                        Map.entry("relationCount", 0),
                        Map.entry("entityEvidenceCount", 0),
                        Map.entry("relationEvidenceCount", 0),
                        Map.entry("mentionCount", 0),
                        Map.entry("promotionH2BoundaryPassCount", 0),
                        Map.entry("matchableCandidateImportancePassCount", 0),
                        Map.entry("matchProfileImportancePassCount", 0),
                        Map.entry("midMemoryDecayControlPassCount", 0)));
    }

    private ActualSummary actualSummary(String userId, String sourceId) {
        return new ActualSummary(
                entityRepository.findByUserId(userId).size(),
                relationRepository.findByUserId(userId).size(),
                entityEvidenceRepository.findByUserIdAndSourceTypeAndSourceId(
                        userId, DIARY_SOURCE_TYPE, sourceId).size(),
                relationEvidenceRepository.findByUserIdAndSourceTypeAndSourceId(
                        userId, DIARY_SOURCE_TYPE, sourceId).size(),
                mentionRepository.findByUserIdAndDiaryId(userId, sourceId).size());
    }

    private record ActualSummary(
            int entityCount,
            int relationCount,
            int entityEvidenceCount,
            int relationEvidenceCount,
            int mentionCount) {
    }

    private static final class Checks {
        private final List<String> violationCodes = new ArrayList<>();
        private final Map<String, Boolean> outcomes = new LinkedHashMap<>();
        private int assertionCount;
        private int passedAssertionCount;

        private void check(String code, boolean condition) {
            assertionCount++;
            outcomes.merge(code, condition, (previous, current) -> previous && current);
            if (condition) {
                passedAssertionCount++;
            } else {
                violationCodes.add(code);
            }
        }

        private void violate(String code) {
            assertionCount++;
            outcomes.put(code, false);
            violationCodes.add(code);
        }

        private int passCount(String code) {
            return Boolean.TRUE.equals(outcomes.get(code)) ? 1 : 0;
        }

        private boolean hasFailures() {
            return !violationCodes.isEmpty();
        }
    }
}
