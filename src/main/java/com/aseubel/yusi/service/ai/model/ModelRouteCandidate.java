package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.service.ai.model.constant.ModelRouteExclusionReason;

public record ModelRouteCandidate(
        String tierId,
        ModelInstance instance,
        boolean available,
        String excludedReason,
        ModelSelectionStrategyType strategy,
        int rank,
        boolean fallback,
        int priority,
        int weight,
        double avgLatencyMs,
        String phase,
        String exclusionExplanation) {

    public ModelRouteCandidate(String tierId, ModelInstance instance, boolean available,
            String excludedReason) {
        this(tierId, instance, available, excludedReason, null, 0,
                "fallback-tier".equals(excludedReason),
                instance == null ? 0 : instance.getPriority(),
                instance == null ? 0 : instance.getWeight(), 0D, null, null);
    }

    public ModelRouteCandidate(String tierId, ModelInstance instance, boolean available,
            String excludedReason, ModelSelectionStrategyType strategy) {
        this(tierId, instance, available, excludedReason, strategy, 0,
                "fallback-tier".equals(excludedReason),
                instance == null ? 0 : instance.getPriority(),
                instance == null ? 0 : instance.getWeight(), 0D, null, null);
    }

    public String modelId() {
        return instance == null ? null : instance.getId();
    }

    public String provider() {
        return instance == null ? null : instance.getProvider();
    }

    public String modelName() {
        return instance == null ? null : instance.getModelName();
    }

    public boolean primaryEligible() {
        return available && excludedReason == null;
    }

    public boolean attemptable() {
        return primaryEligible() || (available
                && ModelRouteExclusionReason.FALLBACK_TIER.code().equals(excludedReason));
    }
}
