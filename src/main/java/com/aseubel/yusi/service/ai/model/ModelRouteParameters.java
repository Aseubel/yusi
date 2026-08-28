package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.config.ai.properties.RoutePolicyDefinition;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;

/**
 * Immutable request parameter snapshot captured when a route decision is made.
 */
public record ModelRouteParameters(
        Integer maxInputTokens,
        Integer maxOutputTokens,
        Double temperature,
        Double topP,
        Integer maxCompletionTokens,
        Boolean thinkingEnabled,
        Map<String, Object> customParameters) {

    public static final int DEFAULT_OUTPUT_TOKENS = 1024;

    public ModelRouteParameters {
        customParameters = customParameters == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(customParameters));
    }

    public static ModelRouteParameters from(RoutePolicyDefinition route) {
        return from(route, null);
    }

    /**
     * @param tierThinkingOverride primary-tier 的思考开关覆盖；null 表示沿用模型级配置。
     */
    public static ModelRouteParameters from(RoutePolicyDefinition route, Boolean tierThinkingOverride) {
        if (route == null) {
            return new ModelRouteParameters(null, null, null, null, null, tierThinkingOverride, Map.of());
        }
        Integer maxOutputTokens = route.getMaxOutputTokens();
        Integer maxCompletionTokens = route.getMaxCompletionTokens();
        if (maxOutputTokens == null && maxCompletionTokens == null) {
            maxOutputTokens = DEFAULT_OUTPUT_TOKENS;
        }
        return new ModelRouteParameters(
                route.getMaxInputTokens(),
                maxOutputTokens,
                route.getTemperature(),
                route.getTopP(),
                maxCompletionTokens,
                tierThinkingOverride,
                route.getCustomParameters());
    }
}
