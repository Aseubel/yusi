package com.aseubel.yusi.pojo.dto.model;

import com.aseubel.yusi.config.ai.properties.RoutePolicyDefinition;
import com.aseubel.yusi.service.ai.model.ModelCapability;
import com.aseubel.yusi.service.ai.model.ModelRuntimeState;
import com.aseubel.yusi.service.ai.model.ModelSelectionStrategyType;
import com.aseubel.yusi.service.ai.model.ModelProtocol;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelGovernanceSnapshot {

    @Builder.Default
    private long version = 0L;

    @Builder.Default
    private int schemaVersion = 2;

    private String defaultScene;

    private String defaultTier;

    @Builder.Default
    private List<ModelGovernanceModel> models = new ArrayList<>();

    @Builder.Default
    private List<ModelGovernanceTier> tiers = new ArrayList<>();

    @Builder.Default
    private List<RoutePolicyDefinition> routes = new ArrayList<>();

    private RoutePolicyDefinition defaultRoute;

    @Builder.Default
    private List<ModelGovernanceRoute> routeProjections = new ArrayList<>();

    @Builder.Default
    private List<ModelRuntimeState> runtimeStates = new ArrayList<>();

    private ModelMetricSummary summary;

    private ModelRuntimeSummary runtimeSummary;

    private long lastRefreshedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelGovernanceModel {
        private String id;
        private String displayName;
        private String provider;
        private ModelProtocol protocol;
        private String baseUrl;
        private String endpointHost;
        private String realModelId;
        private boolean apiKeyConfigured;
        @Builder.Default
        private List<ModelCapability> capabilities = new ArrayList<>();
        private Integer timeoutSeconds;
        private Integer contextWindowTokens;
        private BigDecimal inputPricePerMillion;
        private BigDecimal outputPricePerMillion;
        private String priceVersion;
        private int weight;
        private int priority;
        @Builder.Default
        private List<String> scenes = new ArrayList<>();
        private boolean enabled;
        private String runtimeStatus;
        private String phase;
        private boolean available;
        private int consecutiveFailures;
        private double avgLatencyMs;
        private double errorRate;
        private String lastError;
        private long lastUpdatedAt;
        @Builder.Default
        private List<String> tierIds = new ArrayList<>();
        @Builder.Default
        private List<String> routeIds = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelGovernanceTier {
        private String id;
        private String displayName;
        private String description;
        @Builder.Default
        private List<String> members = new ArrayList<>();
        private ModelSelectionStrategyType strategy;
        private boolean enabled;
        @Builder.Default
        private List<ModelCapability> capabilities = new ArrayList<>();
        private int healthyMemberCount;
        private int degradedMemberCount;
        private int downMemberCount;
        private int unknownMemberCount;
        private int memberCount;
        @Builder.Default
        private List<ModelGovernanceTierMember> memberDetails = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelGovernanceTierMember {
        private String modelId;
        private int priority;
        private int weight;
        private String runtimeStatus;
        private String phase;
        private boolean available;
        private double avgLatencyMs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelGovernanceRoute {
        private String id;
        private String scene;
        private String riskLevel;
        private int priority;
        private boolean enabled;
        private String primaryTier;
        private ModelSelectionStrategyType primaryStrategy;
        @Builder.Default
        private List<ModelGovernanceTierReference> fallbackTiers = new ArrayList<>();
        private boolean available;
        private String runtimeStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelGovernanceTierReference {
        private String id;
        private ModelSelectionStrategyType strategy;
        private boolean available;
        private String runtimeStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelRuntimeSummary {
        private int upCount;
        private int unknownCount;
        private int halfOpenCount;
        private int downCount;
        private int noAvailableRouteCount;
    }
}
