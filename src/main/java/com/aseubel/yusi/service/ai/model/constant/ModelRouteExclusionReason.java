package com.aseubel.yusi.service.ai.model.constant;

/** Stable reason codes explaining why a model route candidate is excluded. */
public enum ModelRouteExclusionReason {
    FALLBACK_TIER("fallback-tier"),
    TIER_DISABLED("TIER_DISABLED"),
    TIER_CAPABILITY_MISMATCH("TIER_CAPABILITY_MISMATCH"),
    UNSUPPORTED_CAPABILITY("UNSUPPORTED_CAPABILITY"),
    SCENE_MISMATCH("SCENE_MISMATCH"),
    MODEL_DISABLED("MODEL_DISABLED"),
    MODEL_UNKNOWN("MODEL_UNKNOWN"),
    ZERO_WEIGHT("ZERO_WEIGHT"),
    INPUT_TOKEN_LIMIT_EXCEEDED("INPUT_TOKEN_LIMIT_EXCEEDED"),
    CONTEXT_WINDOW_EXCEEDED("CONTEXT_WINDOW_EXCEEDED"),
    MODEL_NOT_REGISTERED("MODEL_NOT_REGISTERED");

    private final String code;

    ModelRouteExclusionReason(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
