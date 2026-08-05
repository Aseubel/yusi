package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.config.ai.properties.ModelTierDefinition;
import com.aseubel.yusi.config.ai.properties.RoutePolicyDefinition;
import com.aseubel.yusi.pojo.entity.ModelConfigChangeLog;
import com.aseubel.yusi.pojo.entity.ModelRuntimeConfig;
import com.aseubel.yusi.repository.ModelConfigChangeLogRepository;
import com.aseubel.yusi.repository.ModelRuntimeConfigRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class ModelConfigCenter {

    private static final String ACTIVE_CONFIG_KEY = "active";
    private static final String SECRET_PLACEHOLDER = "******";
    private static final String UPDATE_CONFIG = "UPDATE_CONFIG";
    private static final String SWITCH_STRATEGY = "SWITCH_STRATEGY";

    private final ModelRoutingProperties bootstrapProperties;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ModelRuntimeConfigRepository runtimeConfigRepository;
    private final ModelConfigChangeLogRepository changeLogRepository;
    private final AtomicReference<ModelRoutingProperties> currentConfig = new AtomicReference<>();

    /**
     * Kept for focused unit tests and compatibility with callers that construct the center directly.
     */
    public ModelConfigCenter(ModelRoutingProperties bootstrapProperties,
            RedissonClient redissonClient,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher) {
        this(bootstrapProperties, redissonClient, objectMapper, eventPublisher, null, null);
    }

    @Autowired
    public ModelConfigCenter(ModelRoutingProperties bootstrapProperties,
            RedissonClient redissonClient,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher,
            ModelRuntimeConfigRepository runtimeConfigRepository,
            ModelConfigChangeLogRepository changeLogRepository) {
        this.bootstrapProperties = bootstrapProperties;
        this.redissonClient = redissonClient;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.runtimeConfigRepository = runtimeConfigRepository;
        this.changeLogRepository = changeLogRepository;
    }

    @PostConstruct
    public void init() {
        ModelRoutingProperties initial = normalizeLegacyConfig(cloneConfig(bootstrapProperties));
        boolean loaded = false;

        if (runtimeConfigRepository != null) {
            try {
                Optional<ModelRuntimeConfig> snapshot = runtimeConfigRepository.findByConfigKey(ACTIVE_CONFIG_KEY);
                if (snapshot.isPresent() && snapshot.get().getConfigJson() != null) {
                    initial = readRuntimeConfig(snapshot.get().getConfigJson(), snapshot.get().getVersion());
                    loaded = true;
                    log.info("Loaded runtime model config from MySQL, version={}", initial.getVersion());
                }
            } catch (RuntimeException exception) {
                log.warn("Failed to load runtime model config from MySQL: {}", exception.getMessage());
            }
        }

        if (!loaded && redissonClient != null) {
            String raw = redissonClient.<String>getBucket(bootstrapProperties.getRuntimeConfigKey()).get();
            if (raw != null && !raw.isBlank()) {
                try {
                    initial = normalizeLegacyConfig(objectMapper.readValue(raw, ModelRoutingProperties.class));
                    log.info("Loaded runtime model config from Redis, version={}", initial.getVersion());
                } catch (Exception exception) {
                    log.warn("Failed to parse runtime model config from Redis, fallback bootstrap config");
                }
            }
        }

        applyLocal(initial);
        if (redissonClient == null) {
            return;
        }
        redissonClient.getTopic(bootstrapProperties.getConfigChannel()).addListener(String.class, (channel, message) -> {
            if (message == null || message.isBlank()) {
                return;
            }
            try {
                ModelRoutingProperties incoming = objectMapper.readValue(message, ModelRoutingProperties.class);
                applyLocal(normalizeLegacyConfig(incoming));
            } catch (Exception exception) {
                log.warn("Failed to consume model config event: {}", exception.getMessage());
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

    public long getCurrentVersion() {
        return getEffectiveConfig().getVersion();
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

    /**
     * Legacy endpoint update. It remains last-write-wins for clients that do not send a version.
     */
    public void updateFromAdmin(ModelRoutingProperties request) {
        long expectedVersion = request != null && request.getVersion() > 0
                ? request.getVersion() : getCurrentVersion();
        updateFromAdmin(request, expectedVersion, null);
    }

    @Transactional(noRollbackFor = ModelRuntimePublishException.class)
    public ModelRoutingProperties updateFromAdmin(ModelRoutingProperties request,
            long expectedVersion, String operatorId) {
        return updateVersioned(request, expectedVersion, operatorId, UPDATE_CONFIG, null);
    }

    public ModelRoutingProperties switchStrategy(String tierId,
            ModelSelectionStrategyType strategy, String operatorId) {
        if (tierId == null || tierId.isBlank() || strategy == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "tier 和 strategy 不能为空");
        }
        ModelRoutingProperties current = getEffectiveConfig();
        ModelRoutingProperties normalized = normalizeLegacyConfig(current);
        ModelTierDefinition tier = normalized.getTiers().get(tierId);
        if (tier == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "未知 tier: " + tierId);
        }
        ModelRoutingProperties next = cloneConfig(normalized);
        next.getTiers().get(tierId).setStrategy(strategy);
        if (next.getGroups() != null && next.getGroups().containsKey(tierId)) {
            next.getGroups().get(tierId).setStrategy(strategy);
        }
        return updateVersioned(next, current.getVersion(), operatorId, SWITCH_STRATEGY, tierId);
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

    public String secretPlaceholder() {
        return SECRET_PLACEHOLDER;
    }

    private synchronized ModelRoutingProperties updateVersioned(ModelRoutingProperties request,
            long expectedVersion, String operatorId, String action, String tierId) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "模型治理配置不能为空");
        }
        ModelRoutingProperties current = currentConfigForWrite();
        if (expectedVersion != current.getVersion()) {
            throw new BusinessException(ErrorCode.CONFIG_VERSION_CONFLICT,
                    "配置版本已过期，当前版本为 " + current.getVersion()
                            + ", 提交版本为 " + expectedVersion);
        }

        ModelRoutingProperties merged = normalizeLegacyConfig(mergeSecrets(request, current));
        validate(merged);
        merged.setVersion(current.getVersion() + 1);

        String beforeJson = toAuditJson(current);
        String afterJson = toAuditJson(merged);
        try {
            saveRuntimeSnapshot(merged, operatorId);
            saveChangeLog(operatorId, action, tierId, beforeJson, afterJson, true, null);
            publishRuntimeConfig(merged);
        } catch (ModelRuntimePublishException exception) {
            saveChangeLogSafely(operatorId, action, tierId, beforeJson, afterJson, false,
                    exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            saveChangeLogSafely(operatorId, action, tierId, beforeJson, afterJson, false,
                    exception.getMessage());
            throw exception;
        }

        // The local view changes only after Redis accepted the new snapshot.
        applyLocal(merged);
        return cloneConfig(merged);
    }

    private ModelRoutingProperties currentConfigForWrite() {
        if (runtimeConfigRepository == null) {
            return getEffectiveConfig();
        }
        Optional<ModelRuntimeConfig> snapshot = runtimeConfigRepository.findByConfigKey(ACTIVE_CONFIG_KEY);
        if (snapshot.isEmpty()) {
            return getEffectiveConfig();
        }
        String configJson = snapshot.get().getConfigJson();
        if (configJson == null || configJson.isBlank()) {
            ModelRoutingProperties local = getEffectiveConfig();
            if (snapshot.get().getVersion() != null
                    && snapshot.get().getVersion() > local.getVersion()) {
                local.setVersion(snapshot.get().getVersion());
            }
            return local;
        }
        return readRuntimeConfig(configJson, snapshot.get().getVersion());
    }

    private void saveRuntimeSnapshot(ModelRoutingProperties config, String operatorId) {
        if (runtimeConfigRepository == null) {
            return;
        }
        ModelRuntimeConfig snapshot = runtimeConfigRepository.findByConfigKey(ACTIVE_CONFIG_KEY)
                .orElseGet(ModelRuntimeConfig::new);
        snapshot.setConfigKey(ACTIVE_CONFIG_KEY);
        snapshot.setConfigJson(toRuntimeJson(config));
        snapshot.setVersion(config.getVersion());
        snapshot.setOperatorId(operatorId);
        runtimeConfigRepository.save(snapshot);
    }

    private void saveChangeLog(String operatorId, String action, String tierId,
            String beforeJson, String afterJson, boolean success, String errorMessage) {
        if (changeLogRepository == null) {
            return;
        }
        ModelConfigChangeLog changeLog = ModelConfigChangeLog.builder()
                .changeId(UUID.randomUUID().toString().replace("-", ""))
                .operatorId(operatorId)
                .action(action)
                .groupName(tierId)
                .beforeJson(beforeJson)
                .afterJson(afterJson)
                .success(success)
                .errorMessage(errorMessage)
                .build();
        changeLogRepository.save(changeLog);
    }

    private void saveChangeLogSafely(String operatorId, String action, String tierId,
            String beforeJson, String afterJson, boolean success, String errorMessage) {
        try {
            saveChangeLog(operatorId, action, tierId, beforeJson, afterJson, success,
                    truncate(errorMessage));
        } catch (RuntimeException logException) {
            log.warn("Failed to persist model config failure audit: {}", logException.getMessage());
        }
    }

    private void publishRuntimeConfig(ModelRoutingProperties config) {
        if (redissonClient == null) {
            throw new ModelRuntimePublishException("Redis 客户端不可用，未切换本地模型配置");
        }
        try {
            String json = toRuntimeJson(config);
            redissonClient.<String>getBucket(bootstrapProperties.getRuntimeConfigKey()).set(json);
            redissonClient.getTopic(bootstrapProperties.getConfigChannel()).publish(json);
        } catch (RuntimeException exception) {
            throw new ModelRuntimePublishException("Redis 发布模型配置失败: " + exception.getMessage(), exception);
        }
    }

    private ModelRoutingProperties readRuntimeConfig(String raw, Long version) {
        try {
            ModelRoutingProperties config = normalizeLegacyConfig(
                    objectMapper.readValue(raw, ModelRoutingProperties.class));
            if (version != null && version > config.getVersion()) {
                config.setVersion(version);
            }
            return config;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse runtime model config", exception);
        }
    }

    private String toRuntimeJson(ModelRoutingProperties config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize model config", exception);
        }
    }

    private String toAuditJson(ModelRoutingProperties config) {
        ModelRoutingProperties redacted = cloneConfig(config);
        if (redacted.getModels() != null) {
            redacted.getModels().forEach(model -> {
                if (model.getApikey() != null && !model.getApikey().isBlank()) {
                    model.setApikey(SECRET_PLACEHOLDER);
                }
            });
        }
        return toRuntimeJson(redacted);
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

    private ModelRoutingProperties mergeSecrets(ModelRoutingProperties incoming,
            ModelRoutingProperties current) {
        ModelRoutingProperties merged = cloneConfig(incoming);
        if (merged.getModels() == null || current == null || current.getModels() == null) {
            return merged;
        }
        for (ModelRoutingProperties.ModelDefinition model : merged.getModels()) {
            if (model.getId() == null || model.getId().isBlank()) {
                continue;
            }
            if (model.getApikey() == null || model.getApikey().isBlank()
                    || SECRET_PLACEHOLDER.equals(model.getApikey())) {
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
            if (model == null || model.getId() == null || model.getId().isBlank()) {
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
            if (tierId == null || tierId.isBlank()
                    || definition == null || definition.getMembers() == null || definition.getMembers().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "tier[" + tierId + "] 必须至少包含一个成员");
            }
            definition.getMembers().forEach(member -> {
                if (!modelIds.contains(member)) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR,
                            "tier[" + tierId + "] 引用了不存在的模型: " + member);
                }
            });
        });

        if (config.getRoutes() == null || config.getRoutes().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "routes 不能为空");
        }
        Set<String> routeIds = new HashSet<>();
        config.getRoutes().forEach(route -> validateRoute(config, route, routeIds));
        if (config.getDefaultRoute() != null) {
            validateRoute(config, config.getDefaultRoute(), new HashSet<>());
        }

        if (config.getCapabilityGroups() != null) {
            config.getCapabilityGroups().forEach((capabilityName, groupName) -> {
                ModelCapability capability;
                try {
                    capability = ModelCapability.valueOf(capabilityName.toUpperCase(Locale.ROOT)
                            .replace('-', '_'));
                } catch (Exception exception) {
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
        if (config.getMatrix() != null && config.getGroups() != null) {
            config.getMatrix().forEach((lang, sceneMap) -> {
                if (sceneMap == null) {
                    return;
                }
                sceneMap.forEach((scene, sceneDef) -> {
                    if (sceneDef == null || sceneDef.getGroup() == null || sceneDef.getGroup().isBlank()) {
                        throw new BusinessException(ErrorCode.PARAM_ERROR,
                                "matrix[" + normalize(lang) + "." + normalize(scene) + "] 的 group 不能为空");
                    }
                    if (!config.getGroups().containsKey(sceneDef.getGroup())
                            && !config.getTiers().containsKey(sceneDef.getGroup())) {
                        throw new BusinessException(ErrorCode.PARAM_ERROR,
                                "matrix[" + normalize(lang) + "." + normalize(scene)
                                        + "] 引用了不存在的分组: " + sceneDef.getGroup());
                    }
                });
            });
        }
    }

    private void validateRoute(ModelRoutingProperties config, RoutePolicyDefinition route,
            Set<String> routeIds) {
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
        if (!hasEnabledChatModel(config, primary)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "route[" + route.getId() + "] 的 primary-tier 没有启用的 Chat 模型: "
                            + route.getPrimaryTier());
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
    }

    private boolean hasEnabledChatModel(ModelRoutingProperties config, ModelTierDefinition tier) {
        if (tier.getMembers() == null) {
            return false;
        }
        return tier.getMembers().stream()
                .map(memberId -> config.getModels().stream()
                        .filter(model -> Objects.equals(model.getId(), memberId))
                        .findFirst()
                        .orElse(null))
                .anyMatch(model -> model != null
                        && model.isEnabled()
                        && (model.supports(ModelCapability.CHAT)
                        || model.supports(ModelCapability.STREAMING_CHAT)));
    }

    private ModelRoutingProperties cloneConfig(ModelRoutingProperties source) {
        if (source == null) {
            return new ModelRoutingProperties();
        }
        return objectMapper.convertValue(source, ModelRoutingProperties.class);
    }

    private void applyLocal(ModelRoutingProperties config) {
        ModelRoutingProperties cloned = cloneConfig(config);
        currentConfig.set(cloned);
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new ModelConfigUpdatedEvent(this, cloned));
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 512 ? message : message.substring(0, 512);
    }

    public static class ModelRuntimePublishException extends RuntimeException {
        public ModelRuntimePublishException(String message) {
            super(message);
        }

        public ModelRuntimePublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
