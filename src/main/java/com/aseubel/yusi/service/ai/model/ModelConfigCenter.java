package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.config.ai.properties.ModelTierDefinition;
import com.aseubel.yusi.config.ai.properties.RoutePolicyDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModelConfigCenter {

    private static final String SECRET_PLACEHOLDER = "******";

    private final ModelRoutingProperties bootstrapProperties;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final AtomicReference<ModelRoutingProperties> currentConfig = new AtomicReference<>();

    @PostConstruct
    public void init() {
        ModelRoutingProperties initial = normalizeLegacyConfig(cloneConfig(bootstrapProperties));
        String raw = redissonClient.<String>getBucket(bootstrapProperties.getRuntimeConfigKey()).get();
        if (raw != null && !raw.isBlank()) {
            try {
                initial = normalizeLegacyConfig(objectMapper.readValue(raw, ModelRoutingProperties.class));
                log.info("Loaded runtime model config from Redis");
            } catch (Exception e) {
                log.warn("Failed to parse runtime model config from Redis, fallback bootstrap config");
            }
        }
        apply(initial, false);
        redissonClient.getTopic(bootstrapProperties.getConfigChannel()).addListener(String.class, (channel, message) -> {
            if (message == null || message.isBlank()) {
                return;
            }
            try {
                ModelRoutingProperties incoming = objectMapper.readValue(message, ModelRoutingProperties.class);
                apply(incoming, false);
            } catch (Exception e) {
                log.warn("Failed to consume model config event: {}", e.getMessage());
            }
        });
    }

    public ModelRoutingProperties getEffectiveConfig() {
        ModelRoutingProperties config = currentConfig.get();
        if (config == null) {
            return normalizeLegacyConfig(cloneConfig(bootstrapProperties));
        }
        return cloneConfig(config);
    }

    public ModelRoutingProperties getConfigForDisplay() {
        ModelRoutingProperties config = getEffectiveConfig();
        if (config.getModels() != null) {
            config.getModels().forEach(model -> {
                if (model.getApikey() != null && !model.getApikey().isBlank()) {
                    model.setApikey(SECRET_PLACEHOLDER);
                }
            });
        }
        return config;
    }

    public void updateFromAdmin(ModelRoutingProperties request) {
        ModelRoutingProperties merged = normalizeLegacyConfig(mergeSecrets(request, currentConfig.get()));
        validateForAdmin(merged);
        apply(merged, true);
    }

    ModelRoutingProperties normalizeLegacyConfig(ModelRoutingProperties source) {
        ModelRoutingProperties normalized = cloneConfig(source);
        if (normalized.getTiers() == null) {
            normalized.setTiers(new LinkedHashMap<>());
        }
        if (normalized.getRoutes() == null) {
            normalized.setRoutes(new ArrayList<>());
        }

        if (normalized.getSchemaVersion() >= 2
                && !normalized.getTiers().isEmpty()
                && !normalized.getRoutes().isEmpty()) {
            return normalized;
        }

        if (normalized.getGroups() != null) {
            normalized.getGroups().forEach((groupId, groupDefinition) -> {
                if (normalized.getTiers().containsKey(groupId)) {
                    return;
                }
                ModelTierDefinition tier = new ModelTierDefinition();
                tier.setMembers(groupDefinition == null || groupDefinition.getMembers() == null
                        ? new ArrayList<>() : new ArrayList<>(groupDefinition.getMembers()));
                tier.setStrategy(groupDefinition == null || groupDefinition.getStrategy() == null
                        ? ModelSelectionStrategyType.ROUND_ROBIN : groupDefinition.getStrategy());
                tier.setDisplayName(groupId);
                normalized.getTiers().put(groupId, tier);
            });
        }

        if (normalized.getRoutes().isEmpty() && normalized.getMatrix() != null) {
            normalized.getMatrix().forEach((language, sceneMap) -> {
                if (sceneMap == null) {
                    return;
                }
                sceneMap.forEach((scene, sceneDefinition) -> {
                    if (sceneDefinition == null || sceneDefinition.getGroup() == null
                            || sceneDefinition.getGroup().isBlank()) {
                        return;
                    }
                    RoutePolicyDefinition route = new RoutePolicyDefinition();
                    route.setId(normalize(language) + "-" + normalize(scene));
                    route.setLanguage(normalize(language));
                    route.setScene(normalize(scene));
                    route.setPrimaryTier(sceneDefinition.getGroup());
                    route.setMaxOutputTokens(sceneDefinition.getMaxTokens());
                    route.setTemperature(sceneDefinition.getTemperature());
                    route.setTopP(sceneDefinition.getTopP());
                    route.setMaxCompletionTokens(sceneDefinition.getMaxCompletionTokens());
                    route.setCustomParameters(sceneDefinition.getCustomParameters() == null
                            ? new LinkedHashMap<>() : new LinkedHashMap<>(sceneDefinition.getCustomParameters()));
                    normalized.getRoutes().add(route);
                });
            });
        }

        if (normalized.getDefaultTier() == null || normalized.getDefaultTier().isBlank()) {
            normalized.setDefaultTier(resolveLegacyDefaultTier(normalized));
        }
        if (normalized.getDefaultRoute() == null && normalized.getDefaultTier() != null) {
            RoutePolicyDefinition defaultRoute = new RoutePolicyDefinition();
            defaultRoute.setId("default");
            defaultRoute.setLanguage("*");
            defaultRoute.setScene(normalized.getDefaultScene());
            defaultRoute.setPrimaryTier(normalized.getDefaultTier());
            defaultRoute.setPriority(0);
            normalized.setDefaultRoute(defaultRoute);
        }
        normalized.setSchemaVersion(2);
        return normalized;
    }

    void validateForAdmin(ModelRoutingProperties config) {
        validate(normalizeLegacyConfig(config));
    }

    private String resolveLegacyDefaultTier(ModelRoutingProperties config) {
        Map<String, ModelRoutingProperties.SceneDefinition> defaultScenes = config.getMatrix()
                .get(normalize(config.getDefaultLanguage()));
        if (defaultScenes != null) {
            ModelRoutingProperties.SceneDefinition defaultScene = defaultScenes.get(normalize(config.getDefaultScene()));
            if (defaultScene != null && defaultScene.getGroup() != null
                    && config.getTiers().containsKey(defaultScene.getGroup())) {
                return defaultScene.getGroup();
            }
        }
        return config.getTiers().keySet().stream().findFirst().orElse(null);
    }

    public String secretPlaceholder() {
        return SECRET_PLACEHOLDER;
    }

    private synchronized void apply(ModelRoutingProperties config, boolean publish) {
        ModelRoutingProperties cloned = cloneConfig(config);
        currentConfig.set(cloned);
        eventPublisher.publishEvent(new ModelConfigUpdatedEvent(this, cloned));
        if (!publish) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(cloned);
            redissonClient.<String>getBucket(bootstrapProperties.getRuntimeConfigKey()).set(json);
            redissonClient.getTopic(bootstrapProperties.getConfigChannel()).publish(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize model config", e);
        }
    }

    private ModelRoutingProperties mergeSecrets(ModelRoutingProperties incoming, ModelRoutingProperties current) {
        ModelRoutingProperties merged = cloneConfig(incoming);
        if (merged.getModels() == null || current == null || current.getModels() == null) {
            return merged;
        }
        for (ModelRoutingProperties.ModelDefinition model : merged.getModels()) {
            if (model.getId() == null || model.getId().isBlank()) {
                continue;
            }
            if (model.getApikey() == null || model.getApikey().isBlank() || SECRET_PLACEHOLDER.equals(model.getApikey())) {
                current.getModels().stream()
                        .filter(old -> Objects.equals(old.getId(), model.getId()))
                        .findFirst()
                        .ifPresent(old -> model.setApikey(old.getApikey()));
            }
        }
        return merged;
    }

    private void validate(ModelRoutingProperties config) {
        if (config.getModels() == null || config.getModels().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "models 不能为空");
        }
        Set<String> modelIds = new HashSet<>();
        config.getModels().forEach(model -> {
            if (model.getId() == null || model.getId().isBlank()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "model.id 不能为空");
            }
            if (!modelIds.add(model.getId())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "model.id 重复: " + model.getId());
            }
        });

        if (config.getTiers() == null || config.getTiers().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "tiers 不能为空");
        }
        config.getTiers().forEach((tierId, definition) -> {
            if (definition == null || definition.getMembers() == null || definition.getMembers().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "tier[" + tierId + "] 必须至少包含一个成员");
            }
            definition.getMembers().forEach(member -> {
                if (!modelIds.contains(member)) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR, "tier[" + tierId + "] 引用了不存在的模型: " + member);
                }
            });
        });

        if (config.getRoutes() == null || config.getRoutes().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "routes 不能为空");
        }
        Set<String> routeIds = new HashSet<>();
        config.getRoutes().forEach(route -> {
            if (route == null || route.getId() == null || route.getId().isBlank()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "route.id 不能为空");
            }
            if (!routeIds.add(route.getId())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "route.id 重复: " + route.getId());
            }
            if (route.getScene() == null || route.getScene().isBlank()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "route[" + route.getId() + "] 的 scene 不能为空");
            }
            if (route.getLanguage() == null || route.getLanguage().isBlank()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "route[" + route.getId() + "] 的 language 不能为空");
            }
            ModelTierDefinition primary = config.getTiers().get(route.getPrimaryTier());
            if (primary == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR,
                        "route[" + route.getId() + "] 的 primary-tier 不存在: " + route.getPrimaryTier());
            }
            if (!primary.isEnabled()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR,
                        "route[" + route.getId() + "] 的 primary-tier 已禁用: " + route.getPrimaryTier());
            }
            Set<String> fallbackIds = new HashSet<>();
            if (route.getFallbackTiers() != null) {
                route.getFallbackTiers().forEach(fallback -> {
                    if (!fallbackIds.add(fallback)) {
                        throw new BusinessException(ErrorCode.PARAM_ERROR,
                                "route[" + route.getId() + "] 的 fallback-tier 重复: " + fallback);
                    }
                    if (!config.getTiers().containsKey(fallback)) {
                        throw new BusinessException(ErrorCode.PARAM_ERROR,
                                "route[" + route.getId() + "] 的 fallback-tier 不存在: " + fallback);
                    }
                });
            }
            if (route.getMaxInputTokens() != null && route.getMaxInputTokens() < 0
                    || route.getMaxOutputTokens() != null && route.getMaxOutputTokens() < 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR,
                        "route[" + route.getId() + "] 的 token 限制不能为负数");
            }
        });

        if (config.getCapabilityGroups() != null) {
            config.getCapabilityGroups().forEach((capabilityName, groupName) -> {
                ModelCapability capability;
                try {
                    capability = ModelCapability.valueOf(capabilityName.toUpperCase(Locale.ROOT)
                            .replace('-', '_'));
                } catch (Exception e) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR,
                            "不支持的模型 capability: " + capabilityName);
                }
                ModelTierDefinition tier = config.getTiers().get(groupName);
                if (tier == null || tier.getMembers() == null || tier.getMembers().isEmpty()) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR,
                        "capability[" + capabilityName + "] 引用了不存在或为空的分组: " + groupName);
                }
                tier.getMembers().forEach(member -> config.getModels().stream()
                        .filter(model -> Objects.equals(model.getId(), member))
                        .findFirst()
                        .filter(model -> model.supports(capability))
                        .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_ERROR,
                                "capability[" + capabilityName + "] 的成员不支持该能力: " + member)));
            });
        }
        if (config.getMatrix() != null) {
            config.getMatrix().forEach((lang, sceneMap) -> {
                if (sceneMap == null) {
                    return;
                }
                sceneMap.forEach((scene, sceneDef) -> {
                    if (sceneDef == null || sceneDef.getGroup() == null || sceneDef.getGroup().isBlank()) {
                        throw new BusinessException(ErrorCode.PARAM_ERROR,
                                "matrix[" + normalize(lang) + "." + normalize(scene) + "] 的 group 不能为空");
                    }
                    if (!config.getGroups().containsKey(sceneDef.getGroup())) {
                        throw new BusinessException(ErrorCode.PARAM_ERROR,
                                "matrix[" + normalize(lang) + "." + normalize(scene) + "] 引用了不存在的分组: " + sceneDef.getGroup());
                    }
                });
            });
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private ModelRoutingProperties cloneConfig(ModelRoutingProperties source) {
        return objectMapper.convertValue(source, ModelRoutingProperties.class);
    }
}
