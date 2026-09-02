package com.aseubel.yusi.evaluation.chat;

import com.aseubel.yusi.TestInfrastructureConfig;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.common.constant.SourceType;
import com.aseubel.yusi.common.utils.SensitiveWordUtils;
import com.aseubel.yusi.controller.AiController;
import com.aseubel.yusi.evaluation.OfflineEvaluationReportWriter.CaseResult;
import com.aseubel.yusi.evaluation.OfflineEvaluationReportWriter;
import com.aseubel.yusi.evaluation.QualityGatePolicy;
import com.aseubel.yusi.pojo.dto.chat.AgentStreamEvent;
import com.aseubel.yusi.pojo.dto.chat.ChatRequest;
import com.aseubel.yusi.pojo.entity.MidTermMemory;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.ChatMemoryMessageRepository;
import com.aseubel.yusi.repository.MidTermMemoryRepository;
import com.aseubel.yusi.repository.UserPersonaRepository;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.service.ai.chat.ContextBuilderService;
import com.aseubel.yusi.service.ai.prompt.PromptManager;
import com.aseubel.yusi.service.ai.prompt.PromptSnapshot;
import com.aseubel.yusi.service.ai.runtime.AgentRunTraceService;
import com.aseubel.yusi.service.ai.runtime.AgentToolExecutionAttemptRegistry;
import com.aseubel.yusi.service.ai.runtime.AgentToolTraceService;
import com.aseubel.yusi.service.ai.runtime.AiLockService;
import com.aseubel.yusi.service.ai.runtime.ChatStreamCancellationRegistry;
import com.aseubel.yusi.service.cognition.CognitiveConflictDetector;
import com.aseubel.yusi.service.diary.Assistant;
import com.aseubel.yusi.service.oss.OssService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutionResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.aseubel.yusi.evaluation.chat.ChatQualityEvaluationFixture.EvaluationCase;
import static com.aseubel.yusi.evaluation.chat.ChatQualityEvaluationFixture.Scenario;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestInfrastructureConfig.class)
class ChatQualityEvaluationTest {

    private static final String SUITE_ID = "chat-quality-v1";
    private static final Path REPORT_PATH = Path.of(
            "target", "evaluation", "chat-quality-v1-report.json");
    private static final Set<String> CASE_IDS = Set.of(
            "EVAL-CHAT-001", "EVAL-CHAT-002", "EVAL-CHAT-003", "EVAL-TOOL-001");
    private static final int MINIMUM_ASSERTION_COUNT = 8;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserPersonaRepository userPersonaRepository;

    @Autowired
    private MidTermMemoryRepository midTermMemoryRepository;

    @Autowired
    private ChatMemoryMessageRepository chatMemoryMessageRepository;

    @Autowired
    private ContextBuilderService contextBuilderService;

    @MockBean
    private PromptManager promptManager;

    @MockBean
    private CognitiveConflictDetector conflictDetector;

    @MockBean(name = "diaryAssistant")
    private Assistant diaryAssistant;

    @BeforeEach
    void configureDeterministicBoundaries() {
        when(promptManager.getPrompt(any(PromptKey.class))).thenReturn("fixture-token-prompt");
        when(promptManager.getPrompt(anyString())).thenReturn("fixture-token-prompt");
        when(promptManager.getPrompt(PromptKey.CHAT)).thenReturn("fixture-token-chat");
        when(promptManager.getPrompt(PromptKey.AGENT_PERSONA))
                .thenReturn("{\"default\":\"FIXTURE_STYLE\"}");
        when(promptManager.getSnapshot(any(PromptKey.class))).thenReturn(
                new PromptSnapshot("fixture", "fixture-v1", "zh-CN", "fixture-token-prompt"));
        when(conflictDetector.getUnresolvedContext(anyString())).thenReturn(null);
        deleteFixtureRows();
    }

    @AfterEach
    void clearFixtureState() {
        deleteFixtureRows();
        UserContext.clear();
    }

    @Test
    void writesTheChatQualityEvaluationReport() throws Exception {
        List<CaseResult> results = new ArrayList<>();
        Files.deleteIfExists(REPORT_PATH);
        ChatQualityEvaluationFixture.Suite suite =
                new ChatQualityFixtureLoader(objectMapper).load();
        for (EvaluationCase evaluationCase : suite.cases()) {
            for (Scenario scenario : evaluationCase.scenarios()) {
                results.add(replayScenario(evaluationCase.caseId(), scenario));
            }
        }

        try {
            QualityGatePolicy.requirePass(
                    suite.suiteId(),
                    results,
                    new QualityGatePolicy.SuiteContract(
                            SUITE_ID, CASE_IDS, MINIMUM_ASSERTION_COUNT));
        } finally {
            ChatQualityEvaluationReport.write(REPORT_PATH, results);
        }

        assertTrue(results.stream().allMatch(result -> "PASS".equals(result.status())));
        assertTrue(Files.exists(REPORT_PATH));
        assertLowSensitivityReport();
    }

