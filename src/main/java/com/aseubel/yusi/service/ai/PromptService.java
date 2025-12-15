package com.aseubel.yusi.service.ai;

import com.aseubel.yusi.pojo.entity.PromptTemplate;

public interface PromptService {
    String getPrompt(String name);
    PromptTemplate savePrompt(PromptTemplate prompt);
    void activatePrompt(String name, String version);
}
