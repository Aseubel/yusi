package com.aseubel.yusi.pojo.dto.model;

import com.aseubel.yusi.config.ai.properties.RoutePolicyDefinition;
import com.aseubel.yusi.service.ai.model.ModelCapability;
import com.aseubel.yusi.service.ai.model.ModelRuntimeState;
import com.aseubel.yusi.service.ai.model.ModelSelectionStrategyType;
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

    private String defaultLanguage;

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
    private java.util.Map<String, String> capabilityGroups = new java.util.LinkedHashMap<>();

    @Builder.Default
    private List<ModelRuntimeState> runtimeStates = new ArrayList<>();

    private ModelMetricSummary summary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelGovernanceModel {
        private String id;
        private String displayName;
        private String provider;
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
        private List<String> languages = new ArrayList<>();
        @Builder.Default
        private List<String> scenes = new ArrayList<>();
        private boolean enabled;
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
    }
}
