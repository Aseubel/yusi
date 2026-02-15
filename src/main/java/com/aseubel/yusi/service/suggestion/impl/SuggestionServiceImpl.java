package com.aseubel.yusi.service.suggestion.impl;

import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.pojo.entity.Suggestion;
import com.aseubel.yusi.repository.SuggestionRepository;
import com.aseubel.yusi.service.suggestion.SuggestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestionServiceImpl implements SuggestionService {

    private final SuggestionRepository suggestionRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Suggestion createSuggestion(String userId, String content, String contactEmail) {
        Suggestion suggestion = Suggestion.builder()
                .userId(userId)
                .content(content)
                .contactEmail(contactEmail)
                .status("PENDING")
                .build();
        return suggestionRepository.save(suggestion);
    }

    @Override
    public Page<Suggestion> getUserSuggestions(String userId, Pageable pageable) {
        return suggestionRepository.findByUserId(userId, pageable);
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
        suggestion.setReply(reply);
        suggestion.setRepliedBy(repliedBy);
        suggestion.setRepliedAt(LocalDateTime.now());
        suggestion.setStatus("REPLIED");
        suggestionRepository.save(suggestion);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String suggestionId, String status) {
        Suggestion suggestion = getSuggestion(suggestionId);
        suggestion.setStatus(status);
        suggestionRepository.save(suggestion);
    }

    @Override
    public long getPendingCount() {
        return suggestionRepository.countByStatus("PENDING");
    }
}