    private CaseResult replayScenario(String caseId, Scenario scenario) {
        Checks checks = new Checks();
        ReplayMetrics metrics = ReplayMetrics.empty();
        try {
            metrics = switch (scenario.inputKind()) {
                case "NO_HISTORY" -> replayNoHistory(scenario, checks);
                case "SUPPORTED_AND_UNSUPPORTED_MEMORY" -> replayMemoryVisibility(scenario, checks);
                case "UNRESOLVED_CONFLICT" -> replayConflict(scenario, checks);
                case "TOOL_FAILURE" -> replayToolFailure(checks);
                default -> {
                    checks.violate("UNKNOWN_INPUT_KIND");
                    yield ReplayMetrics.empty();
                }
            };
        } catch (Exception exception) {
            checks.violate("REPLAY_EXECUTION");
        }

        Map<String, Object> actualSummary = Map.of(
                "memoryReferencePolicyPassCount", metrics.memoryReferencePolicyPassCount(),
                "tonePolicyPassCount", metrics.tonePolicyPassCount(),
                "noUnsupportedClaimPolicyPassCount", metrics.noUnsupportedClaimPolicyPassCount(),
                "contextPositiveControlPassCount", metrics.contextPositiveControlPassCount(),
                "restrictedContextLeakCount", metrics.restrictedContextLeakCount(),
                "privacyBoundaryViolationCount", metrics.privacyBoundaryViolationCount(),
                "conflictPolicyPassCount", metrics.conflictPolicyPassCount(),
                "toolParameterResultExposureCount", metrics.toolParameterResultExposureCount(),
                "toolLifecyclePassCount", metrics.toolLifecyclePassCount(),
                "semanticModelScoreAvailable", false);
        return new CaseResult(
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

    private ReplayMetrics replayNoHistory(Scenario scenario, Checks checks) {
        ensureUser(scenario.userId());
        String context = contextBuilderService.buildSystemMessageStr(scenario.userId());
        boolean relationshipStage = context.contains("不要凭空捏造回忆");
        boolean noMidMemory = !context.contains("<mid_memory_context>");
        checks.check("RELATIONSHIP_STAGE_NO_INVENTION", relationshipStage);
        checks.check("NO_MID_MEMORY_WITHOUT_HISTORY", noMidMemory);
        return new ReplayMetrics(0, 0, relationshipStage ? 1 : 0, 0, 0, 0, 0, 0, 0);
    }

    private ReplayMetrics replayMemoryVisibility(Scenario scenario, Checks checks) {
        ensureUser(scenario.userId());
        ensureUser("fixture-user-chat-other");
        LocalDateTime now = LocalDateTime.now();
        midTermMemoryRepository.saveAndFlush(memory(scenario.userId(), "fixture-memory-visible-b",
                now.minusMinutes(1), null, false));
        midTermMemoryRepository.saveAndFlush(memory(scenario.userId(), "fixture-memory-hidden-b",
                now.minusMinutes(2), null, true));
        midTermMemoryRepository.saveAndFlush(memory(scenario.userId(), "fixture-memory-expired-b",
                now.minusMinutes(3), now.minusMinutes(1), false));
        midTermMemoryRepository.saveAndFlush(memory("fixture-user-chat-other", "fixture-memory-other-b",
                now.minusMinutes(4), null, false));

        String context = contextBuilderService.buildSystemMessageStr(scenario.userId());
        boolean visible = context.contains("fixture-memory-visible-b");
        int restrictedLeakCount = (int) scenario.restrictedMemoryKeys().stream()
                .filter(context::contains)
                .count();
        checks.check("VISIBLE_MEMORY_POSITIVE_CONTROL", visible);
        checks.check("RESTRICTED_MEMORY_NOT_IN_CONTEXT", restrictedLeakCount == 0);
        checks.check("MID_MEMORY_CONTEXT_PRESENT", context.contains("<mid_memory_context>"));
        return new ReplayMetrics(visible ? 1 : 0, 1, 0, 0, restrictedLeakCount,
                restrictedLeakCount, 0, 0, 0);
    }

    private ReplayMetrics replayConflict(Scenario scenario, Checks checks) {
        ensureUser(scenario.userId());
        when(conflictDetector.getUnresolvedContext(eq(scenario.userId())))
                .thenReturn("FIXTURE_CONFLICT_REQUIRES_ATTENTION");
        String context = contextBuilderService.buildSystemMessageStr(scenario.userId());
        boolean conflictPolicy = context.contains("<cognitive_conflicts>")
                && context.contains("FIXTURE_CONFLICT_REQUIRES_ATTENTION");
        checks.check("CONFLICT_REQUIRES_ATTENTION", conflictPolicy);
        return new ReplayMetrics(0, 0, 0, 0, 0, 0, conflictPolicy ? 1 : 0, 0, 0);
    }

    private ReplayMetrics replayToolFailure(Checks checks) {
        String userId = "fixture-user-chat-d";
        String requestId = "fixture-run-tool";
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        SensitiveWordUtils sensitiveWords = mock(SensitiveWordUtils.class);
        AiLockService aiLockService = mock(AiLockService.class);
        OssService ossService = mock(OssService.class);
        AgentRunTraceService runTraceService = mock(AgentRunTraceService.class);
        AgentToolTraceService toolTraceService = mock(AgentToolTraceService.class);
        AgentToolExecutionAttemptRegistry attemptRegistry = mock(AgentToolExecutionAttemptRegistry.class);
        ChatStreamCancellationRegistry registry = new ChatStreamCancellationRegistry();
        AtomicReference<Runnable> submittedTask = new AtomicReference<>();
        RecordingAiController controller = new RecordingAiController(executor, sensitiveWords);
        ReflectionTestUtils.setField(controller, "diaryAssistant", diaryAssistant);
        ReflectionTestUtils.setField(controller, "aiLockService", aiLockService);
        ReflectionTestUtils.setField(controller, "ossService", ossService);
        ReflectionTestUtils.setField(controller, "agentRunTraceService", runTraceService);
        ReflectionTestUtils.setField(controller, "agentToolTraceService", toolTraceService);
        ReflectionTestUtils.setField(controller, "agentToolExecutionAttemptRegistry", attemptRegistry);
        ReflectionTestUtils.setField(controller, "chatStreamCancellationRegistry", registry);

        TokenStream tokenStream = tokenStream();
        when(diaryAssistant.chatWithMessage(eq(userId), anyString(), anyList())).thenReturn(tokenStream);
        when(aiLockService.tryAcquireLock(userId)).thenReturn(true);
        when(sensitiveWords.checkAndHandleViolation(anyString(), anyString())).thenReturn(null);
        doAnswer(invocation -> {
            submittedTask.set(invocation.getArgument(0));
            return null;
        }).when(executor).execute(any(Runnable.class));

        UserContext.setUserId(userId);
        controller.chatStream(ChatRequest.builder()
                .requestId(requestId)
                .message("fixture-chat-input")
                .images(List.of())
                .build());
        submittedTask.get().run();

        ArgumentCaptor<Consumer<BeforeToolExecution>> beforeCaptor =
                ArgumentCaptor.forClass(Consumer.class);
        ArgumentCaptor<Consumer<ToolExecution>> completedCaptor =
                ArgumentCaptor.forClass(Consumer.class);
        ArgumentCaptor<Consumer<Throwable>> errorCaptor =
                ArgumentCaptor.forClass(Consumer.class);
        org.mockito.Mockito.verify(tokenStream).beforeToolExecution(beforeCaptor.capture());
        org.mockito.Mockito.verify(tokenStream).onToolExecuted(completedCaptor.capture());
        org.mockito.Mockito.verify(tokenStream).onError(errorCaptor.capture());

        InvocationContext invocationContext = mock(InvocationContext.class);
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("fixture-tool-call")
                .name("searchMemories")
                .arguments("{}")
                .build();
        beforeCaptor.getValue().accept(BeforeToolExecution.builder()
                .request(request)
                .invocationContext(invocationContext)
                .build());
        completedCaptor.getValue().accept(ToolExecution.builder()
                .request(request)
                .result(ToolExecutionResult.builder().isError(true).resultText("").build())
                .startTime(LocalDateTime.of(2026, 8, 18, 12, 0))
                .finishTime(LocalDateTime.of(2026, 8, 18, 12, 0, 1))
                .invocationContext(invocationContext)
                .build());
        errorCaptor.getValue().accept(new IllegalStateException());

        List<AgentStreamEvent> events = controller.events();
        List<AgentStreamEvent> lifecycleEvents = events.stream()
                .filter(event -> event.type().startsWith("tool.") || event.type().startsWith("run."))
                .toList();
        try {
            assertTrue(lifecycleEvents.stream().allMatch(event -> event.text() == null));
            assertEquals(0, events.stream()
                    .filter(event -> "response.delta".equals(event.type()))
                    .count());
        } catch (AssertionError failure) {
            checks.violate("TOOL_EVENT_LOW_SENSITIVITY");
        }
        checks.check("TOOL_STARTED_ONCE",
                events.stream().filter(event -> "tool.started".equals(event.type())).count() == 1);
        checks.check("TOOL_COMPLETED_ONCE",
                events.stream().filter(event -> "tool.completed".equals(event.type())).count() == 1);
        checks.check("RUN_FAILURE_TERMINAL",
                events.stream().anyMatch(event -> "run.failed".equals(event.type())));
        checks.check("TOOL_PARAMETER_RESULT_NOT_EXPOSED",
                events.stream().allMatch(event -> event.text() == null));
        UserContext.clear();
        return new ReplayMetrics(0, 0, 0, 0, 0, 0, 0,
                checks.violationCodes.contains("TOOL_EVENT_LOW_SENSITIVITY") ? 0 : 1, 0);
    }

    private TokenStream tokenStream() {
        TokenStream tokenStream = mock(TokenStream.class, RETURNS_SELF);
        doReturn(tokenStream).when(tokenStream).onPartialResponseWithContext(any(BiConsumer.class));
        doReturn(tokenStream).when(tokenStream).onPartialThinkingWithContext(any(BiConsumer.class));
        doReturn(tokenStream).when(tokenStream).onPartialToolCallWithContext(any(BiConsumer.class));
        return tokenStream;
    }

    // validUntil 已被移除，"过期"记忆改用 forgottenAt（懒遗忘）表达
    private MidTermMemory memory(String userId, String summary, LocalDateTime createdAt,
            LocalDateTime forgottenAt, boolean hidden) {
        return MidTermMemory.builder()
                .userId(userId)
                .sourceType(SourceType.MANUAL.code())
                .sourceId("fixture-source-" + summary)
                .summary(summary)
                .importance(0.8)
                .confidence(0.9)
                .matchAllowed(true)
                .hidden(hidden)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .forgottenAt(forgottenAt)
                .build();
    }

    private void ensureUser(String userId) {
        if (userRepository.findByUserId(userId) == null) {
            userRepository.saveAndFlush(User.builder()
                    .userId(userId)
                    .userName("fixture-user-name")
                    .isMatchEnabled(false)
                    .permissionLevel(0)
                    .build());
        }
    }

    private void deleteFixtureRows() {
        for (String userId : List.of(
                "fixture-user-chat-a", "fixture-user-chat-b", "fixture-user-chat-c",
                "fixture-user-chat-d", "fixture-user-chat-other")) {
            chatMemoryMessageRepository.deleteAll(
                    chatMemoryMessageRepository.findByMemoryIdOrderByCreatedAtAsc(userId));
            midTermMemoryRepository.deleteAll(midTermMemoryRepository.findByUserId(userId));
            userPersonaRepository.findByUserId(userId).ifPresent(userPersonaRepository::delete);
            User user = userRepository.findByUserId(userId);
            if (user != null) {
                userRepository.delete(user);
            }
        }
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

    private static final class RecordingAiController extends AiController {

        private final List<AgentStreamEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();

        private RecordingAiController(ThreadPoolTaskExecutor threadPoolExecutor,
                SensitiveWordUtils sensitiveWordUtils) {
            super(threadPoolExecutor, sensitiveWordUtils);
        }

        @Override
        protected void sendAgentEvent(SseEmitter emitter,
                ChatStreamCancellationRegistry.ChatStreamSession session,
                AgentStreamEvent event) {
            if (session.isActive()) {
                events.add(event);
            }
        }

        private List<AgentStreamEvent> events() {
            return events;
        }
    }

    private record ReplayMetrics(int memoryReferencePolicyPassCount,
                                 int tonePolicyPassCount,
                                 int noUnsupportedClaimPolicyPassCount,
                                 int contextPositiveControlPassCount,
                                 int restrictedContextLeakCount,
                                 int privacyBoundaryViolationCount,
                                 int conflictPolicyPassCount,
                                 int toolLifecyclePassCount,
                                 int toolParameterResultExposureCount) {
        private static ReplayMetrics empty() {
            return new ReplayMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0);
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
