package com.aseubel.yusi.service.persona;

import com.aseubel.yusi.pojo.dto.cognition.CognitionRoutingResult;
import com.aseubel.yusi.pojo.entity.UserPersona;
import com.aseubel.yusi.repository.UserPersonaRepository;
import com.aseubel.yusi.service.persona.impl.UserPersonaUpdateServiceImpl;
import com.aseubel.yusi.service.user.impl.UserPersonaServiceImpl;
import com.aseubel.yusi.service.task.TaskExecutionService;
import com.aseubel.yusi.pojo.entity.TaskExecution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPersonaUpdateServiceTest {

    @Mock
    private UserPersonaRepository repository;

    @Mock
    private TaskExecutionService taskExecutionService;

    @Test
    void cognitionSourceUpdatesContentWithoutResettingUserLifecycleControls() {
        UserPersona existing = UserPersona.builder()
                .id(3L)
                .userId("user-1")
                .hidden(true)
                .matchAllowed(true)
                .sourceType("USER_EDIT")
                .confidence(1.0)
                .build();
        when(repository.findByUserId("user-1")).thenReturn(Optional.of(existing));
        when(repository.save(any(UserPersona.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskExecutionService.createOrGet(any())).thenReturn(TaskExecution.builder().taskId("task-1").build());

        CognitionRoutingResult routing = CognitionRoutingResult.builder()
                .interests("胶片摄影")
                .build();

        new UserPersonaUpdateServiceImpl(new UserPersonaServiceImpl(repository), taskExecutionService)
                .mergeFromRouting("user-1", routing, "DIARY", "diary-7");

        assertEquals("胶片摄影", existing.getInterests());
        assertEquals("DIARY", existing.getSourceType());
        assertEquals("diary-7", existing.getSourceId());
        assertEquals(0.5, existing.getConfidence());
        assertTrue(existing.getHidden());
        assertTrue(existing.getMatchAllowed());
    }
}
