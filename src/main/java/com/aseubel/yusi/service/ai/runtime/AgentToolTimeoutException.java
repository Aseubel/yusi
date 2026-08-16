package com.aseubel.yusi.service.ai.runtime;

public class AgentToolTimeoutException extends RuntimeException {

    public AgentToolTimeoutException(String toolName) {
        super("Agent tool execution timed out: " + toolName);
    }
}
