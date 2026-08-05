package com.aseubel.yusi.service.ai.model.strategy;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.service.ai.model.ModelInstance;
import com.aseubel.yusi.service.ai.model.ModelRuntimeState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
public class WeightedRandomSelectionStrategy implements ModelSelectionStrategy {

    private final ModelRoutingProperties properties;

    @Override
    public List<ModelInstance> order(String tierId, List<ModelInstance> candidates,
            Map<String, ModelRuntimeState> states) {
        List<ModelInstance> remaining = new ArrayList<>(candidates.stream()
                .filter(candidate -> isAvailable(states.get(candidate.getId())))
                .toList());
        List<ModelInstance> ordered = new ArrayList<>(remaining.size());
        while (!remaining.isEmpty()) {
            int totalWeight = remaining.stream().mapToInt(candidate -> Math.max(1, candidate.getWeight())).sum();
            int point = ThreadLocalRandom.current().nextInt(totalWeight);
            int cursor = 0;
            for (int i = 0; i < remaining.size(); i++) {
                ModelInstance candidate = remaining.get(i);
                cursor += Math.max(1, candidate.getWeight());
                if (point < cursor) {
                    ordered.add(candidate);
                    remaining.remove(i);
                    break;
                }
            }
        }
        candidates.stream()
                .filter(candidate -> !isAvailable(states.get(candidate.getId())))
                .sorted(Comparator.comparing(ModelInstance::getId))
                .forEach(ordered::add);
        return List.copyOf(ordered);
    }

    private boolean isAvailable(ModelRuntimeState state) {
        return state == null || state.isAvailable() || "HALF_OPEN".equalsIgnoreCase(state.getPhase());
    }
}
