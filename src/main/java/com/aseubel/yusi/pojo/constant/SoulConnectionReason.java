package com.aseubel.yusi.pojo.constant;

/** Default reason categories used when a connection API omits a reason. */
public enum SoulConnectionReason {
    USER_DECLINED("USER_DECLINED"),
    USER_ENDED("USER_ENDED"),
    UNSAFE("UNSAFE"),
    USER_BLOCKED("USER_BLOCKED");

    private final String code;

    SoulConnectionReason(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
