package com.aseubel.yusi.service.suggestion.impl;

import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.pojo.entity.Suggestion;
import com.aseubel.yusi.pojo.constant.SecurityAuditAction;
import com.aseubel.yusi.pojo.constant.SecurityAuditDetailKeys;
import com.aseubel.yusi.pojo.constant.SecurityAuditOperation;
import com.aseubel.yusi.pojo.constant.SecurityAuditOutcome;
import com.aseubel.yusi.pojo.constant.SecurityAuditReasonCode;
import com.aseubel.yusi.pojo.constant.SecurityAuditResourceType;
import com.aseubel.yusi.pojo.constant.SuggestionStatus;
import com.aseubel.yusi.repository.SuggestionRepository;
import com.aseubel.yusi.service.security.SecurityAuditService;
import com.aseubel.yusi.service.suggestion.SuggestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestionServiceImpl implements SuggestionService {

    private final SuggestionRepository suggestionRepository;
    private final SecurityAuditService securityAuditService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Suggestion createSuggestion(String content, String contactEmail) {
        Suggestion suggestion = Suggestion.builder()
                .content(content)
                .contactEmail(contactEmail)
                .status(SuggestionStatus.PENDING.code())
                .build();
        return suggestionRepository.save(suggestion);
    }

    @Override
    public Page<Suggestion> getAllSuggestions(Pageable pageable, String status) {
        if (status != null && !status.isEmpty() && !"ALL".equals(status)) {
            return suggestionRepository.findByStatus(status, pageable);
        }
        return suggestionRepository.findAll(pageable);
    }

    @Override
    public Suggestion getSuggestion(String suggestionId) {
        Suggestion suggestion = suggestionRepository.findBySuggestionId(suggestionId);
        if (suggestion == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Suggestion not found");
        }
        return suggestion;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replySuggestion(String suggestionId, String reply, String repliedBy) {
        Suggestion suggestion = getSuggestion(suggestionId);
        String previousStatus = suggestion.getStatus();
        suggestion.setReply(reply);
        suggestion.setRepliedBy(repliedBy);
        suggestion.setRepliedAt(LocalDateTime.now());
        suggestion.setStatus(SuggestionStatus.REPLIED.code());
        suggestionRepository.save(suggestion);
        recordAdminAudit(SecurityAuditAction.SUGGESTION_REPLIED, repliedBy, suggestionId,
                SecurityAuditOperation.REPLY, previousStatus, suggestion.getStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String suggestionId, String status, String repliedBy, LocalDateTime repliedAt) {
        Suggestion suggestion = getSuggestion(suggestionId);
        String previousStatus = suggestion.getStatus();
        suggestion.setStatus(status);
        if (repliedBy != null) {
            suggestion.setRepliedBy(repliedBy);
        }
        if (repliedAt != null) {
            suggestion.setRepliedAt(repliedAt);
        }
        suggestionRepository.save(suggestion);
        recordAdminAudit(SecurityAuditAction.SUGGESTION_STATUS_UPDATED, repliedBy, suggestionId,
                SecurityAuditOperation.STATUS_CHANGE, previousStatus, status);
    }

    @Override
    public long getPendingCount() {
        return suggestionRepository.countByStatus(SuggestionStatus.PENDING.code());
    }

    private void recordAdminAudit(SecurityAuditAction action, String adminUserId, String suggestionId,
            SecurityAuditOperation operation, String fromStatus, String toStatus) {
        if (securityAuditService == null || adminUserId == null || adminUserId.isBlank()) {
            return;
        }
        securityAuditService.recordAdmin(action, adminUserId, null,
                SecurityAuditResourceType.SUGGESTION, suggestionId, SecurityAuditOutcome.SUCCESS,
                SecurityAuditReasonCode.ADMIN_MUTATION,
                Map.of(
                        SecurityAuditDetailKeys.OPERATION, operation.name(),
                        SecurityAuditDetailKeys.FROM_STATUS, fromStatus == null ? "UNKNOWN" : fromStatus,
                        SecurityAuditDetailKeys.TO_STATUS, toStatus == null ? "UNKNOWN" : toStatus));
    }
}
