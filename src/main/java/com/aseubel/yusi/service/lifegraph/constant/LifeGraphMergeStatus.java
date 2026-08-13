package com.aseubel.yusi.service.lifegraph.constant;

/** Lifecycle status of a LifeGraph merge judgment. */
public enum LifeGraphMergeStatus {
    PENDING("PENDING"),
    ACCEPTED("ACCEPTED"),
    REJECTED("REJECTED");

    private final String code;

    LifeGraphMergeStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
