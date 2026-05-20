package com.aseubel.yusi.service.ai.model.strategy;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.service.ai.model.ModelInstance;
import com.aseubel.yusi.service.ai.model.ModelRuntimeState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoundRobinSelectionStrategyTest {

    @Test
    void rotatesThroughAvailableCandidatesPerGroup() {
        RoundRobinSelectionStrategy strategy = new RoundRobinSelectionStrategy(new ModelRoutingProperties());
        List<ModelInstance> candidates = List.of(instance("a"), instance("b"), instance("c"));

        assertEquals("a", strategy.select("chat", candidates, Map.of()).orElseThrow().getId());
        assertEquals("b", strategy.select("chat", candidates, Map.of()).orElseThrow().getId());
        assertEquals("c", strategy.select("chat", candidates, Map.of()).orElseThrow().getId());
        assertEquals("a", strategy.select("chat", candidates, Map.of()).orElseThrow().getId());
    }

    @Test
    void skipsUnavailableCandidates() {
        RoundRobinSelectionStrategy strategy = new RoundRobinSelectionStrategy(new ModelRoutingProperties());
        List<ModelInstance> candidates = List.of(instance("a"), instance("b"));
        Map<String, ModelRuntimeState> states = Map.of("a", unavailable("a"));

        assertEquals("b", strategy.select("chat", candidates, states).orElseThrow().getId());
    }

    @Test
    void returnsEmptyWhenThereAreNoCandidates() {
        RoundRobinSelectionStrategy strategy = new RoundRobinSelectionStrategy(new ModelRoutingProperties());

        assertTrue(strategy.select("chat", List.of(), Map.of()).isEmpty());
    }

    private static ModelInstance instance(String id) {
        return ModelInstance.builder().id(id).weight(1).priority(1).build();
    }

    private static ModelRuntimeState unavailable(String id) {
        return ModelRuntimeState.builder().instanceId(id).available(false).phase("OPEN").build();
    }
}
