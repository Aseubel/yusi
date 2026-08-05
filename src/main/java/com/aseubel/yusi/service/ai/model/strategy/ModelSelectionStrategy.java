package com.aseubel.yusi.service.ai.model.strategy;

import com.aseubel.yusi.service.ai.model.ModelInstance;
import com.aseubel.yusi.service.ai.model.ModelRuntimeState;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ModelSelectionStrategy {
    List<ModelInstance> order(String tierId, List<ModelInstance> candidates,
            Map<String, ModelRuntimeState> states);

    default Optional<ModelInstance> select(String tierId, List<ModelInstance> candidates,
            Map<String, ModelRuntimeState> states) {
        return order(tierId, candidates, states).stream().findFirst();
    }
}
