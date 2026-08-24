package com.aseubel.yusi.common.web;

import org.springframework.web.cors.CorsConfiguration;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Applies the same allow/deny precedence to HTTP and WebSocket requests. */
public class RuntimeAccessPolicyEvaluator {

    private static final List<String> DEVELOPMENT_ORIGIN_PATTERNS = List.of(
            "http://localhost:*",
            "http://127.0.0.1:*",
            "http://[::1]:*");

    public boolean isIpAllowed(RuntimeAccessPolicySnapshot policy, String clientIp, LocalDateTime now) {
        if (policy == null) {
            return true;
        }
        if (matchesIp(policy.blockedIps(), clientIp)) {
            return false;
        }
        return policy.allowedIps().isEmpty() || matchesIp(policy.allowedIps(), clientIp);
    }

    public boolean isOriginAllowed(RuntimeAccessPolicySnapshot policy, String origin, LocalDateTime now) {
        if (origin == null || origin.isBlank()) {
            return true;
        }
        if (policy == null) {
            return false;
        }
        if (matchesOrigin(policy.blockedOrigins(), origin)) {
            return false;
        }

        List<String> allowedPatterns = new ArrayList<>(policy.environmentOrigins());
        allowedPatterns.addAll(policy.allowedOrigins());
        if (policy.developmentModeActive(now)) {
            allowedPatterns.addAll(DEVELOPMENT_ORIGIN_PATTERNS);
        }
        return matchesOrigin(allowedPatterns, origin);
    }

    public List<String> developmentOriginPatterns() {
        return DEVELOPMENT_ORIGIN_PATTERNS;
    }

    private boolean matchesIp(List<String> rules, String clientIp) {
        return clientIp != null && rules.stream().anyMatch(rule -> IpRuleMatcher.matches(clientIp, rule));
    }

    private boolean matchesOrigin(List<String> patterns, String origin) {
        if (patterns.isEmpty()) {
            return false;
        }
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(patterns);
        return configuration.checkOrigin(origin) != null;
    }
}
