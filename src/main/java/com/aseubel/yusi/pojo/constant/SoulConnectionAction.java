package com.aseubel.yusi.pojo.constant;

/** Actions persisted as the latest connection audit marker. */
public enum SoulConnectionAction {
    ACCEPT("ACCEPT"),
    DECLINE("DECLINE"),
    MUTUAL_RESONANCE("MUTUAL_RESONANCE"),
    END("END"),
    REPORT("REPORT"),
    BLOCK("BLOCK");

    private final String code;

    SoulConnectionAction(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
