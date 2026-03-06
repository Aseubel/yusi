package com.aseubel.yusi.service.ai.model.strategy;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.service.ai.model.ModelInstance;
import com.aseubel.yusi.service.ai.model.ModelRuntimeState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
public class LeastLatencySelectionStrategy implements ModelSelectionStrategy {

    private final ModelRoutingProperties properties;

    @Override
    public Optional<ModelInstance> select(String group, List<ModelInstance> candidates, Map<String, ModelRuntimeState> states) {
        for (ModelInstance candidate : candidates) {
            ModelRuntimeState state = states.get(candidate.getId());
            if (state != null && "HALF_OPEN".equals(state.getPhase())) {
                double probeRatio = properties.getHalfOpenProbeRatio();
                if (ThreadLocalRandom.current().nextDouble() < probeRatio) {
                    return Optional.of(candidate);
                }
            }
        }

        Optional<ModelInstance> available = candidates.stream()
                .filter(candidate -> {
                    ModelRuntimeState state = states.get(candidate.getId());
                    return state == null || state.isAvailable();
                })
                .min(Comparator.comparingDouble(candidate -> {
                    ModelRuntimeState state = states.get(candidate.getId());
                    return state == null ? Double.MAX_VALUE : state.getAvgLatencyMs();
                }));

        if (available.isPresent()) {
            return available;
        }

        return candidates.stream().findFirst();
    }
}
