package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.common.utils.LowSensitivityLogSummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ModelRouterService {

    private final ModelConfigCenter modelConfigCenter;
    private final ModelInstanceRegistry modelInstanceRegistry;
    private final ModelStateCenter modelStateCenter;
    private final ModelRoutePlanner modelRoutePlanner;

    @Autowired
    public ModelRouterService(ModelConfigCenter modelConfigCenter,
            ModelInstanceRegistry modelInstanceRegistry, ModelStateCenter modelStateCenter,
            ModelRoutePlanner modelRoutePlanner) {
        this.modelConfigCenter = modelConfigCenter;
        this.modelInstanceRegistry = modelInstanceRegistry;
        this.modelStateCenter = modelStateCenter;
        this.modelRoutePlanner = modelRoutePlanner;
    }

    /** Constructor retained for existing focused tests. */
    public ModelRouterService(ModelConfigCenter modelConfigCenter,
            ModelInstanceRegistry modelInstanceRegistry, ModelStrategyRegistry strategyRegistry,
            ModelStateCenter modelStateCenter) {
        this(modelConfigCenter, modelInstanceRegistry, modelStateCenter,
                new ModelRoutePlanner(strategyRegistry, modelStateCenter));
    }

    public void init() {
        // The planner obtains the current strategy map at planning time.
    }

    public ModelRouteDecision plan(ModelRouteContext context) {
        ModelRoutingProperties properties = modelConfigCenter.getEffectiveConfig();
        Map<String, java.util.List<ModelInstance>> tierMembers = tierMembersFor(properties);
        Map<String, ModelRuntimeState> states = snapshotStates(tierMembers.values().stream()
                .flatMap(java.util.Collection::stream).map(ModelInstance::getId).toList());
        return plan(properties, context, tierMembers, states);
    }

    public ModelRouteDecision plan(ModelRoutingProperties properties, ModelRouteContext context,
            Map<String, List<ModelInstance>> tierMembers, Map<String, ModelRuntimeState> states) {
        return modelRoutePlanner.plan(properties, context, tierMembers, states);
    }

    private Map<String, java.util.List<ModelInstance>> tierMembersFor(ModelRoutingProperties properties) {
        Map<String, java.util.List<ModelInstance>> tierMembers = new LinkedHashMap<>();
        if (properties.getTiers() == null) {
            return tierMembers;
        }
        Map<String, ModelRoutingProperties.ModelDefinition> definitions = properties.getModels() == null
                ? Map.of()
                : properties.getModels().stream()
                        .filter(definition -> definition != null && definition.getId() != null)
                        .collect(Collectors.toMap(ModelRoutingProperties.ModelDefinition::getId,
                                definition -> definition, (first, ignored) -> first));
        properties.getTiers().forEach((tierId, tier) -> {
            List<ModelInstance> members = new ArrayList<>();
            List<ModelInstance> registeredMembers = modelInstanceRegistry.getTierMembers(tierId);
            Map<String, ModelInstance> registeredById = registeredMembers == null ? Map.of()
                    : registeredMembers.stream()
                            .filter(Objects::nonNull)
                            .filter(instance -> instance.getId() != null)
                            .collect(Collectors.toMap(ModelInstance::getId, instance -> instance,
                                    (first, ignored) -> first));
            if (tier != null && tier.getMembers() != null) {
                for (String memberId : tier.getMembers()) {
                    if (memberId == null) {
                        continue;
                    }
                    ModelInstance registered = registeredById.get(memberId);
                    if (registered != null) {
                        members.add(registered);
                    } else if (definitions.containsKey(memberId)) {
                        members.add(metadataInstance(memberId, definitions.get(memberId)));
                    }
                }
            }
            if (members.isEmpty() && registeredMembers != null) {
                members.addAll(registeredMembers);
            }
            tierMembers.put(tierId, members);
        });
        return tierMembers;
    }

    private ModelInstance metadataInstance(String memberId,
            ModelRoutingProperties.ModelDefinition definition) {
        if (definition == null) {
            return ModelInstance.builder().id(memberId).registered(false).enabled(false).build();
        }
        ModelRoutingProperties.PricingDefinition pricing = definition.getPricing();
        Set<String> scenes = definition.getScenes() == null ? Set.of()
                : definition.getScenes().stream().filter(Objects::nonNull)
                        .map(value -> value.trim().toLowerCase(java.util.Locale.ROOT)).collect(Collectors.toSet());
        Set<ModelCapability> capabilities = definition.getCapabilities() == null
                || definition.getCapabilities().isEmpty()
                ? Set.of(ModelCapability.CHAT, ModelCapability.STREAMING_CHAT)
                : Set.copyOf(definition.getCapabilities());
        return ModelInstance.builder()
                .id(definition.getId())
                .modelName(definition.getModel())
                .provider(definition.getProvider())
                .protocol(ModelProtocol.normalize(definition.getProtocol()))
                .baseUrl(definition.getBaseurl())
                .enabled(definition.isEnabled())
                .registered(false)
                .weight(definition.getWeight() == null ? 100 : definition.getWeight())
                .priority(definition.getPriority() == null ? 100 : definition.getPriority())
                .scenes(scenes)
                .capabilities(capabilities)
                .contextWindowTokens(definition.getContextWindowTokens())
                .inputPricePerMillion(pricing == null ? null : pricing.getInputPerMillion())
                .outputPricePerMillion(pricing == null ? null : pricing.getOutputPerMillion())
                .priceVersion(pricing == null ? null : pricing.getPriceVersion())
                .build();
    }

    private Map<String, ModelRuntimeState> snapshotStates(java.util.Collection<String> modelIds) {
        if (modelIds.isEmpty()) {
            return Map.of();
        }
        try {
            return modelStateCenter.snapshot(modelIds);
        } catch (RuntimeException exception) {
            log.warn("Model state snapshot unavailable: operation=model_state_snapshot, modelCount={}, "
                            + "exceptionType={}", modelIds.size(), LowSensitivityLogSummary.exceptionType(exception));
            return Map.of();
        }
    }
}
