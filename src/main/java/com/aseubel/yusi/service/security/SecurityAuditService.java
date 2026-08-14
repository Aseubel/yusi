package com.aseubel.yusi.service.security;

import cn.hutool.core.util.IdUtil;
import com.aseubel.yusi.pojo.constant.SecurityAuditActorType;
import com.aseubel.yusi.pojo.constant.SecurityAuditDetailKeys;
import com.aseubel.yusi.pojo.constant.SecurityAuditRetention;
import com.aseubel.yusi.pojo.constant.SecurityAuditScopeRole;
import com.aseubel.yusi.pojo.entity.SecurityAuditEvent;
import com.aseubel.yusi.pojo.entity.SecurityAuditEventScope;
import com.aseubel.yusi.repository.SecurityAuditEventRepository;
import com.aseubel.yusi.repository.SecurityAuditEventScopeRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Writes and scopes low-sensitivity security audit records. */
@Service
public class SecurityAuditService {

    private static final Set<String> ALLOWED_DETAIL_KEYS = Set.of(
            SecurityAuditDetailKeys.FROM_STATUS,
            SecurityAuditDetailKeys.TO_STATUS,
            SecurityAuditDetailKeys.ACTION,
            SecurityAuditDetailKeys.REASON_CATEGORY,
            SecurityAuditDetailKeys.TASK_TYPE,
            SecurityAuditDetailKeys.FAILURE_CATEGORY,
            SecurityAuditDetailKeys.RETRY_COUNT,
            SecurityAuditDetailKeys.OPERATION,
            SecurityAuditDetailKeys.SOURCE_TYPE);
    private static final String SAFE_DETAIL_VALUE = "^[A-Za-z0-9][A-Za-z0-9._:/-]{0,63}$";

    private final SecurityAuditEventRepository eventRepository;
    private final SecurityAuditEventScopeRepository scopeRepository;
    private final ObjectMapper objectMapper;

