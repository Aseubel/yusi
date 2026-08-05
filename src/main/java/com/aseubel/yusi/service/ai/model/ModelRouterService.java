package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.config.ai.properties.ModelTierDefinition;
import com.aseubel.yusi.config.ai.properties.RoutePolicyDefinition;
import com.aseubel.yusi.service.ai.model.strategy.ModelSelectionStrategy;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ModelRouterService {

    private final ModelConfigCenter modelConfigCenter;
    private final ModelInstanceRegistry modelInstanceRegistry;
    private final GroupStrategyManager groupStrategyManager;
    private final ModelStrategyRegistry modelStrategyRegistry;
    private final ModelStateCenter modelStateCenter;

    private final ModelRoutePolicyMatcher routePolicyMatcher = new ModelRoutePolicyMatcher();
    private Map<ModelSelectionStrategyType, ModelSelectionStrategy> strategies = Map.of();

    @PostConstruct
    public void init() {
        this.strategies = modelStrategyRegistry.build();
    }

    public ModelRouteDecision plan(ModelRouteContext context) {
        ModelRoutingProperties properties = modelConfigCenter.getEffectiveConfig();
        ModelRouteContext normalizedContext = normalizeContext(context, properties);
        RoutePolicyDefinition policy = routePolicyMatcher.match(properties, normalizedContext);
        if (policy == null) {
            policy = legacyPolicy(properties, normalizedContext);
        }
        if (policy == null || policy.getPrimaryTier() == null || policy.getPrimaryTier().isBlank()) {
            throw new IllegalStateException("No model route configured for language: "
                    + normalizedContext.getLanguage() + ", scene: " + normalizedContext.getScene());
        }

        List<String> fallbackTiers = policy.getFallbackTiers() == null
                ? List.of() : policy.getFallbackTiers().stream()
                .filter(Objects::nonNull)
                .map(this::normalize)
                .filter(tier -> !tier.isBlank())
                .toList();
        String primaryTier = policy.getPrimaryTier();
        List<String> tierOrder = new ArrayList<>();
        tierOrder.add(primaryTier);
        fallbackTiers.stream().filter(tier -> !tier.equals(primaryTier)).forEach(tierOrder::add);

        List<String> modelIds = tierOrder.stream()
                .flatMap(tier -> modelInstanceRegistry.getTierMembers(tier).stream())
                .map(ModelInstance::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<String, ModelRuntimeState> states = modelIds.isEmpty()
                ? Map.of() : modelStateCenter.snapshot(modelIds);

        List<ModelRouteCandidate> candidates = new ArrayList<>();
        Set<String> healthReasons = new LinkedHashSet<>();
        for (int index = 0; index < tierOrder.size(); index++) {
            String tierId = tierOrder.get(index);
            boolean fallback = index > 0;
            List<ModelRouteCandidate> tierCandidates = routeTier(
                    properties, tierId, normalizedContext, states, fallback);
            candidates.addAll(tierCandidates);
            tierCandidates.stream()
                    .map(ModelRouteCandidate::excludedReason)
                    .filter(Objects::nonNull)
                    .filter(reason -> !"fallback-tier".equals(reason))
                    .forEach(healthReasons::add);
        }

        ModelTierDefinition primaryDefinition = properties.getTiers().get(primaryTier);
        ModelSelectionStrategyType strategy = primaryDefinition == null || primaryDefinition.getStrategy() == null
                ? legacyStrategy(properties, primaryTier)
                : primaryDefinition.getStrategy();
        String routeReason = "policy=" + safe(policy.getId(), "default")
                + ";policy-version=" + properties.getSchemaVersion()
                + ";language=" + normalizedContext.getLanguage()
                + ";scene=" + normalizedContext.getScene()
                + ";risk=" + safe(normalizedContext.getRiskLevel(), policy.getRiskLevel())
                + ";primary-tier=" + primaryTier
                + ";strategy=" + strategy.name()
                + ";fallback-tiers=" + String.join(",", fallbackTiers)
                + ";health-filter=" + (healthReasons.isEmpty() ? "none" : String.join(",", healthReasons));
        return new ModelRouteDecision(normalizedContext.getRequestId(), policy.getId(),
                properties.getSchemaVersion(), primaryTier, fallbackTiers, candidates, routeReason);
    }

    public ModelInstance select(ModelRouteContext context) {
        return select(context, Set.of());
    }

    public ModelInstance select(ModelRouteContext context, Set<String> excludedIds) {
        ModelRouteDecision decision = plan(context);
        return decision.attemptCandidates().stream()
                .filter(candidate -> !excludedIds.contains(candidate.modelId()))
                .map(ModelRouteCandidate::instance)
                .findFirst()
                .orElseGet(() -> decision.candidates().stream()
                        .filter(candidate -> !excludedIds.contains(candidate.modelId()))
                        .map(ModelRouteCandidate::instance)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No model candidate for route: "
                                + decision.policyId())));
    }

    public String resolveGroup(String language, String scene) {
        ModelRoutingProperties properties = modelConfigCenter.getEffectiveConfig();
        RoutePolicyDefinition policy = routePolicyMatcher.match(properties,
                ModelRouteContext.builder().language(language).scene(scene).build());
        if (policy != null && policy.getPrimaryTier() != null && !policy.getPrimaryTier().isBlank()) {
            return policy.getPrimaryTier();
        }
        RoutePolicyDefinition legacy = legacyPolicy(properties,
                ModelRouteContext.builder().language(language).scene(scene).build());
        if (legacy != null) {
            return legacy.getPrimaryTier();
        }
        throw new IllegalStateException("No model group configured");
    }

    public ModelRoutingProperties.SceneDefinition resolveSceneDefinition(String language, String scene) {
        ModelRoutingProperties properties = modelConfigCenter.getEffectiveConfig();
        ModelRouteContext context = ModelRouteContext.builder().language(language).scene(scene).build();
        RoutePolicyDefinition route = routePolicyMatcher.match(properties, context);
        if (route != null) {
            return toSceneDefinition(route);
        }
        String normalizedLanguage = normalize(valueOrDefault(language, properties.getDefaultLanguage()));
        String normalizedScene = normalize(valueOrDefault(scene, properties.getDefaultScene()));
        Map<String, ModelRoutingProperties.SceneDefinition> sceneMap = properties.getMatrix().get(normalizedLanguage);
        if (sceneMap != null) {
            return sceneMap.get(normalizedScene);
        }
        return null;
    }

    private List<ModelRouteCandidate> routeTier(ModelRoutingProperties properties, String tierId,
            ModelRouteContext context, Map<String, ModelRuntimeState> states, boolean fallback) {
        ModelTierDefinition tier = properties.getTiers().get(tierId);
        List<ModelInstance> members = modelInstanceRegistry.getTierMembers(tierId);
        ModelSelectionStrategy strategy = strategies.getOrDefault(
                tier == null || tier.getStrategy() == null
                        ? legacyStrategy(properties, tierId) : tier.getStrategy(),
                strategies.get(ModelSelectionStrategyType.ROUND_ROBIN));
        if (strategy == null) {
            return List.of();
        }
        List<ModelInstance> ordered = strategy.order(tierId, members, states);
        List<ModelRouteCandidate> result = new ArrayList<>(ordered.size());
        for (ModelInstance instance : ordered) {
            String excludedReason = exclusionReason(tier, instance, context, states.get(instance.getId()));
            boolean available = excludedReason == null;
            if (fallback && available) {
                excludedReason = "fallback-tier";
            }
            result.add(new ModelRouteCandidate(tierId, instance, available, excludedReason));
        }
        return List.copyOf(result);
    }

    private String exclusionReason(ModelTierDefinition tier, ModelInstance instance,
            ModelRouteContext context, ModelRuntimeState state) {
        if (tier != null && !tier.isEnabled()) {
            return "TIER_DISABLED";
        }
        if (!supports(instance, ModelCapability.CHAT) && !supports(instance, ModelCapability.STREAMING_CHAT)) {
            return "UNSUPPORTED_CAPABILITY";
        }
        if (!supportsValue(instance.getLanguages(), context.getLanguage())) {
            return "LANGUAGE_MISMATCH";
        }
        if (!supportsValue(instance.getScenes(), context.getScene())) {
            return "SCENE_MISMATCH";
        }
        if (state != null && !state.isAvailable()
                && !"HALF_OPEN".equalsIgnoreCase(state.getPhase())) {
            return state.getPhase() == null || state.getPhase().isBlank()
                    ? "DOWN" : state.getPhase().toUpperCase(Locale.ROOT);
        }
        return null;
    }

    private boolean supports(ModelInstance instance, ModelCapability capability) {
        return instance.getCapabilities() == null || instance.getCapabilities().isEmpty()
                || instance.getCapabilities().contains(capability);
    }

    private boolean supportsValue(Set<String> values, String expected) {
        return values == null || values.isEmpty() || values.contains(normalize(expected));
    }

    private ModelRouteContext normalizeContext(ModelRouteContext context, ModelRoutingProperties properties) {
        return ModelRouteContext.builder()
                .requestId(context == null ? null : context.getRequestId())
                .language(normalize(valueOrDefault(context == null ? null : context.getLanguage(),
                        properties.getDefaultLanguage())))
                .scene(normalize(valueOrDefault(context == null ? null : context.getScene(),
                        properties.getDefaultScene())))
                .group(context == null ? null : context.getGroup())
                .riskLevel(context == null ? null : context.getRiskLevel())
                .estimatedInputTokens(context == null ? null : context.getEstimatedInputTokens())
                .reservedOutputTokens(context == null ? null : context.getReservedOutputTokens())
                .build();
    }

    private RoutePolicyDefinition legacyPolicy(ModelRoutingProperties properties, ModelRouteContext context) {
        String group = context.getGroup();
        if (group == null || group.isBlank()) {
            group = resolveLegacyGroup(properties, context.getLanguage(), context.getScene());
        }
        if (group == null || group.isBlank()) {
            group = properties.getDefaultTier();
        }
        if (group == null || group.isBlank()) {
            return null;
        }
        RoutePolicyDefinition route = new RoutePolicyDefinition();
        route.setId("legacy-" + normalize(context.getLanguage()) + "-" + normalize(context.getScene()));
        route.setLanguage(normalize(context.getLanguage()));
        route.setScene(normalize(context.getScene()));
        route.setPrimaryTier(group);
        route.setPriority(0);
        return route;
    }

    private String resolveLegacyGroup(ModelRoutingProperties properties, String language, String scene) {
        String normalizedLanguage = normalize(language);
        String normalizedScene = normalize(scene);
        Map<String, ModelRoutingProperties.SceneDefinition> sceneMap = properties.getMatrix().get(normalizedLanguage);
        if (sceneMap != null) {
            ModelRoutingProperties.SceneDefinition definition = sceneMap.get(normalizedScene);
            if (definition != null && definition.getGroup() != null && !definition.getGroup().isBlank()) {
                return definition.getGroup();
            }
        }
        if (!properties.getGroups().isEmpty()) {
            return properties.getGroups().keySet().iterator().next();
        }
        return null;
    }

    private ModelSelectionStrategyType legacyStrategy(ModelRoutingProperties properties, String tierId) {
        ModelRoutingProperties.GroupDefinition group = properties.getGroups().get(tierId);
        if (group != null && group.getStrategy() != null) {
            return group.getStrategy();
        }
        return groupStrategyManager.getStrategy(tierId);
    }

    private ModelRoutingProperties.SceneDefinition toSceneDefinition(RoutePolicyDefinition route) {
        ModelRoutingProperties.SceneDefinition definition = new ModelRoutingProperties.SceneDefinition();
        definition.setGroup(route.getPrimaryTier());
        definition.setMaxTokens(route.getMaxOutputTokens());
        definition.setTemperature(route.getTemperature());
        definition.setTopP(route.getTopP());
        definition.setMaxCompletionTokens(route.getMaxCompletionTokens());
        definition.setCustomParameters(route.getCustomParameters() == null
                ? Collections.emptyMap() : route.getCustomParameters());
        return definition;
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? safe(fallback, "") : value;
    }
}
