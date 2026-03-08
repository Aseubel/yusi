package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ModelInstanceRegistry {

    private final ModelConfigCenter modelConfigCenter;

    private final Map<String, ModelInstance> instances = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        reload(modelConfigCenter.getEffectiveConfig());
    }

    @EventListener
    public void handleModelConfigUpdated(ModelConfigUpdatedEvent event) {
        if (event == null || event.getConfig() == null) {
            return;
        }
        reload(event.getConfig());
    }

    public synchronized void reload(ModelRoutingProperties config) {
        instances.clear();
        for (ModelRoutingProperties.ModelDefinition definition : config.getModels()) {
            if (!definition.isEnabled() || definition.getId() == null || definition.getId().isBlank()) {
                continue;
            }
            OpenAiChatModel chatModel = OpenAiChatModel.builder()
                    .baseUrl(definition.getBaseurl())
                    .apiKey(definition.getApikey())
                    .modelName(definition.getModel())
                    .build();
            OpenAiStreamingChatModel streamingChatModel = OpenAiStreamingChatModel.builder()
                    .baseUrl(definition.getBaseurl())
                    .apiKey(definition.getApikey())
                    .modelName(definition.getModel())
                    .build();
            ModelInstance instance = ModelInstance.builder()
                    .id(definition.getId())
                    .modelName(definition.getModel())
                    .weight(definition.getWeight() == null ? 100 : definition.getWeight())
                    .priority(definition.getPriority() == null ? 100 : definition.getPriority())
                    .languages(normalize(definition.getLanguages()))
                    .scenes(normalize(definition.getScenes()))
                    .chatModel(chatModel)
                    .streamingChatModel(streamingChatModel)
                    .build();
            instances.put(instance.getId(), instance);
        }
    }

    public Optional<ModelInstance> getById(String modelId) {
        return Optional.ofNullable(instances.get(modelId));
    }

    public List<ModelInstance> getGroupMembers(String groupId) {
        ModelRoutingProperties properties = modelConfigCenter.getEffectiveConfig();
        ModelRoutingProperties.GroupDefinition group = properties.getGroups().get(groupId);
        if (group == null || group.getMembers() == null) {
            return Collections.emptyList();
        }
        List<ModelInstance> members = new ArrayList<>();
        for (String memberId : group.getMembers()) {
            ModelInstance instance = instances.get(memberId);
            if (instance != null) {
                members.add(instance);
            }
        }
        return members;
    }

    public List<ModelInstance> filterByLanguageAndScene(List<ModelInstance> candidates, String language, String scene) {
        String normalizedLanguage = normalizeOne(language);
        String normalizedScene = normalizeOne(scene);
        List<ModelInstance> filtered = candidates.stream()
                .filter(instance -> instance.getLanguages().isEmpty() || instance.getLanguages().contains(normalizedLanguage))
                .filter(instance -> instance.getScenes().isEmpty() || instance.getScenes().contains(normalizedScene))
                .toList();
        if (!filtered.isEmpty()) {
            return filtered;
        }
        return candidates;
    }

    public ChatModel getChatModel(String modelId) {
        ModelInstance instance = instances.get(modelId);
        return instance == null ? null : instance.getChatModel();
    }

    public StreamingChatModel getStreamingChatModel(String modelId) {
        ModelInstance instance = instances.get(modelId);
        return instance == null ? null : instance.getStreamingChatModel();
    }

    private Set<String> normalize(List<String> items) {
        if (items == null) {
            return Collections.emptySet();
        }
        return items.stream().map(this::normalizeOne).collect(Collectors.toSet());
    }

    private String normalizeOne(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT);
    }
}
