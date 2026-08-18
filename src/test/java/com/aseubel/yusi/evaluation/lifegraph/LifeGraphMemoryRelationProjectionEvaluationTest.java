package com.aseubel.yusi.evaluation.lifegraph;

import com.aseubel.yusi.TestInfrastructureConfig;
import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.evaluation.OfflineEvaluationReportWriter;
import com.aseubel.yusi.pojo.dto.memory.LifeGraphMemoryItem;
import com.aseubel.yusi.pojo.dto.memory.LifeGraphMemoryResponse;
import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import com.aseubel.yusi.pojo.entity.LifeGraphRelation;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.LifeGraphEntityRepository;
import com.aseubel.yusi.repository.LifeGraphRelationEvidenceRepository;
import com.aseubel.yusi.repository.LifeGraphRelationRepository;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.service.ai.prompt.PromptManager;
import com.aseubel.yusi.service.ai.prompt.PromptSnapshot;
import com.aseubel.yusi.service.lifegraph.ai.LifeGraphExtractor;
import com.aseubel.yusi.service.lifegraph.constant.LifeGraphConstants;
import com.aseubel.yusi.service.lifegraph.constant.LifeGraphRelationType;
import com.aseubel.yusi.service.memory.LifeGraphLifecycleService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.milvus.v2.client.MilvusClientV2;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
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
class LifeGraphMemoryRelationProjectionEvaluationTest {

    private static final String FIXTURE_VERSION = "fixture-v1";
    private static final String EXPECTATION_VERSION = "memory-relation-projection-v1";
    private static final String USER_ID = "fixture-user-promotion-b";
    private static final String SOURCE_ID = "fixture-diary-promotion-b";
    private static final String OTHER_USER_ID = "fixture-user-relation-other-b";
    private static final String REPORT_NAME = "lifegraph-memory-relation-v1-report.json";
    private static final Path REPORT_PATH = Path.of("target", "evaluation", REPORT_NAME);
    private static final Set<String> PERSON_RELATION_CODES = Set.of(
            "PARTNER_OF", "FAMILY_OF", "FRIEND_OF", "COLLEAGUE_OF",
            "MENTOR_OF", "SIBLING_OF", "PARENT_OF", "CHILD_OF");

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LifeGraphEntityRepository entityRepository;

    @Autowired
    private LifeGraphRelationRepository relationRepository;

    @Autowired
    private LifeGraphRelationEvidenceRepository relationEvidenceRepository;

    @Autowired
    private LifeGraphLifecycleService lifeGraphLifecycleService;

    @PersistenceContext
    private EntityManager entityManager;

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
    void writesTheLifeGraphMemoryRelationProjectionReport() throws Exception {
        List<OfflineEvaluationReportWriter.CaseResult> results = new ArrayList<>();
        Files.deleteIfExists(REPORT_PATH);
        try {
            Suite suite = new LifeGraphPromotionFixtureLoader(objectMapper).load();
            Scenario scenario = findScenario(suite);
            results.add(replayScenario(scenario));
        } catch (LifeGraphPromotionFixtureLoader.FixtureValidationException exception) {
            results.add(failedCase("EVAL-MEM-003", "EVAL-MEM-003-B", "FIXTURE_INVALID"));
        } catch (Exception exception) {
            results.add(failedCase("EVAL-MEM-003", "EVAL-MEM-003-B", "REPLAY_EXECUTION"));
        } finally {
            OfflineEvaluationReportWriter.write(
                    REPORT_PATH, "lifegraph-memory-relation-v1", results);
        }

        assertFalse(results.isEmpty());
        assertTrue(results.stream().allMatch(result -> "PASS".equals(result.status())),
                () -> results.stream().flatMap(result -> result.violationCodes().stream()).toList().toString());
        assertTrue(Files.exists(REPORT_PATH));
        assertLowSensitivityReport();
    }

    private Scenario findScenario(Suite suite) {
        return suite.cases().stream()
                .filter(item -> "EVAL-MEM-003".equals(item.caseId()))
                .flatMap(item -> item.scenarios().stream())
                .filter(item -> "EVAL-MEM-003-B".equals(item.scenarioId()))
                .findFirst()
                .orElseThrow();
    }

