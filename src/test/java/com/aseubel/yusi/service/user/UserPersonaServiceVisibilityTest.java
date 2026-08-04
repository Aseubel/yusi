package com.aseubel.yusi.service.user;

import com.aseubel.yusi.pojo.entity.UserPersona;
import com.aseubel.yusi.repository.UserPersonaRepository;
import com.aseubel.yusi.service.user.impl.UserPersonaServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPersonaServiceVisibilityTest {

    @Mock
    private UserPersonaRepository repository;

    @Test
    void hiddenPersonaIsNotReturnedToAgent() {
        when(repository.findVisibleByUserId(eq("user-1"), any())).thenReturn(Optional.empty());

        UserPersona result = service().getUserPersona("user-1");

        assertEquals("user-1", result.getUserId());
        assertNull(result.getPreferredName());
        verify(repository).findVisibleByUserId(eq("user-1"), any());
        verify(repository, never()).findByUserId("user-1");
    }

    @Test
    void matchablePersonaUsesTheExplicitMatchingScope() {
        UserPersona persona = UserPersona.builder().userId("user-1").preferredName("小予").build();
        when(repository.findMatchableByUserId(eq("user-1"), any())).thenReturn(Optional.of(persona));

        UserPersona result = service().getMatchableUserPersona("user-1");

        assertEquals("小予", result.getPreferredName());
        verify(repository).findMatchableByUserId(eq("user-1"), any());
    }

    @Test
    void expiredPersonaIsAbsentFromVisibleAndMatchableReads() {
        when(repository.findVisibleByUserId(eq("user-1"), any())).thenReturn(Optional.empty());
        when(repository.findMatchableByUserId(eq("user-1"), any())).thenReturn(Optional.empty());

        UserPersona visible = service().getUserPersona("user-1");
        UserPersona matchable = service().getMatchableUserPersona("user-1");

        assertNull(visible.getPreferredName());
        assertNull(matchable.getPreferredName());
    }

    private UserPersonaServiceImpl service() {
        return new UserPersonaServiceImpl(repository);
    }
}
