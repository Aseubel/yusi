package com.aseubel.yusi.common.constant;

import java.util.Locale;

/** Shared source codes persisted by cognition and memory records. */
public enum SourceType {
    DIARY("DIARY"),
    PLAZA("PLAZA"),
    CHAT_SUMMARY("CHAT_SUMMARY"),
    EMOTION_PLAZA("EMOTION_PLAZA"),
    USER_EDIT("USER_EDIT"),
    MANUAL("MANUAL"),
    LEGACY("LEGACY"),
    UNKNOWN("UNKNOWN");

    private final String code;

    SourceType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static SourceType fromCode(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (SourceType sourceType : values()) {
            if (sourceType.code.equals(normalized)) {
                return sourceType;
            }
        }
        return UNKNOWN;
    }
}
