package com.aseubel.yusi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Central CORS policy for browser clients. */
@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    private final String allowedOrigin;

    public WebCorsConfig(@Value("${yusi.web.allowed-origin:http://localhost:5173}") String allowedOrigin) {
        this.allowedOrigin = allowedOrigin;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = StringUtils.commaDelimitedListToStringArray(allowedOrigin);
        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Accept", "X-Refresh-Token",
                        "X-Old-Access-Token")
                .exposedHeaders("Content-Type")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
