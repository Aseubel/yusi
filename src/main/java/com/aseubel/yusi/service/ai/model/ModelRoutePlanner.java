package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.config.ai.properties.ModelTierDefinition;
import com.aseubel.yusi.config.ai.properties.RoutePolicyDefinition;
import com.aseubel.yusi.service.ai.model.constant.ModelHealthPhase;
import com.aseubel.yusi.service.ai.model.constant.ModelRouteExclusionReason;
import com.aseubel.yusi.service.ai.model.strategy.ModelSelectionStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Calculates a route from supplied metadata and runtime state without creating provider clients.
 * The same calculation is used by live routing and the management draft preview.
 */
@Component
@RequiredArgsConstructor
public class ModelRoutePlanner {

    private final ModelStrategyRegistry strategyRegistry;
    private final ModelStateCenter modelStateCenter;
    private final ModelRoutePolicyMatcher routePolicyMatcher = new ModelRoutePolicyMatcher();

    public ModelRouteDecision plan(ModelRoutingProperties properties, ModelRouteContext context,
            Map<String, List<ModelInstance>> tierMembers, Map<String, ModelRuntimeState> states) {
        ModelRouteContext normalizedContext = normalizeContext(context, properties);
        ModelRoutePolicyMatcher.MatchResult routeMatch = routePolicyMatcher.matchWithReason(properties, normalizedContext);
        if (routeMatch == null || routeMatch.route() == null
                || routeMatch.route().getPrimaryTier() == null
                || routeMatch.route().getPrimaryTier().isBlank()) {
            throw new IllegalStateException("No model route configured for scene: "
                    + normalizedContext.getScene());
        }
        RoutePolicyDefinition policy = routeMatch.route();

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

        Map<String, List<ModelInstance>> safeTierMembers = tierMembers == null ? Map.of() : tierMembers;
        Map<String, ModelRuntimeState> safeStates = states == null ? Map.of() : states;
        Map<ModelSelectionStrategyType, ModelSelectionStrategy> strategies = strategyRegistry.build();
        List<ModelRouteCandidate> candidates = new ArrayList<>();
        Set<String> healthReasons = new LinkedHashSet<>();
        for (int index = 0; index < tierOrder.size(); index++) {
                    String tierId = tierOrder.get(index);
                    boolean fallback = index > 0;
            List<ModelRouteCandidate> tierCandidates = routeTier(properties, tierId, policy,
                    budgetContext, safeTierMembers, safeStates, strategies, fallback,
                    candidates.size() + 1);
            candidates.addAll(tierCandidates);
            tierCandidates.stream()
                    .map(ModelRouteCandidate::excludedReason)
                    .filter(Objects::nonNull)
                    .filter(reason -> !ModelRouteExclusionReason.FALLBACK_TIER.code().equals(reason))
                    .forEach(healthReasons::add);
        }

        ModelTierDefinition primaryDefinition = properties.getTiers() == null
                ? null : properties.getTiers().get(primaryTier);
        ModelSelectionStrategyType primaryStrategy = strategyType(primaryDefinition);
        String routeReason = "policy=" + safe(policy.getId(), "default")
                + ";policy-version=" + properties.getVersion()
                + ";scene=" + normalizedContext.getScene()
                + ";risk=" + safe(normalizedContext.getRiskLevel(), "any")
                + ";route-priority=" + policy.getPriority()
                + ";estimated-input-tokens=" + numberOrUnknown(budgetContext.getEstimatedInputTokens())
                + ";reserved-output-tokens=" + numberOrUnknown(budgetContext.getReservedOutputTokens())
                + ";primary-tier=" + primaryTier
                + ";strategy=" + primaryStrategy.name()
                + ";tier-strategies=" + tierOrder.stream()
                        .map(tier -> tier + "=" + strategyType(properties.getTiers() == null
                                ? null : properties.getTiers().get(tier)).name())
                        .collect(java.util.stream.Collectors.joining(","))
                + ";fallback-tiers=" + String.join(",", fallbackTiers)
                + ";health-filter=" + (healthReasons.isEmpty() ? "none" : String.join(",", healthReasons));
        return new ModelRouteDecision(budgetContext.getRequestId(), policy.getId(),
                properties.getVersion(), primaryTier, fallbackTiers, candidates, routeReason,
                ModelRouteParameters.from(policy),
                new ModelRouteDecision.RouteReason(policy.getId(), routeMatch.sceneMatchLevel(),
                        routeMatch.riskMatchLevel(), policy.getPriority(), primaryTier, fallbackTiers,
                        tierOrder.stream()
                                .map(tier -> tier + "=" + strategyType(properties.getTiers() == null
                                        ? null : properties.getTiers().get(tier)).name())
                                .toList()));
    }

