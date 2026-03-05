package com.aseubel.yusi.service.ai.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GroupStrategyEvent {
    private String group;
    private ModelSelectionStrategyType strategy;
    private long timestamp;
}
