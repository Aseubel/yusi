package com.aseubel.yusi.config.ai.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import jakarta.annotation.PostConstruct;

/**
 * Runtime admission limits for model attempts.
 *
 * <p>All limits are fixed-window limits. A value of {@code 0} disables that
 * limit, which keeps the deployment safe to bootstrap before quota values are
 * configured.</p>
 */
@Data
@ConfigurationProperties(prefix = "model.gateway.admission", ignoreUnknownFields = false)
public class ModelGatewayAdmissionProperties {

    private boolean enabled = true;

    private String keyPrefix = "yusi:model:admission:";

    private int windowSeconds = 60;

    private int reservationTtlSeconds = 300;

    private ScopeLimit user = new ScopeLimit();

    private ScopeLimit tenant = new ScopeLimit();

    private ScopeLimit model = new ScopeLimit();

    private ScopeLimit provider = new ScopeLimit();

    @PostConstruct
    public void validate() {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException("model.gateway.admission.key-prefix must not be blank");
        }
        if (windowSeconds <= 0) {
            throw new IllegalArgumentException("model.gateway.admission.window-seconds must be positive");
        }
        if (reservationTtlSeconds <= 0) {
            throw new IllegalArgumentException(
                    "model.gateway.admission.reservation-ttl-seconds must be positive");
        }
        validateLimit("user", user);
        validateLimit("tenant", tenant);
        validateLimit("model", model);
        validateLimit("provider", provider);
    }

    public boolean hasConfiguredLimit() {
        return enabled && (hasLimit(user) || hasLimit(tenant) || hasLimit(model) || hasLimit(provider));
    }

    private boolean hasLimit(ScopeLimit limit) {
        return limit != null && (limit.maxRequests > 0 || limit.maxTokens > 0);
    }

    private void validateLimit(String name, ScopeLimit limit) {
        if (limit != null && (limit.maxRequests < 0 || limit.maxTokens < 0)) {
            throw new IllegalArgumentException(
                    "model.gateway.admission." + name + " limits cannot be negative");
        }
    }

    @Data
    public static class ScopeLimit {
        private long maxRequests;
        private long maxTokens;
    }
}
