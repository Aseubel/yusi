package com.aseubel.yusi.service.security;

import com.aseubel.yusi.pojo.constant.SecurityAuditAction;
import com.aseubel.yusi.pojo.constant.SecurityAuditActorType;
import com.aseubel.yusi.pojo.constant.SecurityAuditOutcome;
import com.aseubel.yusi.pojo.constant.SecurityAuditResourceType;
import com.aseubel.yusi.pojo.dto.admin.SecurityAuditEventResponse;
import com.aseubel.yusi.pojo.dto.admin.SecurityAuditQuery;
import com.aseubel.yusi.pojo.entity.SecurityAuditEvent;
import com.aseubel.yusi.pojo.entity.SecurityAuditEventScope;
import com.aseubel.yusi.repository.SecurityAuditEventRepository;
import com.aseubel.yusi.repository.SecurityAuditEventScopeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityAuditServiceTest {

    @Mock
    private SecurityAuditEventRepository eventRepository;

    @Mock
    private SecurityAuditEventScopeRepository scopeRepository;

    @Test
    void recordsOnlyRedactedLowSensitivityMetadataAndParticipantScopes() {
        when(eventRepository.save(any(SecurityAuditEvent.class))).thenAnswer(invocation -> {
            SecurityAuditEvent event = invocation.getArgument(0);
            event.setId(7L);
            return event;
        });

        SecurityAuditEvent result = service().record(SecurityAuditCommand.builder()
                .action(SecurityAuditAction.MEMORY_UPDATED)
                .actorType(SecurityAuditActorType.USER)
                .actorUserId("user-1")
                .subjectUserId("user-1")
                .resourceType(SecurityAuditResourceType.MID_TERM_MEMORY)
                .resourceId("memory-7")
                .outcome(SecurityAuditOutcome.SUCCESS)
                .scopeUserIds(Set.of("user-1", "user-2"))
                .details(Map.of(
                        "reasonCategory", "USER_EDIT",
                        "summary", "private diary text must not be stored",
                        "prompt", "secret prompt must not be stored"))
                .build());

        assertNotNull(result.getEventId());
        assertTrue(result.getDetailsJson().contains("USER_EDIT"));
        assertFalse(result.getDetailsJson().contains("private diary text"));
        assertFalse(result.getDetailsJson().contains("secret prompt"));

        ArgumentCaptor<List<SecurityAuditEventScope>> captor = ArgumentCaptor.forClass(List.class);
        verify(scopeRepository).saveAll(captor.capture());
        assertTrue(captor.getValue().stream().allMatch(scope -> scope.getAuditEventId().equals(7L)));
        assertTrue(captor.getValue().stream().map(SecurityAuditEventScope::getUserId)
                .toList().containsAll(List.of("user-1", "user-2")));
    }

    @Test
    void userAndAdministratorQueriesUseExplicitScopes() {
        PageRequest page = PageRequest.of(0, 20);
        when(eventRepository.findAccessibleToUser("user-1", page)).thenReturn(List.of());
        when(eventRepository.findAllByOrderByOccurredAtDesc(page)).thenReturn(List.of());

        service().findForUser("user-1", page);
        service().findForAdmin(true, page);

        verify(eventRepository).findAccessibleToUser("user-1", page);
        verify(eventRepository).findAllByOrderByOccurredAtDesc(page);
        assertThrows(SecurityException.class, () -> service().findForAdmin(false, page));
    }

    @Test
    void administratorQueryReturnsOnlyLowSensitivityProjection() {
        PageRequest page = PageRequest.of(0, 20);
        SecurityAuditEvent event = SecurityAuditEvent.builder()
                .id(8L)
                .eventId("event-8")
                .action(SecurityAuditAction.MEMORY_UPDATED)
                .actorType(SecurityAuditActorType.ADMIN)
                .actorUserId("admin-1")
                .subjectUserId("user-1")
                .resourceType(SecurityAuditResourceType.MID_TERM_MEMORY)
                .resourceId("memory-8")
                .outcome(SecurityAuditOutcome.SUCCESS)
                .reasonCode("ADMIN_MUTATION")
                .detailsJson("{\"operation\":\"UPDATE\",\"prompt\":\"must-not-leak\"}")
                .occurredAt(LocalDateTime.of(2026, 8, 14, 12, 0))
                .build();
        when(eventRepository.searchForAdmin(SecurityAuditAction.MEMORY_UPDATED,
                SecurityAuditOutcome.SUCCESS, SecurityAuditResourceType.MID_TERM_MEMORY,
                "user-1", page)).thenReturn(new PageImpl<>(List.of(event), page, 1));

        var result = service().findAdminPage(true, SecurityAuditQuery.builder()
                .action(SecurityAuditAction.MEMORY_UPDATED)
                .outcome(SecurityAuditOutcome.SUCCESS)
                .resourceType(SecurityAuditResourceType.MID_TERM_MEMORY)
                .userId("user-1")
                .build(), page);

        SecurityAuditEventResponse projection = result.getContent().getFirst();
        assertEquals("event-8", projection.getEventId());
        assertEquals("admin-1", projection.getActorUserId());
        assertEquals("memory-8", projection.getResourceId());
        assertEquals("UPDATE", projection.getDetails().get("operation"));
        assertFalse(projection.getDetails().containsKey("prompt"));
    }

    @Test
    void retentionDeletesScopesBeforeAuditEvents() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 14, 12, 0);
        when(eventRepository.findIdsByOccurredAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(1L, 2L));
        when(scopeRepository.deleteByAuditEventIdIn(List.of(1L, 2L))).thenReturn(2);
        when(eventRepository.deleteByIdIn(List.of(1L, 2L))).thenReturn(2);

        assertTrue(service().cleanupExpired(now) == 2);
        verify(scopeRepository).deleteByAuditEventIdIn(List.of(1L, 2L));
        verify(eventRepository).deleteByIdIn(List.of(1L, 2L));
    }

    private SecurityAuditService service() {
        return new SecurityAuditService(eventRepository, scopeRepository, new ObjectMapper());
    }
}
