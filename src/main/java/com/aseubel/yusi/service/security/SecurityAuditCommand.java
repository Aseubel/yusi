package com.aseubel.yusi.service.security;

import com.aseubel.yusi.pojo.constant.SecurityAuditAction;
import com.aseubel.yusi.pojo.constant.SecurityAuditActorType;
import com.aseubel.yusi.pojo.constant.SecurityAuditOutcome;
import com.aseubel.yusi.pojo.constant.SecurityAuditResourceType;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Value
@Builder
public class SecurityAuditCommand {

    SecurityAuditAction action;
    SecurityAuditActorType actorType;
    String actorUserId;
    String subjectUserId;
    SecurityAuditResourceType resourceType;
    String resourceId;
    SecurityAuditOutcome outcome;
    String reasonCode;
    Map<String, String> details;
    Set<String> scopeUserIds;
    LocalDateTime occurredAt;
}
