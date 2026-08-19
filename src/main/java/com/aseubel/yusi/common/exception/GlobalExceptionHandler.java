package com.aseubel.yusi.common.exception;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.utils.LowSensitivityLogSummary;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Response<String> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        if (isStreamingResponse()) {
            return null;
        }
        setStatus(HttpServletResponse.SC_OK);
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("参数验证失败");
        return Response.<String>builder()
                .code(ErrorCode.PARAM_ERROR.getCode())
                .info(message)
                .build();
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Response<String> handleConstraintViolationException(ConstraintViolationException e) {
        if (isStreamingResponse()) {
            return null;
        }
        setStatus(HttpServletResponse.SC_OK);
        String message = e.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .findFirst()
                .orElse("参数验证失败");
        return Response.<String>builder()
                .code(ErrorCode.PARAM_ERROR.getCode())
                .info(message)
                .build();
    }

    @ExceptionHandler(BusinessException.class)
    public Response<String> handleBusinessException(BusinessException e) {
        if (isStreamingResponse()) {
            return null;
        }
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
        if (isStreamingResponse()) {
            return null;
        }
        setStatus(HttpServletResponse.SC_NOT_FOUND);
        return Response.<String>builder().code(ErrorCode.RESOURCE_NOT_FOUND.getCode()).info(e.getMessage()).build();
    }

    @ExceptionHandler(RateLimitException.class)
    public Response<String> handleRateLimitException(RateLimitException e) {
        if (isStreamingResponse()) {
            return null;
        }
        setStatus(ErrorCode.RATE_LIMIT_EXCEEDED.getHttpStatus());
        return Response.<String>builder().code(ErrorCode.RATE_LIMIT_EXCEEDED.getCode()).info(e.getMessage()).build();
    }

    @ExceptionHandler(Exception.class)
    public Response<String> handleException(Exception e) {
        if (isStreamingResponse()) {
            log.debug("Ignoring exception after SSE response started: operation=sse_after_commit, exceptionType={}",
                    LowSensitivityLogSummary.exceptionType(e));
            return null;
        }
        log.error("Unhandled HTTP exception: operation=global_unhandled_exception, status=500, exceptionType={}",
                LowSensitivityLogSummary.exceptionType(e));
        setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        return Response.fail("系统内部错误: " + e.getMessage());
    }

    @ExceptionHandler(AuthorizationException.class)
    public Response<String> handleAuthorizationException(AuthorizationException e) {
        if (isStreamingResponse()) {
            return null;
        }
        ErrorCode ec = e.getErrorCode();
        setStatus(ec != null ? ec.getHttpStatus() : HttpServletResponse.SC_UNAUTHORIZED);
        int code = ec != null ? ec.getCode() : ErrorCode.UNAUTHORIZED.getCode();
        return Response.<String>builder().code(code).info(e.getMessage()).build();
    }

    @ExceptionHandler(AuthenticationException.class)
    public Response<String> handleAuthenticationException(AuthenticationException e) {
        if (isStreamingResponse()) {
            return null;
        }
        setStatus(HttpServletResponse.SC_FORBIDDEN);
        return Response.<String>builder().code(ErrorCode.FORBIDDEN.getCode()).info(e.getMessage()).build();
    }

    @ExceptionHandler(AiLockException.class)
    public Response<String> handleAiLockException(AiLockException e) {
        if (isStreamingResponse()) {
            return null;
        }
        setStatus(ErrorCode.AI_REQUEST_IN_PROGRESS.getHttpStatus());
        return Response.<String>builder()
                .code(ErrorCode.AI_REQUEST_IN_PROGRESS.getCode())
                .info(e.getMessage())
                .build();
    }

    private void setStatus(int code) {
        HttpServletResponse response = currentResponse();
        if (response != null) {
            response.setStatus(code);
        }
    }

    private boolean isStreamingResponse() {
        HttpServletResponse response = currentResponse();
        if (response == null) {
            return false;
        }
        String contentType = response.getContentType();
        return response.isCommitted()
                || (contentType != null && contentType.startsWith("text/event-stream"));
    }

    private HttpServletResponse currentResponse() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        return attributes.getResponse();
    }
}
