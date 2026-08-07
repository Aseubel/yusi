package com.aseubel.yusi.config.ai.properties;

import com.aseubel.yusi.redis.common.RedisKey;
import com.aseubel.yusi.service.ai.model.ModelCapability;
import com.aseubel.yusi.service.ai.model.ModelProtocol;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "model.routing", ignoreUnknownFields = false)
public class ModelRoutingProperties {

    private long version = 0L;

    private int schemaVersion = 2;

    private String defaultScene = "chat";

    private String defaultTier;

    private String stateChannel = RedisKey.MODEL_STATE_CHANNEL;

    private String instanceStateMapKey = RedisKey.MODEL_STATE_MAP;

    private String runtimeConfigKey = RedisKey.MODEL_RUNTIME_CONFIG_KEY;

    private String configChannel = RedisKey.MODEL_CONFIG_CHANNEL;

    private int failureThreshold = 3;

    private int recoverySuccessThreshold = 2;

    private long recoveryProbeIntervalMs = 15_000L;

    private double halfOpenProbeRatio = 0.1;

    private List<ModelDefinition> models = new ArrayList<>();

    private Map<String, ModelTierDefinition> tiers = new LinkedHashMap<>();

    private List<RoutePolicyDefinition> routes = new ArrayList<>();

    private RoutePolicyDefinition defaultRoute;

    @Data
    public static class ModelDefinition {
        private String id;
        private String displayName;
        private String provider;
        private ModelProtocol protocol;
        @JsonAlias("baseUrl")
        private String baseurl;
        @JsonAlias("apiKey")
        private String apikey;
        private String model;
        private List<ModelCapability> capabilities = new ArrayList<>();
        private Integer weight = 100;
        private Integer priority = 100;
        private List<String> scenes = new ArrayList<>();
        private boolean enabled = true;
        /**
         * 请求超时时间（秒），默认60秒
         */
        private Integer timeoutSeconds = 60;

        private Integer contextWindowTokens;

        private PricingDefinition pricing = new PricingDefinition();

        public boolean supports(ModelCapability capability) {
            if (capabilities == null || capabilities.isEmpty()) {
                return capability == ModelCapability.CHAT || capability == ModelCapability.STREAMING_CHAT;
            }
            return capabilities.contains(capability);
        }
    }

    @Data
    public static class PricingDefinition {
        private BigDecimal inputPerMillion;
        private BigDecimal outputPerMillion;
        private String priceVersion;
    }
}
