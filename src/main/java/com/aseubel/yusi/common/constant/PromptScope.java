package com.aseubel.yusi.common.constant;

/** Persisted prompt ownership scopes. */
public enum PromptScope {
    GLOBAL("global"),
    ROOM("room"),
    MATCH("match"),
    DIARY("diary");

    private final String code;

    PromptScope(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
