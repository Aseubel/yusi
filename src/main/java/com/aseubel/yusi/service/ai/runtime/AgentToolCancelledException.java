package com.aseubel.yusi.service.ai.runtime;

public class AgentToolCancelledException extends RuntimeException {

    public AgentToolCancelledException(String toolName) {
        super("Agent tool execution cancelled: " + toolName);
    }
}
