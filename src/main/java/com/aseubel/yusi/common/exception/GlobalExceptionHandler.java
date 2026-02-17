package com.aseubel.yusi.common.exception;

import com.aseubel.yusi.common.Response;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Objects;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Response<String> handleBusinessException(BusinessException e) {
        // Business exceptions are expected, do not log error stack trace
        setStatus(HttpServletResponse.SC_OK);
        ErrorCode ec = e.getErrorCode();
        int code = ec != null ? ec.getCode() : 500;
        return Response.<String>builder()
                .code(code)
                .info(e.getMessage())
                .build();
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Response<String> handleNoResourceFoundException(NoResourceFoundException e) {
        setStatus(HttpServletResponse.SC_NOT_FOUND);
        return Response.<String>builder().code(ErrorCode.RESOURCE_NOT_FOUND.getCode()).info(e.getMessage()).build();
    }

    @ExceptionHandler(RateLimitException.class)
    public Response<String> handleRateLimitException(RateLimitException e) {
        setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        return Response.<String>builder().code(ErrorCode.RATE_LIMIT_EXCEEDED.getCode()).info(e.getMessage()).build();
    }

    @ExceptionHandler(Exception.class)
    public Response<String> handleException(Exception e) {
        log.error("System error", e);
        setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        return Response.fail("系统内部错误: " + e.getMessage());
    }

    @ExceptionHandler(AuthorizationException.class)
    public Response<String> handleAuthorizationException(AuthorizationException e) {
        setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        return Response.<String>builder().code(ErrorCode.UNAUTHORIZED.getCode()).info(e.getMessage()).build();
    }

    @ExceptionHandler(AuthenticationException.class)
    public Response<String> handleAuthenticationException(AuthenticationException e) {
        setStatus(HttpServletResponse.SC_FORBIDDEN);
        return Response.<String>builder().code(ErrorCode.FORBIDDEN.getCode()).info(e.getMessage()).build();
    }

    @ExceptionHandler(AiLockException.class)
    public Response<String> handleAiLockException(AiLockException e) {
        setStatus(ErrorCode.AI_REQUEST_IN_PROGRESS.getHttpStatus());
        return Response.<String>builder()
                .code(ErrorCode.AI_REQUEST_IN_PROGRESS.getCode())
                .info(e.getMessage())
                .build();
    }

    private void setStatus(int code) {
        HttpServletResponse response = ((ServletRequestAttributes) Objects
                .requireNonNull(RequestContextHolder.getRequestAttributes())).getResponse();
        if (response != null) {
            response.setStatus(code);
        }
    }
}
