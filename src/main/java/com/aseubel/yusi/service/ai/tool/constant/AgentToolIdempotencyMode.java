package com.aseubel.yusi.service.ai.tool.constant;

public enum AgentToolIdempotencyMode {
    NONE("none"),
    IDEMPOTENT_WRITE("idempotent_write");

    private final String code;

    AgentToolIdempotencyMode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
