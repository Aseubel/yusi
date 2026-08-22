package com.aseubel.yusi.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.read.ListAppender;
import com.aseubel.yusi.common.event.PlazaCardChangedEvent;
import com.aseubel.yusi.common.utils.SensitiveWordUtils;
import com.aseubel.yusi.config.VoiceInputProperties;
import com.aseubel.yusi.config.WebSocketTokenAuthenticator;
import com.aseubel.yusi.controller.AiController;
import com.aseubel.yusi.controller.DiaryVoiceWebSocketHandler;
import com.aseubel.yusi.pojo.dto.cognition.CognitionIngestCommand;
import com.aseubel.yusi.pojo.entity.AgentRunTrace;
import com.aseubel.yusi.pojo.entity.AgentToolTrace;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.pojo.entity.LifeGraphTask;
import com.aseubel.yusi.pojo.entity.TaskExecution;
import com.aseubel.yusi.pojo.entity.ModelCallTrace;
import com.aseubel.yusi.repository.AgentRunTraceRepository;
import com.aseubel.yusi.repository.AgentToolTraceRepository;
import com.aseubel.yusi.repository.LifeGraphTaskRepository;
import com.aseubel.yusi.repository.ModelCallTraceRepository;
import com.aseubel.yusi.repository.SoulCardRepository;
import com.aseubel.yusi.repository.DiaryRepository;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.service.ai.model.ModelUsageSnapshot;
import com.aseubel.yusi.service.ai.model.ModelFailureKind;
import com.aseubel.yusi.service.ai.model.ModelInvocationException;
import com.aseubel.yusi.service.ai.runtime.AgentRunTraceService;
import com.aseubel.yusi.service.ai.runtime.AgentToolExecutionAttemptRegistry;
import com.aseubel.yusi.service.ai.runtime.AgentToolIdempotencyLedgerService;
import com.aseubel.yusi.service.ai.runtime.AgentToolIdempotencyMaintenance;
import com.aseubel.yusi.service.ai.runtime.AgentToolTraceService;
import com.aseubel.yusi.service.ai.runtime.ModelCallAttemptEvent;
import com.aseubel.yusi.service.ai.runtime.ModelCallTraceService;
import com.aseubel.yusi.service.ai.tool.constant.AgentToolAccessMode;
import com.aseubel.yusi.service.ai.tool.constant.AgentToolIdempotencyMode;
import com.aseubel.yusi.config.security.CryptoService;
import com.aseubel.yusi.service.lifegraph.LifeGraphBuildService;
import com.aseubel.yusi.service.lifegraph.LifeGraphTaskBatchService;
import com.aseubel.yusi.service.lifegraph.LifeGraphTaskClaimService;
import com.aseubel.yusi.service.task.TaskExecutionCommand;
import com.aseubel.yusi.service.task.TaskExecutionService;
import com.aseubel.yusi.service.ai.asr.SpeechModelRegistry;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * mock-contract-only Trace and associated-log boundary checks.
 *
 * <p>This suite uses repository, logger and external-service substitutes only. It does not
 * claim that a deployed log collector or a real dependency path is safe.</p>
 */
@ExtendWith(MockitoExtension.class)
class TraceBoundarySensitiveDataTest {

    private static final String USER_SENTINEL = "fixture-user-authz";
    private static final String QUERY_SENTINEL = "fixture-query-authz";
    private static final String CONTENT_SENTINEL = "fixture-content-authz";
    private static final String TOKEN_SENTINEL = "fixture-token-authz";
    private static final String OBJECT_KEY_SENTINEL = "fixture-object-key-authz";
    private static final String PROMPT_SENTINEL = "fixture-prompt-authz";
    private static final String RESPONSE_SENTINEL = "fixture-response-authz";
    private static final String INPUT_SENTINEL = "fixture-input-authz";
    private static final String OUTPUT_SENTINEL = "fixture-output-authz";
    private static final String EXCEPTION_SENTINEL = "fixture-exception-authz";
    private static final String MESSAGE_SENTINEL = "fixture-message-authz";

