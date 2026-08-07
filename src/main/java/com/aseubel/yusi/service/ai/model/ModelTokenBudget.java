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
        return estimatedInputTokens + reservedOutputTokens;
    }
}
