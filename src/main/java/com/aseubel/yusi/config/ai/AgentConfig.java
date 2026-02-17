package com.aseubel.yusi.config.ai;

import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.service.ai.ContextBuilderService;
import com.aseubel.yusi.service.ai.DiarySearchTool;
import com.aseubel.yusi.service.ai.LifeGraphTool;
import com.aseubel.yusi.service.ai.PromptService;
import com.aseubel.yusi.service.diary.Assistant;
import com.aseubel.yusi.service.plaza.EmotionAnalyzer;
import com.aseubel.yusi.service.room.SituationRoomAgent;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * Agent 配置类 - 实现 Agentic RAG 模式 + MCP 集成
 * 
 * 通过 Tool Use 机制替代传统的 ContentRetriever，
 * 让 LLM 能够主动决定何时需要检索日记，并支持时间范围过滤。
 * 
 * 当 MCP 启用时，还会集成外部 MCP Server 提供的工具（如 web_search）。
 * 
 * 用户隔离机制：
 * - ChatMemory 通过 memoryId (userId) 隔离对话历史
 * - SystemMessage 通过 memoryId 动态注入用户画像
 * - Tool 通过 UserContext (ThreadLocal) 获取当前用户ID
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

    private final PromptService promptService;

    @Value("${mcp.enabled:false}")
    private boolean mcpEnabled;

    @Bean(name = "diaryAssistant")
    public Assistant diaryAssistant() {
        log.info("正在配置 DiaryAssistant (Singleton)");

        // 构建 AiServices - 单例模式，用户隔离通过 memoryId 和 UserContext 实现
        AiServices<Assistant> builder = AiServices.builder(Assistant.class)
                .streamingChatModel((StreamingChatModel) applicationContext.getBean("streamingChatModel"))
                .tools(
                        applicationContext.getBean(DiarySearchTool.class),
                        applicationContext.getBean(LifeGraphTool.class)
                )
                .chatMemoryProvider((ChatMemoryProvider) applicationContext.getBean("chatMemoryProvider"))
                .systemMessageProvider(memoryId -> 
                        applicationContext.getBean(ContextBuilderService.class).buildSystemMessage(memoryId));

        // 如果 MCP 启用，添加 MCP Tool Provider
        if (mcpEnabled) {
            try {
                ToolProvider mcpToolProvider = (ToolProvider) applicationContext.getBean("mcpToolProvider");
                builder.toolProvider(mcpToolProvider);
                log.info("DiaryAssistant 已集成 MCP Tool Provider，可使用外部工具（如 web_search）");
            } catch (Exception e) {
                log.warn("MCP 已启用但无法获取 mcpToolProvider Bean: {}，将仅使用本地工具", e.getMessage());
            }
        }

        Assistant assistant = builder.build();
        log.info("DiaryAssistant (Singleton) 已配置完成，支持多用户隔离");
        return assistant;
    }

    @Bean(name = "situationRoomAgent")
    public SituationRoomAgent situationRoomAgent() throws IOException {
        ClassPathResource resource = new ClassPathResource("logic-prompt.txt");
        String fallbackPrompt = resource.getContentAsString(StandardCharsets.UTF_8);
        String dbPrompt = null;
        try {
            dbPrompt = promptService.getPrompt(PromptKey.LOGIC.name());
        } catch (Exception e) {
            log.warn("从数据库加载情景分析系统提示词失败: {}", e.getMessage());
        }
        String systemPrompt = (dbPrompt != null && dbPrompt.length() > 50) ? dbPrompt : fallbackPrompt;
        log.info("情景分析系统提示词来源: {}，长度: {} 字符", 
                (dbPrompt != null && dbPrompt.length() > 50) ? "DB" : "Classpath", systemPrompt.length());

        SituationRoomAgent agent = AiServices.builder(SituationRoomAgent.class)
                .streamingChatModel((StreamingChatModel) applicationContext.getBean("logicModel"))
                .systemMessageProvider(chatMemoryId -> systemPrompt)
                .build();
        return agent;
    }

    @Bean(name = "emotionAnalyzer")
    public EmotionAnalyzer emotionAnalyzer() {
        // 使用轻量级模型进行情感分析，提高响应速度
        log.info("正在配置 EmotionAnalyzer 情感分析服务");

        EmotionAnalyzer analyzer = AiServices.builder(EmotionAnalyzer.class)
                .chatModel((ChatModel) applicationContext.getBean("chatModel"))
                .build();

        log.info("EmotionAnalyzer 已配置完成，可用于广场内容情感分析");
        return analyzer;
    }
}
