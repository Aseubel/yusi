package com.aseubel.yusi.service.ai.impl;

import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.pojo.entity.PromptTemplate;
import com.aseubel.yusi.repository.PromptRepository;
import com.aseubel.yusi.service.ai.PromptService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PromptServiceImpl implements PromptService {

    @Autowired
    private PromptRepository promptRepository;

    @Override
    public String getPrompt(String name) {
        return getPrompt(name, "zh-CN");
    }

    @Override
    public String getPrompt(String name, String locale) {
        Optional<PromptTemplate> promptOpt = promptRepository
                .findTopByNameAndLocaleAndActiveTrueOrderByIsDefaultDescPriorityDescUpdatedAtDesc(name, locale);
        
        if (promptOpt.isEmpty()) {
            return null;
        }
        
        return promptOpt.map(PromptTemplate::getTemplate).orElse(null);
    }

    @Override
    public Page<PromptTemplate> searchPrompts(String name, String scope, String locale, Boolean active, int page,
            int size) {
        return promptRepository.searchPrompts(name, scope, locale, active, PageRequest.of(page, size));
    }

    @Override
    public PromptTemplate savePrompt(PromptTemplate prompt, String updatedBy) {
        String name = prompt.getName();
        String locale = prompt.getLocale() != null ? prompt.getLocale() : "zh-CN";
        if (name != null) {
            promptRepository.findByNameAndLocale(name, locale)
                    .ifPresent(existing -> {
                        throw new BusinessException(ErrorCode.PARAM_ERROR, "Prompt名称与语言已存在");
                    });
        }
        prompt.setUpdatedBy(updatedBy);
        return promptRepository.save(prompt);
    }

    @Override
    public PromptTemplate updatePrompt(Long id, PromptTemplate prompt, String updatedBy) {
        PromptTemplate existing = promptRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Prompt不存在"));
        String targetName = prompt.getName() != null ? prompt.getName() : existing.getName();
        String targetLocale = prompt.getLocale() != null ? prompt.getLocale() : existing.getLocale();
        if (targetName != null && targetLocale != null) {
            promptRepository.findByNameAndLocale(targetName, targetLocale)
                    .ifPresent(found -> {
                        if (!found.getId().equals(id)) {
                            throw new BusinessException(ErrorCode.PARAM_ERROR, "Prompt名称与语言已存在");
                        }
                    });
        }
        if (prompt.getName() != null)
            existing.setName(prompt.getName());
        if (prompt.getTemplate() != null)
            existing.setTemplate(prompt.getTemplate());
        if (prompt.getVersion() != null)
            existing.setVersion(prompt.getVersion());
        if (prompt.getActive() != null)
            existing.setActive(prompt.getActive());
        if (prompt.getScope() != null)
            existing.setScope(prompt.getScope());
        if (prompt.getLocale() != null)
            existing.setLocale(prompt.getLocale());
        if (prompt.getDescription() != null)
            existing.setDescription(prompt.getDescription());
        if (prompt.getTags() != null)
            existing.setTags(prompt.getTags());
        if (prompt.getIsDefault() != null)
            existing.setIsDefault(prompt.getIsDefault());
        if (prompt.getPriority() != null)
            existing.setPriority(prompt.getPriority());
        existing.setUpdatedBy(updatedBy);
        return promptRepository.save(existing);
    }

    @Override
    public void activatePrompt(Long id, String updatedBy) {
        PromptTemplate existing = promptRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Prompt不存在"));
        existing.setActive(true);
        existing.setUpdatedBy(updatedBy);
        promptRepository.save(existing);
    }

    @Override
    public void deletePrompt(Long id) {
        PromptTemplate existing = promptRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Prompt不存在"));
        promptRepository.delete(existing);
    }
}
