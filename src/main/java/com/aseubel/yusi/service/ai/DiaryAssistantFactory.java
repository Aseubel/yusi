package com.aseubel.yusi.service.ai;

import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.service.diary.Assistant;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 日记助手工厂 - 用于创建针对特定用户的 Assistant 实例
 *
 * @author Aseubel
 * @date 2026/02/10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryAssistantFactory {

    @Autowired
    @Qualifier("streamingChatModel")
    private StreamingChatModel streamingChatModel;

    @Autowired
    @Qualifier("chatMemoryProvider")
    private ChatMemoryProvider chatMemoryProvider;

    @Autowired
    @Qualifier("milvusEmbeddingStore")
    private MilvusEmbeddingStore milvusEmbeddingStore;

    @Autowired
    @Qualifier("embeddingModel")
    private EmbeddingModel embeddingModel;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PromptService promptService;

    @Autowired
    private ApplicationContext applicationContext;

    @Value("${mcp.enabled:false}")
    private boolean mcpEnabled;

    /**
     * 为指定用户创建 Assistant 实例
     *
     * @param userId 用户ID
     * @return 绑定了用户上下文的 Assistant
     */
    public Assistant createAssistant(String userId) {
        // 1. 创建绑定了 userId 的工具实例
        DiarySearchTool userScopedTool = new DiarySearchTool(userId, milvusEmbeddingStore, embeddingModel, userRepository);

        // 2. 获取 System Prompt
        String systemPrompt = loadSystemPrompt();

        // 3. 构建 AiServices
        AiServices<Assistant> builder = AiServices.builder(Assistant.class)
                .streamingChatModel(streamingChatModel)
                .tools(userScopedTool) // 使用绑定了用户ID的工具
                .chatMemoryProvider(chatMemoryProvider)
                .systemMessageProvider(chatMemoryId -> systemPrompt);

        // 4. 集成 MCP (如果有)
        if (mcpEnabled) {
            try {
                ToolProvider mcpToolProvider = (ToolProvider) applicationContext.getBean("mcpToolProvider");
                builder.toolProvider(mcpToolProvider);
            } catch (Exception e) {
                log.warn("MCP 已启用但无法获取 mcpToolProvider Bean: {}，将仅使用本地工具", e.getMessage());
            }
        }

        return builder.build();
    }

    private String loadSystemPrompt() {
        try {
            String dbPrompt = promptService.getPrompt("chat");
            if (dbPrompt != null && dbPrompt.length() > 100) {
                return dbPrompt;
            }
        } catch (Exception e) {
            log.warn("从数据库加载聊天助手系统提示词失败: {}", e.getMessage());
        }

        try {
            ClassPathResource resource = new ClassPathResource("chat-prompt.txt");
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("无法加载默认提示词文件", e);
            return "你是我的日记助手。";
        }
    }
}
