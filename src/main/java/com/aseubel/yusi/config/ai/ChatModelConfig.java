package com.aseubel.yusi.config.ai;

import com.aseubel.yusi.config.ai.properties.ChatModelConfigProperties;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Aseubel
 * @date 2025/5/9 下午6:11
 */
@Configuration
@EnableConfigurationProperties(ChatModelConfigProperties.class)
public class ChatModelConfig {

    @Bean(name = "logicModel")
    public OpenAiStreamingChatModel logicModel(ChatModelConfigProperties properties) {
        OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder()
                .baseUrl(properties.getBaseurl())
                .apiKey(properties.getApikey())
                .modelName(properties.getModel())
                .temperature(0.1)
                .strictJsonSchema(true)
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
                .maxCompletionTokens(512)
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
                .temperature(0.3) // 较低温度用于分类任务
                .maxCompletionTokens(32) // 情感分析只需要短输出
                .build();
    }
}
