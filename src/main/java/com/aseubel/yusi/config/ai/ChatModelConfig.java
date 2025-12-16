package com.aseubel.yusi.config.ai;

import com.aseubel.yusi.config.ai.properties.ChatModelConfigProperties;
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
                .topP(0.1)
                .presencePenalty(0.4)
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
                .presencePenalty(0.4)
                .build();
    }
}