    private OfflineEvaluationReportWriter.CaseResult replayScenario(Scenario scenario) {
        Checks checks = new Checks();
        String stage = "SEED";
        try {
            checks.check("FIXTURE_NATIVE_IDENTITY",
                    USER_ID.equals(scenario.userId()) && SOURCE_ID.equals(scenario.sourceId()));
            Seed seed = seedProjectionRows();

            stage = "FIRST_LIST";
            LifeGraphMemoryResponse firstResponse = lifeGraphLifecycleService.list(USER_ID, 50);
            LifeGraphMemoryItem personItem = findItem(firstResponse, seed.personId());
            LifeGraphEntity userEntity = entityRepository.findById(seed.userEntityId()).orElse(null);
            List<LifeGraphRelation> relationRows = relationRepository.findByUserId(USER_ID);
            LifeGraphRelation expected = chooseRepresentativeRelation(
                    directCandidates(relationRows, userEntity == null ? null : userEntity.getId(), seed.personId()));

            checks.check("RELATION_FACT_MATCH", expected != null && personItem != null
                    && expected.getType().equals(personItem.getRelationToUser())
                    && expected.getOrigin().name().equals(personItem.getRelationOrigin()));
            checks.check("IMPORTANCE_TRANSPARENT", personItem != null
                    && Double.compare(0.8, personItem.getImportance()) == 0);

            LifeGraphMemoryItem topicItem = findItem(firstResponse, seed.topicId());
            checks.check("NON_PERSON_RELATION_HIDDEN", topicItem != null
                    && topicItem.getRelationToUser() == null && topicItem.getRelationOrigin() == null);
            checks.check("CROSS_USER_RELATION_FILTER", expected != null
                    && directCandidates(relationRows, seed.otherUserEntityId(), seed.personId()).stream()
                    .anyMatch(relation -> relation.getType().equals("PARTNER_OF"))
                    && expected.getSemanticSourceId().equals(seed.userEntityId()));

            stage = "DELETE_AND_REREAD";
            relationRepository.deleteById(seed.deletionRelationId());
            relationRepository.flush();
            entityManager.clear();
            LifeGraphMemoryItem afterDelete = findItem(
                    lifeGraphLifecycleService.list(USER_ID, 50), seed.deletionPersonId());
            checks.check("RELATION_DELETE_SYNC", afterDelete != null
                    && afterDelete.getRelationToUser() == null && afterDelete.getRelationOrigin() == null);
            checks.check("MEMORY_LOW_SENSITIVITY", Arrays.stream(LifeGraphMemoryItem.class.getDeclaredFields())
                    .map(Field::getName)
                    .noneMatch(Set.of("evidenceSnippet", "snippet", "props", "evidenceDiaryId")::contains));
        } catch (ReplayStageException exception) {
            checks.violate("REPLAY_" + exception.stage);
        } catch (Exception exception) {
            checks.violate("REPLAY_" + stage);
        }
        return caseResult(scenario, checks);
    }

    private Seed seedProjectionRows() {
        String stage = "USER_RECORDS";
        try {
            ensureUserRecord(USER_ID);
            ensureUserRecord(OTHER_USER_ID);

            stage = "ENTITIES";
            LifeGraphEntity userEntity = ensureEntity(
                    USER_ID, LifeGraphEntity.EntityType.User, LifeGraphConstants.USER_ENTITY_NORM, "我", 1.0);
            LifeGraphEntity person = ensureEntity(
                    USER_ID, LifeGraphEntity.EntityType.Person, "fixture-person-b", "fixture-person-b", 0.8);
            LifeGraphEntity deletionPerson = ensureEntity(
                    USER_ID, LifeGraphEntity.EntityType.Person,
                    "fixture-person-relation-deletion-b", "fixture-person-relation-deletion-b", 0.4);
            LifeGraphEntity topic = ensureEntity(
                    USER_ID, LifeGraphEntity.EntityType.Topic, "fixture-topic-relation-b", "fixture-topic-relation-b", 0.3);
            LifeGraphEntity otherUser = ensureEntity(
                    OTHER_USER_ID, LifeGraphEntity.EntityType.User, LifeGraphConstants.USER_ENTITY_NORM, "我", 1.0);
            ensureEntity(OTHER_USER_ID, LifeGraphEntity.EntityType.Person, "fixture-person-b", "fixture-person-b", 0.8);

            stage = "RELATION_CLEANUP";
            removeRelationsBetween(USER_ID, userEntity.getId(), person.getId());
            removeRelationsBetween(USER_ID, userEntity.getId(), deletionPerson.getId());
            removeRelationsBetween(USER_ID, otherUser.getId(), person.getId());

            stage = "RELATION_SEED";
            LocalDateTime now = LocalDateTime.of(2026, 8, 18, 12, 0);
            stage = "FAMILY_RELATION_SEED";
            saveRelation(USER_ID, userEntity.getId(), person.getId(), "FAMILY_OF",
                    LifeGraphRelation.Origin.MANUAL, now.minusDays(2));
            stage = "AUTO_RELATION_SEED";
            saveRelation(USER_ID, person.getId(), userEntity.getId(), "PARTNER_OF",
                    LifeGraphRelation.Origin.AUTO, now);
            stage = "DELETE_RELATION_SEED";
            LifeGraphRelation deletionRelation = saveRelation(USER_ID, userEntity.getId(), deletionPerson.getId(),
                    "FRIEND_OF", LifeGraphRelation.Origin.MANUAL, now);
            stage = "FOREIGN_RELATION_SEED";
            saveRelation(USER_ID, otherUser.getId(), person.getId(), "PARTNER_OF",
                    LifeGraphRelation.Origin.MANUAL, now.plusDays(1));
            stage = "RELATION_FLUSH";
            relationRepository.flush();
            entityManager.clear();

            return new Seed(userEntity.getId(), person.getId(), deletionPerson.getId(), topic.getId(),
                    otherUser.getId(), deletionRelation.getId());
        } catch (RuntimeException exception) {
            throw new ReplayStageException(stage);
        }
    }

