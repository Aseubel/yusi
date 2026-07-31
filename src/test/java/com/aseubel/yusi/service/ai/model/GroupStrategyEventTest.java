package com.aseubel.yusi.service.ai.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroupStrategyEventTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void publishedEventCanBeDeserializedByTopicConsumer() throws Exception {
        GroupStrategyEvent published = GroupStrategyEvent.builder()
                .group("chat")
                .strategy(ModelSelectionStrategyType.LEAST_LATENCY)
                .timestamp(123456789L)
                .build();

        GroupStrategyEvent consumed = objectMapper.readValue(
                objectMapper.writeValueAsString(published),
                GroupStrategyEvent.class);

        assertEquals(published.getGroup(), consumed.getGroup());
        assertEquals(published.getStrategy(), consumed.getStrategy());
        assertEquals(published.getTimestamp(), consumed.getTimestamp());
    }
}
