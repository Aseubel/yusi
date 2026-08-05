package com.aseubel.yusi.config.ai.properties;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class RoutePolicyDefinition {

    private String id;

    private String scene;

    private String language;

    private String riskLevel = "LOW";

    private String primaryTier;

    private List<String> fallbackTiers = new ArrayList<>();

    private Integer maxInputTokens;

    private Integer maxOutputTokens;

    private Double temperature;

    private Double topP;

    private Integer maxCompletionTokens;

    private Map<String, Object> customParameters = new HashMap<>();

    private boolean enabled = true;

    private int priority = 100;
}
