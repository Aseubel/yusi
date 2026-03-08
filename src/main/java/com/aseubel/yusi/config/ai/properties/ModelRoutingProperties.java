package com.aseubel.yusi.config.ai.properties;

import com.aseubel.yusi.redis.common.RedisKey;
import com.aseubel.yusi.service.ai.model.ModelSelectionStrategyType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "model.routing", ignoreInvalidFields = true)
public class ModelRoutingProperties {

    private String defaultLanguage = "zh";

    private String defaultScene = "chat";

    private String strategyChannel = RedisKey.MODEL_GROUP_STRATEGY_CHANNEL;

    private String stateChannel = RedisKey.MODEL_STATE_CHANNEL;

    private String instanceStateMapKey = RedisKey.MODEL_STATE_MAP;

    private String groupStrategyMapKey = RedisKey.MODEL_GROUP_STRATEGY_MAP;

    private String runtimeConfigKey = RedisKey.MODEL_RUNTIME_CONFIG_KEY;

    private String configChannel = RedisKey.MODEL_CONFIG_CHANNEL;

    private int failureThreshold = 3;

    private int recoverySuccessThreshold = 2;

    private long recoveryProbeIntervalMs = 15_000L;

    private double halfOpenProbeRatio = 0.1;

    private List<ModelDefinition> models = new ArrayList<>();

    private Map<String, GroupDefinition> groups = new HashMap<>();

    private Map<String, Map<String, SceneDefinition>> matrix = new HashMap<>();

    @Data
    public static class ModelDefinition {
        private String id;
        private String baseurl;
        private String apikey;
        private String model;
        private Integer weight = 100;
        private Integer priority = 100;
        private List<String> languages = new ArrayList<>();
        private List<String> scenes = new ArrayList<>();
        private boolean enabled = true;
    }

    @Data
    public static class GroupDefinition {
        private List<String> members = new ArrayList<>();
        private ModelSelectionStrategyType strategy = ModelSelectionStrategyType.ROUND_ROBIN;
    }

    @Data
    public static class SceneDefinition {
        private String group;
        private Integer maxTokens;
        private Double temperature;
        private Double topP;
        private Integer maxCompletionTokens;
        private Map<String, Object> customParameters = new HashMap<>();
    }
}
