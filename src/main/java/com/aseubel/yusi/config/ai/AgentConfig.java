package com.aseubel.yusi.config.ai;

import com.aseubel.yusi.service.ai.DiarySearchTool;
import com.aseubel.yusi.service.diary.Assistant;
import com.aseubel.yusi.service.room.SituationRoomAgent;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
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
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Agent 配置类 - 实现 Agentic RAG 模式 + MCP 集成
 * 
 * 通过 Tool Use 机制替代传统的 ContentRetriever，
 * 让 LLM 能够主动决定何时需要检索日记，并支持时间范围过滤。
 * 
 * 当 MCP 启用时，还会集成外部 MCP Server 提供的工具（如 web_search）。
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

    @Value("${mcp.enabled:false}")
    private boolean mcpEnabled;

    @Bean(name = "diaryAssistant")
    public Assistant diaryAssistant() throws IOException {
        ClassPathResource resource = new ClassPathResource("chat-prompt.txt");
        String systemPrompt = resource.getContentAsString(StandardCharsets.UTF_8);
        log.info("成功加载聊天助手系统提示词，长度: {} 字符", systemPrompt.length());

        // 构建 AiServices
        AiServices<Assistant> builder = AiServices.builder(Assistant.class)
                .streamingChatModel((StreamingChatModel) applicationContext.getBean("streamingChatModel"))
                .tools(diarySearchTool) // 本地工具: 日记检索（支持时间范围过滤）
                .chatMemoryProvider((ChatMemoryProvider) applicationContext.getBean("chatMemoryProvider"))
                .systemMessageProvider(chatMemoryId -> systemPrompt);

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

        log.info("DiaryAssistant 已配置 Agentic RAG 模式，注册工具: DiarySearchTool{}",
                mcpEnabled ? " + MCP Tools" : "");
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
