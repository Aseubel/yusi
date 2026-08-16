package com.aseubel.yusi.service.ai.runtime;

public final class AgentToolIdempotencyBlockedException extends RuntimeException {

    public AgentToolIdempotencyBlockedException(String response) {
        super(response);
    }

    public String response() {
        return getMessage();
    }
}
