package com.aseubel.yusi.service.ai;

import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.service.diary.Assistant;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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
    private DiarySearchTool diarySearchTool;

    @Autowired
    private LifeGraphTool lifeGraphTool;

    @Autowired
    private ContextBuilderService contextBuilderService;

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
        // 1. 创建工具列表
        List<Object> tools = new ArrayList<>();
        
        // 1.1 日记搜索工具
        tools.add(diarySearchTool);
        
        // 1.2 人生图谱工具
        tools.add(lifeGraphTool);

        // 2. 构建 AiServices
        AiServices<Assistant> builder = AiServices.builder(Assistant.class)
                .streamingChatModel(streamingChatModel)
                .tools(tools) // 注册所有工具
                .chatMemoryProvider(chatMemoryProvider)
                // 3. 使用 ContextBuilder 动态生成 System Message
                .systemMessageProvider(memoryId -> contextBuilderService.buildSystemMessage(memoryId));

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

}
