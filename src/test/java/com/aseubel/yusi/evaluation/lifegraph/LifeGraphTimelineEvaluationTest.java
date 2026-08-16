package com.aseubel.yusi.evaluation.lifegraph;

import com.aseubel.yusi.TestInfrastructureConfig;
import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.pojo.dto.cognition.CognitionIngestCommand;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import com.aseubel.yusi.pojo.entity.LifeGraphTask;
import com.aseubel.yusi.repository.DiaryRepository;
import com.aseubel.yusi.repository.LifeGraphEntityEvidenceRepository;
import com.aseubel.yusi.repository.LifeGraphEntityRepository;
import com.aseubel.yusi.repository.LifeGraphMentionRepository;
import com.aseubel.yusi.repository.LifeGraphRelationEvidenceRepository;
import com.aseubel.yusi.repository.LifeGraphRelationRepository;
import com.aseubel.yusi.repository.LifeGraphTaskRepository;
import com.aseubel.yusi.service.ai.prompt.PromptManager;
import com.aseubel.yusi.service.ai.prompt.PromptSnapshot;
import com.aseubel.yusi.service.lifegraph.LifeGraphBuildService;
import com.aseubel.yusi.service.lifegraph.LifeGraphTaskBatchService;
import com.aseubel.yusi.service.lifegraph.LifeTimelineService;
import com.aseubel.yusi.service.lifegraph.ai.LifeGraphExtractor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.aseubel.yusi.evaluation.lifegraph.LifeGraphTimelineEvaluationFixture.Event;
import static com.aseubel.yusi.evaluation.lifegraph.LifeGraphTimelineEvaluationFixture.Scenario;
import static com.aseubel.yusi.evaluation.lifegraph.LifeGraphTimelineEvaluationFixture.Source;
import static com.aseubel.yusi.evaluation.lifegraph.LifeGraphTimelineEvaluationFixture.Suite;
import static com.aseubel.yusi.evaluation.lifegraph.LifeGraphTimelineEvaluationReport.ActualSummary;
import static com.aseubel.yusi.evaluation.lifegraph.LifeGraphTimelineEvaluationReport.CaseResult;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestInfrastructureConfig.class)
class LifeGraphTimelineEvaluationTest {

    private static final Path REPORT_PATH = Path.of(
            "target", "evaluation", "lifegraph-timeline-v1-report.json");

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LifeGraphBuildService lifeGraphBuildService;

    @Autowired
    private LifeGraphTaskBatchService lifeGraphTaskBatchService;

    @Autowired
    private LifeTimelineService lifeTimelineService;

    @Autowired
    private DiaryRepository diaryRepository;

    @Autowired
    private LifeGraphTaskRepository lifeGraphTaskRepository;

