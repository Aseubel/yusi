package com.aseubel.yusi.config;

import com.aseubel.yusi.common.web.DynamicCorsConfigurationSource;
import com.aseubel.yusi.common.web.RuntimeAccessPolicySnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class WebCorsConfigTest {

    @Test
    void keepsPatchInTheRuntimeCorsConfiguration() {
        RuntimeAccessPolicySnapshot policy = new RuntimeAccessPolicySnapshot(
                false, null, java.util.List.of("http://localhost:5174"), java.util.List.of(),
                java.util.List.of(), java.util.List.of(), java.util.List.of(), 1L, null);
        DynamicCorsConfigurationSource source = new DynamicCorsConfigurationSource(() -> policy);
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/memory/center/1");
        request.addHeader("Origin", "http://localhost:5174");

        assertThat(source.getCorsConfiguration(request).getAllowedMethods()).contains("PATCH");
    }
}
