package com.aseubel.yusi.common.web;

import java.time.LocalDateTime;
import java.util.List;

/** Immutable runtime access policy used by HTTP and WebSocket entry points. */
public record RuntimeAccessPolicySnapshot(
        boolean developmentModeEnabled,
        LocalDateTime developmentModeExpiresAt,
        List<String> allowedOrigins,
        List<String> blockedOrigins,
        List<String> allowedIps,
        List<String> blockedIps,
        List<String> environmentOrigins,
        Long version,
        LocalDateTime updatedAt) {

    public RuntimeAccessPolicySnapshot {
        allowedOrigins = List.copyOf(allowedOrigins == null ? List.of() : allowedOrigins);
        blockedOrigins = List.copyOf(blockedOrigins == null ? List.of() : blockedOrigins);
        allowedIps = List.copyOf(allowedIps == null ? List.of() : allowedIps);
        blockedIps = List.copyOf(blockedIps == null ? List.of() : blockedIps);
        environmentOrigins = List.copyOf(environmentOrigins == null ? List.of() : environmentOrigins);
    }

    public boolean developmentModeActive(LocalDateTime now) {
        if (!developmentModeEnabled || developmentModeExpiresAt == null) {
            return false;
        }
        LocalDateTime effectiveNow = now == null ? LocalDateTime.now() : now;
        return effectiveNow.isBefore(developmentModeExpiresAt);
    }
}
