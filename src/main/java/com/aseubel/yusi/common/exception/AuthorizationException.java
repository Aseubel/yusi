package com.aseubel.yusi.common.exception;

import lombok.Getter;

/**
 * 授权异常
 */
@Getter
public class AuthorizationException extends RuntimeException {
    
    private ErrorCode errorCode;

    public AuthorizationException(String message) {
        super(message);
        this.errorCode = ErrorCode.UNAUTHORIZED;
    }
    
    public AuthorizationException(ErrorCode errorCode) {
        super(errorCode.getMsg());
        this.errorCode = errorCode;
    }

    public AuthorizationException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
