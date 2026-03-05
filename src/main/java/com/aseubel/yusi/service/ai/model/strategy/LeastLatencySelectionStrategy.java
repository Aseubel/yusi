package com.aseubel.yusi.service.ai.model.strategy;

import com.aseubel.yusi.service.ai.model.ModelInstance;
import com.aseubel.yusi.service.ai.model.ModelRuntimeState;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class LeastLatencySelectionStrategy implements ModelSelectionStrategy {
    @Override
    public Optional<ModelInstance> select(String group, List<ModelInstance> candidates, Map<String, ModelRuntimeState> states) {
        return candidates.stream()
                .filter(candidate -> {
                    ModelRuntimeState state = states.get(candidate.getId());
                    return state == null || state.isAvailable();
                })
                .min(Comparator.comparingDouble(candidate -> {
                    ModelRuntimeState state = states.get(candidate.getId());
                    return state == null ? Double.MAX_VALUE : state.getAvgLatencyMs();
                }))
                .or(() -> candidates.stream().findFirst());
    }
}
