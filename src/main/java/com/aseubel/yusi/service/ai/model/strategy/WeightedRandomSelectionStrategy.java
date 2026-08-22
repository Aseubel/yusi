package com.aseubel.yusi.service.ai.model.strategy;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.service.ai.model.ModelInstance;
import com.aseubel.yusi.service.ai.model.ModelRuntimeState;
import com.aseubel.yusi.service.ai.model.constant.ModelHealthPhase;
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
                .filter(candidate -> candidate.getWeight() > 0)
                .toList());
        List<ModelInstance> ordered = new ArrayList<>(candidates.size());
        while (!remaining.isEmpty()) {
            long totalWeight = remaining.stream().mapToLong(ModelInstance::getWeight).sum();
            long point = ThreadLocalRandom.current().nextLong(totalWeight);
            long cursor = 0;
            for (int i = 0; i < remaining.size(); i++) {
                ModelInstance candidate = remaining.get(i);
                cursor += candidate.getWeight();
                if (point < cursor) {
                    ordered.add(candidate);
                    remaining.remove(i);
                    break;
                }
            }
        }
        candidates.stream()
                .filter(candidate -> !ordered.contains(candidate))
                .sorted(Comparator.comparing(ModelInstance::getId))
                .forEach(ordered::add);
        return List.copyOf(ordered);
    }

    private boolean isAvailable(ModelRuntimeState state) {
        return state == null || state.isAvailable()
                || ModelHealthPhase.HALF_OPEN.code().equalsIgnoreCase(state.getPhase());
    }
}
