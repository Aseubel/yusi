package com.aseubel.yusi.service.ai.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ModelStateEvent {
    private String instanceId;
    private String action;
    private long timestamp;
    private ModelRuntimeState state;
}
