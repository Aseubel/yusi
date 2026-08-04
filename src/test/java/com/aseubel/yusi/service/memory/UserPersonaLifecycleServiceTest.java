package com.aseubel.yusi.service.memory;

import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.pojo.dto.memory.PersonaMemoryItem;
import com.aseubel.yusi.pojo.dto.memory.UpdatePersonaMemoryRequest;
import com.aseubel.yusi.pojo.entity.UserPersona;
import com.aseubel.yusi.repository.UserPersonaRepository;
import com.aseubel.yusi.service.match.MatchProfileAssembler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPersonaLifecycleServiceTest {

    @Mock
    private UserPersonaRepository repository;

    @Mock
    private MatchProfileAssembler matchProfileAssembler;

    @Test
    void updateChangesLifecycleAndRefreshesMatchProfile() {
        UserPersona persona = persona("user-1");
        when(repository.findByUserId("user-1")).thenReturn(Optional.of(persona));
        when(repository.save(any(UserPersona.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdatePersonaMemoryRequest request = new UpdatePersonaMemoryRequest();
        request.setPreferredName("新的称呼");
        request.setHidden(true);
        request.setMatchAllowed(false);

        PersonaMemoryItem result = lifecycle().update("user-1", request);

        assertEquals("新的称呼", result.getPreferredName());
        assertEquals("HIDDEN", result.getLifecycleStatus());
        assertTrue(result.getHidden());
        assertEquals(1.0, result.getConfidence());
        assertEquals("USER_EDIT", result.getSourceType());
        verify(matchProfileAssembler).refreshProfile("user-1");
    }

    @Test
    void updateRejectsUnknownClearField() {
        UpdatePersonaMemoryRequest request = new UpdatePersonaMemoryRequest();
        request.setClearFields(java.util.List.of("secretField"));

        assertThrows(BusinessException.class, () -> lifecycle().update("user-1", request));

        verify(repository, never()).save(any(UserPersona.class));
        verifyNoInteractions(matchProfileAssembler);
    }

    @Test
    void updateCanClearAValueWithoutChangingOtherFields() {
        UserPersona persona = persona("user-1");
        persona.setPreferredName("保留称呼");
        persona.setInterests("摄影");
        when(repository.findByUserId("user-1")).thenReturn(Optional.of(persona));
        when(repository.save(any(UserPersona.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdatePersonaMemoryRequest request = new UpdatePersonaMemoryRequest();
        request.setClearFields(java.util.List.of("interests"));

        PersonaMemoryItem result = lifecycle().update("user-1", request);

        assertEquals("保留称呼", result.getPreferredName());
        assertEquals(null, result.getInterests());
        verify(repository).save(persona);
    }

    @Test
    void deleteOnlyDeletesTheCurrentUsersPersona() {
        when(repository.findByUserId("user-1")).thenReturn(Optional.empty());

        lifecycle().delete("user-1");

        verify(repository, never()).delete(any(UserPersona.class));
        verifyNoInteractions(matchProfileAssembler);
    }

    @Test
    void expiredPersonaIsReportedAsExpired() {
        UserPersona persona = persona("user-1");
        persona.setValidUntil(LocalDateTime.now().minusMinutes(1));
        when(repository.findByUserId("user-1")).thenReturn(Optional.of(persona));

        PersonaMemoryItem result = lifecycle().get("user-1");

        assertEquals("EXPIRED", result.getLifecycleStatus());
    }

    private UserPersonaLifecycleService lifecycle() {
        return new UserPersonaLifecycleService(repository, matchProfileAssembler);
    }

    private UserPersona persona(String userId) {
        LocalDateTime updatedAt = LocalDateTime.now().minusDays(1);
        return UserPersona.builder()
                .id(1L)
                .userId(userId)
                .preferredName("旧称呼")
                .confidence(0.6)
                .matchAllowed(true)
                .hidden(false)
                .createdAt(updatedAt)
                .updatedAt(updatedAt)
                .build();
    }
}
