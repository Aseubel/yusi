package com.aseubel.yusi.pojo.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelMetricSummary {
    private long routeCount;
    private long fallbackCount;
    private double fallbackRate;
    private double successRate;
    private double averageLatencyMs;
    private Double p95LatencyMs;
    private long rateLimitedCount;
    private long errorCount;
    private long inputTokens;
    private long outputTokens;
    private BigDecimal knownCost;
    private long unknownCostCount;
}
