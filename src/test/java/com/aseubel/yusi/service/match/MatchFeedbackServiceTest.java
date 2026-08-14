package com.aseubel.yusi.service.match;

import com.aseubel.yusi.pojo.entity.MatchFeedback;
import com.aseubel.yusi.pojo.entity.SoulConnection;
import com.aseubel.yusi.pojo.entity.SoulMatch;
import com.aseubel.yusi.repository.MatchFeedbackRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchFeedbackServiceTest {

    @Mock
    private MatchFeedbackRepository feedbackRepository;

    @Test
    void recordConnectionFeedbackPersistsConnectionIdAndCategory() {
        MatchFeedbackService service = new MatchFeedbackService(feedbackRepository);

        service.recordConnectionFeedback(99L, 7L, "user-a", "DEEP_INTERACTION");

        ArgumentCaptor<MatchFeedback> captor = ArgumentCaptor.forClass(MatchFeedback.class);
        verify(feedbackRepository).save(captor.capture());
        assertEquals(99L, captor.getValue().getConnectionId());
        assertEquals(7L, captor.getValue().getMatchId());
        assertEquals("DEEP_INTERACTION", captor.getValue().getAction());
    }

    @Test
    void recordConnectionFeedbackPersistsSourceEventAndIdempotencyKey() {
        MatchFeedbackService service = new MatchFeedbackService(feedbackRepository);

        service.recordConnectionFeedback(99L, 7L, "user-a", "DEEP_INTERACTION", 2,
                "event-feedback-1", "feedback-key-1");

        ArgumentCaptor<MatchFeedback> captor = ArgumentCaptor.forClass(MatchFeedback.class);
        verify(feedbackRepository).save(captor.capture());
        assertEquals("event-feedback-1", captor.getValue().getSourceEventId());
        assertEquals("feedback-key-1", captor.getValue().getIdempotencyKey());
    }

    @Test
    void strongNegativeSignalIncludesReportAndDoNotContinue() {
        when(feedbackRepository.countByMatchIdAndActionIn(eq(7L), any(Collection.class))).thenReturn(1L);
        MatchFeedbackService service = new MatchFeedbackService(feedbackRepository);

        assertTrue(service.hasStrongNegativeSignal(7L));
        verify(feedbackRepository).countByMatchIdAndActionIn(eq(7L), any(Collection.class));
    }

    @Test
    void mutualDeepInteractionRequiresBothUsers() {
        SoulConnection connection = SoulConnection.builder().id(99L).build();
        SoulMatch match = SoulMatch.builder().userAId("user-a").userBId("user-b").build();
        when(feedbackRepository.existsByConnectionIdAndUserIdAndActionIn(eq(99L), eq("user-a"), any(Collection.class)))
                .thenReturn(true);
        when(feedbackRepository.existsByConnectionIdAndUserIdAndActionIn(eq(99L), eq("user-b"), any(Collection.class)))
                .thenReturn(false, true);
        MatchFeedbackService service = new MatchFeedbackService(feedbackRepository);

        assertFalse(service.hasMutualDeepInteraction(connection, match));
        assertTrue(service.hasMutualDeepInteraction(connection, match));
    }
}
