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

    @Bean(name = "streamingChatModel")
    public StreamingChatModel streamingChatModel(ModelProxyFactory modelProxyFactory) {
        return modelProxyFactory.createStreamingProxy("zh", "chat");
    }

    @Bean(name = "chatModel")
    public ChatModel chatModel(ModelProxyFactory modelProxyFactory) {
        return modelProxyFactory.createChatProxy("zh", "chat");
    }
}
