package com.aseubel.yusi.service.ai.model.provider;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;

public interface ChatModelProviderAdapter {

    String providerId();

    boolean supports(ModelRoutingProperties.ModelDefinition definition);

    ProviderClientBundle create(ModelRoutingProperties.ModelDefinition definition);

    record ProviderClientBundle(
            String provider,
            ChatModel chatModel,
            StreamingChatModel streamingChatModel) {
    }
}
