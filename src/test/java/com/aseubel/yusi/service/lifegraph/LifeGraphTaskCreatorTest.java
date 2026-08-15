package com.aseubel.yusi.service.lifegraph;

import com.aseubel.yusi.common.event.DiaryChangedEvent;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.pojo.entity.LifeGraphTask;
import com.aseubel.yusi.pojo.entity.TaskExecution;
import com.aseubel.yusi.repository.LifeGraphTaskRepository;
import com.aseubel.yusi.service.ai.runtime.AgentRunTraceService;
import com.aseubel.yusi.service.task.TaskExecutionCommand;
import com.aseubel.yusi.service.task.TaskExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LifeGraphTaskCreatorTest {

    @Mock
    private LifeGraphTaskRepository taskRepository;

    @Mock
    private LifeGraphTaskBatchService batchService;

    @Mock
    private ThreadPoolTaskExecutor threadPoolExecutor;

    @Mock
    private TaskExecutionService taskExecutionService;

    @Mock
    private AgentRunTraceService agentRunTraceService;

    @Test
    void createsLifeGraphTaskWithAStableRunId() {
        when(taskRepository.findByUserIdAndDiaryIdAndStatusIn(any(), any(), any()))
                .thenReturn(List.of());
        when(taskExecutionService.createOrGet(any(TaskExecutionCommand.class)))
                .thenAnswer(invocation -> {
                    TaskExecutionCommand command = invocation.getArgument(0);
                    return TaskExecution.builder()
                            .taskId("execution-1")
                            .runId(command.getRunId())
                            .build();
                });
        when(taskRepository.findByTaskExecutionId("execution-1"))
                .thenReturn(Optional.empty());
        when(taskRepository.save(any(LifeGraphTask.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Diary diary = Diary.builder()
                .diaryId("diary-1")
                .userId("user-1")
                .sourceRevision(4L)
                .plainContent(" ")
                .build();
        new LifeGraphTaskCreator(taskRepository, batchService, threadPoolExecutor,
                taskExecutionService, agentRunTraceService)
                .onDiaryChanged(new DiaryChangedEvent(this, diary, DiaryChangedEvent.Type.WRITE));

        ArgumentCaptor<TaskExecutionCommand> captor = ArgumentCaptor.forClass(TaskExecutionCommand.class);
        verify(taskExecutionService).createOrGet(captor.capture());
        assertNotNull(captor.getValue().getRunId());
        assertFalse(captor.getValue().getRunId().isBlank());
        verify(agentRunTraceService).start(eq("user-1"), eq(captor.getValue().getRunId()), eq("life_graph"));
    }
}
