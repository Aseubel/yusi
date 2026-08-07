package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.config.ai.properties.ModelTierDefinition;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ModelInstanceRegistry {

    private final ModelConfigCenter modelConfigCenter;
    private final ChatModelProviderRegistry providerRegistry;

    private final AtomicReference<Map<String, ModelInstance>> instances = new AtomicReference<>(Map.of());

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
        Map<String, ModelInstance> next = new java.util.LinkedHashMap<>();
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
                    .protocol(ModelProtocol.normalize(definition.getProtocol()))
                    .weight(definition.getWeight() == null ? 100 : definition.getWeight())
                    .priority(definition.getPriority() == null ? 100 : definition.getPriority())
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
        instances.set(Map.copyOf(next));
    }

    public Optional<ModelInstance> getById(String modelId) {
        return Optional.ofNullable(instances.get().get(modelId));
    }

    public List<ModelInstance> getTierMembers(String tierId) {
        ModelRoutingProperties properties = modelConfigCenter.getEffectiveConfig();
        ModelTierDefinition tier = properties.getTiers().get(tierId);
        if (tier == null || tier.getMembers() == null) {
            return List.of();
        }
        return membersForIds(tier.getMembers());
    }

    public ChatModel getChatModel(String modelId) {
        ModelInstance instance = instances.get().get(modelId);
        return instance == null ? null : instance.getChatModel();
    }

    public StreamingChatModel getStreamingChatModel(String modelId) {
        ModelInstance instance = instances.get().get(modelId);
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

    private List<ModelInstance> membersForIds(List<String> memberIds) {
        List<ModelInstance> members = new ArrayList<>();
        Map<String, ModelInstance> current = instances.get();
        for (String memberId : memberIds) {
            ModelInstance instance = current.get(memberId);
            if (instance != null) {
                members.add(instance);
            }
        }
        return members;
    }
}
