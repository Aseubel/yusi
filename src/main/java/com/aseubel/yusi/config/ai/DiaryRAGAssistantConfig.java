package com.aseubel.yusi.config.ai;

import com.aseubel.yusi.service.ai.Assistant;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Aseubel
 * @date 2025/5/9 下午11:18
 */
@Configuration
public class DiaryRAGAssistantConfig {

    @Autowired
    private ApplicationContext applicationContext;

    @Bean(name = "diaryRAGAssistant")
    public Assistant diaryAssistantConfig() {
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel((ChatLanguageModel) applicationContext.getBean("chatModel"))
                .contentRetriever((ContentRetriever) applicationContext.getBean("contentRetriever"))
                .chatMemoryProvider((ChatMemoryProvider) applicationContext.getBean("chatMemoryProvider"))
                .build();
        return assistant;
    }
}
