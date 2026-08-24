package com.aseubel.yusi.pojo.dto.model;

import java.time.LocalDateTime;

public record ModelMetricBucket(
        LocalDateTime bucketStart,
        long callCount,
        long successCount,
        long errorCount,
        long fallbackCount,
        long inputTokens,
        long outputTokens,
        double averageLatencyMs) {
}
