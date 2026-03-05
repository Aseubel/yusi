package com.aseubel.yusi.service.ai.model.strategy;

import com.aseubel.yusi.service.ai.model.ModelInstance;
import com.aseubel.yusi.service.ai.model.ModelRuntimeState;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RoundRobinSelectionStrategy implements ModelSelectionStrategy {

    private final Map<String, AtomicInteger> sequence = new ConcurrentHashMap<>();

    @Override
    public Optional<ModelInstance> select(String group, List<ModelInstance> candidates, Map<String, ModelRuntimeState> states) {
        if (candidates.isEmpty()) {
            return Optional.empty();
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
