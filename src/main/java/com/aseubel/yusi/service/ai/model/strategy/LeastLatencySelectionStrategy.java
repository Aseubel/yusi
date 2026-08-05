package com.aseubel.yusi.service.ai.model.strategy;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.service.ai.model.ModelInstance;
import com.aseubel.yusi.service.ai.model.ModelRuntimeState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LeastLatencySelectionStrategy implements ModelSelectionStrategy {

    private final ModelRoutingProperties properties;

    @Override
    public List<ModelInstance> order(String tierId, List<ModelInstance> candidates,
            Map<String, ModelRuntimeState> states) {
        List<ModelInstance> available = candidates.stream()
                .filter(candidate -> isAvailable(states.get(candidate.getId())))
                .sorted(Comparator.<ModelInstance>comparingDouble(candidate -> latency(states.get(candidate.getId())))
                        .thenComparing(ModelInstance::getId))
                .toList();
        List<ModelInstance> unavailable = candidates.stream()
                .filter(candidate -> !isAvailable(states.get(candidate.getId())))
                .sorted(Comparator.comparing(ModelInstance::getId))
                .toList();
        List<ModelInstance> result = new ArrayList<>(available.size() + unavailable.size());
        result.addAll(available);
        result.addAll(unavailable);
        return List.copyOf(result);
    }

    private boolean isAvailable(ModelRuntimeState state) {
        return state == null || state.isAvailable() || "HALF_OPEN".equalsIgnoreCase(state.getPhase());
    }

    private double latency(ModelRuntimeState state) {
        return state == null || state.getAvgLatencyMs() <= 0 ? Double.MAX_VALUE : state.getAvgLatencyMs();
    }
}