    private List<ModelRouteCandidate> routeTier(ModelRoutingProperties properties, String tierId,
            RoutePolicyDefinition policy, ModelRouteContext context,
            Map<String, List<ModelInstance>> tierMembers,
            Map<String, ModelRuntimeState> states,
            Map<ModelSelectionStrategyType, ModelSelectionStrategy> strategies,
            boolean fallback, int rankStart) {
        ModelTierDefinition tier = properties.getTiers() == null ? null : properties.getTiers().get(tierId);
        List<ModelInstance> members = tierMembers.getOrDefault(tierId, List.of());
        ModelSelectionStrategyType strategyType = strategyType(tier);
        ModelSelectionStrategy strategy = strategies.get(strategyType);
        if (strategy == null) {
            return List.of();
        }

        List<ModelInstance> eligibleForOrdering = members.stream()
                .filter(Objects::nonNull)
                .filter(ModelInstance::isEnabled)
                .filter(instance -> supportsTierCapabilities(tier, instance))
                .filter(instance -> ModelCapabilityPolicy.supportsScene(instance, context.getScene()))
                .filter(instance -> supportsValue(instance.getScenes(), context.getScene()))
                .toList();
        List<ModelInstance> ordered = new ArrayList<>(strategy.order(tierId, eligibleForOrdering, states));
        Set<String> orderedIds = ordered.stream().map(ModelInstance::getId)
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        members.stream().filter(instance -> !orderedIds.contains(instance.getId())).forEach(ordered::add);

        // Strategies determine the order inside the eligible group. Keep excluded members visible,
        // but move them behind attemptable members so the preview mirrors the production chain.
        List<ModelInstance> attemptable = ordered.stream()
                .filter(instance -> exclusionReason(policy, tier, instance, context,
                        states.get(instance.getId()), strategyType) == null)
                .toList();
        List<ModelInstance> excluded = ordered.stream()
                .filter(instance -> !attemptable.contains(instance))
                .toList();
        ordered = new ArrayList<>(attemptable.size() + excluded.size());
        ordered.addAll(attemptable);
        ordered.addAll(excluded);

        List<ModelRouteCandidate> result = new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            ModelInstance instance = ordered.get(index);
            ModelRuntimeState state = states.get(instance.getId());
            String excludedReason = exclusionReason(policy, tier, instance, context, state, strategyType);
            boolean available = excludedReason == null;
            if (fallback && available) {
                excludedReason = ModelRouteExclusionReason.FALLBACK_TIER.code();
            }
            result.add(new ModelRouteCandidate(tierId, instance, available, excludedReason, strategyType,
                    rankStart + index, fallback, instance.getPriority(), instance.getWeight(),
                    state == null ? 0D : state.getAvgLatencyMs(), state == null ? "UNKNOWN" : state.getPhase(),
                    explanation(excludedReason)));
        }
        return List.copyOf(result);
    }

    private String exclusionReason(RoutePolicyDefinition policy, ModelTierDefinition tier,
            ModelInstance instance, ModelRouteContext context, ModelRuntimeState state,
            ModelSelectionStrategyType strategyType) {
        if (tier == null || !tier.isEnabled()) {
            return ModelRouteExclusionReason.TIER_DISABLED.code();
        }
        if (instance == null || !instance.isRegistered()) {
            return ModelRouteExclusionReason.MODEL_UNKNOWN.code();
        }
        if (!instance.isEnabled()) {
            return ModelRouteExclusionReason.MODEL_DISABLED.code();
        }
        if (!supportsTierCapabilities(tier, instance)) {
            return ModelRouteExclusionReason.TIER_CAPABILITY_MISMATCH.code();
        }
        if (!ModelCapabilityPolicy.supportsScene(instance, context.getScene())) {
            return ModelRouteExclusionReason.UNSUPPORTED_CAPABILITY.code();
        }
        if (!supportsValue(instance.getScenes(), context.getScene())) {
            return ModelRouteExclusionReason.SCENE_MISMATCH.code();
        }
        if (strategyType == ModelSelectionStrategyType.WEIGHTED_RANDOM && instance.getWeight() <= 0) {
            return ModelRouteExclusionReason.ZERO_WEIGHT.code();
        }
        Integer estimatedInputTokens = context.getEstimatedInputTokens();
        if (estimatedInputTokens != null && policy.getMaxInputTokens() != null
                && estimatedInputTokens > policy.getMaxInputTokens()) {
            return ModelRouteExclusionReason.INPUT_TOKEN_LIMIT_EXCEEDED.code();
        }
        if (estimatedInputTokens != null && instance.getContextWindowTokens() != null) {
            long reservedOutputTokens = context.getReservedOutputTokens() == null
                    ? 0L : Math.max(0L, context.getReservedOutputTokens());
            if ((long) estimatedInputTokens + reservedOutputTokens > instance.getContextWindowTokens()) {
                return ModelRouteExclusionReason.CONTEXT_WINDOW_EXCEEDED.code();
            }
        }
        if (state != null && !state.isAvailable()
                && !ModelHealthPhase.HALF_OPEN.code().equalsIgnoreCase(state.getPhase())
                && !modelStateCenter.isProbeDue(state)) {
            return state.getPhase() == null || state.getPhase().isBlank()
                    ? "DOWN" : state.getPhase().toUpperCase(Locale.ROOT);
        }
        return null;
    }

    private boolean supportsTierCapabilities(ModelTierDefinition tier, ModelInstance instance) {
        if (tier == null || instance == null || tier.getCapabilities() == null
                || tier.getCapabilities().isEmpty()) {
            return true;
        }
        Set<ModelCapability> capabilities = instance.getCapabilities();
        return tier.getCapabilities().stream()
                .allMatch(capability -> capabilities != null && capabilities.contains(capability));
    }

    private boolean supportsValue(Set<String> values, String expected) {
        return values == null || values.isEmpty() || values.contains(normalize(expected));
    }

    private ModelSelectionStrategyType strategyType(ModelTierDefinition tier) {
        return tier == null || tier.getStrategy() == null
                ? ModelSelectionStrategyType.ROUND_ROBIN : tier.getStrategy();
    }

    private String explanation(String reason) {
        if (reason == null) return null;
        return switch (reason) {
            case "fallback-tier" -> "备用 tier，仅在主候选链耗尽且错误允许回退时尝试";
            case "TIER_DISABLED" -> "tier 已禁用";
            case "TIER_CAPABILITY_MISMATCH" -> "模型不满足 tier 能力要求";
            case "UNSUPPORTED_CAPABILITY" -> "模型不满足当前场景能力要求";
            case "SCENE_MISMATCH" -> "模型未声明支持当前场景";
            case "MODEL_DISABLED" -> "模型已停用";
            case "MODEL_UNKNOWN" -> "模型未注册或尚未加载到实例注册表";
            case "ZERO_WEIGHT" -> "权重为 0，不参与加权随机";
            case "INPUT_TOKEN_LIMIT_EXCEEDED" -> "超过 route 输入 token 上限";
            case "CONTEXT_WINDOW_EXCEEDED" -> "输入和预留输出超过模型上下文窗口";
            case "HALF_OPEN" -> "运行态处于半开探测阶段";
            case "DOWN" -> "运行态为 DOWN，等待探测窗口";
            default -> "运行态不可用";
        };
    }

    private ModelRouteContext normalizeContext(ModelRouteContext context, ModelRoutingProperties properties) {
        return ModelRouteContext.builder()
                .requestId(context == null ? null : context.getRequestId())
                .runId(context == null ? null : context.getRunId())
                .userId(context == null ? null : context.getUserId())
                .scene(normalize(valueOrDefault(context == null ? null : context.getScene(),
                        properties.getDefaultScene())))
                .promptKey(context == null ? null : context.getPromptKey())
                .promptVersion(context == null ? null : context.getPromptVersion())
                .promptLocale(context == null ? null : context.getPromptLocale())
                .riskLevel(context == null ? null : context.getRiskLevel())
                .estimatedInputTokens(context == null ? null : context.getEstimatedInputTokens())
                .reservedOutputTokens(context == null ? null : context.getReservedOutputTokens())
                .maskSensitiveData(context == null || context.isMaskSensitiveData())
                .build();
    }

    private ModelRouteContext applyRouteBudget(ModelRouteContext context, RoutePolicyDefinition policy) {
        Integer routeOutputTokens = smallerPositive(policy.getMaxOutputTokens(), policy.getMaxCompletionTokens());
        if (routeOutputTokens == null) routeOutputTokens = ModelRouteParameters.DEFAULT_OUTPUT_TOKENS;
        Integer requestedOutputTokens = context.getReservedOutputTokens();
        int reservedOutputTokens = requestedOutputTokens == null
                ? routeOutputTokens : Math.min(requestedOutputTokens, routeOutputTokens);
        return ModelRouteContext.builder()
                .requestId(context.getRequestId()).runId(context.getRunId()).userId(context.getUserId())
                .scene(context.getScene()).promptKey(context.getPromptKey())
                .promptVersion(context.getPromptVersion()).promptLocale(context.getPromptLocale())
                .riskLevel(context.getRiskLevel()).estimatedInputTokens(context.getEstimatedInputTokens())
                .reservedOutputTokens(reservedOutputTokens).maskSensitiveData(context.isMaskSensitiveData()).build();
    }

    private Integer smallerPositive(Integer first, Integer second) {
        if (first == null || first <= 0) return second == null || second <= 0 ? null : second;
        if (second == null || second <= 0) return first;
        return Math.min(first, second);
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String numberOrUnknown(Integer value) {
        return value == null ? "unknown" : value.toString();
    }
}
