package com.aseubel.yusi.pojo.constant;

import java.util.Locale;

/** User diary encryption key modes. */
public enum KeyMode {
    DEFAULT("DEFAULT"),
    CUSTOM("CUSTOM");

    private final String code;

    KeyMode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static KeyMode fromCode(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (KeyMode mode : values()) {
            if (mode.code.equals(normalized)) {
                return mode;
            }
        }
        return null;
    }
}
