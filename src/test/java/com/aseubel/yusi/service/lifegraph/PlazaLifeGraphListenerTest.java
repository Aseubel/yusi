package com.aseubel.yusi.service.lifegraph;

import com.aseubel.yusi.common.constant.SourceType;
import com.aseubel.yusi.common.event.PlazaCardChangedEvent;
import com.aseubel.yusi.pojo.dto.cognition.CognitionIngestCommand;
import com.aseubel.yusi.pojo.entity.TaskExecution;
import com.aseubel.yusi.pojo.entity.SoulCard;
import com.aseubel.yusi.repository.SoulCardRepository;
import com.aseubel.yusi.service.task.TaskExecutionCommand;
import com.aseubel.yusi.service.task.TaskExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlazaLifeGraphListenerTest {

    @Mock
    private LifeGraphBuildService lifeGraphBuildService;

    @Mock
    private TaskExecutionService taskExecutionService;

    @Mock
    private SoulCardRepository cardRepository;

    @Test
    void completesLedgerExecutionWhenPlazaLifeGraphSourceIsApplied() {
        when(taskExecutionService.createOrGet(any(TaskExecutionCommand.class)))
                .thenReturn(TaskExecution.builder().taskId("task-1").build());

        CognitionIngestCommand command = CognitionIngestCommand.builder()
                .userId("user-1")
                .sourceType(SourceType.PLAZA.code())
                .sourceId("card-1")
                .maskedText("masked")
                .sourceRevision(1L)
                .build();
        PlazaCardChangedEvent event = new PlazaCardChangedEvent(this, command,
                PlazaCardChangedEvent.Type.WRITE);

        new PlazaLifeGraphListener(lifeGraphBuildService, taskExecutionService, cardRepository).onCardChanged(event);

        verify(lifeGraphBuildService).upsertFromPlaza(command);
        verify(taskExecutionService).succeed(eq("task-1"), isNull(), any(LocalDateTime.class));
    }

    @Test
    void skipsOlderPlazaRevisionAfterCardWasUpdated() {
        when(taskExecutionService.createOrGet(any(TaskExecutionCommand.class)))
                .thenReturn(TaskExecution.builder().taskId("task-2").build());
        when(cardRepository.findById(42L)).thenReturn(Optional.of(SoulCard.builder()
                .id(42L)
                .userId("user-1")
                .sourceRevision(2L)
                .build()));

        CognitionIngestCommand command = CognitionIngestCommand.builder()
                .userId("user-1")
                .sourceType(SourceType.PLAZA.code())
                .sourceId("42")
                .sourceRevision(1L)
                .maskedText("old masked")
                .build();

        new PlazaLifeGraphListener(lifeGraphBuildService, taskExecutionService, cardRepository)
                .onCardChanged(new PlazaCardChangedEvent(this, command, PlazaCardChangedEvent.Type.MODIFY));

        org.mockito.Mockito.verify(lifeGraphBuildService,
                org.mockito.Mockito.never()).upsertFromPlaza(command);
        verify(taskExecutionService).succeed(eq("task-2"), isNull(), any(LocalDateTime.class));
    }
}
