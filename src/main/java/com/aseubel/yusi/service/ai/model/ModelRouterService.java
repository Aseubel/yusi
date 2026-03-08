package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.service.ai.model.strategy.ModelSelectionStrategy;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ModelRouterService {

    private final ModelConfigCenter modelConfigCenter;
    private final ModelInstanceRegistry modelInstanceRegistry;
    private final GroupStrategyManager groupStrategyManager;
    private final ModelStrategyRegistry modelStrategyRegistry;
    private final ModelStateCenter modelStateCenter;

    private Map<ModelSelectionStrategyType, ModelSelectionStrategy> strategies;

    @PostConstruct
    public void init() {
        this.strategies = modelStrategyRegistry.build();
    }

    public ModelInstance select(ModelRouteContext context) {
        return select(context, Set.of());
    }

    public ModelInstance select(ModelRouteContext context, Set<String> excludedIds) {
        ModelRoutingProperties properties = modelConfigCenter.getEffectiveConfig();
        String language = normalize(valueOrDefault(context == null ? null : context.getLanguage(), properties.getDefaultLanguage()));
        String scene = normalize(valueOrDefault(context == null ? null : context.getScene(), properties.getDefaultScene()));
        String group = valueOrDefault(context == null ? null : context.getGroup(), resolveGroup(language, scene));
        List<ModelInstance> members = modelInstanceRegistry.getGroupMembers(group);
        List<ModelInstance> candidates = modelInstanceRegistry.filterByLanguageAndScene(members, language, scene);
        if (!excludedIds.isEmpty()) {
            candidates = candidates.stream().filter(candidate -> !excludedIds.contains(candidate.getId())).toList();
        }
        Map<String, ModelRuntimeState> states = modelStateCenter.snapshot(candidates.stream().map(ModelInstance::getId).toList());
        ModelSelectionStrategyType strategyType = groupStrategyManager.getStrategy(group);
        ModelSelectionStrategy strategy = strategies.getOrDefault(strategyType,
                strategies.get(ModelSelectionStrategyType.ROUND_ROBIN));
        Optional<ModelInstance> selected = strategy.select(group, candidates, states);
        return selected.orElseGet(() -> {
            if (!members.isEmpty()) {
                return members.get(0);
            }
            throw new IllegalStateException("No model candidate for group: " + group);
        });
    }

    public String resolveGroup(String language, String scene) {
        ModelRoutingProperties properties = modelConfigCenter.getEffectiveConfig();
        Map<String, ModelRoutingProperties.SceneDefinition> sceneMap = properties.getMatrix().get(language);
        if (sceneMap != null) {
            ModelRoutingProperties.SceneDefinition sceneDef = sceneMap.get(scene);
            if (sceneDef != null && sceneDef.getGroup() != null && !sceneDef.getGroup().isBlank()) {
                return sceneDef.getGroup();
            }
        }
        Map<String, ModelRoutingProperties.SceneDefinition> defaultSceneMap = properties.getMatrix().get(normalize(properties.getDefaultLanguage()));
        if (defaultSceneMap != null) {
            ModelRoutingProperties.SceneDefinition fallback = defaultSceneMap.get(scene);
            if (fallback != null && fallback.getGroup() != null && !fallback.getGroup().isBlank()) {
                return fallback.getGroup();
            }
        }
        if (!properties.getGroups().isEmpty()) {
            return properties.getGroups().keySet().iterator().next();
        }
        throw new IllegalStateException("No model group configured");
    }

    public ModelRoutingProperties.SceneDefinition resolveSceneDefinition(String language, String scene) {
        ModelRoutingProperties properties = modelConfigCenter.getEffectiveConfig();
        String normalizedLanguage = normalize(valueOrDefault(language, properties.getDefaultLanguage()));
        String normalizedScene = normalize(valueOrDefault(scene, properties.getDefaultScene()));
        Map<String, ModelRoutingProperties.SceneDefinition> sceneMap = properties.getMatrix().get(normalizedLanguage);
        if (sceneMap != null) {
            return sceneMap.get(normalizedScene);
        }
        return null;
    }

    private String valueOrDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
