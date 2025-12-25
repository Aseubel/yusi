package com.aseubel.yusi.common.exception;

/**
 * 限流异常
 */
public class RateLimitException extends RuntimeException {
    public RateLimitException(String message) {
        super(message);
    }
}
