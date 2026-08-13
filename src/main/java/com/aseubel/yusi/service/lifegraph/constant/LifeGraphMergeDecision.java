package com.aseubel.yusi.service.lifegraph.constant;

import java.util.Locale;

/** LLM merge decision values persisted for LifeGraph candidate pairs. */
public enum LifeGraphMergeDecision {
    YES("YES"),
    NO("NO");

    private final String code;

    LifeGraphMergeDecision(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static boolean isYes(String value) {
        return YES.code.equalsIgnoreCase(value == null ? null : value.trim());
    }

    public static LifeGraphMergeDecision fromCode(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (LifeGraphMergeDecision decision : values()) {
            if (decision.code.equals(normalized)) {
                return decision;
            }
        }
        return null;
    }
}
