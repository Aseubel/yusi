package com.aseubel.yusi.common.utils;

public final class LowSensitivityLogSummary {

    private LowSensitivityLogSummary() {
    }

    public static String lengthBucket(String value) {
        if (value == null || value.isBlank()) {
            return "empty";
        }
        int codePoints = value.codePointCount(0, value.length());
        if (codePoints <= 32) {
            return "short";
        }
        if (codePoints <= 256) {
            return "medium";
        }
        return "long";
    }

    public static String exceptionType(Throwable error) {
        if (error == null || error.getClass().getSimpleName().isBlank()) {
            return "unknown";
        }
        return error.getClass().getSimpleName();
    }
}
