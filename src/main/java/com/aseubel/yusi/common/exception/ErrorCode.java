package com.aseubel.yusi.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    SUCCESS(200, 200, "success"),
    SYSTEM_ERROR(500, 500, "System error"),
    UNAUTHORIZED(401, 401, "Unauthorized"),
    FORBIDDEN(403, 403, "Forbidden"),

    // Auth specific errors
    TOKEN_EXPIRED(40101, 401, "Token expired"),
    TOKEN_INVALID(40102, 401, "Token invalid"),
    TOKEN_MISSING(40103, 401, "Token missing"),

    // Business errors
    PARAM_ERROR(400, 400, "Parameter error"),
    RESOURCE_NOT_FOUND(404, 404, "Resource not found"),

    // Device limit errors
    DEVICE_LIMIT_EXCEEDED(40104, 401, "Device limit exceeded"),

    // AI errors
    AI_REQUEST_IN_PROGRESS(42901, 429, "AI request in progress");

    private final int code;
    private final int httpStatus;
    private final String msg;
}
