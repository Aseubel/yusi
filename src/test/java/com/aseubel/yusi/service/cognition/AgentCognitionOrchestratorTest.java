package com.aseubel.yusi.service.cognition;

import com.aseubel.yusi.pojo.dto.cognition.CognitionIngestCommand;
import com.aseubel.yusi.pojo.dto.cognition.CognitionRoutingResult;
import com.aseubel.yusi.pojo.constant.TaskExecutionStatus;
import com.aseubel.yusi.pojo.constant.TaskExecutionType;
import com.aseubel.yusi.pojo.entity.TaskExecution;
import com.aseubel.yusi.service.ai.runtime.AgentRunTraceService;
import com.aseubel.yusi.service.cognition.impl.AgentCognitionOrchestratorImpl;
import com.aseubel.yusi.service.lifegraph.LifeGraphCognitionBridgeService;
import com.aseubel.yusi.service.match.MatchProfileAssembler;
import com.aseubel.yusi.service.memory.MidMemoryUpdateService;
import com.aseubel.yusi.service.persona.UserPersonaUpdateService;
import com.aseubel.yusi.service.task.TaskExecutionCommand;
import com.aseubel.yusi.service.task.TaskExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentCognitionOrchestratorTest {

    @Mock
    private CognitionRoutingService cognitionRoutingService;

    @Mock
    private UserPersonaUpdateService userPersonaUpdateService;

    @Mock
    private MidMemoryUpdateService midMemoryUpdateService;

    @Mock
    private LifeGraphCognitionBridgeService lifeGraphCognitionBridgeService;

    @Mock
    private MatchProfileAssembler matchProfileAssembler;

    @Mock
    private ImageUnderstandingService imageUnderstandingService;

    @Mock
    private TaskExecutionService taskExecutionService;

    @Mock
    private AgentRunTraceService agentRunTraceService;

    @Mock
    private AgentRunTraceService.RunScope runScope;

    @Test
    void emptyDiaryIngestRemovesPreviousMemoryWithoutCallingCognition() {
        when(taskExecutionService.createOrGet(any(TaskExecutionCommand.class)))
                .thenReturn(TaskExecution.builder()
                        .taskId("task-empty")
                        .runId("run-empty")
                        .status(TaskExecutionStatus.PENDING)
                        .build());
        when(agentRunTraceService.open(anyString(), anyString(), eq("cognition_ingest")))
                .thenReturn(runScope);
        CognitionIngestCommand command = CognitionIngestCommand.builder()
                .userId("user-1")
                .sourceType("DIARY")
                .sourceId("diary-1")
                .maskedText("")
                .build();

        service().ingest(command);

        verify(midMemoryUpdateService).removeBySource("user-1", "DIARY", "diary-1");
        verifyNoInteractions(cognitionRoutingService, userPersonaUpdateService,
                lifeGraphCognitionBridgeService, matchProfileAssembler, imageUnderstandingService);
        verify(taskExecutionService).succeed(eq("task-empty"), isNull(), any(LocalDateTime.class));
        verify(runScope).complete();
    }

    @Test
    void cognitionIngestCreatesRunAndTaskWithGeneratedRunId() {
        when(taskExecutionService.createOrGet(any(TaskExecutionCommand.class)))
                .thenAnswer(invocation -> {
                    TaskExecutionCommand command = invocation.getArgument(0);
                    return TaskExecution.builder()
                            .taskId("task-1")
                            .runId(command.getRunId())
                            .status(TaskExecutionStatus.PENDING)
                            .build();
                });
        when(agentRunTraceService.open(anyString(), anyString(), eq("cognition_ingest")))
                .thenReturn(runScope);
        when(cognitionRoutingService.route(any(CognitionIngestCommand.class)))
                .thenReturn(CognitionRoutingResult.builder().build());

        service().ingest(CognitionIngestCommand.builder()
                .userId("user-1")
                .sourceType("DIARY")
                .sourceId("diary-1")
                .sourceRevision(3L)
                .maskedText("masked")
                .build());

        ArgumentCaptor<TaskExecutionCommand> captor = ArgumentCaptor.forClass(TaskExecutionCommand.class);
        verify(taskExecutionService).createOrGet(captor.capture());
        TaskExecutionCommand taskCommand = captor.getValue();
        assertEquals(TaskExecutionType.COGNITION_INGEST, taskCommand.getTaskType());
        assertNotNull(taskCommand.getRunId());
        assertFalse(taskCommand.getRunId().isBlank());
        verify(agentRunTraceService).open("user-1", taskCommand.getRunId(), "cognition_ingest");
        verify(taskExecutionService).succeed(eq("task-1"), isNull(), any(LocalDateTime.class));
        verify(runScope).complete();
    }

    @Test
    void cognitionIngestUsesCallerSuppliedRunId() {
        when(taskExecutionService.createOrGet(any(TaskExecutionCommand.class)))
                .thenAnswer(invocation -> {
                    TaskExecutionCommand command = invocation.getArgument(0);
                    return TaskExecution.builder()
                            .taskId("task-supplied")
                            .runId(command.getRunId())
                            .status(TaskExecutionStatus.PENDING)
                            .build();
                });
        when(agentRunTraceService.open(anyString(), anyString(), eq("cognition_ingest")))
                .thenReturn(runScope);
        when(cognitionRoutingService.route(any(CognitionIngestCommand.class)))
                .thenReturn(CognitionRoutingResult.builder().build());

        service().ingest(CognitionIngestCommand.builder()
                .userId("user-1")
                .sourceType("CHAT_SUMMARY")
                .sourceId("memory-1")
                .runId("run-supplied")
                .maskedText("masked")
                .build());

        verify(agentRunTraceService).open("user-1", "run-supplied", "cognition_ingest");
    }

    private AgentCognitionOrchestratorImpl service() {
        return new AgentCognitionOrchestratorImpl(cognitionRoutingService, userPersonaUpdateService,
                midMemoryUpdateService, lifeGraphCognitionBridgeService, matchProfileAssembler,
                imageUnderstandingService, taskExecutionService, agentRunTraceService);
    }
}
