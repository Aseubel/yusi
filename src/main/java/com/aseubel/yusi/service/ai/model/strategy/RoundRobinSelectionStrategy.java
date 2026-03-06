package com.aseubel.yusi.service.ai.model.strategy;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.service.ai.model.ModelInstance;
import com.aseubel.yusi.service.ai.model.ModelRuntimeState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
public class RoundRobinSelectionStrategy implements ModelSelectionStrategy {

    private final ModelRoutingProperties properties;
    private final Map<String, AtomicInteger> sequence = new ConcurrentHashMap<>();

    @Override
    public Optional<ModelInstance> select(String group, List<ModelInstance> candidates, Map<String, ModelRuntimeState> states) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        for (ModelInstance candidate : candidates) {
            ModelRuntimeState state = states.get(candidate.getId());
            if (state != null && "HALF_OPEN".equals(state.getPhase())) {
                double probeRatio = properties.getHalfOpenProbeRatio();
                if (ThreadLocalRandom.current().nextDouble() < probeRatio) {
                    return Optional.of(candidate);
                }
            }
        }

        AtomicInteger cursor = sequence.computeIfAbsent(group, g -> new AtomicInteger(0));
        int size = candidates.size();
        for (int i = 0; i < size; i++) {
            int index = Math.floorMod(cursor.getAndIncrement(), size);
            ModelInstance candidate = candidates.get(index);
            ModelRuntimeState state = states.get(candidate.getId());
            if (state == null || state.isAvailable()) {
                return Optional.of(candidate);
            }
        }

        return Optional.of(candidates.get(Math.floorMod(cursor.get(), size)));
    }
}
