package com.aseubel.yusi.service.ai.model.strategy;

import com.aseubel.yusi.service.ai.model.ModelInstance;
import com.aseubel.yusi.service.ai.model.ModelRuntimeState;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class WeightedRandomSelectionStrategy implements ModelSelectionStrategy {
    @Override
    public Optional<ModelInstance> select(String group, List<ModelInstance> candidates, Map<String, ModelRuntimeState> states) {
        List<ModelInstance> available = new ArrayList<>();
        int totalWeight = 0;
        for (ModelInstance candidate : candidates) {
            ModelRuntimeState state = states.get(candidate.getId());
            if (state != null && !state.isAvailable()) {
                continue;
            }
            available.add(candidate);
            totalWeight += Math.max(1, candidate.getWeight());
        }
        if (available.isEmpty()) {
            return candidates.stream().findFirst();
        }
        int point = ThreadLocalRandom.current().nextInt(totalWeight);
        int cursor = 0;
        for (ModelInstance candidate : available) {
            cursor += Math.max(1, candidate.getWeight());
            if (point < cursor) {
                return Optional.of(candidate);
            }
        }
        return Optional.of(available.get(available.size() - 1));
    }
}
