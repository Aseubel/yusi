package com.aseubel.yusi.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeOriginHandshakeInterceptorTest {

    @Test
    void rejectsAWebSocketHandshakeFromABlockedOrigin() throws Exception {
        RuntimeAccessPolicySnapshot policy = new RuntimeAccessPolicySnapshot(
                false, null, List.of("https://app.aseubel.cn"), List.of("http://localhost:5174"),
                List.of(), List.of(), List.of(), 1L, LocalDateTime.now());
        HandshakeInterceptor interceptor = new RuntimeOriginHandshakeInterceptor(() -> policy);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader("Origin", "http://localhost:5174");
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        boolean allowed = interceptor.beforeHandshake(
                new ServletServerHttpRequest(servletRequest),
                new org.springframework.http.server.ServletServerHttpResponse(servletResponse),
                null,
                Map.of());

        assertThat(allowed).isFalse();
        assertThat(servletResponse.getStatus()).isEqualTo(403);
    }
}
