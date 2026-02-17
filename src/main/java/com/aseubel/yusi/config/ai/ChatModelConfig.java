package com.aseubel.yusi.config.ai;

import com.aseubel.yusi.config.ai.properties.ChatModelConfigProperties;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * AI 模型配置类
 * 
 * 配置阿里云百炼模型的联网搜索功能：
 * - 通过 defaultRequestParameters 设置 enable_search=true
 * - 支持的模型：qwen-max, qwen-plus, qwen-turbo, deepseek-v3 等
 * 
 * @author Aseubel
 * @date 2025/5/9 下午6:11
 */
@Configuration
@EnableConfigurationProperties(ChatModelConfigProperties.class)
public class ChatModelConfig {

    /**
     * 构建默认请求参数，启用联网搜索，关闭thinking模式
     */
    private OpenAiChatRequestParameters buildDefaultParameters() {
        return OpenAiChatRequestParameters.builder()
                .customParameters(Map.of("enable_search", true, "enable_thinking", false))
                .build();
    }

    @Bean(name = "logicModel")
    public OpenAiStreamingChatModel logicModel(ChatModelConfigProperties properties) {
        OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder()
                .baseUrl(properties.getBaseurl())
                .apiKey(properties.getApikey())
                .modelName(properties.getModel())
                .temperature(0.1)
                .strictJsonSchema(true)
                .defaultRequestParameters(buildDefaultParameters())
                .build();
        return model;
    }

    @Bean(name = "streamingChatModel")
    public OpenAiStreamingChatModel streamingChatModel(ChatModelConfigProperties properties) {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(properties.getBaseurl())
                .apiKey(properties.getApikey())
                .modelName(properties.getModel())
                .temperature(1.3)
                .topP(0.85)
                .presencePenalty(0.6)
                .maxCompletionTokens(300)
                .defaultRequestParameters(buildDefaultParameters())
                .build();
    }

    /**
     * 同步聊天模型，用于需要直接获取结果的场景（如情感分析）
     */
    @Bean(name = "chatModel")
    public OpenAiChatModel chatModel(ChatModelConfigProperties properties) {
        return OpenAiChatModel.builder()
                .baseUrl(properties.getBaseurl())
                .apiKey(properties.getApikey())
                .modelName(properties.getModel())
                .temperature(0.3)
                .maxCompletionTokens(32)
                .defaultRequestParameters(buildDefaultParameters())
                .build();
    }

    @Bean(name = "jsonChatModel")
    public OpenAiChatModel jsonChatModel(ChatModelConfigProperties properties) {
        return OpenAiChatModel.builder()
                .baseUrl(properties.getBaseurl())
                .apiKey(properties.getApikey())
                .modelName(properties.getModel())
                .temperature(0.1)
                .strictJsonSchema(true)
                .maxCompletionTokens(2048)
                .defaultRequestParameters(buildDefaultParameters())
                .build();
    }
}
