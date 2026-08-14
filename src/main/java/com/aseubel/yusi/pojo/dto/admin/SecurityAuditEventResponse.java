package com.aseubel.yusi.pojo.dto.admin;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.Map;

/** Redacted administrator-facing projection of a security audit event. */
@Value
@Builder
public class SecurityAuditEventResponse {

    String eventId;
    String action;
    String actionKey;
    String actorType;
    String actorUserId;
    String subjectUserId;
    String resourceType;
    String resourceId;
    String outcome;
    String reasonCode;
    Map<String, String> details;
    LocalDateTime occurredAt;
}
