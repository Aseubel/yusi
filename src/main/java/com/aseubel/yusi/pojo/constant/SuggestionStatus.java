package com.aseubel.yusi.pojo.constant;

/** User suggestion workflow statuses. */
public enum SuggestionStatus {
    PENDING("PENDING"),
    REPLIED("REPLIED");

    private final String code;

    SuggestionStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
