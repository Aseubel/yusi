package com.aseubel.yusi.common.exception;

/**
 * 认证异常类
 */
public class AuthenticationException extends RuntimeException {
    public AuthenticationException(String message) {
        super(message);
    }
}