    private static final Set<String> FORBIDDEN_SENTINELS = Set.of(
            USER_SENTINEL,
            QUERY_SENTINEL,
            CONTENT_SENTINEL,
            TOKEN_SENTINEL,
            OBJECT_KEY_SENTINEL,
            PROMPT_SENTINEL,
            RESPONSE_SENTINEL,
            INPUT_SENTINEL,
            OUTPUT_SENTINEL,
            EXCEPTION_SENTINEL,
            MESSAGE_SENTINEL);

    private static final Set<String> FORBIDDEN_FIELD_NAMES = Set.of(
            "payload", "query", "content", "prompt", "response", "input", "output",
            "arguments", "results", "objectKey", "token", "thinking", "queries");

    private static final Set<String> AGENT_TOOL_FIELDS = Set.of(
            "id", "userId", "runId", "toolCallId", "upstreamToolCallId", "toolName", "toolSource",
            "capabilityVersion", "attemptCount", "idempotencyMode", "idempotencyStatus",
            "idempotencyClaimedAt", "idempotencyResolvedAt", "idempotencyExpiresAt", "status",
            "failureCategory", "startedAt", "completedAt", "durationMs", "createdAt", "updatedAt");

    private static final Set<String> AGENT_RUN_FIELDS = Set.of(
            "id", "runId", "userId", "scene", "status", "currentStage", "toolCount",
            "responseCharCount", "failureCategory", "cancelSource", "startedAt", "completedAt",
            "durationMs", "createdAt", "updatedAt");

    private static final Set<String> MODEL_CALL_FIELDS = Set.of(
            "id", "requestId", "attemptId", "runId", "userId", "scene", "promptKey", "promptVersion",
            "promptLocale", "policyId", "policyVersion", "routeReason", "primaryTier", "selectedTier",
            "modelId", "provider", "modelName", "inputTokens", "outputTokens", "cachedTokens", "cost",
            "priceVersion", "usageSource", "latencyMs", "ttftMs", "retryIndex", "fallbackUsed", "status",
            "errorCode", "finishReason", "createdAt");

    private static final Set<String> MODEL_ATTEMPT_FIELDS = Set.of(
            "requestId", "attemptId", "runId", "userId", "scene", "promptKey", "promptVersion",
            "promptLocale", "policyId", "policyVersion", "routeReason", "primaryTier", "selectedTier",
            "modelId", "provider", "modelName", "usage", "latencyMs", "ttftMs", "retryIndex",
            "fallbackUsed", "status", "errorCode", "finishReason");

    private static final Map<Class<?>, Set<String>> EXPECTED_TRACE_FIELDS = Map.of(
            AgentToolTrace.class, AGENT_TOOL_FIELDS,
            AgentRunTrace.class, AGENT_RUN_FIELDS,
            ModelCallTrace.class, MODEL_CALL_FIELDS,
            ModelCallAttemptEvent.class, MODEL_ATTEMPT_FIELDS);

