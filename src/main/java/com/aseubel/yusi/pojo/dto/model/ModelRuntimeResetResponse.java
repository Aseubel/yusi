package com.aseubel.yusi.pojo.dto.model;

import com.aseubel.yusi.service.ai.model.ModelRuntimeState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelRuntimeResetResponse {
    private String scope;
    private String modelId;
    private int count;
    private String status;
    private boolean convergencePending;
    private ModelRuntimeState state;
}
