package com.aseubel.yusi.evaluation.lifegraph;

import com.aseubel.yusi.TestInfrastructureConfig;
import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.evaluation.OfflineEvaluationReportWriter;
import com.aseubel.yusi.evaluation.QualityGatePolicy;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import com.aseubel.yusi.pojo.entity.LifeGraphTask;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.DiaryRepository;
import com.aseubel.yusi.repository.LifeGraphEntityAliasRepository;
import com.aseubel.yusi.repository.LifeGraphEntityEvidenceRepository;
import com.aseubel.yusi.repository.LifeGraphEntityRepository;
import com.aseubel.yusi.repository.LifeGraphMentionRepository;
import com.aseubel.yusi.repository.LifeGraphRelationEvidenceRepository;
import com.aseubel.yusi.repository.LifeGraphRelationRepository;
import com.aseubel.yusi.repository.LifeGraphTaskRepository;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.service.ai.prompt.PromptManager;
import com.aseubel.yusi.service.ai.prompt.PromptSnapshot;
import com.aseubel.yusi.service.lifegraph.LifeGraphBuildService;
import com.aseubel.yusi.service.lifegraph.LifeGraphTaskBatchService;
import com.aseubel.yusi.service.lifegraph.LifeTimelineService;
import com.aseubel.yusi.service.lifegraph.ai.LifeGraphExtractor;
import com.aseubel.yusi.service.lifegraph.dto.TimelineNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.aseubel.yusi.evaluation.lifegraph.LifeGraphTimelineRebuildEvaluationFixture.Event;
import static com.aseubel.yusi.evaluation.lifegraph.LifeGraphTimelineRebuildEvaluationFixture.Scenario;
import static com.aseubel.yusi.evaluation.lifegraph.LifeGraphTimelineRebuildEvaluationFixture.Source;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestInfrastructureConfig.class)
class LifeGraphTimelineRebuildEvaluationTest {

    private static final String SUITE_ID = "lifegraph-timeline-rebuild-v1";
    private static final Set<String> CASE_IDS = Set.of("EVAL-TIMELINE-002");
    private static final int MINIMUM_ASSERTION_COUNT = 16;
    private static final Path REPORT_PATH = Path.of(
            "target", "evaluation", "lifegraph-timeline-rebuild-v1-report.json");
    private static final String USER_ID = "fixture-user-timeline-rebuild";
    private static final String DIARY_ID = "fixture-diary-timeline-rebuild";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LifeGraphTaskBatchService lifeGraphTaskBatchService;

    @Autowired
    private LifeTimelineService lifeTimelineService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DiaryRepository diaryRepository;

    @Autowired
    private LifeGraphTaskRepository lifeGraphTaskRepository;

    @Autowired
    private LifeGraphBuildService lifeGraphBuildService;

    @Autowired
    private LifeGraphEntityRepository entityRepository;

    @Autowired
    private LifeGraphEntityAliasRepository entityAliasRepository;

    @Autowired
    private LifeGraphEntityEvidenceRepository entityEvidenceRepository;

