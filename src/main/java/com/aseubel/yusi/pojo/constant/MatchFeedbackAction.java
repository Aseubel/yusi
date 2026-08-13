package com.aseubel.yusi.pojo.constant;

import java.util.Locale;

/** Persisted match and connection feedback action codes. */
public enum MatchFeedbackAction {
    ACCEPT("ACCEPT"),
    SKIP("SKIP"),
    INTERACT("INTERACT"),
    REPORT("REPORT"),
    UNSAFE("UNSAFE"),
    BLOCK("BLOCK"),
    LIKE("LIKE"),
    DEEP_INTERACTION("DEEP_INTERACTION"),
    DO_NOT_CONTINUE("DO_NOT_CONTINUE");

    private final String code;

    MatchFeedbackAction(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static MatchFeedbackAction fromCode(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (MatchFeedbackAction action : values()) {
            if (action.code.equals(normalized)) {
                return action;
            }
        }
        return null;
    }
}
