package com.aseubel.yusi.pojo.constant;

/** Actions persisted as the latest connection audit marker. */
public enum SoulConnectionAction {
    ACCEPT("ACCEPT", "connection.accepted"),
    DECLINE("DECLINE", "connection.declined"),
    MUTUAL_RESONANCE("MUTUAL_RESONANCE", "connection.mutual_resonance"),
    END("END", "connection.ended"),
    REPORT("REPORT", "connection.reported"),
    BLOCK("BLOCK", "connection.blocked");

    private final String code;
    private final String eventName;

    SoulConnectionAction(String code, String eventName) {
        this.code = code;
        this.eventName = eventName;
    }

    public String code() {
        return code;
    }

    public String eventName() {
        return eventName;
    }
}
