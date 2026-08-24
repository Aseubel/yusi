package com.aseubel.yusi.pojo.dto.admin;

import java.time.LocalDateTime;
import java.util.List;

/** Effective runtime web access policy shown in the administrator console. */
public record WebAccessPolicyResponse(
        boolean developmentModeEnabled,
        boolean developmentModeActive,
        LocalDateTime developmentModeExpiresAt,
        List<String> allowedOrigins,
        List<String> blockedOrigins,
        List<String> allowedIpRules,
        List<String> blockedIpRules,
        List<String> bootstrapOrigins,
        String currentClientIp,
        Long version,
        LocalDateTime updatedAt) {
}
