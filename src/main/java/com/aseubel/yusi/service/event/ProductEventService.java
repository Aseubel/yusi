package com.aseubel.yusi.service.event;

import cn.hutool.core.util.IdUtil;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.pojo.constant.ProductEventName;
import com.aseubel.yusi.pojo.constant.ProductEventSensitivity;
import com.aseubel.yusi.pojo.constant.ProductEventSource;
import com.aseubel.yusi.pojo.entity.ProductEvent;
import com.aseubel.yusi.pojo.entity.ProductEventScope;
import com.aseubel.yusi.repository.ProductEventRepository;
import com.aseubel.yusi.repository.ProductEventScopeRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes and scopes durable product events. This service deliberately accepts
 * only a small allow-list of event names and payload keys.
 */
@Service
@RequiredArgsConstructor
public class ProductEventService {

    private static final int MAX_PAYLOAD_LENGTH = 2048;
    private static final Set<ProductEventSource> VALID_SOURCES =
            Collections.unmodifiableSet(EnumSet.allOf(ProductEventSource.class));
    private static final Set<ProductEventSensitivity> VALID_SENSITIVITIES =
            Collections.unmodifiableSet(EnumSet.allOf(ProductEventSensitivity.class));
    private static final Map<String, Set<String>> PAYLOAD_KEYS = payloadKeys();

    private final ProductEventRepository eventRepository;
    private final ProductEventScopeRepository scopeRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public ProductEvent record(ProductEventCommand command) {
        validateCommand(command);

        ProductEvent existing = eventRepository.findByIdempotencyKey(command.getIdempotencyKey())
                .orElse(null);
        if (existing != null) {
            validateReplay(existing, command);
            return existing;
        }

        ProductEvent event = ProductEvent.builder()
                .eventId(IdUtil.fastSimpleUUID())
                .eventName(command.getEventName())
                .schemaVersion(command.getSchemaVersion() == null ? 1 : command.getSchemaVersion())
                .userId(command.getUserId())
                .actorUserId(command.getActorUserId())
                .sessionId(command.getSessionId())
                .runId(command.getRunId())
                .matchId(command.getMatchId())
                .connectionId(command.getConnectionId())
                .situationId(command.getSituationId())
                .source(command.getSource())
                .sensitivity(command.getSensitivity())
                .idempotencyKey(command.getIdempotencyKey())
                .payloadJson(serializePayload(command.getPayload()))
                .occurredAt(command.getOccurredAt() == null ? LocalDateTime.now() : command.getOccurredAt())
                .build();
        ProductEvent saved = eventRepository.save(event);

        List<ProductEventScope> scopes = new ArrayList<>();
        for (String scopeUserId : command.getScopeUserIds()) {
            scopes.add(ProductEventScope.builder()
                    .eventId(saved.getEventId())
                    .userId(scopeUserId)
                    .scopeRole(scopeUserId.equals(command.getActorUserId()) ? "ACTOR" : "PARTICIPANT")
                    .build());
        }
        scopeRepository.saveAll(scopes);
        return saved;
    }

    @Transactional(readOnly = true)
    public ProductEvent requireAccessible(String eventId, String userId) {
        if (eventId == null || eventId.isBlank() || userId == null || userId.isBlank()
                || !scopeRepository.existsByEventIdAndUserId(eventId, userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该产品事件");
        }
        return eventRepository.findByEventId(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "产品事件不存在"));
    }

