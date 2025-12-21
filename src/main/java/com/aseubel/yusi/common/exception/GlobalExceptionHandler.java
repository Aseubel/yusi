package com.aseubel.yusi.common.exception;

import com.aseubel.yusi.common.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Response<String> handleBusinessException(BusinessException e) {
        // Business exceptions are expected, do not log error stack trace
        return Response.fail(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Response<String> handleException(Exception e) {
        log.error("System error", e);
        return Response.fail("系统内部错误: " + e.getMessage());
    }
}
