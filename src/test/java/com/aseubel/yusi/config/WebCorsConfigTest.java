package com.aseubel.yusi.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WebCorsConfigTest {

    @Test
    void allowsPatchPreflightForMemoryCenterUpdates() {
        InspectableCorsRegistry registry = new InspectableCorsRegistry();

        new WebCorsConfig("http://localhost:5173").addCorsMappings(registry);

        assertThat(registry.configurations().get("/api/**").getAllowedMethods())
                .contains("PATCH");
    }

    private static final class InspectableCorsRegistry extends CorsRegistry {

        private Map<String, CorsConfiguration> configurations() {
            return getCorsConfigurations();
        }
    }
}
