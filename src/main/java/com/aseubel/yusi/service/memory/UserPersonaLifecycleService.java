package com.aseubel.yusi.service.memory;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.common.constant.LifecycleStatus;
import com.aseubel.yusi.common.constant.SourceType;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.pojo.constant.SecurityAuditAction;
import com.aseubel.yusi.pojo.constant.SecurityAuditActorType;
import com.aseubel.yusi.pojo.constant.SecurityAuditDetailKeys;
import com.aseubel.yusi.pojo.constant.SecurityAuditOutcome;
import com.aseubel.yusi.pojo.constant.SecurityAuditOperation;
import com.aseubel.yusi.pojo.constant.SecurityAuditResourceType;
import com.aseubel.yusi.pojo.dto.memory.PersonaMemoryItem;
import com.aseubel.yusi.pojo.dto.memory.UpdatePersonaMemoryRequest;
import com.aseubel.yusi.pojo.entity.UserPersona;
import com.aseubel.yusi.repository.UserPersonaRepository;
import com.aseubel.yusi.service.match.MatchProfileAssembler;
import com.aseubel.yusi.service.security.SecurityAuditCommand;
import com.aseubel.yusi.service.security.SecurityAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 稳定 Persona 的透明度和生命周期操作。 */
@Slf4j
@Service
public class UserPersonaLifecycleService {

    private static final Set<String> CLEARABLE_FIELDS = Set.of(
            "preferredName", "location", "interests", "tone", "customInstructions");

    private final UserPersonaRepository personaRepository;
    private final MatchProfileAssembler matchProfileAssembler;
    private final SecurityAuditService securityAuditService;

    public UserPersonaLifecycleService(UserPersonaRepository personaRepository,
            MatchProfileAssembler matchProfileAssembler) {
        this(personaRepository, matchProfileAssembler, null);
    }

    @Autowired
    public UserPersonaLifecycleService(UserPersonaRepository personaRepository,
            MatchProfileAssembler matchProfileAssembler, SecurityAuditService securityAuditService) {
        this.personaRepository = personaRepository;
        this.matchProfileAssembler = matchProfileAssembler;
        this.securityAuditService = securityAuditService;
    }

    @Transactional(readOnly = true)
    public PersonaMemoryItem get(String userId) {
        return personaRepository.findByUserId(userId)
                .map(persona -> toItem(persona, LocalDateTime.now()))
                .orElseGet(() -> emptyItem());
    }

