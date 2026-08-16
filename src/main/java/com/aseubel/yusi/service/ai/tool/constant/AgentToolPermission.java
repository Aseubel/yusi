package com.aseubel.yusi.service.ai.tool.constant;

public enum AgentToolPermission {
    MEMORY_READ("memory.read"),
    PERSONA_WRITE("persona.write"),
    NETWORK_READ("network.read"),
    TOOL_EXECUTE("tool.execute");

    private final String code;

    AgentToolPermission(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
