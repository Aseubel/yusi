package com.aseubel.yusi.common.constant;

/** Lifecycle values exposed by the memory center. */
public enum LifecycleStatus {
    ACTIVE("ACTIVE"),
    HIDDEN("HIDDEN"),
    FORGOTTEN("FORGOTTEN"),
    MERGED("MERGED"),
    EMPTY("EMPTY");

    private final String code;

    LifecycleStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
