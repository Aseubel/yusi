package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.config.ai.properties.ModelTierDefinition;
import com.aseubel.yusi.service.ai.model.provider.ChatModelProviderAdapter;
import com.aseubel.yusi.service.ai.model.provider.ChatModelProviderRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.net.URI;
import java.net.URISyntaxException;

@Slf4j
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
                    && !definition.supports(ModelCapability.STREAMING_CHAT)
                    && !definition.supports(ModelCapability.VLM)) {
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
                    .baseUrl(definition.getBaseurl())
                    .enabled(definition.isEnabled())
                    .registered(true)
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
            log.info("Model instance loaded: modelId={}, provider={}, modelName={}, protocol={}, endpoint={}, "
                            + "chatClient={}, streamingClient={}, capabilities={}, priority={}, weight={}",
                    instance.getId(),
                    instance.getProvider(),
                    instance.getModelName(),
                    instance.getProtocol(),
                    sanitizeEndpoint(instance.getProtocol().resolveEndpoint(instance.getBaseUrl())),
                    clientType(instance.getChatModel()),
                    clientType(instance.getStreamingChatModel()),
                    instance.getCapabilities(),
                    instance.getPriority(),
                    instance.getWeight());
        }
        instances.set(Map.copyOf(next));
        if (config.getTiers() != null) {
            config.getTiers().forEach((tierId, tier) -> {
                List<String> members = tier == null || tier.getMembers() == null
                        ? List.of() : List.copyOf(tier.getMembers());
                List<String> activeMembers = members.stream()
                        .filter(next::containsKey)
                        .toList();
                log.info("Model tier loaded: tierId={}, enabled={}, strategy={}, capabilities={}, "
                                + "members={}, activeMembers={}",
                        tierId,
                        tier != null && tier.isEnabled(),
                        tier == null || tier.getStrategy() == null ? "ROUND_ROBIN" : tier.getStrategy(),
                        tier == null ? List.of() : tier.getCapabilities(),
                        members,
                        activeMembers);
            });
        }
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

    private String clientType(Object client) {
        return client == null ? "none" : client.getClass().getSimpleName();
    }

    private String sanitizeEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "unknown";
        }
        try {
            URI uri = new URI(endpoint);
            if (uri.getHost() == null) {
                return "configured";
            }
            StringBuilder sanitized = new StringBuilder();
            if (uri.getScheme() != null) {
                sanitized.append(uri.getScheme()).append("://");
            }
            sanitized.append(uri.getHost());
            if (uri.getPort() > 0) {
                sanitized.append(':').append(uri.getPort());
            }
            if (uri.getPath() != null && !uri.getPath().isBlank()) {
                sanitized.append(uri.getPath());
            }
            return sanitized.toString();
        } catch (URISyntaxException exception) {
            return "configured";
        }
    }
}
