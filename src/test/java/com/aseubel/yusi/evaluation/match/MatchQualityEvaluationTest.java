package com.aseubel.yusi.evaluation.match;

import com.aseubel.yusi.TestInfrastructureConfig;
import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.evaluation.OfflineEvaluationReportWriter;
import com.aseubel.yusi.evaluation.QualityGatePolicy;
import com.aseubel.yusi.pojo.entity.MatchFeedback;
import com.aseubel.yusi.pojo.entity.MatchProfile;
import com.aseubel.yusi.pojo.entity.ProductEvent;
import com.aseubel.yusi.pojo.entity.SoulConnection;
import com.aseubel.yusi.pojo.entity.SoulConnectionEvent;
import com.aseubel.yusi.pojo.entity.SoulConnectionStatus;
import com.aseubel.yusi.pojo.entity.SoulMatch;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.pojo.constant.MatchFeedbackAction;
import com.aseubel.yusi.repository.MatchFeedbackRepository;
import com.aseubel.yusi.repository.MatchProfileRepository;
import com.aseubel.yusi.repository.ProductEventRepository;
import com.aseubel.yusi.repository.ProductEventScopeRepository;
import com.aseubel.yusi.repository.SoulConnectionEventRepository;
import com.aseubel.yusi.repository.SoulConnectionRepository;
import com.aseubel.yusi.repository.SoulMatchRepository;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.service.ai.prompt.PromptManager;
import com.aseubel.yusi.service.ai.prompt.PromptSnapshot;
import com.aseubel.yusi.service.ai.model.ModelRouteContext;
import com.aseubel.yusi.service.ai.model.ModelRouteContextHolder;
import com.aseubel.yusi.service.match.MatchFeedbackService;
import com.aseubel.yusi.service.match.MatchService;
import com.aseubel.yusi.service.match.SoulConnectionLifecycleService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.response.SearchResp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static com.aseubel.yusi.evaluation.match.MatchQualityEvaluationFixture.EvaluationCase;
import static com.aseubel.yusi.evaluation.match.MatchQualityEvaluationFixture.Scenario;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestInfrastructureConfig.class)
class MatchQualityEvaluationTest {

    private static final String SUITE_ID = "match-quality-v1";
    private static final String CASE_ID = "EVAL-MATCH-001";
    private static final String REPORT_SCENARIO_ID = "EVAL-MATCH-001";
    private static final Set<String> CASE_IDS = Set.of(CASE_ID);
    private static final int MINIMUM_ASSERTION_COUNT = 20;
    private static final Path REPORT_PATH = Path.of(
            "target", "evaluation", "match-quality-v1-report.json");
    private static final String RERANK_JSON = "{\"resonance\":true,\"score\":99,"
            + "\"reason\":\"fixture-reason-one\",\"timingReason\":\"fixture-reason-two\","
            + "\"iceBreaker\":\"fixture-reason-three\"}";
    private static final String NON_RESONANT_JSON = "{\"resonance\":false,\"score\":0}";
    private static final String LETTER_TOKEN = "fixture-letter-token";
    private static final Set<String> FIXTURE_USERS = Set.of(
            "fixture-user-match-recall",
            "fixture-user-match-target",
            "fixture-user-match-secondary",
            "fixture-user-match-lifecycle-a",
            "fixture-user-match-lifecycle-b",
            "fixture-user-match-negative-a",
            "fixture-user-match-negative-b");
    private static final Set<String> RECOMMENDATION_PAYLOAD_KEYS = Set.of(
            "reasonCount", "profileVersion");

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MatchService matchService;

    @Autowired
    private SoulConnectionLifecycleService connectionLifecycleService;

    @Autowired
    private MatchFeedbackService matchFeedbackService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MatchProfileRepository matchProfileRepository;

    @Autowired
    private SoulMatchRepository soulMatchRepository;

    @Autowired
    private SoulConnectionRepository connectionRepository;

