package com.aseubel.yusi.pojo.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelRouteReason {
    private String routeId;
    private int sceneMatchLevel;
    private int riskMatchLevel;
    private int routePriority;
    private String primaryTier;
    @Builder.Default
    private List<String> fallbackTierOrder = new ArrayList<>();
    @Builder.Default
    private List<String> strategyOrder = new ArrayList<>();
}
