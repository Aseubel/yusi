package com.aseubel.yusi.common.exception;

import lombok.Getter;

/**
 * Exception thrown when an AI request is already in progress for a user
 */
@Getter
public class AiLockException extends RuntimeException {

    private final ErrorCode errorCode;

    public AiLockException() {
        super("AI请求正在处理中，请稍后再试");
        this.errorCode = ErrorCode.AI_REQUEST_IN_PROGRESS;
    }

    public AiLockException(String message) {
        super(message);
        this.errorCode = ErrorCode.AI_REQUEST_IN_PROGRESS;
    }
}
