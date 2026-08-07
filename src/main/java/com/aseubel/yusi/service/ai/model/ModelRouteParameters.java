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
        Map<String, Object> customParameters) {

    public ModelRouteParameters {
        customParameters = customParameters == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(customParameters));
    }

    public static ModelRouteParameters from(RoutePolicyDefinition route) {
        if (route == null) {
            return new ModelRouteParameters(null, null, null, null, null, Map.of());
        }
        return new ModelRouteParameters(
                route.getMaxInputTokens(),
                route.getMaxOutputTokens(),
                route.getTemperature(),
                route.getTopP(),
                route.getMaxCompletionTokens(),
                route.getCustomParameters());
    }
}
