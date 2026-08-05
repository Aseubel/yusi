package com.aseubel.yusi.service.match;

import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.pojo.entity.SoulConnection;
import com.aseubel.yusi.pojo.entity.SoulConnectionStatus;
import com.aseubel.yusi.pojo.entity.SoulMatch;
import com.aseubel.yusi.repository.SoulConnectionRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SoulConnectionLifecycleServiceTest {

    private final SoulConnectionRepository connectionRepository = mock(SoulConnectionRepository.class);
    private final SoulConnectionLifecycleService service = new SoulConnectionLifecycleService(connectionRepository);

    @Test
    void firstAcceptanceCreatesWaitingConnection() {
        SoulMatch match = match(7L, 1, 0, false);
        when(connectionRepository.findByMatchId(7L)).thenReturn(Optional.empty());
        when(connectionRepository.save(any(SoulConnection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

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
        when(connectionRepository.save(any(SoulConnection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

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
        when(connectionRepository.save(any(SoulConnection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SoulConnection reported = service.report(match(7L, 1, 1, true), "user-a", "UNSAFE");

        assertEquals(SoulConnectionStatus.REPORTED, reported.getStatus());
        assertEquals("UNSAFE", reported.getReasonCategory());
        assertTrue(reported.getEndedAt() == null);
        assertThrows(BusinessException.class,
                () -> service.accept(match(7L, 1, 1, true), "user-b"));
        assertThrows(BusinessException.class,
                () -> service.assertChatAllowed(match(7L), "user-a"));
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
        when(connectionRepository.save(any(SoulConnection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SoulConnection blocked = service.block(match(7L, 1, 1, true), "user-b", "SAFETY");

        assertEquals(SoulConnectionStatus.BLOCKED, blocked.getStatus());
        assertThrows(BusinessException.class,
                () -> service.end(match(7L), "user-a", "NO_LONGER_CONTINUE"));
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
