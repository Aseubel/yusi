package com.aseubel.yusi.service.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupStrategyEvent {
    private String group;
    private ModelSelectionStrategyType strategy;
    private long timestamp;
}
