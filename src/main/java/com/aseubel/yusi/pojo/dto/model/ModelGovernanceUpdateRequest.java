package com.aseubel.yusi.pojo.dto.model;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.config.ai.properties.ModelTierDefinition;
import com.aseubel.yusi.config.ai.properties.RoutePolicyDefinition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelGovernanceUpdateRequest {

    private long expectedVersion;
    @Builder.Default
    private int schemaVersion = 2;
    private String defaultScene;
    private String defaultTier;
    @Builder.Default
    private List<ModelRoutingProperties.ModelDefinition> models = new ArrayList<>();
    @Builder.Default
    private Map<String, ModelTierDefinition> tiers = new LinkedHashMap<>();
    @Builder.Default
    private List<RoutePolicyDefinition> routes = new ArrayList<>();
    private RoutePolicyDefinition defaultRoute;
    public ModelRoutingProperties toProperties() {
        ModelRoutingProperties properties = new ModelRoutingProperties();
        properties.setSchemaVersion(schemaVersion <= 0 ? 2 : schemaVersion);
        if (defaultScene != null && !defaultScene.isBlank()) {
            properties.setDefaultScene(defaultScene);
        }
        if (defaultTier != null && !defaultTier.isBlank()) {
            properties.setDefaultTier(defaultTier);
        }
        properties.setModels(models == null ? new ArrayList<>() : models);
        properties.setTiers(tiers == null ? new LinkedHashMap<>() : tiers);
        properties.setRoutes(routes == null ? new ArrayList<>() : routes);
        properties.setDefaultRoute(defaultRoute);
        return properties;
    }
}
