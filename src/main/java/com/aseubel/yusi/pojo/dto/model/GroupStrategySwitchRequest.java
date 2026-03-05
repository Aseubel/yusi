package com.aseubel.yusi.pojo.dto.model;

import com.aseubel.yusi.service.ai.model.ModelSelectionStrategyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GroupStrategySwitchRequest {
    @NotBlank
    private String group;

    @NotNull
    private ModelSelectionStrategyType strategy;
}
