package com.aseubel.yusi.service.ai;

import com.aseubel.yusi.pojo.entity.PromptTemplate;
import org.springframework.data.domain.Page;

public interface PromptService {
    String getPrompt(String name);
    String getPrompt(String name, String locale);
    Page<PromptTemplate> searchPrompts(String name, String scope, String locale, Boolean active, int page, int size);
    PromptTemplate savePrompt(PromptTemplate prompt, String updatedBy);
    PromptTemplate updatePrompt(Long id, PromptTemplate prompt, String updatedBy);
    void activatePrompt(Long id, String updatedBy);
    void deletePrompt(Long id);
}
