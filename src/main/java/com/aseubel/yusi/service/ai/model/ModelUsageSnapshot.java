package com.aseubel.yusi.service.ai.model;

import java.math.BigDecimal;

public record ModelUsageSnapshot(
        Long inputTokens,
        Long outputTokens,
        Long cachedTokens,
        String finishReason,
        BigDecimal cost,
        String priceVersion,
        String usageSource) {

    public static ModelUsageSnapshot unavailable(String priceVersion) {
        return new ModelUsageSnapshot(null, null, null, null, null, priceVersion, "unavailable");
    }
}