    @Transactional
    public PersonaMemoryItem update(String userId, UpdatePersonaMemoryRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "画像修改内容不能为空");
        }

        Set<String> clearFields = validateClearFields(request.getClearFields());
        UserPersona persona = personaRepository.findByUserId(userId)
                .orElseGet(() -> UserPersona.builder().userId(userId).build());

        boolean contentChanged = applyContent(persona, request, clearFields);
        boolean lifecycleChanged = false;

        if (request.getConfidence() != null) {
            persona.setConfidence(request.getConfidence());
        }
        if (request.getMatchAllowed() != null
                && !request.getMatchAllowed().equals(Boolean.TRUE.equals(persona.getMatchAllowed()))) {
            persona.setMatchAllowed(request.getMatchAllowed());
            lifecycleChanged = true;
        }
        if (request.getHidden() != null
                && !request.getHidden().equals(Boolean.TRUE.equals(persona.getHidden()))) {
            persona.setHidden(request.getHidden());
            lifecycleChanged = true;
        }
        if (Boolean.TRUE.equals(request.getClearValidUntil())) {
            if (persona.getValidUntil() != null) {
                persona.setValidUntil(null);
                lifecycleChanged = true;
            }
        } else if (request.getValidUntil() != null
                && !request.getValidUntil().equals(persona.getValidUntil())) {
            persona.setValidUntil(request.getValidUntil());
            lifecycleChanged = true;
        }

        if (contentChanged) {
            persona.setSourceType(SourceType.USER_EDIT.code());
            persona.setSourceId(null);
            persona.setConfidence(1.0);
        }
        persona.setUserId(userId);
        persona.setUpdatedAt(LocalDateTime.now());
        UserPersona saved = personaRepository.save(persona);
        recordAudit(SecurityAuditAction.PERSONA_UPDATED, userId, saved.getId());

        if (contentChanged || lifecycleChanged) {
            refreshMatchProfile(userId);
        }
        return toItem(saved, LocalDateTime.now());
    }

    @Transactional
    public void delete(String userId) {
        personaRepository.findByUserId(userId).ifPresent(persona -> {
            personaRepository.delete(persona);
            recordAudit(SecurityAuditAction.PERSONA_DELETED, userId, persona.getId());
            refreshMatchProfile(userId);
        });
    }

    private void recordAudit(SecurityAuditAction action, String userId, Long personaId) {
        if (securityAuditService == null) {
            return;
        }
        securityAuditService.record(SecurityAuditCommand.builder()
                .action(action)
                .actorType(SecurityAuditActorType.USER)
                .actorUserId(userId)
                .subjectUserId(userId)
                .resourceType(SecurityAuditResourceType.PERSONA)
                .resourceId(personaId == null ? null : String.valueOf(personaId))
                .outcome(SecurityAuditOutcome.SUCCESS)
                .details(java.util.Map.of(SecurityAuditDetailKeys.OPERATION,
                        action == SecurityAuditAction.PERSONA_UPDATED
                                ? SecurityAuditOperation.UPDATE.name() : SecurityAuditOperation.DELETE.name()))
                .scopeUserIds(java.util.Set.of(userId))
                .build());
    }

    private Set<String> validateClearFields(List<String> fields) {
        if (fields == null || fields.isEmpty()) {
            return Set.of();
        }
        Set<String> clearFields = new HashSet<>(fields);
        if (!CLEARABLE_FIELDS.containsAll(clearFields)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "包含不支持清空的画像字段");
        }
        return clearFields;
    }

    private boolean applyContent(UserPersona persona, UpdatePersonaMemoryRequest request, Set<String> clearFields) {
        boolean changed = false;
        if (request.getPreferredName() != null || clearFields.contains("preferredName")) {
            String next = clearFields.contains("preferredName") ? null : blankToNull(request.getPreferredName());
            changed |= !java.util.Objects.equals(persona.getPreferredName(), next);
            persona.setPreferredName(next);
        }
        if (request.getLocation() != null || clearFields.contains("location")) {
            String next = clearFields.contains("location") ? null : blankToNull(request.getLocation());
            changed |= !java.util.Objects.equals(persona.getLocation(), next);
            persona.setLocation(next);
        }
        if (request.getInterests() != null || clearFields.contains("interests")) {
            String next = clearFields.contains("interests") ? null : blankToNull(request.getInterests());
            changed |= !java.util.Objects.equals(persona.getInterests(), next);
            persona.setInterests(next);
        }
        if (request.getTone() != null || clearFields.contains("tone")) {
            String next = clearFields.contains("tone") ? null : blankToNull(request.getTone());
            changed |= !java.util.Objects.equals(persona.getTone(), next);
            persona.setTone(next);
        }
        if (request.getCustomInstructions() != null || clearFields.contains("customInstructions")) {
            String next = clearFields.contains("customInstructions")
                    ? null
                    : blankToNull(request.getCustomInstructions());
            changed |= !java.util.Objects.equals(persona.getCustomInstructions(), next);
            persona.setCustomInstructions(next);
        }
        return changed;
    }

    private PersonaMemoryItem emptyItem() {
        return PersonaMemoryItem.builder()
                .sourceType(SourceType.UNKNOWN.code())
                .confidence(0.5)
                .matchAllowed(false)
                .hidden(false)
                .lifecycleStatus(LifecycleStatus.EMPTY.code())
                .build();
    }

    private PersonaMemoryItem toItem(UserPersona persona, LocalDateTime now) {
        String lifecycleStatus;
        if (Boolean.TRUE.equals(persona.getHidden())) {
            lifecycleStatus = LifecycleStatus.HIDDEN.code();
        } else if (persona.getValidUntil() != null && !persona.getValidUntil().isAfter(now)) {
            lifecycleStatus = LifecycleStatus.EXPIRED.code();
        } else {
            lifecycleStatus = LifecycleStatus.ACTIVE.code();
        }

        return PersonaMemoryItem.builder()
                .id(persona.getId())
                .preferredName(persona.getPreferredName())
                .location(persona.getLocation())
                .interests(persona.getInterests())
                .tone(persona.getTone())
                .customInstructions(persona.getCustomInstructions())
                .sourceType(StrUtil.blankToDefault(persona.getSourceType(), SourceType.UNKNOWN.code()))
                .sourceId(persona.getSourceId())
                .confidence(persona.getConfidence() == null ? 0.5 : persona.getConfidence())
                .createdAt(persona.getCreatedAt())
                .updatedAt(persona.getUpdatedAt())
                .validUntil(persona.getValidUntil())
                .matchAllowed(Boolean.TRUE.equals(persona.getMatchAllowed()))
                .hidden(Boolean.TRUE.equals(persona.getHidden()))
                .lifecycleStatus(lifecycleStatus)
                .build();
    }

    private String blankToNull(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    private void refreshMatchProfile(String userId) {
        try {
            matchProfileAssembler.refreshProfile(userId);
        } catch (Exception exception) {
            log.warn("刷新匹配画像失败: userId={}", userId, exception);
        }
    }
}
