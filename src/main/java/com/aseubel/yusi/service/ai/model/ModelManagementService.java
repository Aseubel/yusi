package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ModelManagementService {

    private final GroupStrategyManager groupStrategyManager;
    private final ModelStateCenter modelStateCenter;
    private final ModelConfigCenter modelConfigCenter;

    public void switchGroupStrategy(String group, ModelSelectionStrategyType strategy) {
        groupStrategyManager.switchStrategy(group, strategy);
    }

    public ModelSelectionStrategyType getGroupStrategy(String group) {
        return groupStrategyManager.getStrategy(group);
    }

    public List<ModelRuntimeState> listModelStates() {
        return modelStateCenter.listStates();
    }

    public ModelRoutingProperties getModelConfigForDisplay() {
        return modelConfigCenter.getConfigForDisplay();
    }

    public void updateModelConfig(ModelRoutingProperties request) {
        modelConfigCenter.updateFromAdmin(request);
    }
}
