package com.aseubel.yusi.pojo.dto.model;

import com.aseubel.yusi.pojo.entity.ModelCallTrace;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelCallTraceItem {
    private LocalDateTime createdAt;
    private String requestId;
    private String attemptId;
    private String userId;
    private String scene;
    private String promptKey;
    private String promptVersion;
    private String promptLocale;
    private String policyId;
    private String routeReason;
    private String primaryTier;
    private String selectedTier;
    private String modelId;
    private String provider;
    private String modelName;
    private Long inputTokens;
    private Long outputTokens;
    private Long cachedTokens;
    private BigDecimal cost;
    private Long latencyMs;
    private Long ttftMs;
    private Integer retryIndex;
    private Boolean fallbackUsed;
    private String status;
    private String errorCode;
    private String finishReason;

    public static ModelCallTraceItem from(ModelCallTrace trace) {
        return ModelCallTraceItem.builder()
                .createdAt(trace.getCreatedAt())
                .requestId(trace.getRequestId())
                .attemptId(trace.getAttemptId())
                .userId(trace.getUserId())
                .scene(trace.getScene())
                .promptKey(trace.getPromptKey())
                .promptVersion(trace.getPromptVersion())
                .promptLocale(trace.getPromptLocale())
                .policyId(trace.getPolicyId())
                .routeReason(trace.getRouteReason())
                .primaryTier(trace.getPrimaryTier())
                .selectedTier(trace.getSelectedTier())
                .modelId(trace.getModelId())
                .provider(trace.getProvider())
                .modelName(trace.getModelName())
                .inputTokens(trace.getInputTokens())
                .outputTokens(trace.getOutputTokens())
                .cachedTokens(trace.getCachedTokens())
                .cost(trace.getCost())
                .latencyMs(trace.getLatencyMs())
                .ttftMs(trace.getTtftMs())
                .retryIndex(trace.getRetryIndex())
                .fallbackUsed(trace.getFallbackUsed())
                .status(trace.getStatus())
                .errorCode(trace.getErrorCode())
                .finishReason(trace.getFinishReason())
                .build();
    }
}
