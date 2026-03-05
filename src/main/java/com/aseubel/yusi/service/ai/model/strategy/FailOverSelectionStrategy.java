package com.aseubel.yusi.service.ai.model.strategy;

import com.aseubel.yusi.service.ai.model.ModelInstance;
import com.aseubel.yusi.service.ai.model.ModelRuntimeState;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class FailOverSelectionStrategy implements ModelSelectionStrategy {
    @Override
    public Optional<ModelInstance> select(String group, List<ModelInstance> candidates, Map<String, ModelRuntimeState> states) {
        return candidates.stream()
                .sorted(Comparator.comparingInt(ModelInstance::getPriority))
                .filter(candidate -> {
                    ModelRuntimeState state = states.get(candidate.getId());
                    return state == null || state.isAvailable();
                })
                .findFirst()
                .or(() -> candidates.stream().sorted(Comparator.comparingInt(ModelInstance::getPriority)).findFirst());
    }
}
