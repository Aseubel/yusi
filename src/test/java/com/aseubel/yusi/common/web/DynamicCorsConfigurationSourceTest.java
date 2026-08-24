package com.aseubel.yusi.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicCorsConfigurationSourceTest {

    @Test
    void echoesAnAllowedOriginAndKeepsPatchPreflightSupport() {
        RuntimeAccessPolicySnapshot policy = policy(
                false,
                List.of("https://app.aseubel.cn"),
                List.of());
        DynamicCorsConfigurationSource source = new DynamicCorsConfigurationSource(() -> policy);

        CorsConfiguration configuration = source.getCorsConfiguration(request("https://app.aseubel.cn"));

        assertThat(configuration.getAllowedOrigins()).containsExactly("https://app.aseubel.cn");
        assertThat(configuration.getAllowedMethods()).contains("PATCH");
        assertThat(configuration.getAllowedHeaders()).contains("Authorization", "Content-Type");
    }

    @Test
    void returnsNoAllowedOriginForARejectedPreflight() {
        RuntimeAccessPolicySnapshot policy = policy(
                true,
                List.of("https://app.aseubel.cn"),
                List.of("http://localhost:5174"));
        DynamicCorsConfigurationSource source = new DynamicCorsConfigurationSource(() -> policy);

        CorsConfiguration configuration = source.getCorsConfiguration(request("http://localhost:5174"));

        assertThat(configuration.getAllowedOrigins()).isEmpty();
    }

    private HttpServletRequest request(String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/user/login");
        request.addHeader("Origin", origin);
        return request;
    }

    private RuntimeAccessPolicySnapshot policy(boolean developmentMode, List<String> origins, List<String> blockedOrigins) {
        return new RuntimeAccessPolicySnapshot(
                developmentMode,
                developmentMode ? LocalDateTime.now().plusHours(1) : null,
                origins,
                blockedOrigins,
                List.of(),
                List.of(),
                List.of(),
                1L,
                LocalDateTime.now());
    }
}