    private void ensureUserRecord(String userId) {
        if (userRepository.findByUserId(userId) == null) {
            userRepository.saveAndFlush(User.builder().userId(userId).build());
        }
    }

    private LifeGraphEntity ensureEntity(String userId, LifeGraphEntity.EntityType type,
            String nameNorm, String displayName, double importance) {
        LifeGraphEntity entity = entityRepository.findByUserIdAndTypeAndNameNorm(userId, type, nameNorm)
                .orElseGet(() -> LifeGraphEntity.builder()
                        .userId(userId)
                        .type(type)
                        .nameNorm(nameNorm)
                        .displayName(displayName)
                        .build());
        entity.setDisplayName(displayName);
        entity.setMentionCount(type == LifeGraphEntity.EntityType.User ? 0 : 1);
        entity.setRelationCount(0);
        entity.setImportance(importance);
        entity.setConfidence(0.9);
        entity.setMatchAllowed(false);
        entity.setHidden(false);
        return entityRepository.saveAndFlush(entity);
    }

    private void removeRelationsBetween(String userId, Long firstId, Long secondId) {
        List<LifeGraphRelation> existing = relationRepository.findByUserId(userId).stream()
                .filter(relation -> connects(relation, firstId, secondId))
                .toList();
        for (LifeGraphRelation relation : existing) {
            if (relation.getId() != null) {
                relationEvidenceRepository.deleteByUserIdAndRelationId(userId, relation.getId());
            }
        }
        if (!existing.isEmpty()) {
            relationRepository.deleteAll(existing);
            relationRepository.flush();
        }
    }

    private boolean connects(LifeGraphRelation relation, Long firstId, Long secondId) {
        Long sourceId = semanticSourceId(relation);
        Long targetId = semanticTargetId(relation);
        return (Objects.equals(sourceId, firstId) && Objects.equals(targetId, secondId))
                || (Objects.equals(sourceId, secondId) && Objects.equals(targetId, firstId));
    }

    private LifeGraphRelation saveRelation(String userId, Long semanticSourceId, Long semanticTargetId,
            String type, LifeGraphRelation.Origin origin, LocalDateTime updatedAt) {
        return relationRepository.saveAndFlush(LifeGraphRelation.builder()
                .userId(userId)
                .sourceId(Math.min(semanticSourceId, semanticTargetId))
                .targetId(Math.max(semanticSourceId, semanticTargetId))
                .semanticSourceId(semanticSourceId)
                .semanticTargetId(semanticTargetId)
                .type(type)
                .confidence(BigDecimal.valueOf(0.9))
                .weight(1)
                .manualWeight(origin == LifeGraphRelation.Origin.MANUAL ? 1 : 0)
                .firstSeen(updatedAt)
                .lastSeen(updatedAt)
                .origin(origin)
                .createdAt(updatedAt)
                .updatedAt(updatedAt)
                .build());
    }

