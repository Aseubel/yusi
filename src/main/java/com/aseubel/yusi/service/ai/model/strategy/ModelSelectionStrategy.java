package com.aseubel.yusi.service.ai.model.strategy;

import com.aseubel.yusi.service.ai.model.ModelInstance;
import com.aseubel.yusi.service.ai.model.ModelRuntimeState;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ModelSelectionStrategy {
    Optional<ModelInstance> select(String group, List<ModelInstance> candidates, Map<String, ModelRuntimeState> states);
}
