package com.aseubel.yusi.service.ai.model;

public enum ModelFailureKind {
    TRANSIENT_NETWORK,
    RATE_LIMITED,
    SERVER_ERROR,
    AUTHENTICATION,
    MODEL_NOT_FOUND,
    CONTEXT_LIMIT,
    INVALID_REQUEST,
    SAFETY_REFUSAL,
    STRUCTURED_OUTPUT,
    CANCELLED,
    UNKNOWN
}
