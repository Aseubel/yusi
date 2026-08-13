package com.aseubel.yusi.service.ai.model.strategy;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.service.ai.model.ModelInstance;
import com.aseubel.yusi.service.ai.model.ModelRuntimeState;
import com.aseubel.yusi.service.ai.model.constant.ModelHealthPhase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
public class RoundRobinSelectionStrategy implements ModelSelectionStrategy {

    private final ModelRoutingProperties properties;
    private final Map<String, AtomicInteger> sequence = new ConcurrentHashMap<>();

    @Override
    public List<ModelInstance> order(String tierId, List<ModelInstance> candidates,
            Map<String, ModelRuntimeState> states) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<ModelInstance> available = candidates.stream()
                .filter(candidate -> isAvailable(states.get(candidate.getId())))
                .toList();
        List<ModelInstance> unavailable = candidates.stream()
                .filter(candidate -> !isAvailable(states.get(candidate.getId())))
                .toList();
        AtomicInteger cursor = sequence.computeIfAbsent(tierId, g -> new AtomicInteger(0));
        List<ModelInstance> ordered = rotate(available, cursor.getAndIncrement());
        List<ModelInstance> result = new ArrayList<>(ordered.size() + unavailable.size());
        result.addAll(ordered);
        result.addAll(unavailable);
        return List.copyOf(result);
    }

    private boolean isAvailable(ModelRuntimeState state) {
        return state == null || state.isAvailable()
                || ModelHealthPhase.HALF_OPEN.code().equalsIgnoreCase(state.getPhase());
    }

    private List<ModelInstance> rotate(List<ModelInstance> candidates, int offset) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<ModelInstance> result = new ArrayList<>(candidates);
        Collections.rotate(result, -Math.floorMod(offset, result.size()));
        return result;
    }
}
