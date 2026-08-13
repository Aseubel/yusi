package com.aseubel.yusi.common.constant;

import java.util.Locale;
import java.util.Set;

/** Model trace status codes, including historical successful values. */
public enum ModelCallStatus {
    SUCCESS("SUCCESS"),
    FAILED("FAILED"),
    REJECTED("REJECTED");

    private static final Set<String> SUCCESS_CODES = Set.of(
            "SUCCESS", "SUCCEEDED", "COMPLETED", "OK");
    private static final Set<String> FAILURE_CODES = Set.of("FAILED", "FAILURE", "ERROR", "REJECTED");

    private final String code;

    ModelCallStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static boolean isSuccess(String value) {
        return value != null && SUCCESS_CODES.contains(value.trim().toUpperCase(Locale.ROOT));
    }

    public static boolean isFailure(String value) {
        return value != null && FAILURE_CODES.contains(value.trim().toUpperCase(Locale.ROOT));
    }
}
