package com.aseubel.yusi.service.ai.model;

public record ModelRouteCandidate(
        String tierId,
        ModelInstance instance,
        boolean available,
        String excludedReason) {

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
}
