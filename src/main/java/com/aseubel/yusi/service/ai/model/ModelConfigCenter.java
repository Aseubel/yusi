package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.service.ai.model.constant.ModelProviderType;
import com.aseubel.yusi.config.ai.properties.ModelTierDefinition;
import com.aseubel.yusi.config.ai.properties.RoutePolicyDefinition;
import com.aseubel.yusi.pojo.entity.ModelConfigChangeLog;
import com.aseubel.yusi.pojo.entity.ModelRuntimeConfig;
import com.aseubel.yusi.repository.ModelConfigChangeLogRepository;
import com.aseubel.yusi.repository.ModelRuntimeConfigRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
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

    private final ModelRoutingProperties bootstrapProperties;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ModelRuntimeConfigRepository runtimeConfigRepository;
    private final ModelConfigChangeLogRepository changeLogRepository;
    private final AtomicReference<ModelRoutingProperties> currentConfig = new AtomicReference<>();

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
        ModelRoutingProperties initial = cloneConfig(bootstrapProperties);
        String configSource = "yaml";
        boolean loaded = false;

        if (runtimeConfigRepository != null) {
            try {
                Optional<ModelRuntimeConfig> snapshot = runtimeConfigRepository.findByConfigKey(ACTIVE_CONFIG_KEY);
                if (snapshot.isPresent() && snapshot.get().getConfigJson() != null) {
                    initial = readRuntimeConfig(snapshot.get().getConfigJson(), snapshot.get().getVersion());
                    loaded = true;
                    configSource = "mysql";
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
                    initial = readRuntimeConfig(raw, null);
                    log.info("Loaded runtime model config from Redis, version={}", initial.getVersion());
                    configSource = "redis";
                } catch (RuntimeException exception) {
                    log.warn("Failed to load canonical v2 model config from Redis: {}", exception.getMessage());
                }
            }
        }

        validate(initial);
        applyLocal(initial);
        logEffectiveConfig(configSource, initial);
        if (redissonClient == null) {
            return;
        }
        redissonClient.getTopic(bootstrapProperties.getConfigChannel()).addListener(String.class, (channel, message) -> {
            if (message == null || message.isBlank()) {
                return;
            }
            try {
                ModelRoutingProperties updated = readRuntimeConfig(message, null);
                if (applyIfNewer(updated, "redis-pubsub")) {
                    logEffectiveConfig("redis-pubsub", updated);
                }
            } catch (RuntimeException exception) {
                log.warn("Failed to consume canonical v2 model config event: {}", exception.getMessage());
            }
        });
    }

    public ModelRoutingProperties getEffectiveConfig() {
        ModelRoutingProperties config = currentConfig.get();
        return cloneConfig(config == null ? bootstrapProperties : config);
    }

    public long getCurrentVersion() {
        return getEffectiveConfig().getVersion();
    }

    @Transactional(noRollbackFor = ModelRuntimePublishException.class)
    public ModelRoutingProperties updateCanonical(ModelRoutingProperties request,
            long expectedVersion, String operatorId) {
        return updateVersioned(request, expectedVersion, operatorId);
    }

    void validateForAdmin(ModelRoutingProperties config) {
        validate(config);
    }

    private synchronized ModelRoutingProperties updateVersioned(ModelRoutingProperties request,
            long expectedVersion, String operatorId) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "模型治理配置不能为空");
        }
        ModelRoutingProperties current = currentConfigForWrite();
        if (expectedVersion != current.getVersion()) {
            throw new BusinessException(ErrorCode.CONFIG_VERSION_CONFLICT,
                    "配置版本已过期，当前版本为 " + current.getVersion()
                            + ", 提交版本为 " + expectedVersion);
        }

        ModelRoutingProperties merged = mergeSecrets(request, current);
        validateStableModelIds(current, merged);
        validate(merged);
        merged.setVersion(current.getVersion() + 1);

        String beforeJson = toAuditJson(current);
        String afterJson = toAuditJson(merged);
        try {
            saveRuntimeSnapshot(merged, operatorId);
            saveChangeLog(operatorId, beforeJson, afterJson, true, null);
            publishRuntimeConfig(merged);
        } catch (ModelRuntimePublishException exception) {
            saveChangeLogSafely(operatorId, beforeJson, afterJson, false, exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            saveChangeLogSafely(operatorId, beforeJson, afterJson, false, exception.getMessage());
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

    private void saveChangeLog(String operatorId, String beforeJson, String afterJson,
            boolean success, String errorMessage) {
        if (changeLogRepository == null) {
            return;
        }
        ModelConfigChangeLog changeLog = ModelConfigChangeLog.builder()
                .changeId(UUID.randomUUID().toString().replace("-", ""))
                .operatorId(operatorId)
                .action(UPDATE_CONFIG)
                .beforeJson(beforeJson)
                .afterJson(afterJson)
                .success(success)
                .errorMessage(errorMessage)
                .build();
        changeLogRepository.save(changeLog);
    }

    private void saveChangeLogSafely(String operatorId, String beforeJson, String afterJson,
            boolean success, String errorMessage) {
        try {
            saveChangeLog(operatorId, beforeJson, afterJson, success, truncate(errorMessage));
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
            JsonNode root = objectMapper.readTree(raw);
            if (root.has("groups") || root.has("matrix")) {
                throw new IllegalArgumentException("model routing config only accepts schema v2 tiers and routes");
            }
            ModelRoutingProperties config = objectMapper.treeToValue(root, ModelRoutingProperties.class);
            if (version != null && version > config.getVersion()) {
                config.setVersion(version);
            }
            validate(config);
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
        if (config == null || config.getSchemaVersion() != 2) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "模型治理配置必须使用 schema-version: 2");
        }
        if (config.getFailureThreshold() <= 0 || config.getRecoverySuccessThreshold() <= 0
                || config.getRecoveryProbeIntervalMs() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "模型健康阈值和恢复探测间隔必须为正数");
        }
        if (config.getHalfOpenProbeRatio() < 0 || config.getHalfOpenProbeRatio() > 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "half-open-probe-ratio 必须在 0 到 1 之间");
        }
        if (config.getModels() == null || config.getModels().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "models 不能为空");
        }
        Set<String> modelIds = new HashSet<>();
        Map<String, ModelRoutingProperties.ModelDefinition> modelsById = new HashMap<>();
        config.getModels().forEach(model -> {
            if (model == null || model.getId() == null || model.getId().isBlank()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "model.id 不能为空");
            }
            String normalizedId = model.getId().trim();
            if (!normalizedId.equals(model.getId())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR,
                        "models[" + model.getId() + "].id 必须去除首尾空格");
            }
            validateModel(model);
            if (!modelIds.add(model.getId())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "model.id 重复: " + model.getId());
            }
            modelsById.put(model.getId(), model);
        });

        if (config.getTiers() == null || config.getTiers().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "tiers 不能为空");
        }
        for (Map.Entry<String, ModelTierDefinition> entry : config.getTiers().entrySet()) {
            String tierId = entry.getKey();
            ModelTierDefinition definition = entry.getValue();
            if (tierId == null || tierId.isBlank()
                    || definition == null || definition.getMembers() == null || definition.getMembers().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "tier[" + tierId + "] 必须至少包含一个成员");
            }
            validateCapabilities("tier[" + tierId + "]", definition.getCapabilities());
            Set<String> tierMembers = new HashSet<>();
            for (String member : definition.getMembers()) {
                if (member == null || member.isBlank() || !tierMembers.add(member)) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR,
                            "tier[" + tierId + "] 的 members 不能重复或为空: " + member);
                }
                if (member == null || !modelIds.contains(member)) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR,
                            "tier[" + tierId + "] 引用了不存在的模型: " + member);
                }
                ModelRoutingProperties.ModelDefinition model = modelsById.get(member);
                if (!supportsTierCapabilities(definition, model)) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR,
                            "tier[" + tierId + "] 的 capabilities 与模型[" + member + "] 不匹配");
                }
            }
            if (definition.getStrategy() == ModelSelectionStrategyType.WEIGHTED_RANDOM
                    && !hasPositiveWeightedMember(config, definition)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR,
                        "tier[" + tierId + "] 使用 WEIGHTED_RANDOM 时至少需要一个启用且 weight > 0 的成员");
            }
        }

        if (config.getDefaultTier() != null && !config.getDefaultTier().isBlank()
                && !config.getTiers().containsKey(config.getDefaultTier())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "default-tier 引用了不存在的 tier: " + config.getDefaultTier());
        }

        if (config.getRoutes() == null || config.getRoutes().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "routes 不能为空");
        }
        Set<String> routeIds = new HashSet<>();
        config.getRoutes().forEach(route -> validateRoute(config, route, routeIds));
        if (config.getDefaultRoute() != null) {
            validateRoute(config, config.getDefaultRoute(), routeIds);
        }

    }

    private void validateModel(ModelRoutingProperties.ModelDefinition model) {
        if (model.getProvider() == null || model.getProvider().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "model[" + model.getId() + "] 的 provider 不能为空");
        }
        if (model.getWeight() == null || model.getWeight() < 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "model[" + model.getId() + "].weight 不能为负数");
        }
        if (model.getPriority() == null || model.getPriority() < 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "model[" + model.getId() + "] 的 priority 不能为负数");
        }
        if (model.getTimeoutSeconds() == null || model.getTimeoutSeconds() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "model[" + model.getId() + "] 的 timeout-seconds 必须为正数");
        }
        if (model.getContextWindowTokens() != null && model.getContextWindowTokens() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "model[" + model.getId() + "] 的 context-window-tokens 必须为正数");
        }
        validateCapabilities("model[" + model.getId() + "]", model.getCapabilities());
        boolean chatCompatibleModel = model.supports(ModelCapability.CHAT)
                || model.supports(ModelCapability.STREAMING_CHAT)
                || model.supports(ModelCapability.VLM);
        if (!chatCompatibleModel) {
            return;
        }
        if (model.getProtocol() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "model[" + model.getId() + "] 的 protocol 不能为空");
        }
        String provider = model.getProvider().trim().toLowerCase(Locale.ROOT);
        boolean openAiProvider = ModelProviderType.OPENAI_COMPATIBLE.aliases().contains(provider);
        boolean openAiProtocol = model.getProtocol() == ModelProtocol.CHAT_COMPLETIONS
                || model.getProtocol() == ModelProtocol.RESPONSES;
        boolean anthropicProvider = ModelProviderType.ANTHROPIC.aliases().contains(provider);
        if (!((openAiProvider && openAiProtocol)
                || (anthropicProvider && model.getProtocol() == ModelProtocol.ANTHROPIC_MESSAGES))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "model[" + model.getId() + "] 的 provider 与 protocol 不匹配: "
                + model.getProvider() + "/" + model.getProtocol());
        }
    }

    private void validateCapabilities(String owner, List<ModelCapability> capabilities) {
        if (capabilities == null) {
            return;
        }
        Set<ModelCapability> unique = new LinkedHashSet<>();
        for (ModelCapability capability : capabilities) {
            if (capability == null || !unique.add(capability)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR,
                        owner + " 的 capabilities 不能重复或为空");
            }
        }
    }

    private boolean supportsTierCapabilities(ModelTierDefinition tier,
            ModelRoutingProperties.ModelDefinition model) {
        if (tier == null || model == null || tier.getCapabilities() == null
                || tier.getCapabilities().isEmpty()) {
            return true;
        }
        return tier.getCapabilities().stream().allMatch(model::supports);
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
        String riskLevel = route.getRiskLevel() == null ? "LOW" : route.getRiskLevel().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("LOW", "MEDIUM", "HIGH", "*").contains(riskLevel)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "routes[" + route.getId() + "].riskLevel 只能是 LOW、MEDIUM、HIGH 或 *");
        }
        route.setRiskLevel(riskLevel);
        if (route.getPrimaryTier() == null || route.getPrimaryTier().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "route[" + route.getId() + "] 的 primary-tier 不能为空");
        }
        if (route.getPriority() < 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "route[" + route.getId() + "] 的 priority 不能为负数");
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
        if (!hasEligibleModelForScene(config, primary, route.getScene())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "route[" + route.getId() + "] 的 primary-tier 没有启用的 "
                            + ModelCapabilityPolicy.requiredCapabilityLabel(route.getScene())
                            + " 模型支持 scene: "
                            + route.getScene());
        }
        Set<String> fallbackIds = new HashSet<>();
        if (route.getFallbackTiers() != null) {
            route.getFallbackTiers().forEach(fallback -> {
                if (fallback == null || fallback.isBlank()) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR,
                            "route[" + route.getId() + "] 的 fallback-tier 不能为空");
                }
                if (!fallbackIds.add(fallback)) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR,
                            "route[" + route.getId() + "] 的 fallback-tier 重复: " + fallback);
                }
                if (fallback.equals(route.getPrimaryTier())) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR,
                            "route[" + route.getId() + "] 的 fallback-tier 不能与 primary-tier 相同: " + fallback);
                }
                if (!config.getTiers().containsKey(fallback)) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR,
                            "route[" + route.getId() + "] 的 fallback-tier 不存在: " + fallback);
                }
                if (!config.getTiers().get(fallback).isEnabled()) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR,
                            "route[" + route.getId() + "] 的 fallback-tier 已禁用: " + fallback);
                }
                if (!hasEligibleModelForScene(config, config.getTiers().get(fallback), route.getScene())) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR,
                            "route[" + route.getId() + "] 的 fallback-tier 没有启用的 "
                                    + ModelCapabilityPolicy.requiredCapabilityLabel(route.getScene())
                                    + " 模型支持 scene: "
                                    + route.getScene());
                }
            });
        }
        if (route.getMaxInputTokens() != null && route.getMaxInputTokens() < 0
                || route.getMaxOutputTokens() != null && route.getMaxOutputTokens() < 0
                || route.getMaxCompletionTokens() != null && route.getMaxCompletionTokens() < 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "route[" + route.getId() + "] 的 token 限制不能为负数");
        }
        if (route.getTemperature() != null && (route.getTemperature() < 0 || route.getTemperature() > 2)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "route[" + route.getId() + "] 的 temperature 必须在 0 到 2 之间");
        }
        if (route.getTopP() != null && (route.getTopP() < 0 || route.getTopP() > 1)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "route[" + route.getId() + "] 的 top-p 必须在 0 到 1 之间");
        }
    }

    private boolean hasEnabledModelForScene(ModelRoutingProperties config, ModelTierDefinition tier, String scene) {
        if (tier == null || !tier.isEnabled() || tier.getMembers() == null) {
            return false;
        }
        return tier.getMembers().stream()
                .map(memberId -> config.getModels().stream()
                        .filter(model -> Objects.equals(model.getId(), memberId))
                .findFirst()
                        .orElse(null))
                .anyMatch(model -> model != null
                        && model.isEnabled()
                        && supportsTierCapabilities(tier, model)
                        && ModelCapabilityPolicy.supportsScene(model, scene)
                        && declaresScene(model, scene));
    }

    private boolean hasEligibleModelForScene(ModelRoutingProperties config, ModelTierDefinition tier, String scene) {
        if (!hasEnabledModelForScene(config, tier, scene)) {
            return false;
        }
        if (tier.getStrategy() != ModelSelectionStrategyType.WEIGHTED_RANDOM) {
            return true;
        }
        return tier.getMembers().stream()
                .map(memberId -> config.getModels().stream()
                        .filter(model -> Objects.equals(model.getId(), memberId))
                        .findFirst().orElse(null))
                .anyMatch(model -> model != null && model.isEnabled() && model.getWeight() != null
                        && model.getWeight() > 0
                        && supportsTierCapabilities(tier, model)
                        && ModelCapabilityPolicy.supportsScene(model, scene)
                        && declaresScene(model, scene));
    }

    private boolean hasPositiveWeightedMember(ModelRoutingProperties config, ModelTierDefinition tier) {
        if (tier == null || tier.getMembers() == null) {
            return false;
        }
        return tier.getMembers().stream()
                .map(memberId -> config.getModels().stream()
                        .filter(model -> Objects.equals(model.getId(), memberId))
                        .findFirst().orElse(null))
                .anyMatch(model -> model != null && model.isEnabled() && model.getWeight() != null
                        && model.getWeight() > 0 && supportsTierCapabilities(tier, model));
    }

    private void validateStableModelIds(ModelRoutingProperties current, ModelRoutingProperties incoming) {
        if (current == null || current.getModels() == null || incoming == null || incoming.getModels() == null) {
            return;
        }
        Set<String> incomingIds = incoming.getModels().stream()
                .filter(Objects::nonNull)
                .map(ModelRoutingProperties.ModelDefinition::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        current.getModels().stream()
                .map(ModelRoutingProperties.ModelDefinition::getId)
                .filter(Objects::nonNull)
                .filter(id -> !incomingIds.contains(id))
                .findFirst()
                .ifPresent(id -> {
                    throw new BusinessException(ErrorCode.PARAM_ERROR,
                            "models[" + id + "].id 不可通过普通更新删除或改名；请先停用模型");
                });
    }

    private boolean declaresScene(ModelRoutingProperties.ModelDefinition model, String scene) {
        if (scene == null || scene.isBlank() || "*".equals(scene.trim())) {
            return true;
        }
        List<String> scenes = model.getScenes();
        return scenes == null || scenes.isEmpty() || scenes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .anyMatch(scene.trim()::equalsIgnoreCase);
    }

    private ModelRoutingProperties cloneConfig(ModelRoutingProperties source) {
        if (source == null) {
            return new ModelRoutingProperties();
        }
        return objectMapper.convertValue(source, ModelRoutingProperties.class);
    }

    private void applyLocal(ModelRoutingProperties config) {
        validate(config);
        ModelRoutingProperties cloned = cloneConfig(config);
        currentConfig.set(cloned);
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new ModelConfigUpdatedEvent(this, cloned));
        }
    }

    private synchronized boolean applyIfNewer(ModelRoutingProperties config, String source) {
        ModelRoutingProperties current = currentConfig.get();
        if (current != null && config != null && config.getVersion() < current.getVersion()) {
            log.warn("Ignored stale model routing config: source={}, incomingVersion={}, currentVersion={}",
                    source, config.getVersion(), current.getVersion());
            return false;
        }
        applyLocal(config);
        return true;
    }

    private void logEffectiveConfig(String source, ModelRoutingProperties config) {
        log.info("Effective model routing config: source={}, version={}, schemaVersion={}, defaultTier={}, "
                        + "modelCount={}, tierCount={}, routeCount={}",
                source,
                config == null ? null : config.getVersion(),
                config == null ? null : config.getSchemaVersion(),
                config == null ? null : config.getDefaultTier(),
                config == null || config.getModels() == null ? 0 : config.getModels().size(),
                config == null || config.getTiers() == null ? 0 : config.getTiers().size(),
                config == null || config.getRoutes() == null ? 0 : config.getRoutes().size());
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
