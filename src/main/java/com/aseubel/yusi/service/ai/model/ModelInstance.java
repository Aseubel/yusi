package com.aseubel.yusi.service.ai.model;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import lombok.Builder;
import lombok.Value;

import java.util.Set;
import java.math.BigDecimal;

@Value
@Builder
public class ModelInstance {
    String id;
    String modelName;
    String provider;
    ModelProtocol protocol;
    String baseUrl;
    int weight;
    int priority;
    Set<String> scenes;
    Set<ModelCapability> capabilities;
    Integer contextWindowTokens;
    BigDecimal inputPricePerMillion;
    BigDecimal outputPricePerMillion;
    String priceVersion;
    ChatModel chatModel;
    StreamingChatModel streamingChatModel;
}
