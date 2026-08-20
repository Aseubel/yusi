package com.aseubel.yusi.common.exception;

/**
 * 限流异常
 */
public class RateLimitException extends RuntimeException {
    private static final String PUBLIC_MESSAGE = "请求频率过快，请稍后再试";

    public RateLimitException() {
        super(PUBLIC_MESSAGE);
    }

    public RateLimitException(String ignoredMessage) {
        this();
    }
}
