package com.aseubel.yusi.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Builds a CORS configuration from the current runtime policy for each request. */
public class DynamicCorsConfigurationSource implements CorsConfigurationSource {

    private static final List<String> ALLOWED_METHODS = List.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    private static final List<String> ALLOWED_HEADERS = List.of(
            "Authorization", "Content-Type", "Accept", "X-Refresh-Token", "X-Old-Access-Token");

    private final Supplier<RuntimeAccessPolicySnapshot> policySupplier;
    private final RuntimeAccessPolicyEvaluator evaluator;

    public DynamicCorsConfigurationSource(Supplier<RuntimeAccessPolicySnapshot> policySupplier) {
        this(policySupplier, new RuntimeAccessPolicyEvaluator());
    }

    DynamicCorsConfigurationSource(Supplier<RuntimeAccessPolicySnapshot> policySupplier,
            RuntimeAccessPolicyEvaluator evaluator) {
        this.policySupplier = Objects.requireNonNull(policySupplier, "policySupplier");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    }

    @Override
    public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
        if (request == null || !isCorsPath(request.getRequestURI())) {
            return null;
        }

        RuntimeAccessPolicySnapshot policy = policySupplier.get();
        String origin = request.getHeader("Origin");
        CorsConfiguration configuration = new CorsConfiguration();
        if (origin != null && !evaluator.isOriginAllowed(policy, origin, LocalDateTime.now())) {
            configuration.setAllowedOrigins(List.of());
        } else if (policy != null) {
            List<String> exactOrigins = new ArrayList<>();
            List<String> originPatterns = new ArrayList<>();
            addOriginRules(exactOrigins, originPatterns, policy.environmentOrigins());
            addOriginRules(exactOrigins, originPatterns, policy.allowedOrigins());
            if (policy.developmentModeActive(LocalDateTime.now())) {
                originPatterns.addAll(evaluator.developmentOriginPatterns());
            }
            configuration.setAllowedOrigins(exactOrigins);
            configuration.setAllowedOriginPatterns(originPatterns);
        } else {
            configuration.setAllowedOrigins(List.of());
        }
        configuration.setAllowedMethods(ALLOWED_METHODS);
        configuration.setAllowedHeaders(ALLOWED_HEADERS);
        configuration.setExposedHeaders(List.of("Content-Type"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);
        return configuration;
    }

    private void addOriginRules(List<String> exactOrigins, List<String> originPatterns, List<String> rules) {
        for (String rule : rules) {
            if (rule != null && rule.contains("*")) {
                originPatterns.add(rule);
            } else if (rule != null && !rule.isBlank()) {
                exactOrigins.add(rule);
            }
        }
    }

    private boolean isCorsPath(String requestUri) {
        if (requestUri == null) {
            return false;
        }
        return requestUri.equals("/api")
                || requestUri.startsWith("/api/")
                || requestUri.equals("/ws-chat")
                || requestUri.startsWith("/ws-chat/")
                || requestUri.equals("/ws-diary-voice")
                || requestUri.startsWith("/ws-diary-voice/");
    }
}