    private void validateCommand(ProductEventCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Product event command is required");
        }
        if (command.getEventName() == null || command.getEventName().isBlank()
                || !isKnownEventName(command.getEventName())) {
            throw new IllegalArgumentException("Unsupported product event name");
        }
        if (command.getUserId() == null || command.getUserId().isBlank()) {
            throw new IllegalArgumentException("Product event user is required");
        }
        if (command.getActorUserId() != null && command.getActorUserId().isBlank()) {
            throw new IllegalArgumentException("Product event actor is invalid");
        }
        if (command.getSource() == null || VALID_SOURCES.stream()
                .noneMatch(source -> source.code().equals(command.getSource()))) {
            throw new IllegalArgumentException("Unsupported product event source");
        }
        if (command.getSensitivity() == null || !isKnownSensitivity(command.getSensitivity())) {
            throw new IllegalArgumentException("Unsupported product event sensitivity");
        }
        if (command.getIdempotencyKey() == null || command.getIdempotencyKey().isBlank()
                || command.getIdempotencyKey().length() > 160) {
            throw new IllegalArgumentException("Product event idempotency key is required");
        }
        if (command.getScopeUserIds() == null || command.getScopeUserIds().isEmpty()
                || !command.getScopeUserIds().contains(command.getUserId())) {
            throw new IllegalArgumentException("Product event scope must include its user");
        }
        if (command.getActorUserId() != null && !command.getScopeUserIds().contains(command.getActorUserId())) {
            throw new IllegalArgumentException("Product event scope must include its actor");
        }
        for (String scopeUserId : command.getScopeUserIds()) {
            if (scopeUserId == null || scopeUserId.isBlank()) {
                throw new IllegalArgumentException("Product event scope contains an invalid user");
            }
        }
        if (command.getSchemaVersion() != null && command.getSchemaVersion() < 1) {
            throw new IllegalArgumentException("Product event schema version is invalid");
        }
        validatePayloadKeys(command.getEventName(), command.getPayload());
    }

    private void validateReplay(ProductEvent existing, ProductEventCommand command) {
        if (!command.getEventName().equals(existing.getEventName())
                || !command.getUserId().equals(existing.getUserId())
                || !command.getSource().equals(existing.getSource())) {
            throw new IllegalArgumentException("Idempotency key belongs to another product event");
        }
    }

    private String serializePayload(Map<String, Object> payload) {
        Map<String, Object> safePayload = payload == null ? Map.of() : new HashMap<>(payload);
        try {
            String json = objectMapper.writeValueAsString(safePayload);
            if (json.length() > MAX_PAYLOAD_LENGTH) {
                throw new IllegalArgumentException("Product event payload is too large");
            }
            return json;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Product event payload is not serializable", exception);
        }
    }

    private void validatePayloadKeys(String eventName, Map<String, Object> payload) {
        if (payload == null) {
            return;
        }
        Set<String> allowedKeys = PAYLOAD_KEYS.getOrDefault(eventName, Set.of());
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (!allowedKeys.contains(entry.getKey())) {
                throw new IllegalArgumentException("Unsupported or sensitive product event payload key: "
                        + entry.getKey());
            }
            if (entry.getValue() instanceof String value && value.length() > 256) {
                throw new IllegalArgumentException("Product event payload value is too large");
            }
        }
    }

    private boolean isKnownEventName(String eventName) {
        for (ProductEventName name : ProductEventName.values()) {
            if (name.value().equals(eventName)) {
                return true;
            }
        }
        return false;
    }

    private boolean isKnownSensitivity(String sensitivity) {
        for (ProductEventSensitivity value : VALID_SENSITIVITIES) {
            if (value.name().equals(sensitivity)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Set<String>> payloadKeys() {
        Map<String, Set<String>> keys = new HashMap<>();
        keys.put("match.recommended", Set.of("reasonCount", "profileVersion"));
        keys.put("connection.accepted", Set.of("fromStatus", "toStatus", "action", "reasonCategory"));
        keys.put("connection.declined", Set.of("fromStatus", "toStatus", "action", "reasonCategory"));
        keys.put("connection.mutual_resonance", Set.of("fromStatus", "toStatus", "action", "reasonCategory"));
        keys.put("connection.ended", Set.of("fromStatus", "toStatus", "action", "reasonCategory"));
        keys.put("connection.reported", Set.of("fromStatus", "toStatus", "action", "reasonCategory"));
        keys.put("connection.blocked", Set.of("fromStatus", "toStatus", "action", "reasonCategory"));
        keys.put("connection.feedback_submitted", Set.of("feedbackCategory"));
        keys.put("notification.created", Set.of("notificationId", "notificationType", "refType", "refId"));
        keys.put("notification.announcement_published", Set.of("announcementId", "recipientCount"));
        keys.put("chat.message_created", Set.of("messageId", "matchId", "connectionId"));
        return Collections.unmodifiableMap(keys);
    }
}
