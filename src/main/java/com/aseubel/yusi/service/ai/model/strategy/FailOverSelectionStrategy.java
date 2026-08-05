package com.aseubel.yusi.service.ai.model.strategy;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.service.ai.model.ModelInstance;
import com.aseubel.yusi.service.ai.model.ModelRuntimeState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class FailOverSelectionStrategy implements ModelSelectionStrategy {

    private final ModelRoutingProperties properties;

    @Override
    public List<ModelInstance> order(String tierId, List<ModelInstance> candidates,
            Map<String, ModelRuntimeState> states) {
        return candidates.stream()
                .sorted(Comparator.comparing((ModelInstance candidate) -> !isAvailable(states.get(candidate.getId())))
                        .thenComparingInt(ModelInstance::getPriority)
                        .thenComparing(ModelInstance::getId))
                .toList();
    }

    private boolean isAvailable(ModelRuntimeState state) {
        return state == null || state.isAvailable() || "HALF_OPEN".equalsIgnoreCase(state.getPhase());
    }
}
