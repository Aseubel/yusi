package com.aseubel.yusi.service.event;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Value
@Builder
public class ProductEventCommand {

    String eventName;
    Integer schemaVersion;
    String userId;
    String actorUserId;
    String sessionId;
    String runId;
    Long matchId;
    Long connectionId;
    String situationId;
    String source;
    String sensitivity;
    String idempotencyKey;
    Map<String, Object> payload;
    Set<String> scopeUserIds;
    LocalDateTime occurredAt;
}
