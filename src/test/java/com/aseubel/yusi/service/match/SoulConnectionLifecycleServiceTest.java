package com.aseubel.yusi.service.match;

import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.pojo.entity.SoulConnection;
import com.aseubel.yusi.pojo.entity.SoulConnectionEvent;
import com.aseubel.yusi.pojo.entity.SoulConnectionStatus;
import com.aseubel.yusi.pojo.entity.SoulMatch;
import com.aseubel.yusi.pojo.entity.ProductEvent;
import com.aseubel.yusi.repository.SoulConnectionRepository;
import com.aseubel.yusi.repository.SoulConnectionEventRepository;
import com.aseubel.yusi.service.event.ProductEventService;
import com.aseubel.yusi.service.security.SecurityAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SoulConnectionLifecycleServiceTest {

    private final SoulConnectionRepository connectionRepository = mock(SoulConnectionRepository.class);
    private final SoulConnectionEventRepository eventRepository = mock(SoulConnectionEventRepository.class);
    private final ProductEventService productEventService = mock(ProductEventService.class);
    private final SecurityAuditService securityAuditService = mock(SecurityAuditService.class);
    private final SoulConnectionLifecycleService service =
            new SoulConnectionLifecycleService(connectionRepository, eventRepository, productEventService,
                    securityAuditService);

    @BeforeEach
    void assignConnectionIdWhenSaved() {
        when(productEventService.record(any())).thenReturn(ProductEvent.builder()
                .eventId("event-connection-1")
                .build());
        when(connectionRepository.save(any(SoulConnection.class))).thenAnswer(invocation -> {
            SoulConnection connection = invocation.getArgument(0);
            if (connection.getId() == null) {
                connection.setId(99L);
            }
            return connection;
        });
    }

    @Test
    void firstAcceptanceCreatesWaitingConnection() {
        SoulMatch match = match(7L, 1, 0, false);
        when(connectionRepository.findByMatchId(7L)).thenReturn(Optional.empty());
        SoulConnection connection = service.accept(match, "user-a");

        assertEquals(SoulConnectionStatus.WAITING_REPLY, connection.getStatus());
        assertEquals(7L, connection.getMatchId());
        assertEquals("user-a", connection.getLastActionBy());
        assertEquals("ACCEPT", connection.getLastAction());
        assertEquals("user-a", connection.getUserAId());
        assertEquals("user-b", connection.getUserBId());
    }

    @Test
    void secondAcceptanceStartsConnection() {
        SoulConnection waiting = SoulConnection.builder()
                .id(99L)
                .matchId(7L)
                .userAId("user-a")
                .userBId("user-b")
                .status(SoulConnectionStatus.WAITING_REPLY)
                .build();
        when(connectionRepository.findByMatchId(7L)).thenReturn(Optional.of(waiting));
        SoulConnection connection = service.accept(match(7L, 1, 1, true), "user-b");

        assertEquals(SoulConnectionStatus.STARTED, connection.getStatus());
        assertNotNull(connection.getStartedAt());
    }

    @Test
    void reportedConnectionCannotBeReactivatedOrUsedForChat() {
        SoulConnection started = SoulConnection.builder()
                .id(99L)
                .matchId(7L)
                .userAId("user-a")
                .userBId("user-b")
                .status(SoulConnectionStatus.STARTED)
                .build();
        when(connectionRepository.findByMatchId(7L)).thenReturn(Optional.of(started));
        SoulConnection reported = service.report(match(7L, 1, 1, true), "user-a", "UNSAFE");

        assertEquals(SoulConnectionStatus.REPORTED, reported.getStatus());
        assertEquals("UNSAFE", reported.getReasonCategory());
        assertTrue(reported.getEndedAt() == null);
        verify(securityAuditService).record(any());
        assertThrows(BusinessException.class,
                () -> service.accept(match(7L, 1, 1, true), "user-b"));
        assertThrows(BusinessException.class,
                () -> service.assertChatAllowed(match(7L), "user-a"));
    }

    @Test
    void nonParticipantAccessIsRecordedAsDenied() {
        assertThrows(BusinessException.class,
                () -> service.assertChatAllowed(match(7L), "outsider"));

        verify(securityAuditService).record(any());
    }

    @Test
    void blockedConnectionIsTerminal() {
        SoulConnection reported = SoulConnection.builder()
                .id(99L)
                .matchId(7L)
                .userAId("user-a")
                .userBId("user-b")
                .status(SoulConnectionStatus.REPORTED)
                .build();
        when(connectionRepository.findByMatchId(7L)).thenReturn(Optional.of(reported));
        SoulConnection blocked = service.block(match(7L, 1, 1, true), "user-b", "SAFETY");

        assertEquals(SoulConnectionStatus.BLOCKED, blocked.getStatus());
        assertThrows(BusinessException.class,
                () -> service.end(match(7L), "user-a", "NO_LONGER_CONTINUE"));
    }

    @Test
    void acceptancePersistsEvidenceBackedConnectionEvent() {
        when(connectionRepository.findByMatchId(7L)).thenReturn(Optional.empty());

        service.accept(match(7L, 1, 0, false), "user-a");

        ArgumentCaptor<SoulConnectionEvent> captor = ArgumentCaptor.forClass(SoulConnectionEvent.class);
        verify(eventRepository).save(captor.capture());
        SoulConnectionEvent event = captor.getValue();
        assertNotNull(event.getEventId());
        assertEquals("connection.accepted", event.getEventName());
        assertEquals(1, event.getSchemaVersion());
        assertEquals(99L, event.getConnectionId());
        assertEquals(7L, event.getMatchId());
        assertEquals("user-a", event.getActorUserId());
        assertEquals("event-connection-1", event.getEventId());
        assertEquals(SoulConnectionStatus.RECOMMENDED, event.getFromStatus());
        assertEquals(SoulConnectionStatus.WAITING_REPLY, event.getToStatus());
    }

    @Test
    void retryingSameAcceptanceDoesNotCreateDuplicateEvent() {
        SoulConnection waiting = SoulConnection.builder()
                .id(99L)
                .matchId(7L)
                .userAId("user-a")
                .userBId("user-b")
                .status(SoulConnectionStatus.WAITING_REPLY)
                .lastAction("ACCEPT")
                .lastActionBy("user-a")
                .build();
        when(connectionRepository.findByMatchId(7L)).thenReturn(Optional.of(waiting));

        SoulConnection connection = service.accept(match(7L, 1, 0, false), "user-a");

        assertEquals(SoulConnectionStatus.WAITING_REPLY, connection.getStatus());
        verify(eventRepository, never()).save(any(SoulConnectionEvent.class));
        verify(connectionRepository, never()).save(any(SoulConnection.class));
    }

    private SoulMatch match(Long id, int statusA, int statusB, boolean matched) {
        return SoulMatch.builder()
                .id(id)
                .userAId("user-a")
                .userBId("user-b")
                .statusA(statusA)
                .statusB(statusB)
                .isMatched(matched)
                .build();
    }

    private SoulMatch match(Long id) {
        return SoulMatch.builder()
                .id(id)
                .userAId("user-a")
                .userBId("user-b")
                .statusA(1)
                .statusB(1)
                .isMatched(true)
                .build();
    }
}
