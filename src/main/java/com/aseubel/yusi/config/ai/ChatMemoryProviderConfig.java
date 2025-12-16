package com.aseubel.yusi.config.ai;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Aseubel
 * @date 2025/5/9 下午11:32
 */
@Slf4j
@Configuration
public class ChatMemoryProviderConfig {

    @Autowired
    private ApplicationContext applicationContext;

    @Bean(name = "chatMemoryProvider")
    public ChatMemoryProvider chatMemoryProviderConfig() {
        ChatMemoryProvider chatMemoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(10)
                .chatMemoryStore(applicationContext.getBean(PersistentChatMemoryStore.class))
                .build();
        return chatMemoryProvider;
    }
}
