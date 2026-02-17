package com.aseubel.yusi.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    SUCCESS(200, 200, "success"),
    SYSTEM_ERROR(500, 500, "系统错误"),
    UNAUTHORIZED(401, 401, "未授权"),
    FORBIDDEN(403, 403, "禁止访问"),

    // Auth specific errors
    TOKEN_EXPIRED(40101, 401, "登录过期，请重新登陆"),
    TOKEN_INVALID(40102, 401, "登录凭证无效，请重新登陆"),
    TOKEN_MISSING(40103, 401, "登录凭证缺失，请重新登陆"),

    // Business errors
    PARAM_ERROR(400, 400, "参数错误"),
    RESOURCE_NOT_FOUND(404, 404, "资源未找到"),
    RATE_LIMIT_EXCEEDED(429, 429, "请求频率过快，请稍后再试"),

    // Device limit errors
    DEVICE_LIMIT_EXCEEDED(40104, 401, "设备登录数量超限"),

    // AI errors
    AI_REQUEST_IN_PROGRESS(42901, 429, "AI请求处理中，请稍后再试");

    private final int code;
    private final int httpStatus;
    private final String msg;
}
