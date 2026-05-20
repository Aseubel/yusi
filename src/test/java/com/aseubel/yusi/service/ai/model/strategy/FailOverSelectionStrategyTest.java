package com.aseubel.yusi.service.ai.model.strategy;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.service.ai.model.ModelInstance;
import com.aseubel.yusi.service.ai.model.ModelRuntimeState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FailOverSelectionStrategyTest {

    @Test
    void picksLowestPriorityAvailableCandidate() {
        FailOverSelectionStrategy strategy = new FailOverSelectionStrategy(new ModelRoutingProperties());

        assertEquals("fast", strategy.select("chat", List.of(
                instance("slow", 20),
                instance("fast", 10)
        ), Map.of()).orElseThrow().getId());
    }

    @Test
    void skipsUnavailableCandidateAndFallsBackByPriority() {
        FailOverSelectionStrategy strategy = new FailOverSelectionStrategy(new ModelRoutingProperties());
        ModelInstance primary = instance("primary", 1);
        ModelInstance secondary = instance("secondary", 2);

        assertEquals("secondary", strategy.select("chat", List.of(primary, secondary),
                Map.of("primary", unavailable("primary"))).orElseThrow().getId());
    }

    @Test
    void returnsFirstSortedCandidateWhenAllAreUnavailable() {
        FailOverSelectionStrategy strategy = new FailOverSelectionStrategy(new ModelRoutingProperties());

        assertEquals("primary", strategy.select("chat", List.of(
                instance("secondary", 2),
                instance("primary", 1)
        ), Map.of(
                "primary", unavailable("primary"),
                "secondary", unavailable("secondary")
        )).orElseThrow().getId());
    }

    @Test
    void returnsEmptyWhenThereAreNoCandidates() {
        FailOverSelectionStrategy strategy = new FailOverSelectionStrategy(new ModelRoutingProperties());

        assertTrue(strategy.select("chat", List.of(), Map.of()).isEmpty());
    }

    private static ModelInstance instance(String id, int priority) {
        return ModelInstance.builder().id(id).weight(1).priority(priority).build();
    }

    private static ModelRuntimeState unavailable(String id) {
        return ModelRuntimeState.builder().instanceId(id).available(false).phase("OPEN").build();
    }
}
