package com.aseubel.yusi.pojo.dto.admin;

import java.time.LocalDateTime;
import java.util.List;

/** Administrator update payload for the runtime web access policy. */
public record WebAccessPolicyUpdateRequest(
        Boolean developmentModeEnabled,
        LocalDateTime developmentModeExpiresAt,
        List<String> allowedOrigins,
        List<String> blockedOrigins,
        List<String> allowedIpRules,
        List<String> blockedIpRules) {
}
