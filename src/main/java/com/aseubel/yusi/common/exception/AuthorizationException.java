package com.aseubel.yusi.common.exception;

/**
 * 授权异常
 */
public class AuthorizationException extends RuntimeException {
    public AuthorizationException(String message) {
        super(message);
    }
}