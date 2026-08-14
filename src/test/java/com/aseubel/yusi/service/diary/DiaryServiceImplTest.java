package com.aseubel.yusi.service.diary;

import com.aseubel.yusi.common.event.DiaryCognitionIngestEvent;
import com.aseubel.yusi.common.event.DiaryChangedEvent;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.service.ai.mask.MaskResult;
import com.aseubel.yusi.service.ai.mask.SensitiveDataMaskService;
import com.aseubel.yusi.service.diary.impl.DiaryServiceImpl;
import com.aseubel.yusi.service.task.TaskExecutionService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DiaryServiceImplTest {

    @Test
    void emptyDiaryStillPublishesCognitionEventForSourceCleanup() {
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        SensitiveDataMaskService maskService = mock(SensitiveDataMaskService.class);
        org.mockito.Mockito.when(maskService.mask("")).thenReturn(MaskResult.empty());
        DiaryServiceImpl service = new DiaryServiceImpl();
        ReflectionTestUtils.setField(service, "eventPublisher", eventPublisher);
        ReflectionTestUtils.setField(service, "sensitiveDataMaskService", maskService);
        ReflectionTestUtils.setField(service, "taskExecutionService", mock(TaskExecutionService.class));

        Diary diary = Diary.builder()
                .userId("user-1")
                .diaryId("diary-1")
                .title("Empty diary")
                .build();

        ReflectionTestUtils.invokeMethod(service, "publishDiaryEvents", diary, null, DiaryChangedEvent.Type.MODIFY);

        verify(eventPublisher, times(2)).publishEvent(any(ApplicationEvent.class));
        var captor = org.mockito.ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(eventPublisher, times(2)).publishEvent(captor.capture());
        List<ApplicationEvent> events = captor.getAllValues();
        DiaryCognitionIngestEvent cognitionEvent = events.stream()
                .filter(DiaryCognitionIngestEvent.class::isInstance)
                .map(DiaryCognitionIngestEvent.class::cast)
                .findFirst()
                .orElse(null);

        assertNotNull(cognitionEvent);
        assertTrue(cognitionEvent.getCommand().getMaskedText() == null
                || cognitionEvent.getCommand().getMaskedText().isBlank());
    }
}
