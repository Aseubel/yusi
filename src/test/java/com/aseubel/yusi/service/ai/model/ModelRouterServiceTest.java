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
    private ModelRoutingProperties properties;
    private ModelRouterService router;

    @BeforeEach
    void setUp() {
        properties = config();
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
        ModelRouteDecision decision = router.plan(context("summary"));

        assertThat(decision.candidates()).extracting(ModelRouteCandidate::tierId)
                .containsExactly("fast", "fast", "balanced", "balanced");
        assertThat(decision.candidates().get(2).excludedReason()).isEqualTo("fallback-tier");
    }

    @Test
    void unavailablePrimaryModelIsRecordedAndHealthyFallbackRemainsSelectable() {
        ModelRouteDecision decision = router.plan(context("chat"));

        assertThat(decision.candidates()).anyMatch(candidate ->
                candidate.modelId().equals("qwen") && "DOWN".equals(candidate.excludedReason()));
        assertThat(decision.candidates()).anyMatch(candidate ->
                candidate.tierId().equals("fast") && candidate.available());
        assertThat(decision.routeReason()).contains("policy=chat", "scene=chat");
    }

    @Test
    void contextWindowExcludesShortPrimaryAndKeepsLongFallback() {
        ModelInstance shortPrimary = instance("short-primary", 1, 24);
        ModelInstance longFallback = instance("long-fallback", 1, 128);
        when(instanceRegistry.getTierMembers("balanced")).thenReturn(List.of(shortPrimary));
        when(instanceRegistry.getTierMembers("fast")).thenReturn(List.of(longFallback));

        ModelRouteDecision decision = router.plan(ModelRouteContext.builder()
                .scene("chat")
                .estimatedInputTokens(20)
                .reservedOutputTokens(16)
                .build());

        assertThat(decision.candidates()).anyMatch(candidate ->
                candidate.modelId().equals("short-primary")
                        && candidate.excludedReason().equals("CONTEXT_WINDOW_EXCEEDED"));
        assertThat(decision.attemptCandidates()).extracting(ModelRouteCandidate::modelId)
                .containsExactly("long-fallback");
        assertThat(decision.routeReason()).contains(
                "estimated-input-tokens=20", "reserved-output-tokens=16");
    }

    @Test
    void routeInputLimitExcludesCandidatesBeforeInvocation() {
        RoutePolicyDefinition route = properties.getRoutes().stream()
                .filter(candidate -> candidate.getId().equals("chat"))
                .findFirst()
                .orElseThrow();
        route.setMaxInputTokens(10);

        ModelRouteDecision decision = router.plan(ModelRouteContext.builder()
                .scene("chat")
                .estimatedInputTokens(11)
                .build());

        assertThat(decision.candidates()).allMatch(candidate ->
                "INPUT_TOKEN_LIMIT_EXCEEDED".equals(candidate.excludedReason())
                        || "fallback-tier".equals(candidate.excludedReason()));
        assertThat(decision.attemptCandidates()).isEmpty();
    }

    @Test
    void imageSceneExcludesTextOnlyCandidates() {
        properties.setRoutes(List.of(route("image", "image-understanding", "fast", 100)));
        ModelInstance textOnly = instance("text-only", 1);
        when(instanceRegistry.getTierMembers("fast")).thenReturn(List.of(textOnly));

        ModelRouteDecision decision = router.plan(context("image-understanding"));

        assertThat(decision.candidates()).singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.modelId()).isEqualTo("text-only");
                    assertThat(candidate.available()).isFalse();
                    assertThat(candidate.excludedReason()).isEqualTo("UNSUPPORTED_CAPABILITY");
                });
        assertThat(decision.attemptCandidates()).isEmpty();
    }

    @Test
    void imageSceneSelectsAnExplicitVlmCandidate() {
        properties.setRoutes(List.of(route("image", "image-understanding", "fast", 100)));
        ModelInstance vision = ModelInstance.builder()
                .id("vision")
                .modelName("vision-model")
                .provider("openai-compatible")
                .priority(1)
                .weight(100)
                .capabilities(Set.of(ModelCapability.VLM))
                .scenes(Set.of("image-understanding"))
                .build();
        when(instanceRegistry.getTierMembers("fast")).thenReturn(List.of(vision));

        ModelRouteDecision decision = router.plan(context("image-understanding"));

        assertThat(decision.attemptCandidates()).extracting(ModelRouteCandidate::modelId)
                .containsExactly("vision");
    }

    private ModelRouteContext context(String scene) {
        return ModelRouteContext.builder().scene(scene).build();
    }

    private ModelRoutingProperties config() {
        ModelRoutingProperties properties = new ModelRoutingProperties();
        properties.setDefaultTier("fast");
        properties.setDefaultRoute(route("default", "*", "fast", 0));
        properties.setRoutes(List.of(
                routeWithFallback("chat", "chat", "balanced", List.of("fast"), 100),
                routeWithFallback("summary", "summary", "fast", List.of("balanced"), 100)));
        properties.setTiers(new LinkedHashMap<>(Map.of(
                "balanced", tier(List.of("qwen", "balanced-backup"), FAIL_OVER),
                "fast", tier(List.of("fast-primary", "fast-backup"), FAIL_OVER))));
        return properties;
    }

    private RoutePolicyDefinition route(String id, String scene, String tier, int priority) {
        return routeWithFallback(id, scene, tier, List.of(), priority);
    }

    private RoutePolicyDefinition routeWithFallback(String id, String scene, String tier,
            List<String> fallbackTiers, int priority) {
        RoutePolicyDefinition route = new RoutePolicyDefinition();
        route.setId(id);
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
        return instance(id, priority, null);
    }

    private ModelInstance instance(String id, int priority, Integer contextWindowTokens) {
        return ModelInstance.builder()
                .id(id)
                .modelName(id)
                .provider("openai-compatible")
                .priority(priority)
                .weight(100)
                .capabilities(Set.of(ModelCapability.CHAT, ModelCapability.STREAMING_CHAT))
                .scenes(Set.of())
                .contextWindowTokens(contextWindowTokens)
                .build();
    }
}
