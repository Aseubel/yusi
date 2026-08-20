package com.aseubel.yusi.common.ratelimit;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitResponseContractTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void rateLimitResponseUsesOnlyTheFixedPublicCodeAndMessage() {
        Response<String> response = new GlobalExceptionHandler()
                .handleRateLimitException(new com.aseubel.yusi.common.exception.RateLimitException(
                        "fixture-query-rate backend=redis key=fixture-object-key-rate"));

        assertThat(response.getCode()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED.getCode());
        assertThat(response.getInfo()).isEqualTo("请求频率过快，请稍后再试");
        assertThat(response.getInfo()).doesNotContain(
                "fixture-user-rate", "fixture-query-rate", "fixture-content-rate",
                "fixture-token-rate", "fixture-object-key-rate", "redis", "backend");
    }

    @Test
    void uncommittedSseResponseStillGetsTheFixed429Contract() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType("text/event-stream");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));

        Response<String> result = new GlobalExceptionHandler().handleRateLimitException(
                new com.aseubel.yusi.common.exception.RateLimitException(
                        "fixture-query-rate backend=redis key=fixture-object-key-rate"));

        assertThat(result.getCode()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED.getCode());
        assertThat(result.getInfo()).isEqualTo("请求频率过快，请稍后再试");
        assertThat(response.getStatus()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED.getHttpStatus());
    }
}
