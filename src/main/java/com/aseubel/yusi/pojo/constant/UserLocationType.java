package com.aseubel.yusi.pojo.constant;

/** User-defined location categories persisted by the location feature. */
public enum UserLocationType {
    FREQUENT("FREQUENT"),
    IMPORTANT("IMPORTANT");

    private final String code;

    UserLocationType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
