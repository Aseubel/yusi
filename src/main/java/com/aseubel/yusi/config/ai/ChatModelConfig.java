package com.aseubel.yusi.config.ai;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.service.ai.model.ModelProxyFactory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ModelRoutingProperties.class)
public class ChatModelConfig {

    @Bean(name = "logicModel")
    public StreamingChatModel logicModel(ModelProxyFactory modelProxyFactory) {
        return modelProxyFactory.createStreamingProxy("logicModel", "zh", "situation-analysis");
    }

    @Bean(name = "streamingChatModel")
    public StreamingChatModel streamingChatModel(ModelProxyFactory modelProxyFactory) {
        return modelProxyFactory.createStreamingProxy("streamingChatModel", "zh", "chat");
    }

    @Bean(name = "chatModel")
    public ChatModel chatModel(ModelProxyFactory modelProxyFactory) {
        return modelProxyFactory.createChatProxy("chatModel", "zh", "situation-analysis");
    }

    @Bean(name = "emotionModel")
    public ChatModel emotionModel(ModelProxyFactory modelProxyFactory) {
        // 情感分析使用轻量级场景，避免使用复杂的 situation-analysis 场景
        return modelProxyFactory.createChatProxy("emotionModel", "zh", "emotion-analysis");
    }

    @Bean(name = "jsonChatModel")
    public ChatModel jsonChatModel(ModelProxyFactory modelProxyFactory) {
        return modelProxyFactory.createChatProxy("jsonChatModel", "zh", "memory-extract");
    }
}