    private static final List<SourceLine> EXPECTED_WRITE_POINTS = List.of(
            new SourceLine("src/main/java/com/aseubel/yusi/controller/AiController.java", 447,
                    "log.error(\"AI chat stream failed: operation=ai_chat_stream_callback, requestId={}, \""),
            new SourceLine("src/main/java/com/aseubel/yusi/controller/AiController.java", 657,
                    "log.warn(\"AgentRun trace failed: operation=agent_run_trace, operationName={}, exceptionType={}\", operation, com.aseubel.yusi.common.utils.LowSensitivityLogSummary.exceptionType(exception));"),
            new SourceLine("src/main/java/com/aseubel/yusi/controller/AiController.java", 668,
                    "log.warn(\"AgentToolTrace failed: operation=agent_tool_trace, operationName={}, exceptionType={}\", operation, com.aseubel.yusi.common.utils.LowSensitivityLogSummary.exceptionType(exception));"),
            new SourceLine("src/main/java/com/aseubel/yusi/service/ai/runtime/AgentRunTraceService.java", 137,
                    "log.warn(\"AgentToolTrace terminal convergence failed: operation=agent_tool_trace_terminal_convergence, exceptionType={}\", com.aseubel.yusi.common.utils.LowSensitivityLogSummary.exceptionType(exception));"),
            new SourceLine("src/main/java/com/aseubel/yusi/service/ai/runtime/AgentToolExecutionAttemptRegistry.java", 72,
                    "log.debug(\"Unable to persist agent tool retry count: operation=agent_tool_retry_count, runId={}, exceptionType={}\", pending.runId, com.aseubel.yusi.common.utils.LowSensitivityLogSummary.exceptionType(exception));"),
            new SourceLine("src/main/java/com/aseubel/yusi/service/ai/runtime/AgentToolIdempotencyMaintenance.java", 26,
                    "log.warn(\"Unable to recover orphaned agent tool idempotency claims: operation=agent_tool_idempotency_recovery, exceptionType={}\", com.aseubel.yusi.common.utils.LowSensitivityLogSummary.exceptionType(exception));"),
            new SourceLine("src/main/java/com/aseubel/yusi/service/ai/runtime/AgentToolIdempotencyMaintenance.java", 36,
                    "log.warn(\"Unable to clear expired agent tool idempotency states: operation=agent_tool_idempotency_expiry_cleanup, exceptionType={}\", com.aseubel.yusi.common.utils.LowSensitivityLogSummary.exceptionType(exception));"),
            new SourceLine("src/main/java/com/aseubel/yusi/service/ai/runtime/ModelCallTraceService.java", 44,
                    "log.warn(\"Failed to persist model call trace: operation=model_call_trace_persist, exceptionType={}\","),
            new SourceLine("src/main/java/com/aseubel/yusi/service/ai/runtime/ModelCallTraceService.java", 45,
                    "com.aseubel.yusi.common.utils.LowSensitivityLogSummary.exceptionType(exception));"),
            new SourceLine("src/main/java/com/aseubel/yusi/controller/DiaryVoiceWebSocketHandler.java", 270,
                    "log.warn(\"日记语音输入失败: operation=diary_voice_input, exceptionType={}\","),
            new SourceLine("src/main/java/com/aseubel/yusi/controller/DiaryVoiceWebSocketHandler.java", 271,
                    "com.aseubel.yusi.common.utils.LowSensitivityLogSummary.exceptionType(cause));"),
            new SourceLine("src/main/java/com/aseubel/yusi/service/lifegraph/PlazaLifeGraphListener.java", 93,
                    "log.warn(\"Plaza LifeGraph source processing failed: operation=plaza_life_graph, sourceId={}, exceptionType={}\","),
            new SourceLine("src/main/java/com/aseubel/yusi/service/lifegraph/PlazaLifeGraphListener.java", 94,
                    "event.getCommand().getSourceId(), com.aseubel.yusi.common.utils.LowSensitivityLogSummary.exceptionType(exception));"),
            new SourceLine("src/main/java/com/aseubel/yusi/service/lifegraph/LifeGraphTaskBatchService.java", 178,
                    "taskRepository.incrementRetryAndSetNextAttempt(taskId, TaskFailureCategory.DEPENDENCY.name().toLowerCase(), nextRetry, now);"),
            new SourceLine("src/main/java/com/aseubel/yusi/service/lifegraph/LifeGraphTaskBatchService.java", 221,
                    "taskRepository.incrementRetryAndSetNextAttempt(task.getId(), TaskFailureCategory.DEPENDENCY.name().toLowerCase(), nextRetry, now);")
    );

    @Mock
    private AgentToolTraceRepository agentToolTraceRepository;

    @Mock
    private AgentRunTraceRepository agentRunTraceRepository;

    @Mock
    private ModelCallTraceRepository modelCallTraceRepository;

    @Mock
    private AgentToolTraceService agentToolTraceService;

    @Mock
    private AgentToolIdempotencyLedgerService ledgerService;

    @Mock
    private LifeGraphBuildService lifeGraphBuildService;

    @Mock
    private TaskExecutionService taskExecutionService;

    @Mock
    private AgentRunTraceService agentRunTraceService;

    @Mock
    private SoulCardRepository soulCardRepository;

    @Mock
    private LifeGraphTaskRepository lifeGraphTaskRepository;

    @Mock
    private LifeGraphTaskClaimService lifeGraphTaskClaimService;

    @Mock
    private DiaryRepository diaryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CryptoService cryptoService;

    private final List<AttachedLogger> attachedLoggers = new ArrayList<>();

    @AfterEach
    void detachLoggers() {
        attachedLoggers.forEach(attached -> {
            attached.logger().detachAppender(attached.appender());
            attached.appender().stop();
            attached.logger().setLevel(attached.originalLevel());
        });
        attachedLoggers.clear();
    }

