package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.config.ai.properties.ModelTierDefinition;
import com.aseubel.yusi.config.ai.properties.RoutePolicyDefinition;
import com.aseubel.yusi.service.ai.model.strategy.FailOverSelectionStrategy;
import com.aseubel.yusi.service.ai.model.strategy.ModelSelectionStrategy;
import com.aseubel.yusi.service.ai.model.strategy.RoundRobinSelectionStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.aseubel.yusi.service.ai.model.ModelSelectionStrategyType.FAIL_OVER;
import static com.aseubel.yusi.service.ai.model.ModelSelectionStrategyType.ROUND_ROBIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelRouterServiceTest {

    private final ModelConfigCenter configCenter = mock(ModelConfigCenter.class);
    private final ModelInstanceRegistry instanceRegistry = mock(ModelInstanceRegistry.class);
    private final ModelStrategyRegistry strategyRegistry = mock(ModelStrategyRegistry.class);
    private final ModelStateCenter stateCenter = mock(ModelStateCenter.class);
    private ModelRouterService router;

    @BeforeEach
    void setUp() {
        ModelRoutingProperties properties = config();
        when(configCenter.getEffectiveConfig()).thenReturn(properties);
        when(strategyRegistry.build()).thenReturn(Map.of(
                ROUND_ROBIN, new RoundRobinSelectionStrategy(properties),
                FAIL_OVER, new FailOverSelectionStrategy(properties)));
        when(stateCenter.snapshot(anyCollection())).thenReturn(Map.of(
                "qwen", ModelRuntimeState.builder().instanceId("qwen").available(false).phase("DOWN").build()));

        ModelInstance qwen = instance("qwen", 1);
        ModelInstance balancedBackup = instance("balanced-backup", 2);
        ModelInstance fastPrimary = instance("fast-primary", 1);
        ModelInstance fastBackup = instance("fast-backup", 2);
        when(instanceRegistry.getTierMembers("balanced")).thenReturn(List.of(qwen, balancedBackup));
        when(instanceRegistry.getTierMembers("fast")).thenReturn(List.of(fastPrimary, fastBackup));

        router = new ModelRouterService(configCenter, instanceRegistry, strategyRegistry, stateCenter);
        router.init();
    }

    @Test
    void fallbackTierIsAppendedOnlyAfterPrimaryTierCandidates() {
        ModelRouteDecision decision = router.plan(context("zh", "summary"));

        assertThat(decision.candidates()).extracting(ModelRouteCandidate::tierId)
                .containsExactly("fast", "fast", "balanced", "balanced");
        assertThat(decision.candidates().get(2).excludedReason()).isEqualTo("fallback-tier");
    }

    @Test
    void unavailablePrimaryModelIsRecordedAndHealthyFallbackRemainsSelectable() {
        ModelRouteDecision decision = router.plan(context("zh", "chat"));

        assertThat(decision.candidates()).anyMatch(candidate ->
                candidate.modelId().equals("qwen") && "DOWN".equals(candidate.excludedReason()));
        assertThat(decision.candidates()).anyMatch(candidate ->
                candidate.tierId().equals("fast") && candidate.available());
        assertThat(decision.routeReason()).contains("policy=chat-zh", "language=zh", "scene=chat");
    }

    private ModelRouteContext context(String language, String scene) {
        return ModelRouteContext.builder().language(language).scene(scene).build();
    }

    private ModelRoutingProperties config() {
        ModelRoutingProperties properties = new ModelRoutingProperties();
        properties.setDefaultTier("fast");
        properties.setDefaultRoute(route("default", "*", "*", "fast", 0));
        properties.setRoutes(List.of(
                routeWithFallback("chat-zh", "zh", "chat", "balanced", List.of("fast"), 100),
                routeWithFallback("summary-zh", "zh", "summary", "fast", List.of("balanced"), 100)));
        properties.setTiers(new LinkedHashMap<>(Map.of(
                "balanced", tier(List.of("qwen", "balanced-backup"), FAIL_OVER),
                "fast", tier(List.of("fast-primary", "fast-backup"), FAIL_OVER))));
        return properties;
    }

    private RoutePolicyDefinition route(String id, String language, String scene, String tier, int priority) {
        return routeWithFallback(id, language, scene, tier, List.of(), priority);
    }

    private RoutePolicyDefinition routeWithFallback(String id, String language, String scene, String tier,
            List<String> fallbackTiers, int priority) {
        RoutePolicyDefinition route = new RoutePolicyDefinition();
        route.setId(id);
        route.setLanguage(language);
        route.setScene(scene);
        route.setPrimaryTier(tier);
        route.setFallbackTiers(fallbackTiers);
        route.setPriority(priority);
        return route;
    }

    private ModelTierDefinition tier(List<String> members, ModelSelectionStrategyType strategy) {
        ModelTierDefinition tier = new ModelTierDefinition();
        tier.setMembers(members);
        tier.setStrategy(strategy);
        return tier;
    }

    private ModelInstance instance(String id, int priority) {
        return ModelInstance.builder()
                .id(id)
                .modelName(id)
                .provider("openai-compatible")
                .priority(priority)
                .weight(100)
                .capabilities(Set.of(ModelCapability.CHAT, ModelCapability.STREAMING_CHAT))
                .languages(Set.of())
                .scenes(Set.of())
                .build();
    }
}