    public SecurityAuditService(SecurityAuditEventRepository eventRepository,
            SecurityAuditEventScopeRepository scopeRepository, ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.scopeRepository = scopeRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SecurityAuditEvent record(SecurityAuditCommand command) {
        validate(command);
        LocalDateTime occurredAt = command.getOccurredAt() == null
                ? LocalDateTime.now() : command.getOccurredAt();
        SecurityAuditEvent event = SecurityAuditEvent.builder()
                .eventId(IdUtil.fastSimpleUUID())
                .action(command.getAction())
                .actorType(command.getActorType())
                .actorUserId(normalize(command.getActorUserId()))
                .subjectUserId(normalize(command.getSubjectUserId()))
                .resourceType(command.getResourceType())
                .resourceId(normalize(command.getResourceId()))
                .outcome(command.getOutcome())
                .reasonCode(normalizeReasonCode(command.getReasonCode()))
                .detailsJson(serializeDetails(command.getDetails()))
                .occurredAt(occurredAt)
                .build();
        SecurityAuditEvent saved = eventRepository.save(event);

        List<SecurityAuditEventScope> scopes = buildScopes(saved, command);
        if (!scopes.isEmpty()) {
            scopeRepository.saveAll(scopes);
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<SecurityAuditEvent> findForUser(String userId, Pageable pageable) {
        if (isBlank(userId) || pageable == null) {
            throw new IllegalArgumentException("Audit user and page are required");
        }
        return eventRepository.findAccessibleToUser(userId, pageable);
    }

    @Transactional(readOnly = true)
    public List<SecurityAuditEvent> findForAdmin(boolean authorized, Pageable pageable) {
        if (!authorized) {
            throw new SecurityException("Administrator authorization is required");
        }
        if (pageable == null) {
            throw new IllegalArgumentException("Audit page is required");
        }
        return eventRepository.findAllByOrderByOccurredAtDesc(pageable);
    }

    @Transactional
    public int cleanupExpired(LocalDateTime now) {
        LocalDateTime effectiveNow = now == null ? LocalDateTime.now() : now;
        LocalDateTime before = effectiveNow.minusDays(SecurityAuditRetention.RETENTION_DAYS);
        List<Long> expiredIds = eventRepository.findIdsByOccurredAtBefore(before);
        if (expiredIds == null || expiredIds.isEmpty()) {
            return 0;
        }
        scopeRepository.deleteByAuditEventIdIn(expiredIds);
        return eventRepository.deleteByIdIn(expiredIds);
    }

    private List<SecurityAuditEventScope> buildScopes(SecurityAuditEvent event, SecurityAuditCommand command) {
        Set<String> userIds = new LinkedHashSet<>();
        if (command.getScopeUserIds() != null) {
            command.getScopeUserIds().stream()
                    .filter(userId -> !isBlank(userId))
                    .map(String::trim)
                    .sorted()
                    .forEach(userIds::add);
        }
        if (!isBlank(command.getActorUserId())) {
            userIds.add(command.getActorUserId().trim());
        }
        if (!isBlank(command.getSubjectUserId())) {
            userIds.add(command.getSubjectUserId().trim());
        }

        List<SecurityAuditEventScope> scopes = new ArrayList<>();
        for (String userId : userIds) {
            SecurityAuditScopeRole role = scopeRole(userId, command);
            scopes.add(SecurityAuditEventScope.builder()
                    .auditEventId(event.getId())
                    .userId(userId)
                    .scopeRole(role)
                    .build());
        }
        return scopes;
    }

    private SecurityAuditScopeRole scopeRole(String userId, SecurityAuditCommand command) {
        if (userId.equals(normalize(command.getActorUserId()))) {
            return SecurityAuditScopeRole.ACTOR;
        }
        if (userId.equals(normalize(command.getSubjectUserId()))) {
            return SecurityAuditScopeRole.SUBJECT;
        }
        return SecurityAuditScopeRole.PARTICIPANT;
    }

    private String serializeDetails(Map<String, String> details) {
        Map<String, String> safeDetails = new LinkedHashMap<>();
        if (details != null) {
            details.entrySet().stream()
                    .filter(entry -> ALLOWED_DETAIL_KEYS.contains(entry.getKey()))
                    .filter(entry -> isSafeDetailValue(entry.getValue()))
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> safeDetails.put(entry.getKey(), entry.getValue()));
        }
        try {
            String json = objectMapper.writeValueAsString(safeDetails);
            if (json.length() <= SecurityAuditRetention.MAX_DETAILS_LENGTH) {
                return json;
            }
            while (!safeDetails.isEmpty() && json.length() > SecurityAuditRetention.MAX_DETAILS_LENGTH) {
                String lastKey = safeDetails.keySet().stream()
                        .max(Comparator.naturalOrder())
                        .orElseThrow();
                safeDetails.remove(lastKey);
                json = objectMapper.writeValueAsString(safeDetails);
            }
            return json;
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private void validate(SecurityAuditCommand command) {
        if (command == null || command.getAction() == null || command.getActorType() == null
                || command.getResourceType() == null || command.getOutcome() == null) {
            throw new IllegalArgumentException("Audit action, actor, resource and outcome are required");
        }
        if ((command.getActorType() == SecurityAuditActorType.USER
                || command.getActorType() == SecurityAuditActorType.ADMIN)
                && isBlank(command.getActorUserId())) {
            throw new IllegalArgumentException("User and administrator audit actors require an actor user");
        }
        validateLength(command.getActorUserId(), 64, "Audit actor");
        validateLength(command.getSubjectUserId(), 64, "Audit subject");
        validateLength(command.getResourceId(), 255, "Audit resource");
        validateLength(command.getReasonCode(), 64, "Audit reason");
        if (command.getScopeUserIds() != null) {
            command.getScopeUserIds().forEach(userId -> validateLength(userId, 64, "Audit scope user"));
        }
    }

    private boolean isSafeDetailValue(String value) {
        return value != null && value.length() <= SecurityAuditRetention.MAX_DETAIL_VALUE_LENGTH
                && value.matches(SAFE_DETAIL_VALUE);
    }

    private void validateLength(String value, int maxLength, String label) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(label + " is too long");
        }
    }

    private String normalize(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String normalizeReasonCode(String value) {
        String normalized = normalize(value);
        return isSafeDetailValue(normalized) ? normalized : null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
