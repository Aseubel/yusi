package com.aseubel.yusi.config.ai;

import dev.langchain4j.model.openai.OpenAiChatModel;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.aseubel.yusi.config.ai.properties.ChatModelConfigProperties;

/**
 * @author Aseubel
 * @date 2025/5/9 下午6:11
 */
@Configuration
@EnableConfigurationProperties(ChatModelConfigProperties.class)
public class ChatModelConfig {

    @Bean(name = "chatModel")
    public OpenAiChatModel chatModel(ChatModelConfigProperties properties) {
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl(properties.getBaseurl())
                .apiKey(properties.getApikey())
                .modelName(properties.getName())
                .build();
        return model;
    }
}
