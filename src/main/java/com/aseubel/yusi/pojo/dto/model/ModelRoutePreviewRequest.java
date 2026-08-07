package com.aseubel.yusi.pojo.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelRoutePreviewRequest {
    private String scene;
    private String riskLevel;
    private Integer estimatedInputTokens;
    private Integer reservedOutputTokens;
}
