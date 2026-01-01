package com.aseubel.yusi.service.ai.impl;

import com.aseubel.yusi.pojo.entity.PromptTemplate;
import com.aseubel.yusi.repository.PromptRepository;
import com.aseubel.yusi.service.ai.PromptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PromptServiceImpl implements PromptService {

    @Autowired
    private PromptRepository promptRepository;

    private static final String DEFAULT_PROMPT = "你是智能日记助手，需要根据数据库中用户的日记回答用户的问题";

    @Override
    public String getPrompt(String name) {
        return promptRepository.findByNameAndActiveTrue(name)
                .map(prompt -> prompt.getTemplate())
                .orElse(DEFAULT_PROMPT);
    }

    @Override
    public PromptTemplate savePrompt(PromptTemplate prompt) {
        return promptRepository.save(prompt);
    }

    @Override
    public void activatePrompt(String name, String version) {
        // Implementation for version management (simplified)
    }
}
