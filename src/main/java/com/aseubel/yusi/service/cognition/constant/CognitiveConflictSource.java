package com.aseubel.yusi.service.cognition.constant;

/** Sources used when a new observation conflicts with an existing belief. */
public enum CognitiveConflictSource {
    PERSONA("PERSONA"),
    LIFEGRAPH("LIFEGRAPH");

    private final String code;

    CognitiveConflictSource(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
