package com.aseubel.yusi.service.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.aseubel.yusi.pojo.constant.AgentToolConstants;
import com.aseubel.yusi.pojo.entity.AgentToolTrace;
import com.aseubel.yusi.pojo.entity.UserPersona;
import com.aseubel.yusi.repository.AgentToolTraceRepository;
import com.aseubel.yusi.service.ai.runtime.AgentToolExecutionAttemptObserver;
import com.aseubel.yusi.service.ai.runtime.AgentToolIdempotencyLedgerService;
import com.aseubel.yusi.service.ai.runtime.AgentToolInvocationContext;
import com.aseubel.yusi.service.ai.runtime.AgentToolInvocationContextProvider;
import com.aseubel.yusi.service.ai.tool.constant.AgentToolAccessMode;
import com.aseubel.yusi.service.ai.tool.constant.AgentToolIdempotencyMode;
import com.aseubel.yusi.service.user.UserPersonaService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserPersonaToolIdempotencyTest {

    private final ExecutorService workers = Executors.newFixedThreadPool(1);

    @AfterEach
    void tearDown() {
        workers.shutdownNow();
    }

    @Test
    void realPersonaToolUsesContextUserAndBlocksSameLogicalCallReplay() {
        UserPersonaService personaService = mock(UserPersonaService.class);
        when(personaService.updateUserPersona(eq("user-1"), any(UserPersona.class)))
                .thenReturn(UserPersona.builder().preferredName("小美").build());
        UserPersonaTool personaTool = new UserPersonaTool(personaService);

        AgentToolCapabilityCatalog catalog = new AgentToolCapabilityCatalog(new ObjectMapper());
        catalog.registerLocal(personaTool);
        AgentToolInvocationContext context = new AgentToolInvocationContext(
                "user-1", "run-1", "local-1", AgentToolConstants.UPDATE_USER_PERSONA,
                AgentToolConstants.SOURCE_LOCAL, AgentToolAccessMode.WRITE,
                AgentToolIdempotencyMode.IDEMPOTENT_WRITE, "v1");
        AgentToolInvocationContextProvider provider = ignored -> Optional.of(context);
        AgentToolTraceRepository repository = mock(AgentToolTraceRepository.class);
        AtomicReference<AgentToolTrace.IdempotencyStatus> status = new AtomicReference<>();
        when(repository.claimIdempotency(anyString(), anyString(), anyString(), any(), any(), any(), any()))
                .thenAnswer(invocation -> status.compareAndSet(null, AgentToolTrace.IdempotencyStatus.CLAIMED)
                        ? 1 : 0);
        when(repository.findByUserIdAndRunIdAndToolCallId("user-1", "run-1", "local-1"))
                .thenAnswer(invocation -> Optional.ofNullable(status.get()).map(this::traceWith));
        when(repository.resolveIdempotency(anyString(), anyString(), anyString(), any(), any(), any(), any()))
                .thenAnswer(invocation -> status.compareAndSet(
                        AgentToolTrace.IdempotencyStatus.CLAIMED,
                        invocation.getArgument(4, AgentToolTrace.IdempotencyStatus.class)) ? 1 : 0);
        AgentToolIdempotencyLedgerService ledger = new AgentToolIdempotencyLedgerService(repository);

        AgentToolExecutionPolicyService policyService = new AgentToolExecutionPolicyService(
                catalog, workers, AgentToolExecutionAttemptObserver.NOOP, provider, ledger);
        Map<?, ToolExecutor> executors = policyService.localExecutors(personaTool);
        ToolExecutor executor = executors.values().iterator().next();
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name(AgentToolConstants.UPDATE_USER_PERSONA)
                .arguments("{\"preferredName\":\"小美\",\"location\":null,"
                        + "\"interests\":null,\"tone\":null,\"customInstructions\":null}")
                .build();
        InvocationContext invocationContext = InvocationContext.builder()
                .chatMemoryId("memory-user")
                .timestampNow()
                .build();

        ToolExecutionResult first = executor.executeWithContext(request, invocationContext);
        ToolExecutionResult replay = executor.executeWithContext(request, invocationContext);

        assertFalse(first.isError());
        assertTrue(replay.isError());
        assertTrue(replay.resultText().contains("IDEMPOTENCY_ALREADY_COMPLETED"));
        assertEquals(AgentToolTrace.IdempotencyStatus.COMPLETED, status.get());
        verify(personaService).updateUserPersona(eq("user-1"), any(UserPersona.class));
    }

    private AgentToolTrace traceWith(AgentToolTrace.IdempotencyStatus status) {
        return AgentToolTrace.builder()
                .userId("user-1")
                .runId("run-1")
                .toolCallId("local-1")
                .toolName(AgentToolConstants.UPDATE_USER_PERSONA)
                .toolSource(AgentToolConstants.SOURCE_LOCAL)
                .idempotencyMode(AgentToolIdempotencyMode.IDEMPOTENT_WRITE)
                .idempotencyStatus(status)
                .status(AgentToolTrace.Status.RUNNING)
                .startedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