    @Autowired
    private LifeGraphEntityRepository entityRepository;

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
        reset(promptManager, extractor);
        when(promptManager.getSnapshot(PromptKey.GRAPHRAG_EXTRACT))
                .thenReturn(new PromptSnapshot("graphrag-extract", "fixture-v1", "zh-CN", "fixture"));
    }

    @Test
    void writesTheOfflineReplayBaselineReport() throws Exception {
        List<CaseResult> results = new ArrayList<>();
        Files.deleteIfExists(REPORT_PATH);
        try {
            Suite suite = new LifeGraphTimelineFixtureLoader(objectMapper).load();
            for (var evaluationCase : suite.cases()) {
                for (Scenario scenario : evaluationCase.scenarios()) {
                    results.add(replayScenario(evaluationCase.caseId(), scenario));
                }
            }
        } catch (Exception exception) {
            String code = exception instanceof LifeGraphTimelineFixtureLoader.FixtureValidationException validation
                    ? validation.code() : "REPLAY_EXECUTION";
            results.add(failedCase("EVAL-MEM-002", "EVAL-MEM-002-A", code));
        } finally {
            LifeGraphTimelineEvaluationReport.write(REPORT_PATH, results);
        }

        assertFalse(results.isEmpty());
        assertTrue(results.stream().allMatch(result -> "PASS".equals(result.status())),
                () -> results.stream().flatMap(result -> result.violationCodes().stream()).toList().toString());
        assertTrue(Files.exists(REPORT_PATH));
    }

    private CaseResult replayScenario(String caseId, Scenario scenario) throws Exception {
        Checks checks = new Checks();
        Snapshot snapshot = Snapshot.empty();
        Snapshot beforeDeleteSnapshot = Snapshot.empty();
        try {
            prepareDiarySources(scenario);
            for (Source source : scenario.sources()) {
                for (Event event : source.events()) {
                    applyEvent(scenario, source, event);
                    if ("EVAL-MEM-002-A".equals(scenario.scenarioId())
                            && event.sourceRevision() == 3 && "UPSERT".equals(event.operation())) {
                        snapshot = snapshot(scenario.userId());
                    }
                    if ("EVAL-TIMELINE-001-A".equals(scenario.scenarioId())
                            && "UPSERT".equals(event.operation())) {
                        beforeDeleteSnapshot = snapshot(scenario.userId());
                    }
                }
            }
            evaluateScenario(scenario, checks, snapshot, beforeDeleteSnapshot);
        } catch (Exception exception) {
            checks.violate("REPLAY_EXECUTION");
        }
        return new CaseResult(
                caseId,
                scenario.scenarioId(),
                checks.hasFailures() ? "FAIL" : "PASS",
                "fixture-v1",
                "expectation-v1",
                LifeGraphTimelineEvaluationReport.Versions.fixtureBaseline(),
                checks.assertionCount,
                checks.passedAssertionCount,
                checks.violationCodes,
                snapshot(scenario.userId()).toReportSummary());
    }

    private void prepareDiarySources(Scenario scenario) {
        for (Source source : scenario.sources()) {
            if (!"DIARY".equals(source.sourceType())) {
                continue;
            }
            long latestRevision = source.events().stream()
                    .mapToLong(Event::sourceRevision)
                    .max()
                    .orElse(1L);
            Event firstEvent = source.events().get(0);
            diaryRepository.save(Diary.builder()
                    .diaryId(source.sourceId())
                    .userId(scenario.userId())
                    .title("fixture-title-" + source.sourceId())
                    .entryDate(parseDate(firstEvent.entryDate()))
                    .sourceRevision(latestRevision)
                    .build());
        }
    }

    private void applyEvent(Scenario scenario, Source source, Event event) throws Exception {
        if ("DIARY".equals(source.sourceType())) {
            applyDiaryEvent(scenario, source, event);
            return;
        }
        CognitionIngestCommand command = CognitionIngestCommand.builder()
                .userId(scenario.userId())
                .sourceType(source.sourceType())
                .sourceId(source.sourceId())
                .sourceRevision(event.sourceRevision())
                .maskedText("evidence-token-plaza-input")
                .title("fixture-title-" + source.sourceId())
                .timestamp(parseDate(event.entryDate()).atStartOfDay())
                .build();
        if ("DELETE".equals(event.operation())) {
            lifeGraphBuildService.deleteBySource(scenario.userId(), source.sourceType(), source.sourceId());
        } else {
            configureExtractor(event);
            lifeGraphBuildService.upsertFromPlaza(command);
        }
    }

    private void applyDiaryEvent(Scenario scenario, Source source, Event event) throws Exception {
        Diary eventDiary = Diary.builder()
                .diaryId(source.sourceId())
                .userId(scenario.userId())
                .title("fixture-title-" + source.sourceId())
                .entryDate(parseDate(event.entryDate()))
                .sourceRevision(event.sourceRevision())
                .plainContent("evidence-token-diary-input")
                .build();
        LifeGraphTask task = "DELETE".equals(event.operation())
                ? LifeGraphTask.createDeleteTask(source.sourceId(), scenario.userId(), scenario.scenarioId())
                : LifeGraphTask.createUpsertTask(source.sourceId(), scenario.userId(),
                        event.sourceRevision(), scenario.scenarioId());
        task.setSourceRevision(event.sourceRevision());
        LifeGraphTask saved = lifeGraphTaskRepository.save(task);
        if ("UPSERT".equals(event.operation())) {
            configureExtractor(event);
            lifeGraphTaskBatchService.processSingleTask(saved.getId(), eventDiary,
                    "evidence-token-diary-input");
        } else {
            lifeGraphTaskBatchService.processSingleTask(saved.getId(), eventDiary, null);
        }
    }

    private void configureExtractor(Event event) throws Exception {
        when(extractor.extract(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString())).thenReturn(objectMapper.writeValueAsString(event.extraction()));
    }

    private void evaluateScenario(Scenario scenario, Checks checks,
                                  Snapshot revisionThreeSnapshot, Snapshot beforeDeleteSnapshot) {
        Snapshot actual = snapshot(scenario.userId());
        switch (scenario.scenarioId()) {
            case "EVAL-MEM-002-A" -> evaluateRevisionOrdering(scenario, checks, revisionThreeSnapshot, actual);
            case "EVAL-MEM-002-B" -> evaluateDuplicateContribution(scenario, checks, actual);
            case "EVAL-MEM-002-C" -> evaluateSourceDeletion(scenario, checks, actual);
            case "EVAL-TIMELINE-001-A" -> evaluateTimeline(scenario, checks, actual, beforeDeleteSnapshot);
            default -> checks.violate("UNKNOWN_SCENARIO");
        }
    }

    private void evaluateRevisionOrdering(Scenario scenario, Checks checks,
                                          Snapshot revisionThreeSnapshot, Snapshot actual) {
        checks.check("STALE_REVISION_APPLIED",
                entityExists(scenario.userId(), "fixture-trip-v3")
                        && !entityExists(scenario.userId(), "fixture-trip-v1"));
        checks.check("PROMOTION_BOUNDARY",
                entityExists(scenario.userId(), "fixture-partner")
                        && entityExists(scenario.userId(), "fixture-strawberry")
                        && !entityExists(scenario.userId(), "fixture-coworker")
                        && !entityExists(scenario.userId(), "fixture-basketball"));
        checks.check("LOW_VALUE_RELATION_FILTER",
                relationRepository.findByUserId(scenario.userId()).stream()
                        .noneMatch(relation -> Set.of("MENTIONED", "MENTIONED_IN", "SAID")
                                .contains(relation.getType())));
        checks.check("STALE_REVISION_PRESERVED_CURRENT", revisionThreeSnapshot.timelineNodeCount == 1
                && actual.timelineNodeCount == 1);
    }

    private void evaluateDuplicateContribution(Scenario scenario, Checks checks, Snapshot actual) {
        checks.check("DUPLICATE_CONTRIBUTION",
                entityEvidenceRepository.findByUserIdAndSourceTypeAndSourceId(
                        scenario.userId(), "DIARY", "fixture-diary-b").size() == 1
                        && relationEvidenceRepository.findByUserIdAndSourceTypeAndSourceId(
                        scenario.userId(), "DIARY", "fixture-diary-b").size() == 1
                        && mentionRepository.findByUserIdAndDiaryId(
                        scenario.userId(), "fixture-diary-b").size() == 1);
        LifeGraphEntity event = entityRepository.findByUserIdAndNameNorm(
                        scenario.userId(), "fixture-duplicate-event").stream().findFirst().orElse(null);
        checks.check("DUPLICATE_AGGREGATE_WEIGHT", event != null && event.getMentionCount() == 1
                && relationRepository.findByUserId(scenario.userId()).stream()
                .allMatch(relation -> relation.getWeight() == 1));
        checks.check("DUPLICATE_SNAPSHOT_PRESENT", actual.entityCount >= 2 && actual.relationCount == 1);
    }

    private void evaluateSourceDeletion(Scenario scenario, Checks checks, Snapshot actual) {
        checks.check("SOURCE_RESIDUAL", actual.entityEvidenceCount == 0
                && actual.relationEvidenceCount == 0
                && actual.mentionCount == 0);
        checks.check("AUTO_GRAPH_RESIDUAL", !entityExists(scenario.userId(), "fixture-shared-event")
                && relationRepository.findByUserId(scenario.userId()).isEmpty());
        checks.check("TIMELINE_RESIDUAL", actual.timelineNodeCount == 0);
        checks.check("USER_SCOPE_LEAK", entityRepository.findByUserId(scenario.userId()).stream()
                .noneMatch(entity -> "fixture-shared-event".equals(entity.getNameNorm())));
    }

    private void evaluateTimeline(Scenario scenario, Checks checks, Snapshot actual,
                                  Snapshot beforeDeleteSnapshot) {
        checks.check("TIMELINE_EVENT_ELIGIBILITY", actual.timelineNodeCount == 0
                && beforeDeleteSnapshot.timelineNodeCount == 1);
        checks.check("TIMELINE_NON_EVENT_FILTER", beforeDeleteSnapshot.personCount == 1
                && beforeDeleteSnapshot.topicCount == 1);
    }

    private boolean entityExists(String userId, String nameNorm) {
        return entityRepository.findByUserIdAndNameNorm(userId, nameNorm).stream().findAny().isPresent();
    }

    private Snapshot snapshot(String userId) {
        int timelineNodeCount = lifeTimelineService.getLifeChapters(userId).stream()
                .mapToInt(chapter -> chapter.getNodes() == null ? 0 : chapter.getNodes().size())
                .sum();
        List<LifeGraphEntity> entities = entityRepository.findByUserId(userId);
        List<com.aseubel.yusi.pojo.entity.LifeGraphRelation> relations = relationRepository.findByUserId(userId);
        int entityEvidenceCount = entities.stream()
                .filter(entity -> entity.getId() != null)
                .mapToInt(entity -> entityEvidenceRepository.findByUserIdAndEntityId(userId, entity.getId()).size())
                .sum();
        int relationEvidenceCount = relations.stream()
                .filter(relation -> relation.getId() != null)
                .mapToInt(relation -> relationEvidenceRepository.findByUserIdAndRelationId(userId, relation.getId()).size())
                .sum();
        int mentionCount = entities.stream()
                .filter(entity -> entity.getId() != null)
                .mapToInt(entity -> mentionRepository.findByUserIdAndEntityId(userId, entity.getId()).size())
                .sum();
        return new Snapshot(
                entities.size(),
                relations.size(),
                entityEvidenceCount,
                relationEvidenceCount,
                mentionCount,
                timelineNodeCount,
                entityRepository.findByUserIdAndType(userId, LifeGraphEntity.EntityType.Person).size(),
                entityRepository.findByUserIdAndType(userId, LifeGraphEntity.EntityType.Topic).size());
    }

    private LocalDate parseDate(String value) {
        return LocalDate.parse(value);
    }

    private CaseResult failedCase(String caseId, String scenarioId, String code) {
        return new CaseResult(caseId, scenarioId, "FAIL", "fixture-v1", "expectation-v1",
                LifeGraphTimelineEvaluationReport.Versions.fixtureBaseline(), 1, 0, List.of(code),
                new ActualSummary(0, 0, 0, 0, 0, 0));
    }

    private record Snapshot(int entityCount, int relationCount, int entityEvidenceCount,
                            int relationEvidenceCount, int mentionCount, int timelineNodeCount,
                            int personCount, int topicCount) {
        private static Snapshot empty() {
            return new Snapshot(0, 0, 0, 0, 0, 0, 0, 0);
        }

        private ActualSummary toReportSummary() {
            return new ActualSummary(entityCount, relationCount, entityEvidenceCount,
                    relationEvidenceCount, mentionCount, timelineNodeCount);
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
}
