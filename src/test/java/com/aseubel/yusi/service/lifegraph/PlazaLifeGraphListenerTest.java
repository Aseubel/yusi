package com.aseubel.yusi.service.lifegraph;

import com.aseubel.yusi.common.constant.SourceType;
import com.aseubel.yusi.common.event.PlazaCardChangedEvent;
import com.aseubel.yusi.pojo.dto.cognition.CognitionIngestCommand;
import com.aseubel.yusi.pojo.entity.TaskExecution;
import com.aseubel.yusi.service.task.TaskExecutionCommand;
import com.aseubel.yusi.service.task.TaskExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

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

    @Test
    void completesLedgerExecutionWhenPlazaLifeGraphSourceIsApplied() {
        when(taskExecutionService.createOrGet(any(TaskExecutionCommand.class)))
                .thenReturn(TaskExecution.builder().taskId("task-1").build());

        CognitionIngestCommand command = CognitionIngestCommand.builder()
                .userId("user-1")
                .sourceType(SourceType.PLAZA.code())
                .sourceId("card-1")
                .maskedText("masked")
                .build();
        PlazaCardChangedEvent event = new PlazaCardChangedEvent(this, command,
                PlazaCardChangedEvent.Type.WRITE);

        new PlazaLifeGraphListener(lifeGraphBuildService, taskExecutionService).onCardChanged(event);

        verify(lifeGraphBuildService).upsertFromPlaza(command);
        verify(taskExecutionService).succeed(eq("task-1"), isNull(), any(LocalDateTime.class));
    }
}