    @Test
    void traceTypesExposeExactlyApprovedMetadataFields() {
        Map<Class<?>, Set<String>> actual = EXPECTED_TRACE_FIELDS.keySet().stream()
                .collect(Collectors.toMap(type -> type, this::instanceFields, (left, right) -> left, LinkedHashMap::new));

        assertThat(actual).isEqualTo(EXPECTED_TRACE_FIELDS);

        Set<String> forbiddenDeclaredFields = actual.values().stream()
                .flatMap(Set::stream)
                .filter(FORBIDDEN_FIELD_NAMES::contains)
                .collect(Collectors.toCollection(TreeSet::new));
        assertThat(forbiddenDeclaredFields).isEmpty();
    }

    @Test
    void locksExactlyTheElevenForbiddenSentinels() {
        assertThat(FORBIDDEN_SENTINELS).isEqualTo(Set.of(
                "fixture-user-authz",
                "fixture-query-authz",
                "fixture-content-authz",
                "fixture-token-authz",
                "fixture-object-key-authz",
                "fixture-prompt-authz",
                "fixture-response-authz",
                "fixture-input-authz",
                "fixture-output-authz",
                "fixture-exception-authz",
                "fixture-message-authz"));
    }

    @Test
    void agentToolTraceSaveProjectionPreservesMetadata() throws IllegalAccessException {
        when(agentToolTraceRepository.findByUserIdAndRunIdAndToolCallId(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        AgentToolTraceService service = new AgentToolTraceService(agentToolTraceRepository);
        String userId = "fixture-trace-user";
        String runId = "fixture-trace-run";
        String toolCallId = "fixture-trace-tool-call";
        String upstreamToolCallId = "fixture-trace-upstream-call";
        String toolName = "fixture-trace-tool";
        String toolSource = "fixture-trace-source";
        String capabilityVersion = "fixture-trace-capability";
        service.start(userId, runId, toolCallId, upstreamToolCallId,
                toolName, toolSource, capabilityVersion, AgentToolIdempotencyMode.NONE);

        ArgumentCaptor<AgentToolTrace> captor = ArgumentCaptor.forClass(AgentToolTrace.class);
        verify(agentToolTraceRepository).save(captor.capture());
        AgentToolTrace trace = captor.getValue();
        assertThat(trace.getUserId()).isEqualTo(userId);
        assertThat(trace.getRunId()).isEqualTo(runId);
        assertThat(trace.getToolCallId()).isEqualTo(toolCallId);
        assertThat(trace.getUpstreamToolCallId()).isEqualTo(upstreamToolCallId);
        assertThat(trace.getToolName()).isEqualTo(toolName);
        assertThat(trace.getToolSource()).isEqualTo(toolSource);
        assertThat(trace.getCapabilityVersion()).isEqualTo(capabilityVersion);
        assertNoForbiddenValues(trace);
    }

    @Test
    void agentRunTraceSaveProjectionPreservesMetadata() throws IllegalAccessException {
        when(agentRunTraceRepository.findByUserIdAndRunId(anyString(), anyString()))
                .thenReturn(Optional.empty());

        AgentRunTraceService service = new AgentRunTraceService(agentRunTraceRepository, agentToolTraceService);
        String userId = "fixture-trace-user";
        String runId = "fixture-trace-run";
        String scene = "fixture-trace-scene";
        service.start(userId, runId, scene);

        ArgumentCaptor<AgentRunTrace> captor = ArgumentCaptor.forClass(AgentRunTrace.class);
        verify(agentRunTraceRepository).save(captor.capture());
        AgentRunTrace trace = captor.getValue();
        assertThat(trace.getUserId()).isEqualTo(userId);
        assertThat(trace.getRunId()).isEqualTo(runId);
        assertThat(trace.getScene()).isEqualTo(scene);
        assertNoForbiddenValues(trace);
    }

    @Test
    void modelCallTraceSaveProjectionPreservesMetadataAndClassifiesFailure()
            throws IllegalAccessException {
        ModelCallTraceService service = new ModelCallTraceService(modelCallTraceRepository);
        ModelInvocationException failure = new ModelInvocationException(
                ModelFailureKind.SERVER_ERROR, "fixture-provider", "fixture-model", null,
                new IllegalStateException(EXCEPTION_SENTINEL));
        service.persist(new ModelCallAttemptEvent(
                "fixture-request-trace", "fixture-attempt-trace", "fixture-run-trace", "fixture-trace-user",
                "fixture-scene-trace", "fixture-prompt-key", "fixture-version-trace", "fixture-locale-trace",
                "fixture-policy-trace", 1L, "fixture-route-reason", "fixture-primary-tier",
                "fixture-selected-tier", "fixture-model-id", "fixture-provider", "fixture-model-name",
                ModelUsageSnapshot.unavailable("fixture-price-version"), 10L, null, 0, false,
                "FAILED", failure.kind().name(), "fixture-finish-reason"));

        ArgumentCaptor<ModelCallTrace> captor = ArgumentCaptor.forClass(ModelCallTrace.class);
        verify(modelCallTraceRepository).save(captor.capture());
        ModelCallTrace trace = captor.getValue();
        assertThat(trace.getRequestId()).isEqualTo("fixture-request-trace");
        assertThat(trace.getAttemptId()).isEqualTo("fixture-attempt-trace");
        assertThat(trace.getRunId()).isEqualTo("fixture-run-trace");
        assertThat(trace.getUserId()).isEqualTo("fixture-trace-user");
        assertThat(trace.getScene()).isEqualTo("fixture-scene-trace");
        assertThat(trace.getPromptKey()).isEqualTo("fixture-prompt-key");
        assertThat(trace.getPromptVersion()).isEqualTo("fixture-version-trace");
        assertThat(trace.getPromptLocale()).isEqualTo("fixture-locale-trace");
        assertThat(trace.getPolicyId()).isEqualTo("fixture-policy-trace");
        assertThat(trace.getRouteReason()).isEqualTo("fixture-route-reason");
        assertThat(trace.getPrimaryTier()).isEqualTo("fixture-primary-tier");
        assertThat(trace.getSelectedTier()).isEqualTo("fixture-selected-tier");
        assertThat(trace.getModelId()).isEqualTo("fixture-model-id");
        assertThat(trace.getProvider()).isEqualTo("fixture-provider");
        assertThat(trace.getModelName()).isEqualTo("fixture-model-name");
        assertThat(trace.getErrorCode()).isEqualTo(ModelFailureKind.SERVER_ERROR.name());
        assertThat(trace.getFinishReason()).isEqualTo("fixture-finish-reason");
        assertNoForbiddenValues(trace);
    }

    @Test
    void associatedTraceWritePointInventoryIsExact() throws Exception {
        Map<String, String> actual = new LinkedHashMap<>();
        for (SourceLine expected : EXPECTED_WRITE_POINTS) {
            List<String> lines = Files.readAllLines(Path.of(expected.file()));
            assertThat(lines).as("source file for %s", expected.location()).isNotEmpty();
            assertThat(lines.size()).as("source line for %s", expected.location())
                    .isGreaterThanOrEqualTo(expected.line());
            actual.put(expected.location(), lines.get(expected.line() - 1).trim());
        }

        Map<String, String> expected = EXPECTED_WRITE_POINTS.stream()
                .collect(Collectors.toMap(SourceLine::location, SourceLine::text,
                        (left, right) -> left, LinkedHashMap::new));
        assertThat(actual).isEqualTo(expected);
        assertThat(actual.keySet()).hasSize(15);
    }

    @Test
    void aiRunTraceWrapperRejectsThrowableAndSentinelAcrossAllLogDimensions() {
        AiController controller = new AiController(mock(org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor.class),
                mock(SensitiveWordUtils.class));
        ReflectionTestUtils.setField(controller, "agentRunTraceService", mock(AgentRunTraceService.class));
        ReflectionTestUtils.setField(controller, "agentToolTraceService", mock(AgentToolTraceService.class));
        ListAppender<ILoggingEvent> appender = attach(AiController.class);

        ReflectionTestUtils.invokeMethod(controller, "runTrace", "fixture-operation-trace",
                (Runnable) () -> {
                    throw new IllegalStateException(EXCEPTION_SENTINEL);
                });

        assertNoSensitiveLogProjection(appender);
    }

    @Test
    void aiToolTraceWrapperRejectsThrowableAndSentinelAcrossAllLogDimensions() {
        AiController controller = new AiController(mock(org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor.class),
                mock(SensitiveWordUtils.class));
        ReflectionTestUtils.setField(controller, "agentRunTraceService", mock(AgentRunTraceService.class));
        ReflectionTestUtils.setField(controller, "agentToolTraceService", mock(AgentToolTraceService.class));
        ListAppender<ILoggingEvent> appender = attach(AiController.class);

        ReflectionTestUtils.invokeMethod(controller, "runToolTrace", "fixture-operation-tool-trace",
                (Runnable) () -> {
                    throw new IllegalStateException(MESSAGE_SENTINEL);
                });

        assertNoSensitiveLogProjection(appender);
    }

    @Test
    void modelCallTraceFailureRejectsExceptionMessageAndThrowableProjection() {
        when(modelCallTraceRepository.save(any(ModelCallTrace.class)))
                .thenThrow(new IllegalStateException(EXCEPTION_SENTINEL));
        ListAppender<ILoggingEvent> appender = attach(ModelCallTraceService.class);

        new ModelCallTraceService(modelCallTraceRepository).persist(safeModelAttempt());

        assertNoSensitiveLogProjection(appender);
    }

    @Test
    void agentRunTraceConvergenceRejectsThrowableProjection() {
        AgentRunTrace running = AgentRunTrace.builder()
                .userId("fixture-trace-user")
                .runId("fixture-trace-run")
                .scene("fixture-scene")
                .status(AgentRunTrace.Status.RUNNING)
                .startedAt(LocalDateTime.now().minusSeconds(1))
                .build();
        when(agentRunTraceRepository.findByUserIdAndRunId("fixture-trace-user", "fixture-trace-run"))
                .thenReturn(Optional.of(running));
        doThrow(new IllegalStateException(EXCEPTION_SENTINEL)).when(agentToolTraceService)
                .closeRunning(anyString(), anyString(), any(AgentToolTrace.Status.class), any());
        ListAppender<ILoggingEvent> appender = attach(AgentRunTraceService.class);

        new AgentRunTraceService(agentRunTraceRepository, agentToolTraceService)
                .complete("fixture-trace-user", "fixture-trace-run");

        assertNoSensitiveLogProjection(appender);
    }

    @Test
    void agentToolRetryRegistryRejectsThrowableProjection() {
        doThrow(new IllegalStateException(EXCEPTION_SENTINEL)).when(agentToolTraceService)
                .incrementAttemptCount(anyString(), anyString(), anyString());
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("fixture-request-trace")
                .name("fixture-tool-trace")
                .arguments(INPUT_SENTINEL)
                .build();
        AgentToolExecutionAttemptRegistry registry = new AgentToolExecutionAttemptRegistry(agentToolTraceService);
        registry.register("fixture-trace-user", "fixture-trace-run", request, "fixture-upstream-trace",
                "fixture-tool-trace", "fixture-source-trace", "fixture-call-trace",
                AgentToolAccessMode.READ, AgentToolIdempotencyMode.NONE, "fixture-capability-trace");
        ListAppender<ILoggingEvent> appender = attach(AgentToolExecutionAttemptRegistry.class);

        registry.onRetry(request);

        assertNoSensitiveLogProjection(appender);
    }

    @Test
    void idempotencyRecoveryRejectsThrowableProjection() {
        when(ledgerService.recoverOrphanedClaims(any(LocalDateTime.class)))
                .thenThrow(new IllegalStateException(EXCEPTION_SENTINEL));
        AgentToolIdempotencyMaintenance maintenance = new AgentToolIdempotencyMaintenance(ledgerService);
        ListAppender<ILoggingEvent> appender = attach(AgentToolIdempotencyMaintenance.class);

        maintenance.recoverOrphanedClaimsOnStartup();

        assertNoSensitiveLogProjection(appender);
    }

    @Test
    void idempotencyExpiryCleanupRejectsThrowableProjection() {
        when(ledgerService.clearExpiredStates(any(LocalDateTime.class)))
                .thenThrow(new IllegalStateException(MESSAGE_SENTINEL));
        AgentToolIdempotencyMaintenance maintenance = new AgentToolIdempotencyMaintenance(ledgerService);
        ListAppender<ILoggingEvent> appender = attach(AgentToolIdempotencyMaintenance.class);

        maintenance.clearExpiredLedgerStates();

        assertNoSensitiveLogProjection(appender);
    }

    @Test
    void diaryVoiceFailureRejectsThrowableProjection() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("fixture-connection-trace");
        when(session.isOpen()).thenReturn(true);
        DiaryVoiceWebSocketHandler handler = new DiaryVoiceWebSocketHandler(
                new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(SpeechModelRegistry.class),
                mock(WebSocketTokenAuthenticator.class),
                new VoiceInputProperties());
        ListAppender<ILoggingEvent> appender = attach(DiaryVoiceWebSocketHandler.class);
        try {
            handler.afterConnectionEstablished(session);
            @SuppressWarnings("unchecked")
            Map<String, Object> connections = (Map<String, Object>) ReflectionTestUtils
                    .getField(handler, "connections");
            ReflectionTestUtils.setField(connections.get("fixture-connection-trace"), "userId", USER_SENTINEL);
            handler.handleTransportError(session, new IllegalStateException(EXCEPTION_SENTINEL));
            assertNoSensitiveLogProjection(appender);
        } finally {
            handler.shutdown();
        }
    }

    @Test
    void plazaLifeGraphFailureRejectsThrowableProjection() {
        TaskExecution execution = TaskExecution.builder()
                .taskId("fixture-task-trace")
                .runId("fixture-run-trace")
                .build();
        AgentRunTraceService.RunScope scope = mock(AgentRunTraceService.RunScope.class);
        when(taskExecutionService.createOrGet(any(TaskExecutionCommand.class))).thenReturn(execution);
        when(agentRunTraceService.open(anyString(), anyString(), anyString())).thenReturn(scope);
        doThrow(new IllegalStateException(EXCEPTION_SENTINEL)).when(lifeGraphBuildService)
                .upsertFromPlaza(any(CognitionIngestCommand.class));
        CognitionIngestCommand command = CognitionIngestCommand.builder()
                .userId("fixture-trace-user")
                .runId("fixture-run-trace")
                .sourceType("fixture-source-type")
                .sourceId("fixture-source-trace")
                .sourceRevision(1L)
                .build();
        ListAppender<ILoggingEvent> appender = attach(com.aseubel.yusi.service.lifegraph.PlazaLifeGraphListener.class);

        new com.aseubel.yusi.service.lifegraph.PlazaLifeGraphListener(
                lifeGraphBuildService, taskExecutionService, soulCardRepository, agentRunTraceService)
                .onCardChanged(new PlazaCardChangedEvent(this, command, PlazaCardChangedEvent.Type.WRITE));

        assertNoSensitiveLogProjection(appender);
    }

    @Test
    void lifeGraphRetryErrorFieldRejectsExceptionMessage() {
        LifeGraphTask task = LifeGraphTask.createUpsertTask("fixture-diary-trace", "fixture-trace-user");
        task.setId(1L);
        Diary diary = Diary.builder()
                .diaryId("fixture-diary-trace")
                .userId("fixture-trace-user")
                .build();
        when(lifeGraphTaskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(diaryRepository.findByDiaryIdAndUserId("fixture-diary-trace", "fixture-trace-user"))
                .thenReturn(diary);
        doThrow(new IllegalStateException(MESSAGE_SENTINEL)).when(lifeGraphBuildService)
                .upsertFromDiary(any(Diary.class), anyString());

        LifeGraphTaskBatchService service = new LifeGraphTaskBatchService(
                lifeGraphTaskRepository, lifeGraphTaskClaimService, diaryRepository, userRepository,
                cryptoService, lifeGraphBuildService, taskExecutionService, agentRunTraceService);
        service.processSingleTask(1L, diary, "fixture-plain-trace");

        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(lifeGraphTaskRepository).incrementRetryAndSetNextAttempt(
                eq(1L), errorCaptor.capture(), any(LocalDateTime.class), any(LocalDateTime.class));
        assertThat(errorCaptor.getValue()).doesNotContain(MESSAGE_SENTINEL);
    }

    private ModelCallAttemptEvent safeModelAttempt() {
        return new ModelCallAttemptEvent(
                "fixture-request-trace", "fixture-attempt-trace", "fixture-run-trace", "fixture-trace-user",
                "fixture-scene-trace", "fixture-prompt-key-trace", "fixture-version-trace", "fixture-locale",
                "fixture-policy-trace", 1L, "fixture-route-trace", "fixture-primary-tier",
                "fixture-selected-tier", "fixture-model-id", "fixture-provider", "fixture-model-name",
                ModelUsageSnapshot.unavailable("fixture-price-version"), 10L, null, 0, false,
                "SUCCESS", null, "fixture-finish");
    }

    private Set<String> instanceFields(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .filter(field -> !field.isSynthetic())
                .map(Field::getName)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private void assertNoForbiddenValues(Object target) throws IllegalAccessException {
        List<String> violations = new ArrayList<>();
        for (Field field : target.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            field.setAccessible(true);
            String rendered = String.valueOf(field.get(target));
            for (String sentinel : FORBIDDEN_SENTINELS) {
                if (rendered.contains(sentinel)) {
                    violations.add(target.getClass().getSimpleName() + "." + field.getName()
                            + " contains " + sentinel);
                }
            }
        }
        assertThat(violations)
                .as("repository save projection must exclude every forbidden sentinel")
                .isEmpty();
    }

    private ListAppender<ILoggingEvent> attach(Class<?> type) {
        Logger logger = (Logger) LoggerFactory.getLogger(type);
        Level originalLevel = logger.getLevel();
        logger.setLevel(Level.ALL);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        attachedLoggers.add(new AttachedLogger(logger, appender, originalLevel));
        return appender;
    }

    private void assertNoSensitiveLogProjection(ListAppender<ILoggingEvent> appender) {
        assertThat(appender.list).as("mock-contract-only log capture must contain an event").isNotEmpty();
        List<String> violations = new ArrayList<>();
        for (ILoggingEvent event : appender.list) {
            collectSentinelViolations(violations, "formatted message", event.getFormattedMessage());
            IThrowableProxy proxy = event.getThrowableProxy();
            String proxyText = throwableProxyText(proxy);
            String exceptionMessage = proxy == null ? "" : proxy.getMessage();
            String stackText = stackText(proxy);
            collectSentinelViolations(violations, "throwable proxy", proxyText);
            collectSentinelViolations(violations, "exception message", exceptionMessage);
            collectSentinelViolations(violations, "stack text", stackText);
            if (proxy != null) {
                violations.add("throwable proxy present");
            }
        }
        assertThat(violations)
                .as("associated log must exclude sentinel text and throwable proxies")
                .isEmpty();
    }

    private void collectSentinelViolations(List<String> violations, String dimension, String text) {
        String safeText = text == null ? "" : text;
        for (String sentinel : FORBIDDEN_SENTINELS) {
            if (safeText.contains(sentinel)) {
                violations.add(dimension + " contains " + sentinel);
            }
        }
    }

    private String stackText(IThrowableProxy proxy) {
        if (proxy == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        appendStackText(proxy, text);
        return text.toString();
    }

    private String throwableProxyText(IThrowableProxy proxy) {
        if (proxy == null) {
            return "";
        }
        StringBuilder text = new StringBuilder(proxy.getClassName())
                .append(':').append(proxy.getMessage());
        if (proxy.getCause() != null) {
            text.append(' ').append(throwableProxyText(proxy.getCause()));
        }
        for (IThrowableProxy suppressed : proxy.getSuppressed()) {
            text.append(' ').append(throwableProxyText(suppressed));
        }
        return text.toString();
    }

    private void appendStackText(IThrowableProxy proxy, StringBuilder text) {
        for (StackTraceElementProxy frame : proxy.getStackTraceElementProxyArray()) {
            text.append(frame.getStackTraceElement()).append(' ');
        }
        if (proxy.getCause() != null) {
            appendStackText(proxy.getCause(), text);
        }
        for (IThrowableProxy suppressed : proxy.getSuppressed()) {
            appendStackText(suppressed, text);
        }
    }

    private record SourceLine(String file, int line, String text) {
        String location() {
            return file + ":" + line;
        }
    }

    private record AttachedLogger(Logger logger, ListAppender<ILoggingEvent> appender, Level originalLevel) {
    }
}
