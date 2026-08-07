package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.config.ai.properties.ModelTierDefinition;
import com.aseubel.yusi.config.ai.properties.RoutePolicyDefinition;
import com.aseubel.yusi.service.ai.model.strategy.ModelSelectionStrategy;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
        if (policy == null || policy.getPrimaryTier() == null || policy.getPrimaryTier().isBlank()) {
            throw new IllegalStateException("No model route configured for language: "
                    + normalizedContext.getLanguage() + ", scene: " + normalizedContext.getScene());
        }
        ModelRouteContext budgetContext = applyRouteBudget(normalizedContext, policy);

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
                    properties, tierId, policy, budgetContext, states, fallback);
            candidates.addAll(tierCandidates);
            tierCandidates.stream()
                    .map(ModelRouteCandidate::excludedReason)
                    .filter(Objects::nonNull)
                    .filter(reason -> !"fallback-tier".equals(reason))
                    .forEach(healthReasons::add);
        }

        ModelTierDefinition primaryDefinition = properties.getTiers().get(primaryTier);
        ModelSelectionStrategyType strategy = primaryDefinition == null || primaryDefinition.getStrategy() == null
                ? ModelSelectionStrategyType.ROUND_ROBIN : primaryDefinition.getStrategy();
        String routeReason = "policy=" + safe(policy.getId(), "default")
                + ";policy-version=" + properties.getVersion()
                + ";language=" + normalizedContext.getLanguage()
                + ";scene=" + normalizedContext.getScene()
                + ";risk=" + safe(normalizedContext.getRiskLevel(), policy.getRiskLevel())
                + ";estimated-input-tokens=" + numberOrUnknown(budgetContext.getEstimatedInputTokens())
                + ";reserved-output-tokens=" + numberOrUnknown(budgetContext.getReservedOutputTokens())
                + ";primary-tier=" + primaryTier
                + ";strategy=" + strategy.name()
                + ";fallback-tiers=" + String.join(",", fallbackTiers)
                + ";health-filter=" + (healthReasons.isEmpty() ? "none" : String.join(",", healthReasons));
        return new ModelRouteDecision(budgetContext.getRequestId(), policy.getId(),
                properties.getVersion(), primaryTier, fallbackTiers, candidates, routeReason,
                ModelRouteParameters.from(policy));
    }

    private List<ModelRouteCandidate> routeTier(ModelRoutingProperties properties, String tierId,
            RoutePolicyDefinition policy, ModelRouteContext context,
            Map<String, ModelRuntimeState> states, boolean fallback) {
        ModelTierDefinition tier = properties.getTiers().get(tierId);
        List<ModelInstance> members = modelInstanceRegistry.getTierMembers(tierId);
        ModelSelectionStrategy strategy = strategies.getOrDefault(
                tier == null || tier.getStrategy() == null
                        ? ModelSelectionStrategyType.ROUND_ROBIN : tier.getStrategy(),
                strategies.get(ModelSelectionStrategyType.ROUND_ROBIN));
        if (strategy == null) {
            return List.of();
        }
        List<ModelInstance> ordered = strategy.order(tierId, members, states);
        List<ModelRouteCandidate> result = new ArrayList<>(ordered.size());
        for (ModelInstance instance : ordered) {
            String excludedReason = exclusionReason(policy, tier, instance, context, states.get(instance.getId()));
            boolean available = excludedReason == null;
            if (fallback && available) {
                excludedReason = "fallback-tier";
            }
            result.add(new ModelRouteCandidate(tierId, instance, available, excludedReason));
        }
        return List.copyOf(result);
    }

    private String exclusionReason(RoutePolicyDefinition policy, ModelTierDefinition tier, ModelInstance instance,
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
        Integer estimatedInputTokens = context.getEstimatedInputTokens();
        if (estimatedInputTokens != null && policy.getMaxInputTokens() != null
                && estimatedInputTokens > policy.getMaxInputTokens()) {
            return "INPUT_TOKEN_LIMIT_EXCEEDED";
        }
        if (estimatedInputTokens != null && instance.getContextWindowTokens() != null) {
            long reservedOutputTokens = context.getReservedOutputTokens() == null
                    ? 0L : Math.max(0L, context.getReservedOutputTokens());
            long requestedTokens = (long) estimatedInputTokens + reservedOutputTokens;
            if (requestedTokens > instance.getContextWindowTokens()) {
                return "CONTEXT_WINDOW_EXCEEDED";
            }
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
                .runId(context == null ? null : context.getRunId())
                .userId(context == null ? null : context.getUserId())
                .language(normalize(valueOrDefault(context == null ? null : context.getLanguage(),
                        properties.getDefaultLanguage())))
                .scene(normalize(valueOrDefault(context == null ? null : context.getScene(),
                        properties.getDefaultScene())))
                .riskLevel(context == null ? null : context.getRiskLevel())
                .estimatedInputTokens(context == null ? null : context.getEstimatedInputTokens())
                .reservedOutputTokens(context == null ? null : context.getReservedOutputTokens())
                .maskSensitiveData(context == null || context.isMaskSensitiveData())
                .build();
    }

    private ModelRouteContext applyRouteBudget(ModelRouteContext context, RoutePolicyDefinition policy) {
        Integer routeOutputTokens = smallerPositive(policy.getMaxOutputTokens(), policy.getMaxCompletionTokens());
        if (routeOutputTokens == null) {
            routeOutputTokens = ModelRouteParameters.DEFAULT_OUTPUT_TOKENS;
        }
        Integer requestedOutputTokens = context.getReservedOutputTokens();
        Integer reservedOutputTokens;
        if (requestedOutputTokens == null) {
            reservedOutputTokens = routeOutputTokens;
        } else {
            reservedOutputTokens = Math.min(requestedOutputTokens, routeOutputTokens);
        }
        return ModelRouteContext.builder()
                .requestId(context.getRequestId())
                .runId(context.getRunId())
                .userId(context.getUserId())
                .language(context.getLanguage())
                .scene(context.getScene())
                .riskLevel(context.getRiskLevel())
                .estimatedInputTokens(context.getEstimatedInputTokens())
                .reservedOutputTokens(reservedOutputTokens)
                .maskSensitiveData(context.isMaskSensitiveData())
                .build();
    }

    private Integer smallerPositive(Integer first, Integer second) {
        if (first == null || first <= 0) {
            return second == null || second <= 0 ? null : second;
        }
        if (second == null || second <= 0) {
            return first;
        }
        return Math.min(first, second);
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

    private String numberOrUnknown(Integer value) {
        return value == null ? "unknown" : value.toString();
    }
}
