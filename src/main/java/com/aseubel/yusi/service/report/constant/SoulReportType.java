package com.aseubel.yusi.service.report.constant;

import java.util.Locale;

/** Persisted soul report period types. */
public enum SoulReportType {
    WEEKLY("WEEKLY"),
    MONTHLY("MONTHLY");

    private final String code;

    SoulReportType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static SoulReportType fromCode(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (SoulReportType type : values()) {
            if (type.code.equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}
