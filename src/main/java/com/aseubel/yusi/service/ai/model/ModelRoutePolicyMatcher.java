package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.config.ai.properties.RoutePolicyDefinition;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ModelRoutePolicyMatcher {

    public RoutePolicyDefinition match(ModelRoutingProperties properties, ModelRouteContext context) {
        if (properties == null) {
            return null;
        }
        String language = normalize(context == null ? null : context.getLanguage());
        String scene = normalize(context == null ? null : context.getScene());

        List<RouteMatch> matches = properties.getRoutes() == null ? List.of() : properties.getRoutes().stream()
                .filter(route -> route != null && route.isEnabled())
                .filter(route -> route.getScene() != null && route.getLanguage() != null)
                .map(route -> score(route, language, scene))
                .filter(match -> match.score() >= 0)
                .sorted(Comparator.comparingInt(RouteMatch::specificity).reversed()
                        .thenComparing(Comparator.comparingInt((RouteMatch match) -> match.route().getPriority()).reversed())
                        .thenComparing(match -> normalize(match.route().getId())))
                .toList();
        if (!matches.isEmpty()) {
            return matches.getFirst().route();
        }

        RoutePolicyDefinition defaultRoute = properties.getDefaultRoute();
        if (defaultRoute != null && defaultRoute.isEnabled()) {
            return defaultRoute;
        }
        return null;
    }

    private RouteMatch score(RoutePolicyDefinition route, String language, String scene) {
        String routeLanguage = normalize(route.getLanguage());
        String routeScene = normalize(route.getScene());
        boolean languageMatches = "*".equals(routeLanguage) || routeLanguage.equals(language);
        boolean sceneMatches = "*".equals(routeScene) || routeScene.equals(scene);
        if (!languageMatches || !sceneMatches) {
            return new RouteMatch(route, -1, -1);
        }
        int languageSpecificity = "*".equals(routeLanguage) ? 0 : 2;
        int sceneSpecificity = "*".equals(routeScene) ? 0 : 1;
        return new RouteMatch(route, languageSpecificity + sceneSpecificity,
                languageSpecificity + sceneSpecificity);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record RouteMatch(RoutePolicyDefinition route, int score, int specificity) {
    }
}
