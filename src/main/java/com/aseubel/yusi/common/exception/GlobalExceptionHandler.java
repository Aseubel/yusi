package com.aseubel.yusi.common.exception;

import com.aseubel.yusi.common.Response;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Response<String> handleBusinessException(BusinessException e) {
        // Business exceptions are expected, do not log error stack trace
        setStatus(HttpServletResponse.SC_OK);
        return Response.fail(e.getMessage());
    }

    @ExceptionHandler(RateLimitException.class)
    public Response<String> handleRateLimitException(RateLimitException e) {
        setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        return Response.<String>builder().code(429).info(e.getMessage()).build();
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
        return Response.<String>builder().code(401).info(e.getMessage()).build();
    }

    @ExceptionHandler(AuthenticationException.class)
    public Response<String> handleAuthenticationException(AuthenticationException e) {
        setStatus(HttpServletResponse.SC_FORBIDDEN);
        return Response.<String>builder().code(403).info(e.getMessage()).build();
    }

    @ExceptionHandler(AiLockException.class)
    public Response<String> handleAiLockException(AiLockException e) {
        setStatus(429);
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
