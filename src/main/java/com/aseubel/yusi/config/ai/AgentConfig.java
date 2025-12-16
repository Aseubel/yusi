package com.aseubel.yusi.config.ai;

import com.aseubel.yusi.service.diary.Assistant;
import com.aseubel.yusi.service.room.SituationRoomAgent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @author Aseubel
 * @date 2025/5/9 下午11:18
 */
@Slf4j
@Configuration
public class AgentConfig {

    @Autowired
    private ApplicationContext applicationContext;

    @Bean(name = "diaryAssistant")
    public Assistant diaryAssistant() throws IOException {
        ClassPathResource resource = new ClassPathResource("chat-prompt.txt");
        String systemPrompt = resource.getContentAsString(StandardCharsets.UTF_8);
        log.info("成功加载聊天助手系统提示词，长度: {} 字符", systemPrompt.length());

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel((StreamingChatModel) applicationContext.getBean("streamingChatModel"))
                .contentRetriever((ContentRetriever) applicationContext.getBean("contentRetriever"))
                .chatMemoryProvider((ChatMemoryProvider) applicationContext.getBean("chatMemoryProvider"))
                .systemMessageProvider(chatMemoryId -> systemPrompt)
                .build();
        return assistant;
    }

    @Bean(name = "situationRoomAgent")
    public SituationRoomAgent situationRoomAgent() throws IOException {
        ClassPathResource resource = new ClassPathResource("logic-prompt.txt");
        String systemPrompt = resource.getContentAsString(StandardCharsets.UTF_8);
        log.info("成功加载情景分析系统提示词，长度: {} 字符", systemPrompt.length());

        SituationRoomAgent agent = AiServices.builder(SituationRoomAgent.class)
                .streamingChatModel((StreamingChatModel) applicationContext.getBean("logicModel"))
                .systemMessageProvider(chatMemoryId -> systemPrompt)
                .build();
        return agent;
    }
}
