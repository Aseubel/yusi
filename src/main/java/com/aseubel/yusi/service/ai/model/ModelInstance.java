package com.aseubel.yusi.service.ai.model;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import lombok.Builder;
import lombok.Value;

import java.util.Set;

@Value
@Builder
public class ModelInstance {
    String id;
    String modelName;
    int weight;
    int priority;
    Set<String> languages;
    Set<String> scenes;
    ChatModel chatModel;
    StreamingChatModel streamingChatModel;
}
