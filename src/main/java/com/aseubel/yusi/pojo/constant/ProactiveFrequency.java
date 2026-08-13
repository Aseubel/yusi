package com.aseubel.yusi.pojo.constant;

import java.util.Locale;

/** Persisted proactive greeting frequency values. */
public enum ProactiveFrequency {
    OFF("off"),
    LOW("low"),
    NORMAL("normal");

    private final String code;

    ProactiveFrequency(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static ProactiveFrequency fromCode(String value) {
        if (value != null) {
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            for (ProactiveFrequency frequency : values()) {
                if (frequency.code.equals(normalized)) {
                    return frequency;
                }
            }
        }
        return LOW;
    }
}
