package com.aseubel.yusi.service.ai.model;

/**
 * Conservative token budget reserved for one provider attempt.
 */
public record ModelTokenBudget(long estimatedInputTokens, long reservedOutputTokens) {

    public ModelTokenBudget {
        estimatedInputTokens = Math.max(0L, estimatedInputTokens);
        reservedOutputTokens = Math.max(0L, reservedOutputTokens);
    }

    public long totalTokens() {
        return saturatingAdd(estimatedInputTokens, reservedOutputTokens);
    }

    public static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        if (right < 0L && left < Long.MIN_VALUE - right) {
            return Long.MIN_VALUE;
        }
        return left + right;
    }
}
