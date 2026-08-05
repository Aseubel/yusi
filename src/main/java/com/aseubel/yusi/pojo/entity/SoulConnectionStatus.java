package com.aseubel.yusi.pojo.entity;

public enum SoulConnectionStatus {
    RECOMMENDED,
    ACCEPTED,
    WAITING_REPLY,
    STARTED,
    MUTUAL_RESONANCE,
    DECLINED,
    EXPIRED,
    ENDED,
    REPORTED,
    BLOCKED;

    public boolean isTerminal() {
        return this == DECLINED || this == EXPIRED || this == ENDED || this == BLOCKED;
    }

    public boolean allowsChat() {
        return this == STARTED || this == MUTUAL_RESONANCE;
    }
}
