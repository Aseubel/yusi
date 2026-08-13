package com.aseubel.yusi.pojo.constant;

import java.util.Locale;

/** Attachment types accepted by diary binding payloads. */
public enum DiaryAttachmentType {
    IMAGE("IMAGE"),
    AUDIO("AUDIO");

    private final String code;

    DiaryAttachmentType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static DiaryAttachmentType fromCode(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (DiaryAttachmentType type : values()) {
            if (type.code.equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}
