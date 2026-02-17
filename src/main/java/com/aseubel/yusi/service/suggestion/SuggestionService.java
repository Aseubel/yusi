package com.aseubel.yusi.service.suggestion;

import com.aseubel.yusi.pojo.entity.Suggestion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SuggestionService {

    Suggestion createSuggestion(String content, String contactEmail);

    Page<Suggestion> getAllSuggestions(Pageable pageable, String status);

    Suggestion getSuggestion(String suggestionId);

    void replySuggestion(String suggestionId, String reply, String repliedBy);

    void updateStatus(String suggestionId, String status);

    long getPendingCount();
}
