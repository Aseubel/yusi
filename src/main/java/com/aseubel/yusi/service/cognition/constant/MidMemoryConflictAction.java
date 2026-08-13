package com.aseubel.yusi.service.cognition.constant;

import java.util.Locale;

/** Conflict decisions emitted by the mid-memory fusion prompt. */
public enum MidMemoryConflictAction {
    NONE("NONE"),
    OVERWRITE_B("OVERWRITE_B");

    private final String code;

    MidMemoryConflictAction(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static MidMemoryConflictAction fromCode(String value) {
        if (value == null) {
            return NONE;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (MidMemoryConflictAction action : values()) {
            if (action.code.equals(normalized)) {
                return action;
            }
        }
        return NONE;
    }
}
