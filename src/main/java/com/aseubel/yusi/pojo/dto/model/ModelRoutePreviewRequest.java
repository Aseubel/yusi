package com.aseubel.yusi.pojo.dto.model;

import com.fasterxml.jackson.databind.JsonNode;
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
    /** Schema v2 draft projection; secret fields are rejected by the service. */
    private JsonNode draft;
}
