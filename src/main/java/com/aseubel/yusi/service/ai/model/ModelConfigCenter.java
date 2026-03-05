package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Locale;
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
        ModelRoutingProperties initial = cloneConfig(bootstrapProperties);
        String raw = redissonClient.<String>getBucket(bootstrapProperties.getRuntimeConfigKey()).get();
        if (raw != null && !raw.isBlank()) {
            try {
                initial = objectMapper.readValue(raw, ModelRoutingProperties.class);
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
        return cloneConfig(currentConfig.get());
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
        ModelRoutingProperties merged = mergeSecrets(request, currentConfig.get());
        validate(merged);
        apply(merged, true);
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
        if (config.getGroups() == null || config.getGroups().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "groups 不能为空");
        }
        Set<String> modelIds = new HashSet<>();
        config.getModels().forEach(model -> {
            if (model.getId() == null || model.getId().isBlank()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "model.id 不能为空");
            }
            modelIds.add(model.getId());
        });
        config.getGroups().forEach((group, definition) -> {
            if (definition.getMembers() == null || definition.getMembers().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "group[" + group + "] 必须至少包含一个成员");
            }
            definition.getMembers().forEach(member -> {
                if (!modelIds.contains(member)) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR, "group[" + group + "] 引用了不存在的模型: " + member);
                }
            });
        });
        if (config.getMatrix() != null) {
            config.getMatrix().forEach((lang, sceneMap) -> {
                if (sceneMap == null) {
                    return;
                }
                sceneMap.forEach((scene, group) -> {
                    if (!config.getGroups().containsKey(group)) {
                        throw new BusinessException(ErrorCode.PARAM_ERROR,
                                "matrix[" + normalize(lang) + "." + normalize(scene) + "] 引用了不存在的分组: " + group);
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
