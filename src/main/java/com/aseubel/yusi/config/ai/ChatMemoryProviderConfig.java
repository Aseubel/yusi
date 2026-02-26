package com.aseubel.yusi.config.ai;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatMemory 配置
 * 
 * 注意：maxMessages 应该足够大以容纳完整的对话历史
 * SystemMessage 不计入此限制（LangChain4j 会特殊处理）
 * 
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
                .maxMessages(60)
                .chatMemoryStore(applicationContext.getBean(PersistentChatMemoryStore.class))
                .build();
        return chatMemoryProvider;
    }
}
