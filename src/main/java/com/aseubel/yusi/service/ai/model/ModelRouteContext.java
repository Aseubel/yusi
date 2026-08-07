package com.aseubel.yusi.service.ai.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ModelRouteContext {
    String requestId;
    String runId;
    String userId;
    String language;
    String scene;
    String riskLevel;
    Integer estimatedInputTokens;
    Integer reservedOutputTokens;
    @Builder.Default
    boolean maskSensitiveData = true;
}
