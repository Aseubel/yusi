package com.aseubel.yusi.pojo.constant;

/** Public numeric match action codes and their feedback representations. */
public enum MatchAction {
    INTERESTED(1, "ACCEPT"),
    SKIPPED(2, "SKIP");

    private final int apiCode;
    private final String feedbackCode;

    MatchAction(int apiCode, String feedbackCode) {
        this.apiCode = apiCode;
        this.feedbackCode = feedbackCode;
    }

    public int apiCode() {
        return apiCode;
    }

    public String feedbackCode() {
        return feedbackCode;
    }

    public static MatchAction fromApiCode(Integer value) {
        if (value == null) {
            return null;
        }
        for (MatchAction action : values()) {
            if (action.apiCode == value) {
                return action;
            }
        }
        return null;
    }
}
