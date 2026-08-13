package com.aseubel.yusi.common.constant;

import java.util.Locale;

/** Persisted chat message roles shared by memory storage and retrieval. */
public enum ChatMessageRole {
    USER("USER"),
    AI("AI"),
    SYSTEM("SYSTEM"),
    TOOL("TOOL");

    private final String code;

    ChatMessageRole(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static ChatMessageRole fromCode(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (ChatMessageRole role : values()) {
            if (role.code.equals(normalized)) {
                return role;
            }
        }
        return null;
    }
}
