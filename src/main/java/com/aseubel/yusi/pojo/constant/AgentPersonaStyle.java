package com.aseubel.yusi.pojo.constant;

import java.util.Locale;

/** Persisted agent personality styles. */
public enum AgentPersonaStyle {
    GENTLE("gentle"),
    LIVELY("lively"),
    CALM("calm"),
    RATIONAL("rational");

    private final String code;

    AgentPersonaStyle(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static AgentPersonaStyle fromCode(String value) {
        if (value != null) {
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            for (AgentPersonaStyle style : values()) {
                if (style.code.equals(normalized)) {
                    return style;
                }
            }
        }
        return GENTLE;
    }
}
