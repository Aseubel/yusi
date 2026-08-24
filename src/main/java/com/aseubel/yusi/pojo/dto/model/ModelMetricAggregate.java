package com.aseubel.yusi.pojo.dto.model;

import java.math.BigDecimal;

public record ModelMetricAggregate(
        long callCount,
        long fallbackCount,
        double fallbackRate,
        double successRate,
        double averageLatencyMs,
        Double p95LatencyMs,
        long rateLimitedCount,
        long errorCount,
        long inputTokens,
        long outputTokens,
        BigDecimal knownCost,
        long unknownCostCount) {
}
