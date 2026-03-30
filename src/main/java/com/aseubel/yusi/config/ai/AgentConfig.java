package com.aseubel.yusi.config.ai;

import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.service.ai.MemorySearchTool;
import com.aseubel.yusi.service.ai.UserPersonaTool;
import com.aseubel.yusi.service.ai.MemoryCompressionAssistant;
import com.aseubel.yusi.service.ai.PromptManager;
import com.aseubel.yusi.service.diary.Assistant;
import com.aseubel.yusi.service.plaza.EmotionAnalyzer;
import com.aseubel.yusi.service.room.SituationRoomAgent;
import com.aseubel.yusi.service.match.MatchAssistant;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    private final PromptManager promptManager;

    @Value("${mcp.enabled:false}")
    private boolean mcpEnabled;

    @Bean(name = "diaryAssistant")
    public Assistant diaryAssistant() {
        log.info("正在配置 DiaryAssistant (Singleton)");

        // 构建 AiServices - 单例模式，用户隔离通过 memoryId 和 UserContext 实现
        AiServices<Assistant> builder = AiServices.builder(Assistant.class)
                .streamingChatModel((StreamingChatModel) applicationContext.getBean("streamingChatModel"))
                .tools(
                        applicationContext.getBean(MemorySearchTool.class),
                        applicationContext.getBean(UserPersonaTool.class))
                .chatMemoryProvider((ChatMemoryProvider) applicationContext.getBean("chatMemoryProvider"));

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
    public SituationRoomAgent situationRoomAgent() {
        SituationRoomAgent agent = AiServices.builder(SituationRoomAgent.class)
                .streamingChatModel((StreamingChatModel) applicationContext.getBean("streamingChatModel"))
                .systemMessageProvider(chatMemoryId -> promptManager.getPrompt(PromptKey.LOGIC))
                .build();
        return agent;
    }

    @Bean(name = "emotionAnalyzer")
    public EmotionAnalyzer emotionAnalyzer(
            com.aseubel.yusi.service.plaza.impl.EmotionAnalyzerImpl emotionAnalyzerImpl) {
        // 使用自定义的情感分析实现，直接调用 LLM API，避免 langchain4j AiServices 的开销
        log.info("正在配置 EmotionAnalyzer 情感分析服务（使用自定义实现）");

        log.info("EmotionAnalyzer 已配置完成，可用于广场内容情感分析");
        return emotionAnalyzerImpl;
    }

    @Bean(name = "memoryCompressionAssistant")
    public MemoryCompressionAssistant memoryCompressionAssistant(UserPersonaTool userPersonaTool) {
        return AiServices.builder(MemoryCompressionAssistant.class)
                .chatModel((ChatModel) applicationContext.getBean("chatModel"))
                .build();
    }

    @Bean(name = "matchAssistant")
    public MatchAssistant matchAssistant() {
        return AiServices.builder(MatchAssistant.class)
                .chatModel((ChatModel) applicationContext.getBean("chatModel"))
                .build();
    }
}
