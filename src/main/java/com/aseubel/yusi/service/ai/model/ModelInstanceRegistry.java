package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.service.ai.model.provider.ChatModelProviderAdapter;
import com.aseubel.yusi.service.ai.model.provider.ChatModelProviderRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
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
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class ModelInstanceRegistry {

    private final ModelConfigCenter modelConfigCenter;
    private final ChatModelProviderRegistry providerRegistry;

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
        Map<String, ModelInstance> next = new ConcurrentHashMap<>();
        for (ModelRoutingProperties.ModelDefinition definition : config.getModels()) {
            if (!definition.isEnabled() || definition.getId() == null || definition.getId().isBlank()) {
                continue;
            }
            if (!definition.supports(ModelCapability.CHAT)
                    && !definition.supports(ModelCapability.STREAMING_CHAT)) {
                continue;
            }
            ChatModelProviderAdapter.ProviderClientBundle clients = providerRegistry.create(definition);
            Set<ModelCapability> capabilities = definition.getCapabilities() == null
                    || definition.getCapabilities().isEmpty()
                    ? Set.of(ModelCapability.CHAT, ModelCapability.STREAMING_CHAT)
                    : Set.copyOf(definition.getCapabilities());
            ModelRoutingProperties.PricingDefinition pricing = definition.getPricing();
            ModelInstance instance = ModelInstance.builder()
                    .id(definition.getId())
                    .modelName(definition.getModel())
                    .provider(clients.provider())
                    .weight(definition.getWeight() == null ? 100 : definition.getWeight())
                    .priority(definition.getPriority() == null ? 100 : definition.getPriority())
                    .languages(normalize(definition.getLanguages()))
                    .scenes(normalize(definition.getScenes()))
                    .capabilities(capabilities)
                    .contextWindowTokens(definition.getContextWindowTokens())
                    .inputPricePerMillion(pricing == null ? null : pricing.getInputPerMillion())
                    .outputPricePerMillion(pricing == null ? null : pricing.getOutputPerMillion())
                    .priceVersion(pricing == null ? null : pricing.getPriceVersion())
                    .chatModel(clients.chatModel())
                    .streamingChatModel(clients.streamingChatModel())
                    .build();
            next.put(instance.getId(), instance);
        }
        instances.clear();
        instances.putAll(next);
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
