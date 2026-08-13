package com.aseubel.yusi.pojo.constant;

import java.util.Locale;

/** Categories emitted by cognition routing for mid-term memory retention. */
public enum MidMemoryCategory {
    EMOTION_OR_STATE("EMOTION_OR_STATE"),
    EVENT_OR_PLAN("EVENT_OR_PLAN"),
    PREFERENCE_OR_HABIT("PREFERENCE_OR_HABIT");

    private final String code;

    MidMemoryCategory(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static MidMemoryCategory fromCode(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (MidMemoryCategory category : values()) {
            if (category.code.equals(normalized)) {
                return category;
            }
        }
        return null;
    }
}
