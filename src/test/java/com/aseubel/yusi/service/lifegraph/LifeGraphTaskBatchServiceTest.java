package com.aseubel.yusi.service.lifegraph;

import com.aseubel.yusi.config.security.CryptoService;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.pojo.entity.LifeGraphTask;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.DiaryRepository;
import com.aseubel.yusi.repository.LifeGraphTaskRepository;
import com.aseubel.yusi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LifeGraphTaskBatchServiceTest {

    @Mock
    private LifeGraphTaskRepository taskRepository;

    @Mock
    private LifeGraphTaskClaimService taskClaimService;

    @Mock
    private DiaryRepository diaryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CryptoService cryptoService;

    @Mock
    private LifeGraphBuildService lifeGraphBuildService;

    @Test
    void loadsDiaryWithTheTaskUserAndRetriesWhenExtractionFails() {
        LifeGraphTask task = LifeGraphTask.createUpsertTask("diary-1", "user-a");
        task.setId(7L);
        task.setStatus(LifeGraphTask.TaskStatus.PROCESSING);
        when(taskClaimService.claimPendingTasks(any(LocalDateTime.class), any(Integer.class)))
                .thenReturn(List.of(task));
        Diary diary = Diary.builder()
                .diaryId("diary-1")
                .userId("user-a")
                .plainContent("content")
                .build();
        when(diaryRepository.findByDiaryIdAndUserId("diary-1", "user-a")).thenReturn(diary);
        org.mockito.Mockito.doThrow(new IllegalStateException("invalid extraction"))
                .when(lifeGraphBuildService).upsertFromDiary(diary, "content");

        service().processPendingTasks();

        verify(diaryRepository).findByDiaryIdAndUserId("diary-1", "user-a");
        verify(diaryRepository, never()).findByDiaryId("diary-1");
        verify(taskRepository).incrementRetryAndSetNextAttempt(
                any(Long.class), anyString(), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(taskRepository, never()).markAsCompleted(any(Long.class), any(LocalDateTime.class));
    }

    @Test
    void removesExistingDiarySourceWhenCurrentContentIsBlank() {
        LifeGraphTask task = LifeGraphTask.createUpsertTask("diary-1", "user-a");
        task.setId(8L);
        when(taskClaimService.claimPendingTasks(any(LocalDateTime.class), any(Integer.class)))
                .thenReturn(List.of(task));
        Diary diary = Diary.builder()
                .diaryId("diary-1")
                .userId("user-a")
                .plainContent(" ")
                .build();
        when(diaryRepository.findByDiaryIdAndUserId("diary-1", "user-a")).thenReturn(diary);

        service().processPendingTasks();

        verify(lifeGraphBuildService).deleteByDiary("user-a", "diary-1");
        verify(taskRepository).markAsCompleted(any(Long.class), any(LocalDateTime.class));
    }

    private LifeGraphTaskBatchService service() {
        return new LifeGraphTaskBatchService(taskRepository, taskClaimService, diaryRepository,
                userRepository, cryptoService, lifeGraphBuildService);
    }
}
