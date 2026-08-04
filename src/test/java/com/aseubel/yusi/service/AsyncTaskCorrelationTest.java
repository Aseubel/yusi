package com.aseubel.yusi.service;

import com.aseubel.yusi.common.event.DiaryChangedEvent;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.pojo.entity.EmbeddingTask;
import com.aseubel.yusi.pojo.entity.LifeGraphTask;
import com.aseubel.yusi.repository.EmbeddingTaskRepository;
import com.aseubel.yusi.repository.LifeGraphTaskRepository;
import com.aseubel.yusi.service.ai.embedding.EmbeddingService;
import com.aseubel.yusi.service.lifegraph.LifeGraphTaskBatchService;
import com.aseubel.yusi.service.lifegraph.LifeGraphTaskCreator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void embeddingTaskKeepsDiaryChangeEventId() {
        when(embeddingTaskRepository.findPendingByDiaryId("diary-1")).thenReturn(List.of());

        Diary diary = Diary.builder()
                .diaryId("diary-1")
                .userId("user-1")
                .build();
        DiaryChangedEvent event = new DiaryChangedEvent(this, diary, DiaryChangedEvent.Type.MODIFY,
                "diary-change-1");

        new EmbeddingService(embeddingTaskRepository).onDiaryChanged(event);

        ArgumentCaptor<EmbeddingTask> captor = ArgumentCaptor.forClass(EmbeddingTask.class);
        verify(embeddingTaskRepository).save(captor.capture());
        assertEquals("diary-change-1", captor.getValue().getTriggerEventId());
    }

    @Test
    void lifeGraphTaskKeepsDiaryChangeEventId() {
        Diary diary = Diary.builder()
                .diaryId("diary-2")
                .userId("user-2")
                .build();
        DiaryChangedEvent event = new DiaryChangedEvent(this, diary, DiaryChangedEvent.Type.DELETE,
                "diary-change-2");

        new LifeGraphTaskCreator(lifeGraphTaskRepository, lifeGraphTaskBatchService, threadPoolTaskExecutor)
                .onDiaryChanged(event);

        ArgumentCaptor<LifeGraphTask> captor = ArgumentCaptor.forClass(LifeGraphTask.class);
        verify(lifeGraphTaskRepository).save(captor.capture());
        assertEquals("diary-change-2", captor.getValue().getTriggerEventId());
    }

    @Test
    void diaryChangeEventGeneratesAnIdWhenCallerDoesNotProvideOne() {
        Diary diary = Diary.builder().diaryId("diary-3").userId("user-3").build();

        DiaryChangedEvent event = new DiaryChangedEvent(this, diary, DiaryChangedEvent.Type.WRITE);

        assertFalse(event.getEventId().isBlank());
    }
}
