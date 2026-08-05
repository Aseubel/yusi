package com.aseubel.yusi.config.ai.properties;

import com.aseubel.yusi.service.ai.model.ModelCapability;
import com.aseubel.yusi.service.ai.model.ModelSelectionStrategyType;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ModelTierDefinition {

    private String displayName;

    private String description;

    private List<String> members = new ArrayList<>();

    private ModelSelectionStrategyType strategy = ModelSelectionStrategyType.ROUND_ROBIN;

    private boolean enabled = true;

    private List<ModelCapability> capabilities = new ArrayList<>();
}