    @Autowired
    private LifeGraphRelationRepository relationRepository;

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
        clearSyntheticState();
        reset(promptManager, extractor);
        when(promptManager.getSnapshot(PromptKey.GRAPHRAG_EXTRACT))
                .thenReturn(new PromptSnapshot("graphrag-extract", "fixture-v1", "zh-CN", "fixture"));
    }

    @AfterEach
    void clearFixtureState() {
        clearSyntheticState();
    }

    @Test
    void writesTheTimelineRebuildEvaluationReport() throws Exception {
        Files.deleteIfExists(REPORT_PATH);
        LifeGraphTimelineRebuildEvaluationFixture.Suite suite =
                new LifeGraphTimelineRebuildFixtureLoader(objectMapper).load();
        List<LifeGraphTimelineRebuildEvaluationReport.CaseResult> results = new ArrayList<>();
        for (var evaluationCase : suite.cases()) {
            for (Scenario scenario : evaluationCase.scenarios()) {
                results.add(replayScenario(evaluationCase.caseId(), scenario));
            }
        }

        List<OfflineEvaluationReportWriter.CaseResult> genericResults = results.stream()
                .map(LifeGraphTimelineRebuildEvaluationReport::toGenericCase)
                .toList();
        QualityGatePolicy.requirePass(
                suite.suiteId(),
                genericResults,
                new QualityGatePolicy.SuiteContract(
                        SUITE_ID, CASE_IDS, MINIMUM_ASSERTION_COUNT));
        LifeGraphTimelineRebuildEvaluationReport.CaseResult result = results.get(0);
        Map<String, Object> actualSummary =
                LifeGraphTimelineRebuildEvaluationReport.toGenericCase(result).actualSummary();
        var expected = suite.cases().get(0).scenarios().get(0).expected();
        QualityGatePolicy.requireMetricEquals(actualSummary, "beforeRevisionNodeCount",
                expected.beforeRevisionNodeCount(), "TIMELINE_REBUILD_BEFORE_NODE_COUNT");
        QualityGatePolicy.requireMetricEquals(actualSummary, "afterRevisionOldResidualCount",
                expected.afterRevisionOldResidualCount(), "TIMELINE_REBUILD_OLD_RESIDUAL");
        QualityGatePolicy.requireMetricEquals(actualSummary, "afterRevisionNewNodeCount",
                expected.afterRevisionNewNodeCount(), "TIMELINE_REBUILD_NEW_NODE_COUNT");
        QualityGatePolicy.requireMetricEquals(actualSummary, "afterDeleteTimelineNodeCount",
                expected.afterDeleteTimelineNodeCount(), "TIMELINE_REBUILD_DELETE_RESIDUAL");
        QualityGatePolicy.requireMetricEquals(actualSummary, "sourceResidualCount",
                expected.sourceResidualCount(), "TIMELINE_REBUILD_SOURCE_RESIDUAL");

        LifeGraphTimelineRebuildEvaluationReport.write(REPORT_PATH, results);
        assertTrue(Files.exists(REPORT_PATH));
        assertLowSensitivityReport();
    }

    private LifeGraphTimelineRebuildEvaluationReport.CaseResult replayScenario(
            String caseId, Scenario scenario) {
        Checks checks = new Checks();
        Snapshot beforeRevision = Snapshot.empty();
        Snapshot afterRevision = Snapshot.empty();
        Snapshot afterDelete = Snapshot.empty();
        try {
            Source source = scenario.sources().get(0);
            Event revisionOne = source.events().get(0);
            Event revisionTwo = source.events().get(1);
            Event delete = source.events().get(2);

            userRepository.saveAndFlush(User.builder()
                    .userId(USER_ID)
                    .userName("fixture-user-timeline-rebuild")
                    .build());
            saveDiary(revisionOne.sourceRevision(), LocalDate.parse(revisionOne.entryDate()));
            runTask(revisionOne, source);
            beforeRevision = snapshot();
            List<String> beforeRevisionEvidence = sourceEvidenceTokens();

            saveDiary(revisionTwo.sourceRevision(), LocalDate.parse(revisionTwo.entryDate()));
            runTask(revisionTwo, source);
            afterRevision = snapshot();
            List<String> afterRevisionEvidence = sourceEvidenceTokens();

            runTask(delete, source);
            afterDelete = snapshot();

            var expected = scenario.expected();
            checks.check("TIMELINE_BEFORE_REVISION_NODE",
                    beforeRevision.timelineNodeCount() == expected.beforeRevisionNodeCount());
            checks.check("TIMELINE_BEFORE_REVISION_OLD_PRESENT",
                    beforeRevision.timelineEventKeys().equals(List.of(expected.oldEventKey())));
            String oldEvidenceToken = revisionOne.extraction().mentions().get(0).snippet();
            String newEvidenceToken = revisionTwo.extraction().mentions().get(0).snippet();
            checks.check("TIMELINE_BEFORE_REVISION_EVIDENCE_PRESENT",
                    beforeRevisionEvidence.size() == 2
                            && beforeRevisionEvidence.stream().allMatch(oldEvidenceToken::equals));
            checks.check("TIMELINE_REBUILD_OLD_RESIDUAL",
                    residualForKey(afterRevision, expected.oldEventKey())
                            == expected.afterRevisionOldResidualCount());
            checks.check("TIMELINE_REBUILD_OLD_EVIDENCE_REMOVED",
                    afterRevisionEvidence.stream().noneMatch(oldEvidenceToken::equals));
            checks.check("TIMELINE_REBUILD_NEW_EVIDENCE_PRESENT",
                    afterRevisionEvidence.size() == 2
                            && afterRevisionEvidence.stream().allMatch(newEvidenceToken::equals));
            checks.check("TIMELINE_REBUILD_NEW_PRESENT",
                    afterRevision.timelineEventKeys().stream()
                            .filter(expected.newEventKey()::equals)
                            .count() == expected.afterRevisionNewNodeCount());
            checks.check("TIMELINE_REBUILD_ONLY_NEW_NODE",
                    afterRevision.timelineEventKeys().equals(List.of(expected.newEventKey())));
            checks.check("TIMELINE_REBUILD_ENTITY_FACTS",
                    afterRevision.entityCount() == expected.afterRevisionEntityCount());
            checks.check("TIMELINE_REBUILD_RELATION_FACTS",
                    afterRevision.relationCount() == expected.afterRevisionRelationCount());
            checks.check("TIMELINE_REBUILD_ENTITY_EVIDENCE_FACTS",
                    afterRevision.entityEvidenceCount() == expected.afterRevisionEntityEvidenceCount());
            checks.check("TIMELINE_REBUILD_RELATION_EVIDENCE_FACTS",
                    afterRevision.relationEvidenceCount() == expected.afterRevisionRelationEvidenceCount());
            checks.check("TIMELINE_REBUILD_MENTION_FACTS",
                    afterRevision.mentionCount() == expected.afterRevisionMentionCount());
            checks.check("TIMELINE_DELETE_NODE_COUNT",
                    afterDelete.timelineNodeCount() == expected.afterDeleteTimelineNodeCount());
            checks.check("TIMELINE_DELETE_NODE_EMPTY", afterDelete.timelineEventKeys().isEmpty());
            checks.check("TIMELINE_DELETE_SOURCE_RESIDUAL",
                    afterDelete.sourceResidualCount() == expected.sourceResidualCount());
        } catch (Exception exception) {
            checks.violate("REPLAY_EXECUTION");
        }

        return new LifeGraphTimelineRebuildEvaluationReport.CaseResult(
                caseId,
                scenario.scenarioId(),
                checks.hasFailures() ? "FAIL" : "PASS",
                "fixture-v1",
                "expectation-v1",
                LifeGraphTimelineRebuildEvaluationReport.Versions.fixtureBaseline(),
                checks.assertionCount,
                checks.passedAssertionCount,
                checks.violationCodes,
                new LifeGraphTimelineRebuildEvaluationReport.ActualSummary(
                        beforeRevision.timelineNodeCount(),
                        residualForKey(afterRevision, scenario.expected().oldEventKey()),
                        (int) afterRevision.timelineEventKeys().stream()
                                .filter(scenario.expected().newEventKey()::equals)
                                .count(),
                        afterDelete.timelineNodeCount(),
                        afterDelete.sourceResidualCount()));
    }

    private void clearSyntheticState() {
        lifeGraphBuildService.deleteBySource(USER_ID, "DIARY", DIARY_ID);
        relationEvidenceRepository.deleteByUserIdAndSourceTypeAndSourceId(USER_ID, "DIARY", DIARY_ID);
        entityEvidenceRepository.deleteByUserIdAndSourceTypeAndSourceId(USER_ID, "DIARY", DIARY_ID);
        mentionRepository.deleteByUserIdAndDiaryId(USER_ID, DIARY_ID);
        for (var relation : relationRepository.findByUserId(USER_ID)) {
            relationEvidenceRepository.deleteByUserIdAndRelationId(USER_ID, relation.getId());
            relationRepository.delete(relation);
        }
        for (LifeGraphEntity entity : entityRepository.findByUserId(USER_ID)) {
            entityAliasRepository.deleteByUserIdAndEntityId(USER_ID, entity.getId());
            entityEvidenceRepository.deleteAll(entityEvidenceRepository.findByUserIdAndEntityId(
                    USER_ID, entity.getId()));
            mentionRepository.deleteAll(mentionRepository.findByUserIdAndEntityId(USER_ID, entity.getId()));
            entityRepository.delete(entity);
        }
        for (LifeGraphTask task : lifeGraphTaskRepository.findByUserIdAndDiaryIdAndStatusIn(
                USER_ID, DIARY_ID, Arrays.asList(LifeGraphTask.TaskStatus.values()))) {
            lifeGraphTaskRepository.delete(task);
        }
        Diary diary = diaryRepository.findByDiaryIdAndUserId(DIARY_ID, USER_ID);
        if (diary != null) {
            diaryRepository.delete(diary);
        }
        User user = userRepository.findByUserId(USER_ID);
        if (user != null) {
            userRepository.delete(user);
        }
    }

    private Snapshot snapshot() {
        List<LifeGraphEntity> entities = entityRepository.findByUserId(USER_ID);
        return new Snapshot(
                entities.size(),
                relationRepository.findByUserId(USER_ID).size(),
                entityEvidenceRepository.findByUserIdAndSourceTypeAndSourceId(
                        USER_ID, "DIARY", DIARY_ID).size(),
                relationEvidenceRepository.findByUserIdAndSourceTypeAndSourceId(
                        USER_ID, "DIARY", DIARY_ID).size(),
                mentionRepository.findByUserIdAndDiaryId(USER_ID, DIARY_ID).size(),
                timelineNodes().size(),
                timelineNodes().stream().map(TimelineNode::getTitle).toList(),
                sourceResidualCount(entities));
    }

    private int residualForKey(Snapshot snapshot, String eventKey) {
        List<LifeGraphEntity> entities = entityRepository.findByUserIdAndNameNorm(USER_ID, eventKey);
        int evidenceCount = entities.stream()
                .filter(entity -> entity.getId() != null)
                .mapToInt(entity -> entityEvidenceRepository.findByUserIdAndEntityId(
                        USER_ID, entity.getId()).size())
                .sum();
        int mentionCount = entities.stream()
                .filter(entity -> entity.getId() != null)
                .mapToInt(entity -> mentionRepository.findByUserIdAndEntityId(
                        USER_ID, entity.getId()).size())
                .sum();
        int timelineCount = (int) snapshot.timelineEventKeys().stream()
                .filter(eventKey::equals)
                .count();
        return entities.size() + evidenceCount + mentionCount + timelineCount;
    }

    private int sourceResidualCount(List<LifeGraphEntity> entities) {
        int nonUserEntityCount = (int) entities.stream()
                .filter(entity -> entity.getType() != LifeGraphEntity.EntityType.User)
                .count();
        return nonUserEntityCount
                + relationRepository.findByUserId(USER_ID).size()
                + entityEvidenceRepository.findByUserIdAndSourceTypeAndSourceId(
                        USER_ID, "DIARY", DIARY_ID).size()
                + relationEvidenceRepository.findByUserIdAndSourceTypeAndSourceId(
                        USER_ID, "DIARY", DIARY_ID).size()
                + mentionRepository.findByUserIdAndDiaryId(USER_ID, DIARY_ID).size();
    }

    private List<String> sourceEvidenceTokens() {
        List<String> tokens = new ArrayList<>();
        entityEvidenceRepository.findByUserIdAndSourceTypeAndSourceId(
                        USER_ID, "DIARY", DIARY_ID).stream()
                .map(com.aseubel.yusi.pojo.entity.LifeGraphEntityEvidence::getSnippet)
                .filter(java.util.Objects::nonNull)
                .forEach(tokens::add);
        relationEvidenceRepository.findByUserIdAndSourceTypeAndSourceId(
                        USER_ID, "DIARY", DIARY_ID).stream()
                .map(com.aseubel.yusi.pojo.entity.LifeGraphRelationEvidence::getEvidenceSnippet)
                .filter(java.util.Objects::nonNull)
                .forEach(tokens::add);
        return tokens;
    }

    private void saveDiary(long revision, LocalDate entryDate) {
        Diary diary = diaryRepository.findByDiaryIdAndUserId(DIARY_ID, USER_ID);
        if (diary == null) {
            diary = Diary.builder()
                    .diaryId(DIARY_ID)
                    .userId(USER_ID)
                    .title("fixture-diary-timeline-rebuild")
                    .build();
        }
        diary.setEntryDate(entryDate);
        diary.setSourceRevision(revision);
        diaryRepository.saveAndFlush(diary);
    }

    private void runTask(Event event, Source source) {
        if ("UPSERT".equals(event.operation())) {
            try {
                when(extractor.extract(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                        anyString(), anyString())).thenReturn(objectMapper.writeValueAsString(event.extraction()));
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("fixture serialization failed", exception);
            }
        }
        LifeGraphTask task = LifeGraphTask.createUpsertTask(
                source.sourceId(), USER_ID, event.sourceRevision(), "EVAL-TIMELINE-002-A");
        if ("DELETE".equals(event.operation())) {
            task = LifeGraphTask.createDeleteTask(
                    source.sourceId(), USER_ID, event.sourceRevision(), "EVAL-TIMELINE-002-A");
        }
        LifeGraphTask saved = lifeGraphTaskRepository.saveAndFlush(task);
        Diary diary = diaryRepository.findByDiaryIdAndUserId(DIARY_ID, USER_ID);
        lifeGraphTaskBatchService.processSingleTask(saved.getId(), diary,
                "UPSERT".equals(event.operation()) ? "evidence-token-rebuild-input" : null);
    }

    private List<TimelineNode> timelineNodes() {
        return lifeTimelineService.getLifeChapters(USER_ID).stream()
                .flatMap(chapter -> chapter.getNodes() == null
                        ? java.util.stream.Stream.<TimelineNode>empty()
                        : chapter.getNodes().stream())
                .toList();
    }

    private void assertLowSensitivityReport() throws IOException {
        JsonNode report = objectMapper.readTree(REPORT_PATH.toFile());
        JsonNode expectedPrompt = objectMapper.readTree(
                "{\"key\":\"fixture\",\"version\":\"fixture-v1\",\"locale\":\"zh-CN\"}");
        for (JsonNode result : report.path("cases")) {
            assertEquals(expectedPrompt, result.at("/versions/prompt"));
        }

        JsonNode reportWithoutPrompt = report.deepCopy();
        for (JsonNode result : reportWithoutPrompt.path("cases")) {
            if (result.isObject() && result.path("versions").isObject()) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) result.path("versions")).remove("prompt");
            }
        }
        String reportText = reportWithoutPrompt.toString().toLowerCase(java.util.Locale.ROOT);
        for (String forbidden : List.of(
                "evidence-token-", "rawtext", "plaincontent", "toolarguments", "toolresult",
                "secret", "password", "query", "response")) {
            org.junit.jupiter.api.Assertions.assertFalse(reportText.contains(forbidden));
        }
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

    private record Snapshot(int entityCount, int relationCount, int entityEvidenceCount,
                            int relationEvidenceCount, int mentionCount, int timelineNodeCount,
                            List<String> timelineEventKeys, int sourceResidualCount) {
        private static Snapshot empty() {
            return new Snapshot(0, 0, 0, 0, 0, 0, List.of(), 0);
        }
    }
}
