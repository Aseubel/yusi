package com.aseubel.yusi.service.ai.tool.constant;

public enum AgentToolAccessMode {
    READ("read"),
    WRITE("write"),
    UNKNOWN("unknown");

    private final String code;

    AgentToolAccessMode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
