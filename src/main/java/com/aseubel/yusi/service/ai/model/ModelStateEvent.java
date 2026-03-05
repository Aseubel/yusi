package com.aseubel.yusi.service.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelStateEvent {
    private String instanceId;
    private String action;
    private long timestamp;
    private ModelRuntimeState state;
}
