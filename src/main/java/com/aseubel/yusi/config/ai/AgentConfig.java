package com.aseubel.yusi.config.ai;

import com.aseubel.yusi.service.ai.DiarySearchTool;
import com.aseubel.yusi.service.diary.Assistant;
import com.aseubel.yusi.service.room.SituationRoomAgent;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Agent 配置类 - 实现 Agentic RAG 模式
 * 
 * 通过 Tool Use 机制替代传统的 ContentRetriever，
 * 让 LLM 能够主动决定何时需要检索日记，并支持时间范围过滤。
 * 
 * @author Aseubel
 * @date 2025/5/9 下午11:18
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AgentConfig {

    @Autowired
    private ApplicationContext applicationContext;

    private final DiarySearchTool diarySearchTool;

    @Bean(name = "diaryAssistant")
    public Assistant diaryAssistant() throws IOException {
        ClassPathResource resource = new ClassPathResource("chat-prompt.txt");
        String systemPrompt = resource.getContentAsString(StandardCharsets.UTF_8);
        log.info("成功加载聊天助手系统提示词，长度: {} 字符", systemPrompt.length());

        // 使用 Agentic RAG 模式：通过 tools() 注册检索工具，而非 contentRetriever
        // LLM 会根据用户问题主动判断是否需要调用 searchDiary 工具
        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel((StreamingChatModel) applicationContext.getBean("streamingChatModel"))
                .tools(diarySearchTool) // 注册日记检索工具，支持时间范围过滤
                .chatMemoryProvider((ChatMemoryProvider) applicationContext.getBean("chatMemoryProvider"))
                .systemMessageProvider(chatMemoryId -> systemPrompt)
                .build();

        log.info("DiaryAssistant 已配置 Agentic RAG 模式，注册工具: DiarySearchTool");
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