    @Autowired
    private SoulConnectionEventRepository connectionEventRepository;

    @Autowired
    private ProductEventRepository productEventRepository;

    @Autowired
    private ProductEventScopeRepository productEventScopeRepository;

    @Autowired
    private MatchFeedbackRepository matchFeedbackRepository;

    @MockBean
    private PromptManager promptManager;

    @MockBean(name = "milvusClientV2")
    private MilvusClientV2 milvusClientV2;

    @MockBean(name = "embeddingModel")
    private EmbeddingModel embeddingModel;

    @MockBean(name = "chatModel")
    private ChatModel chatModel;

    private final AtomicInteger chatCallCount = new AtomicInteger();
    private SearchResp currentSearchResponse;
    private String chatSuccessUserId;

    @BeforeEach
    void configureDeterministicBoundaries() {
        clearFixtureState();
        reset(promptManager, milvusClientV2, embeddingModel, chatModel);

        PromptSnapshot fixtureSnapshot = new PromptSnapshot(
                "fixture", "fixture-v1", "zh-CN", "fixture-token-prompt");
        when(promptManager.getPrompt(any(PromptKey.class))).thenReturn("fixture-token-prompt");
        when(promptManager.getPrompt(anyString())).thenReturn("fixture-token-prompt");
        when(promptManager.getSnapshot(any(PromptKey.class))).thenReturn(fixtureSnapshot);
        when(promptManager.getSnapshot(anyString())).thenReturn(fixtureSnapshot);
        when(embeddingModel.embed(anyString()))
                .thenReturn(Response.from(Embedding.from(new float[] {0.1f, 0.2f})));
        when(milvusClientV2.hybridSearch(any(HybridSearchReq.class)))
                .thenAnswer(invocation -> currentSearchResponse == null
                        ? searchResponse(List.of()) : currentSearchResponse);
        chatCallCount.set(0);
        chatSuccessUserId = null;
        when(chatModel.chat(any(UserMessage.class))).thenAnswer(invocation -> {
            ModelRouteContext context = ModelRouteContextHolder.get();
            chatCallCount.incrementAndGet();
            if (context != null && PromptKey.SOUL_MATCH.getKey().equals(context.getScene())) {
                if (chatSuccessUserId == null || !chatSuccessUserId.equals(context.getUserId())) {
                    return ChatResponse.builder().aiMessage(AiMessage.from(NON_RESONANT_JSON)).build();
                }
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(RERANK_JSON))
                        .build();
            }
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(LETTER_TOKEN))
                    .build();
        });
    }

    @AfterEach
    void clearFixtureRows() {
        clearFixtureState();
    }

    @Test
    void writesTheMatchingQualityEvaluationReport() throws Exception {
        Files.deleteIfExists(REPORT_PATH);
        MatchQualityEvaluationFixture.Suite suite =
                new MatchQualityFixtureLoader(objectMapper).load();
        List<ScenarioResult> scenarioResults = new ArrayList<>();
        for (EvaluationCase evaluationCase : suite.cases()) {
            for (Scenario scenario : evaluationCase.scenarios()) {
                clearFixtureState();
                configureDeterministicBoundaries();
                scenarioResults.add(replayScenario(scenario));
            }
        }

        MatchQualityEvaluationReport.CaseResult result = aggregateCaseResult(
                suite.cases().get(0).caseId(), scenarioResults);
        List<OfflineEvaluationReportWriter.CaseResult> genericResults =
                List.of(MatchQualityEvaluationReport.toGenericCase(result));
        QualityGatePolicy.requirePass(
                suite.suiteId(),
                genericResults,
                new QualityGatePolicy.SuiteContract(
                        SUITE_ID, CASE_IDS, MINIMUM_ASSERTION_COUNT));

        Map<String, Object> actualSummary = genericResults.get(0).actualSummary();
        Scenario recall = scenario(suite, "EVAL-MATCH-001-A");
        Scenario lifecycle = scenario(suite, "EVAL-MATCH-001-B");
        Scenario negative = scenario(suite, "EVAL-MATCH-001-C");
        QualityGatePolicy.requireMetricEquals(actualSummary, "recallExpectedCount",
                recall.recallCandidates().size(), "MATCH_RECALL_EXPECTED_COUNT");
        QualityGatePolicy.requireMetricEquals(actualSummary, "recallMatchedCount",
                recall.recallCandidates().size(), "MATCH_RECALL_COVERAGE");
        QualityGatePolicy.requireMetricEquals(actualSummary, "recommendationCount",
                1, "MATCH_RECOMMENDATION_COUNT");
        QualityGatePolicy.requireMetricEquals(actualSummary, "reasonCoveragePassCount",
                recall.expectedReasonCount(), "MATCH_REASON_COVERAGE");
        QualityGatePolicy.requireMetricEquals(actualSummary, "startedInteractionPassCount",
                1, "MATCH_STARTED_INTERACTION");
        QualityGatePolicy.requireMetricEquals(actualSummary, "mutualResonancePassCount",
                1, "MATCH_MUTUAL_RESONANCE");
        QualityGatePolicy.requireMetricEquals(actualSummary, "strongNegativeExcludedCount",
                1, "MATCH_STRONG_NEGATIVE_EXCLUSION");
        QualityGatePolicy.requireMetricEquals(actualSummary, "recommendedCount",
                1, "MATCH_RECOMMENDED_COUNT");
        QualityGatePolicy.requireMetricEquals(actualSummary, "acceptedCount",
                2,
                "MATCH_ACCEPTED_COUNT");
        QualityGatePolicy.requireMetricEquals(actualSummary, "viewedCount", 0,
                "MATCH_VIEWED_DENOMINATOR");
        assertFalse(negative.acceptanceRateAvailable());
        assertFalse(QualityGatePolicy.booleanMetric(actualSummary, "acceptanceRateAvailable"));

        MatchQualityEvaluationReport.write(REPORT_PATH, List.of(result));
        assertTrue(Files.exists(REPORT_PATH));
        assertTrue(scenarioResults.stream().allMatch(item -> !item.checks().hasFailures()),
                () -> scenarioResults.stream().flatMap(item -> item.checks().violationCodes.stream()).toList()
                        .toString());
        assertLowSensitivityReport();
    }

    private ScenarioResult replayScenario(Scenario scenario) {
        Checks checks = new Checks();
        Metrics metrics = Metrics.empty();
        try {
            metrics = switch (scenario.scenarioId()) {
                case "EVAL-MATCH-001-A" -> replayRecall(scenario, checks);
                case "EVAL-MATCH-001-B" -> replayLifecycle(scenario, checks);
                case "EVAL-MATCH-001-C" -> replayStrongNegative(scenario, checks);
                default -> {
                    checks.violate("UNKNOWN_SCENARIO");
                    yield Metrics.empty();
                }
            };
        } catch (Exception exception) {
            checks.violate("REPLAY_EXECUTION");
        }
        return new ScenarioResult(scenario.scenarioId(), checks, metrics);
    }

    private Metrics replayRecall(Scenario scenario, Checks checks) throws Exception {
        saveUser(scenario.userId(), true);
        saveUser(scenario.targetProfileKey(), true);
        saveUser(scenario.recallCandidates().get(1), true);
        matchProfileRepository.saveAndFlush(profile(scenario.userId()));
        matchProfileRepository.saveAndFlush(profile(scenario.targetProfileKey()));
        matchProfileRepository.saveAndFlush(profile(scenario.recallCandidates().get(1)));
        currentSearchResponse = searchResponse(scenario.recallCandidates());
        chatCallCount.set(0);
        chatSuccessUserId = scenario.userId();

        matchService.runWeeklyMatching();

        List<SoulMatch> matches = pairMatches(scenario.userId(), scenario.targetProfileKey());
        ArgumentCaptor<HybridSearchReq> requestCaptor =
                ArgumentCaptor.forClass(HybridSearchReq.class);
        org.mockito.Mockito.verify(milvusClientV2, org.mockito.Mockito.atLeastOnce())
                .hybridSearch(requestCaptor.capture());
        List<HybridSearchReq> userQueries = requestCaptor.getAllValues().stream()
                .filter(request -> request.getSearchRequests() != null
                        && request.getSearchRequests().stream()
                                .allMatch(search -> search.getFilter() != null
                                        && search.getFilter().contains("'" + scenario.userId() + "'")))
                .toList();
        int recallMatchedCount = userQueries.isEmpty() ? 0
                : distinctCandidateIds(currentSearchResponse).stream()
                        .filter(scenario.recallCandidates()::contains)
                        .count() > Integer.MAX_VALUE
                                ? Integer.MAX_VALUE
                                : (int) distinctCandidateIds(currentSearchResponse).stream()
                                        .filter(scenario.recallCandidates()::contains).count();
        checks.check("RECALL_QUERY_CAPTURED", !userQueries.isEmpty());
        checks.check("RECALL_CANDIDATE_COVERAGE",
                recallMatchedCount == scenario.recallCandidates().size());
        checks.check("RECALL_SELF_NOT_RETURNED",
                distinctCandidateIds(currentSearchResponse).stream()
                        .noneMatch(scenario.userId()::equals));
        checks.check("RECALL_QUERY_EXCLUDES_OWNER",
                userQueries.stream().allMatch(request -> request.getSearchRequests().stream()
                        .allMatch(search -> search.getFilter().contains(
                                "metadata[\"userId\"] != '" + scenario.userId() + "'"))));
        checks.check("TARGET_RECOMMENDATION_PERSISTED",
                matches.size() == 1 && matches.get(0).getUserAId() != null
                        && (scenario.targetProfileKey().equals(matches.get(0).getUserAId())
                                || scenario.targetProfileKey().equals(matches.get(0).getUserBId())));
        checks.check("CROSS_USER_CANDIDATE_NO_LEAK",
                matches.size() == 1
                        && Set.of(matches.get(0).getUserAId(), matches.get(0).getUserBId())
                                .equals(Set.of(scenario.userId(), scenario.targetProfileKey())));
        checks.check("RESONANCE_JSON_NOT_PERSISTED",
                matches.stream().allMatch(match -> !RERANK_JSON.equals(match.getLetterAtoB())
                        && !RERANK_JSON.equals(match.getLetterBtoA())));
        checks.check("FIXED_CHAT_BOUNDARY_USED", chatCallCount.get() >= 3);

        List<ProductEvent> recommendationEvents = productEventRepository.findAll().stream()
                .filter(event -> "match.recommended".equals(event.getEventName())
                        && event.getMatchId() != null
                        && matches.stream().map(SoulMatch::getId).anyMatch(event.getMatchId()::equals))
                .toList();
        checks.check("RECOMMENDATION_EVENT_PERSISTED", recommendationEvents.size() == 1);
        ProductEvent recommendationEvent = recommendationEvents.isEmpty()
                ? null : recommendationEvents.get(0);
        checks.check("RECOMMENDATION_EVENT_LOW_SENSITIVITY",
                recommendationEvent != null && "LOW".equals(recommendationEvent.getSensitivity()));

        Map<String, Object> payload = recommendationEvent == null
                ? Map.of() : objectMapper.readValue(recommendationEvent.getPayloadJson(),
                        new TypeReference<Map<String, Object>>() { });
        int reasonCount = payload.get("reasonCount") instanceof Number number
                ? number.intValue() : 0;
        checks.check("RECOMMENDATION_PAYLOAD_ALLOWLIST",
                !payload.isEmpty() && RECOMMENDATION_PAYLOAD_KEYS.containsAll(payload.keySet()));
        checks.check("REASON_COUNT_EXACT", reasonCount == scenario.expectedReasonCount());

        return new Metrics(scenario.recallCandidates().size(), recallMatchedCount, matches.size(),
                reasonCount, 0, 0, 0, matches.size(), 0, 0, false);
    }

    private Metrics replayLifecycle(Scenario scenario, Checks checks) {
        String userA = scenario.participantUserIds().get(0);
        String userB = scenario.participantUserIds().get(1);
        saveUser(userA, false);
        saveUser(userB, false);
        SoulMatch match = soulMatchRepository.saveAndFlush(match(userA, userB,
                LocalDateTime.now().minusDays(1)));

        match.setStatusA(1);
        soulMatchRepository.saveAndFlush(match);
        SoulConnection firstAccept = connectionLifecycleService.accept(match, userA);
        checks.check("FIRST_ACCEPT_WAITING_REPLY",
                firstAccept.getStatus() == SoulConnectionStatus.WAITING_REPLY);

        match = soulMatchRepository.findById(match.getId()).orElseThrow();
        match.setStatusB(1);
        match.setIsMatched(true);
        soulMatchRepository.saveAndFlush(match);
        SoulConnection secondAccept = connectionLifecycleService.accept(match, userB);
        checks.check("SECOND_ACCEPT_STARTED",
                secondAccept.getStatus() == SoulConnectionStatus.STARTED);
        checks.check("STARTED_ALLOWS_CHAT_BEFORE_SECOND_SIGNAL",
                secondAccept.getStatus() == SoulConnectionStatus.STARTED
                        && secondAccept.getStatus().allowsChat());

        matchFeedbackService.recordConnectionFeedback(secondAccept.getId(), match.getId(), userA,
                MatchFeedbackAction.DEEP_INTERACTION.code());
        matchFeedbackRepository.flush();
        SoulConnection beforeSecondSignal = connectionRepository.findByMatchId(match.getId()).orElseThrow();
        checks.check("FIRST_DEEP_SIGNAL_DOES_NOT_RESONATE",
                beforeSecondSignal.getStatus() == SoulConnectionStatus.STARTED
                        && !matchFeedbackService.hasMutualDeepInteraction(beforeSecondSignal, match));

        matchFeedbackService.recordConnectionFeedback(secondAccept.getId(), match.getId(), userB,
                MatchFeedbackAction.DEEP_INTERACTION.code());
        matchFeedbackRepository.flush();
        SoulConnection afterBothSignals = connectionRepository.findByMatchId(match.getId()).orElseThrow();
        boolean bothDeep = matchFeedbackService.hasMutualDeepInteraction(afterBothSignals, match);
        checks.check("BOTH_DEEP_SIGNALS_RECORDED", bothDeep
                && deepFeedbackCount(match.getId()) == 2);
        checks.check("SECOND_DEEP_SIGNAL_REMAINS_STARTED_BEFORE_MARK",
                bothDeep && afterBothSignals.getStatus() == SoulConnectionStatus.STARTED
                        && afterBothSignals.getStatus().allowsChat());
        SoulConnection mutual = connectionLifecycleService.markMutualResonance(match, userB);
        checks.check("MUTUAL_RESONANCE_AFTER_BOTH_SIGNALS",
                mutual.getStatus() == SoulConnectionStatus.MUTUAL_RESONANCE);
        checks.check("MUTUAL_RESONANCE_ALLOWS_CHAT", mutual.getStatus().allowsChat());

        List<SoulConnectionEvent> connectionEvents = connectionEventRepository
                .findByConnectionIdOrderByOccurredAtAscIdAsc(mutual.getId());
        List<String> expectedEventNames = List.of(
                "connection.accepted", "connection.accepted", "connection.mutual_resonance");
        checks.check("CONNECTION_EVENT_ORDER",
                connectionEvents.stream().map(SoulConnectionEvent::getEventName).toList()
                        .equals(expectedEventNames));
        Long lifecycleMatchId = match.getId();
        List<String> productEventNames = productEventRepository.findAll().stream()
                .filter(event -> lifecycleMatchId.equals(event.getMatchId()))
                .sorted(Comparator.comparing(ProductEvent::getOccurredAt)
                        .thenComparing(ProductEvent::getId))
                .map(ProductEvent::getEventName)
                .toList();
        checks.check("PRODUCT_EVENT_ORDER", productEventNames.equals(expectedEventNames));
        checks.check("ACCEPTED_TRANSITION_COUNT",
                connectionEvents.stream().filter(event -> "connection.accepted".equals(event.getEventName()))
                        .count() == 2);

        return new Metrics(0, 0, 0, 0, 1, 1, 0, 0, 2, 0, false);
    }

    private Metrics replayStrongNegative(Scenario scenario, Checks checks) {
        String userA = scenario.participantUserIds().get(0);
        String userB = scenario.participantUserIds().get(1);
        saveUser(userA, true);
        saveUser(userB, true);
        matchProfileRepository.saveAndFlush(profile(userA));
        matchProfileRepository.saveAndFlush(profile(userB));
        SoulMatch oldMatch = soulMatchRepository.saveAndFlush(match(userA, userB,
                LocalDateTime.now().minusDays(30)));
        matchFeedbackService.recordReport(oldMatch.getId(), userA);
        matchFeedbackRepository.flush();

        boolean oldEnough = oldMatch.getCreateTime().isBefore(LocalDateTime.now().minusDays(14));
        boolean strongNegative = matchFeedbackService.hasStrongNegativeSignal(oldMatch.getId());
        long beforePairCount = pairMatches(userA, userB).size();
        currentSearchResponse = searchResponse(List.of(userB));
        chatCallCount.set(0);
        matchService.runWeeklyMatching();
        long afterPairCount = pairMatches(userA, userB).size();
        checks.check("REPORT_FEEDBACK_PERSISTED",
                matchFeedbackRepository.findAll().stream().anyMatch(feedback ->
                        oldMatch.getId().equals(feedback.getMatchId())
                                && userA.equals(feedback.getUserId())
                                && MatchFeedbackAction.REPORT.code().equals(feedback.getAction())));
        checks.check("STRONG_NEGATIVE_SIGNAL_TRUE", strongNegative);
        checks.check("PAIR_OUTSIDE_RECENT_EXPOSURE_WINDOW", oldEnough);
        checks.check("STRONG_NEGATIVE_EXCLUDES_NEW_RECOMMENDATION",
                afterPairCount == beforePairCount && afterPairCount == 1);
        checks.check("NO_NEW_SOUL_MATCH_ROW",
                soulMatchRepository.findAll().stream().filter(item -> samePair(item, userA, userB)).count() == 1);
        checks.check("STRONG_NEGATIVE_BRANCH_PRECEDES_RERANK", chatCallCount.get() == 0);

        return new Metrics(0, 0, 0, 0, 0, 0, 1, 0, 0, 0, false);
    }

    private MatchQualityEvaluationReport.CaseResult aggregateCaseResult(
            String caseId, List<ScenarioResult> scenarioResults) {
        int recallExpected = scenarioResults.stream().mapToInt(item -> item.metrics().recallExpectedCount()).sum();
        int recallMatched = scenarioResults.stream().mapToInt(item -> item.metrics().recallMatchedCount()).sum();
        int recommendationCount = scenarioResults.stream().mapToInt(item -> item.metrics().recommendationCount()).sum();
        int reasonCount = scenarioResults.stream().mapToInt(item -> item.metrics().reasonCoveragePassCount()).sum();
        int startedCount = scenarioResults.stream().mapToInt(item -> item.metrics().startedInteractionPassCount()).sum();
        int mutualCount = scenarioResults.stream().mapToInt(item -> item.metrics().mutualResonancePassCount()).sum();
        int negativeCount = scenarioResults.stream().mapToInt(item -> item.metrics().strongNegativeExcludedCount()).sum();
        int recommendedCount = scenarioResults.stream().mapToInt(item -> item.metrics().recommendedCount()).sum();
        int acceptedCount = scenarioResults.stream().mapToInt(item -> item.metrics().acceptedCount()).sum();
        int viewedCount = scenarioResults.stream().mapToInt(item -> item.metrics().viewedCount()).sum();
        boolean rateAvailable = scenarioResults.stream().anyMatch(item -> item.metrics().acceptanceRateAvailable());
        List<String> violations = scenarioResults.stream()
                .flatMap(item -> item.checks().violationCodes.stream())
                .sorted()
                .toList();
        int assertions = scenarioResults.stream().mapToInt(item -> item.checks().assertionCount).sum();
        int passed = scenarioResults.stream().mapToInt(item -> item.checks().passedAssertionCount).sum();
        return new MatchQualityEvaluationReport.CaseResult(
                caseId,
                REPORT_SCENARIO_ID,
                violations.isEmpty() ? "PASS" : "FAIL",
                "fixture-v1",
                "expectation-v1",
                MatchQualityEvaluationReport.Versions.fixtureBaseline(),
                assertions,
                passed,
                violations,
                new MatchQualityEvaluationReport.ActualSummary(
                        recallExpected, recallMatched, recommendationCount, reasonCount,
                        startedCount, mutualCount, negativeCount, recommendedCount,
                        acceptedCount, viewedCount, rateAvailable));
    }

    private Scenario scenario(MatchQualityEvaluationFixture.Suite suite, String scenarioId) {
        return suite.cases().stream()
                .flatMap(evaluationCase -> evaluationCase.scenarios().stream())
                .filter(item -> scenarioId.equals(item.scenarioId()))
                .findFirst()
                .orElseThrow();
    }

    private void saveUser(String userId, boolean enabled) {
        userRepository.saveAndFlush(User.builder()
                .userId(userId)
                .userName(userId)
                .isMatchEnabled(enabled)
                .matchIntent("fixture-intent")
                .build());
    }

    private MatchProfile profile(String userId) {
        return MatchProfile.builder()
                .userId(userId)
                .profileText("fixture-profile-" + userId)
                .lifeGraphSummary("fixture-life-" + userId)
                .personaSummary("fixture-persona-" + userId)
                .midMemorySummary("fixture-memory-" + userId)
                .version(1L)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private SoulMatch match(String userA, String userB, LocalDateTime createTime) {
        return SoulMatch.builder()
                .generationRunId("fixture-match-run")
                .userAId(userA)
                .userBId(userB)
                .letterAtoB(LETTER_TOKEN)
                .letterBtoA(LETTER_TOKEN)
                .reason("fixture-reason-one")
                .timingReason("fixture-reason-two")
                .iceBreaker("fixture-reason-three")
                .score(99)
                .statusA(0)
                .statusB(0)
                .isMatched(false)
                .createTime(createTime)
                .updateTime(createTime)
                .build();
    }

    private List<SoulMatch> pairMatches(String userA, String userB) {
        return soulMatchRepository.findAll().stream()
                .filter(match -> samePair(match, userA, userB))
                .toList();
    }

    private boolean samePair(SoulMatch match, String userA, String userB) {
        return (userA.equals(match.getUserAId()) && userB.equals(match.getUserBId()))
                || (userB.equals(match.getUserAId()) && userA.equals(match.getUserBId()));
    }

    private long deepFeedbackCount(Long matchId) {
        return matchFeedbackRepository.findAll().stream()
                .filter(feedback -> matchId.equals(feedback.getMatchId())
                        && MatchFeedbackAction.DEEP_INTERACTION.code().equals(feedback.getAction()))
                .count();
    }

    private SearchResp searchResponse(List<String> candidateIds) {
        List<SearchResp.SearchResult> results = candidateIds.stream()
                .map(userId -> SearchResp.SearchResult.builder()
                        .entity(Map.of("metadata", Map.of("userId", userId)))
                        .score(1.0f)
                        .build())
                .toList();
        return SearchResp.builder().searchResults(List.of(results)).build();
    }

    private Set<String> distinctCandidateIds(SearchResp response) {
        if (response == null || response.getSearchResults() == null
                || response.getSearchResults().isEmpty()
                || response.getSearchResults().get(0) == null) {
            return Set.of();
        }
        Set<String> ids = new HashSet<>();
        for (SearchResp.SearchResult result : response.getSearchResults().get(0)) {
            if (result == null || result.getEntity() == null) {
                continue;
            }
            Object metadata = result.getEntity().get("metadata");
            if (metadata instanceof Map<?, ?> map && map.get("userId") != null) {
                ids.add(map.get("userId").toString());
            }
        }
        return ids;
    }

    private void clearFixtureState() {
        List<SoulMatch> matches = soulMatchRepository.findAll().stream()
                .filter(item -> FIXTURE_USERS.contains(item.getUserAId())
                        || FIXTURE_USERS.contains(item.getUserBId()))
                .toList();
        Set<Long> matchIds = matches.stream().map(SoulMatch::getId).collect(java.util.stream.Collectors.toSet());
        List<SoulConnection> connections = connectionRepository.findAll().stream()
                .filter(item -> matchIds.contains(item.getMatchId())
                        || FIXTURE_USERS.contains(item.getUserAId())
                        || FIXTURE_USERS.contains(item.getUserBId()))
                .toList();
        Set<Long> connectionIds = connections.stream().map(SoulConnection::getId)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        List<ProductEvent> events = productEventRepository.findAll().stream()
                .filter(item -> matchIds.contains(item.getMatchId())
                        || connectionIds.contains(item.getConnectionId())
                        || FIXTURE_USERS.contains(item.getUserId())
                        || FIXTURE_USERS.contains(item.getActorUserId()))
                .toList();
        Set<String> eventIds = events.stream().map(ProductEvent::getEventId)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        productEventScopeRepository.deleteAll(productEventScopeRepository.findAll().stream()
                .filter(scope -> eventIds.contains(scope.getEventId())).toList());
        productEventScopeRepository.flush();
        productEventRepository.deleteAll(events);
        productEventRepository.flush();
        connectionEventRepository.deleteAll(connectionEventRepository.findAll().stream()
                .filter(item -> matchIds.contains(item.getMatchId())
                        || connectionIds.contains(item.getConnectionId())).toList());
        connectionEventRepository.flush();
        matchFeedbackRepository.deleteAll(matchFeedbackRepository.findAll().stream()
                .filter(item -> matchIds.contains(item.getMatchId()) || FIXTURE_USERS.contains(item.getUserId()))
                .toList());
        matchFeedbackRepository.flush();
        connectionRepository.deleteAll(connections);
        connectionRepository.flush();
        soulMatchRepository.deleteAll(matches);
        soulMatchRepository.flush();
        matchProfileRepository.deleteAll(matchProfileRepository.findAll().stream()
                .filter(item -> FIXTURE_USERS.contains(item.getUserId())).toList());
        matchProfileRepository.flush();
        userRepository.deleteAll(userRepository.findAll().stream()
                .filter(item -> FIXTURE_USERS.contains(item.getUserId())).toList());
        userRepository.flush();
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
            assertFalse(reportText.contains(forbidden));
        }
    }

    private record ScenarioResult(String scenarioId, Checks checks, Metrics metrics) {
    }

    private record Metrics(int recallExpectedCount,
                           int recallMatchedCount,
                           int recommendationCount,
                           int reasonCoveragePassCount,
                           int startedInteractionPassCount,
                           int mutualResonancePassCount,
                           int strongNegativeExcludedCount,
                           int recommendedCount,
                           int acceptedCount,
                           int viewedCount,
                           boolean acceptanceRateAvailable) {
        private static Metrics empty() {
            return new Metrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false);
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
