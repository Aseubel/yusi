package com.aseubel.yusi.evaluation.memory;

import com.aseubel.yusi.TestInfrastructureConfig;
import com.aseubel.yusi.common.constant.SourceType;
import com.aseubel.yusi.evaluation.OfflineEvaluationReportWriter;
import com.aseubel.yusi.pojo.entity.MidTermMemory;
import com.aseubel.yusi.pojo.entity.MatchProfile;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.MatchProfileRepository;
import com.aseubel.yusi.repository.MidTermMemoryRepository;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.service.match.MatchProfileAssembler;
import com.aseubel.yusi.service.memory.MidTermMemoryLifecycleService;
import com.aseubel.yusi.service.memory.MidTermMemorySearchService;
import com.aseubel.yusi.service.memory.MidTermMemoryVectorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.response.SearchResp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.aseubel.yusi.evaluation.memory.MemoryLifecycleEvaluationFixture.MemoryRecord;
import static com.aseubel.yusi.evaluation.memory.MemoryLifecycleEvaluationFixture.Scenario;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestInfrastructureConfig.class)
class MemoryLifecycleEvaluationTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 8, 16, 12, 0);
    private static final Path REPORT_PATH = Path.of(
            "target", "evaluation", "memory-lifecycle-v1-report.json");

    private List<SearchResp.SearchResult> currentSearchHits = List.of();

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MidTermMemoryRepository memoryRepository;

    @Autowired
    private MatchProfileRepository matchProfileRepository;

    @Autowired
    private MidTermMemoryLifecycleService lifecycleService;

    @Autowired
    private MidTermMemorySearchService searchService;

    @Autowired
    private MatchProfileAssembler matchProfileAssembler;

    @MockBean(name = "embeddingModel")
    private EmbeddingModel embeddingModel;

    @MockBean(name = "milvusClientV2")
    private MilvusClientV2 milvusClientV2;

    @MockBean
    private MidTermMemoryVectorService midTermMemoryVectorService;

    @BeforeEach
    void configureExternalBoundaries() {
        when(embeddingModel.embed(anyString()))
                .thenReturn(Response.from(Embedding.from(new float[] {0.1f})));
        when(milvusClientV2.hybridSearch(any()))
                .thenAnswer(invocation -> SearchResp.builder()
                        .searchResults(List.of(currentSearchHits))
                        .build());
    }

    @Test
    void writesTheMemoryLifecycleEvaluationReport() throws Exception {
        List<OfflineEvaluationReportWriter.CaseResult> results = new ArrayList<>();
        Files.deleteIfExists(REPORT_PATH);
        try {
            MemoryLifecycleEvaluationFixture.Suite suite =
                    new MemoryLifecycleFixtureLoader(objectMapper).load();
            for (MemoryLifecycleEvaluationFixture.EvaluationCase evaluationCase : suite.cases()) {
                for (Scenario scenario : evaluationCase.scenarios()) {
                    results.add(replayScenario(evaluationCase.caseId(), scenario));
                }
            }
        } catch (Exception exception) {
            String code = exception instanceof MemoryLifecycleFixtureLoader.FixtureValidationException validation
                    ? validation.code() : "REPLAY_EXECUTION";
            results.add(failedCase("EVAL-MEM-001", "EVAL-MEM-001-A", code));
        } finally {
            OfflineEvaluationReportWriter.write(REPORT_PATH, "memory-lifecycle-v1", results);
        }

        assertFalse(results.isEmpty());
        assertTrue(results.stream().allMatch(result -> "PASS".equals(result.status())),
                () -> results.stream().flatMap(result -> result.violationCodes().stream()).toList().toString());
        assertTrue(Files.exists(REPORT_PATH));
        assertTrue(results.stream()
                .allMatch(result -> ((Number) result.actualSummary().get("profileLeakCount")).intValue() == 0));
    }

    private OfflineEvaluationReportWriter.CaseResult replayScenario(String caseId, Scenario scenario) {
        Checks checks = new Checks();
        Map<String, Long> memoryIds = Map.of();
        ScenarioMetrics metrics = ScenarioMetrics.empty();
        try {
            memoryIds = persistScenario(scenario);
            configureSearchCandidates(scenario, memoryIds);
            metrics = switch (scenario.scenarioId()) {
                case "EVAL-MEM-001-A" -> evaluateRetrievalScenario(scenario, checks);
                case "EVAL-MEM-001-B" -> evaluateMatchProfileScenario(scenario, checks);
                case "EVAL-MEM-001-C" -> evaluateDeleteScenario(scenario, memoryIds, checks);
                default -> {
                    checks.violate("UNKNOWN_SCENARIO");
                    yield ScenarioMetrics.empty();
                }
            };
        } catch (Exception exception) {
            checks.violate("REPLAY_EXECUTION");
        }

        Map<String, Object> actualSummary = new LinkedHashMap<>();
        actualSummary.put("availableCount", availableCount(scenario.userId()));
        actualSummary.put("matchableCount", matchableCount(scenario.userId()));
        actualSummary.put("retrievedCount", metrics.retrievedCount());
        actualSummary.put("profileLeakCount", metrics.profileLeakCount());
        actualSummary.put("remainingRowCount", memoryRepository.findByUserId(scenario.userId()).size());
        actualSummary.put("crossUserLeakCount", metrics.crossUserLeakCount());

        return new OfflineEvaluationReportWriter.CaseResult(
                caseId,
                scenario.scenarioId(),
                checks.hasFailures() ? "FAIL" : "PASS",
                "fixture-v1",
                "expectation-v1",
                OfflineEvaluationReportWriter.Versions.fixtureBaseline(),
                checks.assertionCount,
                checks.passedAssertionCount,
                checks.violationCodes,
                actualSummary);
    }

    private ScenarioMetrics evaluateRetrievalScenario(Scenario scenario, Checks checks) {
        String positiveToken = summaryFor(scenario, scenario.positiveMemoryKey());
        List<String> restrictedTokens = summaryTokens(scenario, scenario.expected().restrictedKeys());
        List<String> retrieved = searchService.searchMidTermMemory(scenario.userId(), "fixture-query", 10);
        checkRetrieval(checks, positiveToken, restrictedTokens, retrieved);

        String recent = searchService.getRecentMemories(scenario.userId(), 10);
        checkRetrieval(checks, positiveToken, restrictedTokens, List.of(recent));
        return new ScenarioMetrics(retrieved.size(), 0, 0);
    }

    private ScenarioMetrics evaluateMatchProfileScenario(Scenario scenario, Checks checks) {
        ScenarioMetrics retrievalMetrics = evaluateRetrievalScenario(scenario, checks);
        MatchProfile profile = matchProfileAssembler.refreshProfile(scenario.userId());
        String profileSummary = profile == null ? null : profile.getMidMemorySummary();
        String positiveToken = summaryFor(scenario, scenario.positiveMemoryKey());
        int profileLeakCount = scenario.memories().stream()
                .filter(memory -> !memory.memoryKey().equals(scenario.positiveMemoryKey()))
                .mapToInt(memory -> countOccurrences(profileSummary, memory.summaryToken()))
                .sum();
        boolean profilePositive = containsToken(profileSummary, positiveToken);
        checks.check("PROFILE_POSITIVE_CONTROL", profilePositive);
        checks.check("PROFILE_RESTRICTED_MEMORY_ABSENT", profilePositive && profileLeakCount == 0);
        checks.check("PROFILE_LEAK_THRESHOLD", profileLeakCount == 0);

        MatchProfile persisted = matchProfileRepository.findByUserId(scenario.userId()).orElse(null);
        checks.check("PROFILE_PERSISTED_FROM_REAL_H2", persisted != null
                && containsToken(persisted.getMidMemorySummary(), positiveToken));
        return new ScenarioMetrics(retrievalMetrics.retrievedCount(), profileLeakCount, 0);
    }

    private ScenarioMetrics evaluateDeleteScenario(Scenario scenario, Map<String, Long> memoryIds,
                                                    Checks checks) {
        String deleteToken = summaryFor(scenario, scenario.deleteMemoryKey());
        String retainedToken = summaryFor(scenario, scenario.expected().retainedUserMemoryKey());
        String otherToken = summaryFor(scenario, scenario.expected().otherUserMemoryKey());
        List<String> beforeDelete = searchService.searchMidTermMemory(scenario.userId(), "fixture-query", 10);
        checks.check("DELETE_POSITIVE_CONTROL_BEFORE_DELETE", containsToken(beforeDelete, deleteToken));
        String beforeRecent = searchService.getRecentMemories(scenario.userId(), 10);
        checks.check("DELETE_RECENT_POSITIVE_CONTROL_BEFORE_DELETE", containsToken(beforeRecent, deleteToken));

        Long deleteId = memoryIds.get(scenario.deleteMemoryKey());
        doThrow(new RuntimeException("fixture-vector-delete-failure"))
                .when(midTermMemoryVectorService).delete(deleteId);
        boolean deleteCompleted = true;
        try {
            lifecycleService.delete(scenario.userId(), deleteId);
        } catch (Exception exception) {
            deleteCompleted = false;
        }
        checks.check("DELETE_SURVIVES_VECTOR_FAILURE", deleteCompleted);
        verify(midTermMemoryVectorService).delete(deleteId);
        checks.check("DATABASE_ROW_DELETED", memoryRepository.findById(deleteId).isEmpty());

        configureSearchCandidates(scenario, memoryIds);
        List<String> afterDelete = searchService.searchMidTermMemory(scenario.userId(), "fixture-query", 10);
        checkRetrieval(checks, retainedToken, List.of(deleteToken), afterDelete);
        String afterRecent = searchService.getRecentMemories(scenario.userId(), 10);
        checkRetrieval(checks, retainedToken, List.of(deleteToken), List.of(afterRecent));

        boolean otherUserResidualLeaked = containsToken(afterDelete, otherToken);
        checks.check("OTHER_USER_RESIDUAL_NOT_LEAKED", !otherUserResidualLeaked);
        List<String> otherUserResults = searchService.searchMidTermMemory(
                scenario.expected().otherUserId(), "fixture-query", 10);
        checks.check("DELETE_DOES_NOT_AFFECT_OTHER_USER", containsToken(otherUserResults, otherToken));

        MatchProfile profile = matchProfileAssembler.refreshProfile(scenario.userId());
        String profileSummary = profile == null ? null : profile.getMidMemorySummary();
        int profileLeakCount = countOccurrences(profileSummary, deleteToken)
                + countOccurrences(profileSummary, otherToken);
        boolean profilePositive = containsToken(profileSummary, retainedToken);
        checks.check("POST_DELETE_PROFILE_POSITIVE_CONTROL", profilePositive);
        checks.check("POST_DELETE_PROFILE_RESTRICTED_MEMORY_ABSENT",
                profilePositive && profileLeakCount == 0);
        checks.check("PROFILE_LEAK_THRESHOLD", profileLeakCount == 0);

        return new ScenarioMetrics(afterDelete.size(), profileLeakCount,
                otherUserResidualLeaked ? 1 : 0);
    }

    private Map<String, Long> persistScenario(Scenario scenario) {
        scenario.memories().stream()
                .map(MemoryRecord::ownerUserId)
                .distinct()
                .forEach(this::ensureUser);

        Map<String, Long> memoryIds = new LinkedHashMap<>();
        int index = 0;
        for (MemoryRecord memory : scenario.memories()) {
            if ("MERGED".equals(memory.lifecycle())) {
                continue;
            }
            MidTermMemory saved = memoryRepository.saveAndFlush(toEntity(memory, index++, null));
            memoryIds.put(memory.memoryKey(), saved.getId());
        }
        for (MemoryRecord memory : scenario.memories()) {
            if (!"MERGED".equals(memory.lifecycle())) {
                continue;
            }
            Long survivorId = memoryIds.get(memory.mergedIntoKey());
            MidTermMemory saved = memoryRepository.saveAndFlush(toEntity(memory, index++, survivorId));
            memoryIds.put(memory.memoryKey(), saved.getId());
        }
        return memoryIds;
    }

    private void ensureUser(String userId) {
        if (userRepository.findByUserId(userId) != null) {
            return;
        }
        userRepository.saveAndFlush(User.builder()
                .userId(userId)
                .userName("fixture-name-" + userId)
                .password("fixture-password")
                .email(userId + "@fixture.invalid")
                .isMatchEnabled(false)
                .permissionLevel(0)
                .build());
    }

    private MidTermMemory toEntity(MemoryRecord record, int index, Long mergedIntoId) {
        boolean hidden = "HIDDEN".equals(record.lifecycle());
        LocalDateTime validUntil = "EXPIRED".equals(record.lifecycle())
                ? FIXED_NOW.minusMinutes(1) : null;
        return MidTermMemory.builder()
                .userId(record.ownerUserId())
                .sourceType(SourceType.MANUAL.code())
                .sourceId("fixture-source-" + record.memoryKey())
                .summary(record.summaryToken())
                .importance(0.8)
                .confidence(0.9)
                .matchAllowed(record.matchAllowed())
                .hidden(hidden)
                .createdAt(FIXED_NOW.minusMinutes(index))
                .updatedAt(FIXED_NOW)
                .validUntil(validUntil)
                .mergedIntoId(mergedIntoId)
                .build();
    }

    private void configureSearchCandidates(Scenario scenario, Map<String, Long> memoryIds) {
        currentSearchHits = scenario.vectorCandidates().stream()
                .map(candidate -> SearchResp.SearchResult.builder()
                        .entity(Map.of(
                                "text", candidate.summaryToken(),
                                "metadata", Map.of(
                                        "memoryId", String.valueOf(memoryIds.get(candidate.memoryKey())),
                                        "userId", candidate.ownerUserId())))
                        .score(1.0f)
                        .build())
                .toList();
    }

    private int availableCount(String userId) {
        return memoryRepository.findAvailableByUserId(userId, FIXED_NOW, PageRequest.of(0, 100)).size();
    }

    private int matchableCount(String userId) {
        return memoryRepository.findMatchableByUserId(userId, FIXED_NOW, PageRequest.of(0, 100)).size();
    }

    private void checkRetrieval(Checks checks, String positiveToken, List<String> restrictedTokens,
                                List<String> retrieved) {
        boolean positive = containsToken(retrieved, positiveToken);
        checks.check("RETRIEVAL_POSITIVE_CONTROL", positive);
        boolean restrictedAbsent = restrictedTokens.stream()
                .noneMatch(token -> containsToken(retrieved, token));
        checks.check("RESTRICTED_MEMORY_ABSENT", positive && restrictedAbsent);
    }

    private String summaryFor(Scenario scenario, String memoryKey) {
        return scenario.memories().stream()
                .filter(memory -> memory.memoryKey().equals(memoryKey))
                .map(MemoryRecord::summaryToken)
                .findFirst()
                .orElse("");
    }

    private List<String> summaryTokens(Scenario scenario, Set<String> memoryKeys) {
        return memoryKeys.stream()
                .map(memoryKey -> summaryFor(scenario, memoryKey))
                .toList();
    }

    private boolean containsToken(List<String> values, String token) {
        return values != null && values.stream().anyMatch(value -> containsToken(value, token));
    }

    private boolean containsToken(String value, String token) {
        return value != null && token != null && !token.isBlank() && value.contains(token);
    }

    private int countOccurrences(String value, String token) {
        if (value == null || token == null || token.isBlank()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private OfflineEvaluationReportWriter.CaseResult failedCase(String caseId, String scenarioId, String code) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("availableCount", 0);
        summary.put("matchableCount", 0);
        summary.put("retrievedCount", 0);
        summary.put("profileLeakCount", 0);
        summary.put("remainingRowCount", 0);
        summary.put("crossUserLeakCount", 0);
        return new OfflineEvaluationReportWriter.CaseResult(
                caseId, scenarioId, "FAIL", "fixture-v1", "expectation-v1",
                OfflineEvaluationReportWriter.Versions.fixtureBaseline(), 1, 0,
                List.of(code), summary);
    }

    private record ScenarioMetrics(int retrievedCount, int profileLeakCount, int crossUserLeakCount) {
        private static ScenarioMetrics empty() {
            return new ScenarioMetrics(0, 0, 0);
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
