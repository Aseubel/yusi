package com.aseubel.yusi.service.ai.model.constant;

import java.util.Locale;

/** Circuit-breaker phases persisted in model runtime state. */
public enum ModelHealthPhase {
    UP("UP"),
    HALF_OPEN("HALF_OPEN"),
    DOWN("DOWN");

    private final String code;

    ModelHealthPhase(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public boolean isProbePhase() {
        return this == HALF_OPEN;
    }

    public static ModelHealthPhase fromCode(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (ModelHealthPhase phase : values()) {
            if (phase.code.equals(normalized)) {
                return phase;
            }
        }
        return null;
    }
}
