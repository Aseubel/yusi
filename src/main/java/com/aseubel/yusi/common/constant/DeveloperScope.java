package com.aseubel.yusi.common.constant;

import java.util.Locale;

/** API key permission scopes. */
public enum DeveloperScope {
    MEMORY_READ("MEMORY_READ"),
    DIARY_WRITE("DIARY_WRITE"),
    MATCH_READ("MATCH_READ");

    private final String code;

    DeveloperScope(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static DeveloperScope fromCode(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (DeveloperScope scope : values()) {
            if (scope.code.equals(normalized)) {
                return scope;
            }
        }
        return null;
    }
}
