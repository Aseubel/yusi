package com.aseubel.yusi.pojo.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DomainCorrelationFieldsTest {

    @Test
    void domainRecordsExposePhaseTwoCorrelationIdentifiers() {
        SoulMatch match = SoulMatch.builder()
                .generationRunId("match-run-1")
                .recommendationEventId("event-match-1")
                .build();
        SoulMessage soulMessage = SoulMessage.builder()
                .matchId(7L)
                .connectionId(99L)
                .runId("chat-run-1")
                .sourceEventId("event-chat-1")
                .build();
        ChatMemoryMessage chatMemoryMessage = ChatMemoryMessage.builder()
                .runId("chat-run-1")
                .sourceEventId("event-memory-1")
                .build();
        MatchFeedback feedback = MatchFeedback.builder()
                .sourceEventId("event-feedback-1")
                .idempotencyKey("feedback-key-1")
                .build();
        UserNotification notification = UserNotification.builder()
                .sourceEventId("event-notification-1")
                .build();
        SoulReport report = SoulReport.builder()
                .generationRunId("report-run-1")
                .taskExecutionId("task-1")
                .build();

        assertEquals("match-run-1", match.getGenerationRunId());
        assertEquals("event-match-1", match.getRecommendationEventId());
        assertEquals(99L, soulMessage.getConnectionId());
        assertEquals("chat-run-1", soulMessage.getRunId());
        assertEquals("event-chat-1", soulMessage.getSourceEventId());
        assertEquals("chat-run-1", chatMemoryMessage.getRunId());
        assertEquals("event-memory-1", chatMemoryMessage.getSourceEventId());
        assertEquals("event-feedback-1", feedback.getSourceEventId());
        assertEquals("feedback-key-1", feedback.getIdempotencyKey());
        assertEquals("event-notification-1", notification.getSourceEventId());
        assertEquals("report-run-1", report.getGenerationRunId());
        assertEquals("task-1", report.getTaskExecutionId());
    }
}
