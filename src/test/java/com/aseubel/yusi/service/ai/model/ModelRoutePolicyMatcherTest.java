package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.config.ai.properties.ModelTierDefinition;
import com.aseubel.yusi.config.ai.properties.RoutePolicyDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModelRoutePolicyMatcherTest {

    private final ModelRoutePolicyMatcher matcher = new ModelRoutePolicyMatcher();

    @Test
    void exactLanguageAndSceneWinsOverWildcardAndDefault() {
        ModelRoutingProperties properties = properties(
                route("default", "*", "*", "fast", 0),
                route("chat-any", "*", "chat", "fast", 300),
                route("chat-zh", "zh", "chat", "balanced", 100));

        RoutePolicyDefinition selected = matcher.match(properties,
                ModelRouteContext.builder().language("ZH").scene("chat").build());

        assertThat(selected.getId()).isEqualTo("chat-zh");
    }

    @Test
    void exactSceneWithWildcardLanguageBeatsDefaultRoute() {
        ModelRoutingProperties properties = properties(
                route("default", "*", "*", "fast", 0),
                route("chat-any", "*", "chat", "balanced", 10));

        RoutePolicyDefinition selected = matcher.match(properties,
                ModelRouteContext.builder().language("en").scene("chat").build());

        assertThat(selected.getId()).isEqualTo("chat-any");
    }

    private ModelRoutingProperties properties(RoutePolicyDefinition... routes) {
        ModelRoutingProperties properties = new ModelRoutingProperties();
        properties.setRoutes(List.of(routes));
        properties.setDefaultRoute(routes[0]);
        return properties;
    }

    private RoutePolicyDefinition route(String id, String language, String scene, String tier, int priority) {
        RoutePolicyDefinition route = new RoutePolicyDefinition();
        route.setId(id);
        route.setLanguage(language);
        route.setScene(scene);
        route.setPrimaryTier(tier);
        route.setPriority(priority);
        return route;
    }
}
