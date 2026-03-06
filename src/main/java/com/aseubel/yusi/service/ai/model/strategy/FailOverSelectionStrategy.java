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
public class FailOverSelectionStrategy implements ModelSelectionStrategy {

    private final ModelRoutingProperties properties;

    @Override
    public Optional<ModelInstance> select(String group, List<ModelInstance> candidates, Map<String, ModelRuntimeState> states) {
        List<ModelInstance> sorted = candidates.stream()
                .sorted(Comparator.comparingInt(ModelInstance::getPriority))
                .toList();

        for (ModelInstance candidate : sorted) {
            ModelRuntimeState state = states.get(candidate.getId());
            
            if (state == null || state.isAvailable()) {
                return Optional.of(candidate);
            }

            if ("HALF_OPEN".equals(state.getPhase())) {
                double probeRatio = properties.getHalfOpenProbeRatio();
                if (ThreadLocalRandom.current().nextDouble() < probeRatio) {
                    return Optional.of(candidate);
                }
            }
        }

        return sorted.stream().findFirst();
    }
}
