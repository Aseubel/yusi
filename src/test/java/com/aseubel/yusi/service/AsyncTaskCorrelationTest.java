package com.aseubel.yusi.service;

import com.aseubel.yusi.common.event.DiaryChangedEvent;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.pojo.entity.EmbeddingTask;
import com.aseubel.yusi.pojo.entity.LifeGraphTask;
import com.aseubel.yusi.pojo.entity.TaskExecution;
import com.aseubel.yusi.repository.EmbeddingTaskRepository;
import com.aseubel.yusi.repository.LifeGraphTaskRepository;
import com.aseubel.yusi.service.ai.embedding.EmbeddingService;
import com.aseubel.yusi.service.lifegraph.LifeGraphTaskBatchService;
import com.aseubel.yusi.service.lifegraph.LifeGraphTaskCreator;
import com.aseubel.yusi.service.task.TaskExecutionCommand;
import com.aseubel.yusi.service.task.TaskExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsyncTaskCorrelationTest {

    @Mock
    private EmbeddingTaskRepository embeddingTaskRepository;

    @Mock
    private LifeGraphTaskRepository lifeGraphTaskRepository;

    @Mock
    private LifeGraphTaskBatchService lifeGraphTaskBatchService;

    @Mock
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    @Mock
    private TaskExecutionService taskExecutionService;

    @Test
    void embeddingTaskKeepsDiaryChangeEventId() {
        when(embeddingTaskRepository.findPendingByDiaryId("diary-1")).thenReturn(List.of());
        when(taskExecutionService.createOrGet(org.mockito.ArgumentMatchers.any(TaskExecutionCommand.class)))
                .thenReturn(TaskExecution.builder().taskId("execution-1").build());

        Diary diary = Diary.builder()
                .diaryId("diary-1")
                .userId("user-1")
                .build();
        DiaryChangedEvent event = new DiaryChangedEvent(this, diary, DiaryChangedEvent.Type.MODIFY,
                "diary-change-1");

        new EmbeddingService(embeddingTaskRepository, taskExecutionService).onDiaryChanged(event);

        ArgumentCaptor<EmbeddingTask> captor = ArgumentCaptor.forClass(EmbeddingTask.class);
        verify(embeddingTaskRepository).save(captor.capture());
        assertEquals("diary-change-1", captor.getValue().getTriggerEventId());
        assertEquals("execution-1", captor.getValue().getTaskExecutionId());
    }

    @Test
    void lifeGraphTaskKeepsDiaryChangeEventId() {
        Diary diary = Diary.builder()
                .diaryId("diary-2")
                .userId("user-2")
                .build();
        DiaryChangedEvent event = new DiaryChangedEvent(this, diary, DiaryChangedEvent.Type.DELETE,
                "diary-change-2");

        when(taskExecutionService.createOrGet(org.mockito.ArgumentMatchers.any(TaskExecutionCommand.class)))
                .thenReturn(TaskExecution.builder().taskId("execution-2").build());

        new LifeGraphTaskCreator(lifeGraphTaskRepository, lifeGraphTaskBatchService, threadPoolTaskExecutor,
                taskExecutionService)
                .onDiaryChanged(event);

        ArgumentCaptor<LifeGraphTask> captor = ArgumentCaptor.forClass(LifeGraphTask.class);
        verify(lifeGraphTaskRepository).save(captor.capture());
        assertEquals("diary-change-2", captor.getValue().getTriggerEventId());
        assertEquals("execution-2", captor.getValue().getTaskExecutionId());
    }

    @Test
    void diaryChangeEventGeneratesAnIdWhenCallerDoesNotProvideOne() {
        Diary diary = Diary.builder().diaryId("diary-3").userId("user-3").build();

        DiaryChangedEvent event = new DiaryChangedEvent(this, diary, DiaryChangedEvent.Type.WRITE);

        assertFalse(event.getEventId().isBlank());
    }

    @Test
    void queuesOnePendingFollowUpWhenTheSameDiaryIsAlreadyProcessing() {
        Diary diary = Diary.builder()
                .diaryId("diary-4")
                .userId("user-4")
                .plainContent("latest content")
                .build();
        LifeGraphTask processing = LifeGraphTask.createUpsertTask("diary-4", "user-4");
        processing.setStatus(LifeGraphTask.TaskStatus.PROCESSING);
        when(lifeGraphTaskRepository.findByUserIdAndDiaryIdAndStatusIn(
                eq("user-4"), eq("diary-4"), anyList()))
                .thenReturn(List.of(processing));

        when(taskExecutionService.createOrGet(org.mockito.ArgumentMatchers.any(TaskExecutionCommand.class)))
                .thenReturn(TaskExecution.builder().taskId("execution-3").build());

        new LifeGraphTaskCreator(lifeGraphTaskRepository, lifeGraphTaskBatchService, threadPoolTaskExecutor,
                taskExecutionService)
                .onDiaryChanged(new DiaryChangedEvent(this, diary, DiaryChangedEvent.Type.MODIFY));

        ArgumentCaptor<LifeGraphTask> captor = ArgumentCaptor.forClass(LifeGraphTask.class);
        verify(lifeGraphTaskRepository).save(captor.capture());
        assertEquals(LifeGraphTask.TaskStatus.PENDING, captor.getValue().getStatus());
        verify(threadPoolTaskExecutor, never()).execute(org.mockito.ArgumentMatchers.any(Runnable.class));
    }
}