    private List<LifeGraphRelation> directCandidates(List<LifeGraphRelation> relations,
            Long currentUserEntityId, Long personId) {
        return relations.stream()
                .filter(relation -> relation.getOrigin() != null
                        && PERSON_RELATION_CODES.contains(normalizeRelationType(relation.getType())))
                .filter(relation -> {
                    Long sourceId = semanticSourceId(relation);
                    Long targetId = semanticTargetId(relation);
                    return (Objects.equals(sourceId, currentUserEntityId) && Objects.equals(targetId, personId))
                            || (Objects.equals(targetId, currentUserEntityId)
                                    && Objects.equals(sourceId, personId));
                })
                .toList();
    }

    private LifeGraphRelation chooseRepresentativeRelation(List<LifeGraphRelation> candidates) {
        return candidates.stream()
                .sorted(Comparator.comparingInt(this::originPriority).reversed()
                        .thenComparing(LifeGraphRelation::getUpdatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(relation -> normalizeRelationType(relation.getType()))
                        .thenComparing(LifeGraphRelation::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .findFirst()
                .orElse(null);
    }

    private int originPriority(LifeGraphRelation relation) {
        return relation.getOrigin() == LifeGraphRelation.Origin.MANUAL ? 1 : 0;
    }

    private String normalizeRelationType(String type) {
        LifeGraphRelationType relationType = LifeGraphRelationType.fromCode(type);
        return relationType == null ? "" : relationType.code();
    }

    private Long semanticSourceId(LifeGraphRelation relation) {
        return relation.getSemanticSourceId() == null ? relation.getSourceId() : relation.getSemanticSourceId();
    }

    private Long semanticTargetId(LifeGraphRelation relation) {
        return relation.getSemanticTargetId() == null ? relation.getTargetId() : relation.getSemanticTargetId();
    }

    private LifeGraphMemoryItem findItem(LifeGraphMemoryResponse response, Long entityId) {
        return response.getEntities().stream()
                .filter(item -> Objects.equals(item.getId(), entityId))
                .findFirst()
                .orElse(null);
    }

    private OfflineEvaluationReportWriter.CaseResult caseResult(Scenario scenario, Checks checks) {
        return new OfflineEvaluationReportWriter.CaseResult(
                "EVAL-MEM-003",
                scenario.scenarioId(),
                checks.hasFailures() ? "FAIL" : "PASS",
                FIXTURE_VERSION,
                EXPECTATION_VERSION,
                OfflineEvaluationReportWriter.Versions.fixtureBaseline(),
                checks.assertionCount,
                checks.passedAssertionCount,
                checks.violationCodes,
                java.util.Map.ofEntries(
                        java.util.Map.entry("entityCount", entityRepository.findByUserId(USER_ID).size()),
                        java.util.Map.entry("relationCount", relationRepository.findByUserId(USER_ID).size()),
                        java.util.Map.entry("relationFactMatchPassCount", checks.passCount("RELATION_FACT_MATCH")),
                        java.util.Map.entry("relationDeleteSyncPassCount", checks.passCount("RELATION_DELETE_SYNC")),
                        java.util.Map.entry("crossUserFilterPassCount", checks.passCount("CROSS_USER_RELATION_FILTER")),
                        java.util.Map.entry("lowSensitivityPassCount", checks.passCount("MEMORY_LOW_SENSITIVITY"))));
    }

    private OfflineEvaluationReportWriter.CaseResult failedCase(
            String caseId, String scenarioId, String code) {
        return new OfflineEvaluationReportWriter.CaseResult(
                caseId,
                scenarioId,
                "FAIL",
                FIXTURE_VERSION,
                EXPECTATION_VERSION,
                OfflineEvaluationReportWriter.Versions.fixtureBaseline(),
                1,
                0,
                List.of(code),
                java.util.Map.ofEntries(
                        java.util.Map.entry("entityCount", 0),
                        java.util.Map.entry("relationCount", 0),
                        java.util.Map.entry("relationFactMatchPassCount", 0),
                        java.util.Map.entry("relationDeleteSyncPassCount", 0),
                        java.util.Map.entry("crossUserFilterPassCount", 0),
                        java.util.Map.entry("lowSensitivityPassCount", 0)));
    }

    private void assertLowSensitivityReport() throws IOException {
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

    private record Seed(Long userEntityId, Long personId, Long deletionPersonId,
            Long topicId, Long otherUserEntityId, Long deletionRelationId) {
    }

    private static final class ReplayStageException extends RuntimeException {
        private final String stage;

        private ReplayStageException(String stage) {
            this.stage = stage;
        }
    }

    private static final class Checks {
        private final List<String> violationCodes = new ArrayList<>();
        private final java.util.Map<String, Boolean> outcomes = new java.util.LinkedHashMap<>();
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
