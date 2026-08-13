package com.aseubel.yusi.service.ai.model.constant;

/** Stable reason codes explaining why a model route candidate is excluded. */
public enum ModelRouteExclusionReason {
    FALLBACK_TIER("fallback-tier"),
    TIER_DISABLED("TIER_DISABLED"),
    UNSUPPORTED_CAPABILITY("UNSUPPORTED_CAPABILITY"),
    SCENE_MISMATCH("SCENE_MISMATCH"),
    INPUT_TOKEN_LIMIT_EXCEEDED("INPUT_TOKEN_LIMIT_EXCEEDED"),
    CONTEXT_WINDOW_EXCEEDED("CONTEXT_WINDOW_EXCEEDED");

    private final String code;

    ModelRouteExclusionReason(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
