package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.config.ai.properties.RoutePolicyDefinition;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ModelRoutePolicyMatcher {

    public MatchResult matchWithReason(ModelRoutingProperties properties, ModelRouteContext context) {
        if (properties == null) {
            return null;
        }
        String scene = normalize(context == null ? null : context.getScene());
        String riskLevel = normalize(context == null ? null : context.getRiskLevel());

        List<RouteMatch> matches = properties.getRoutes() == null ? List.of() : properties.getRoutes().stream()
                .filter(route -> route != null && route.isEnabled())
                .filter(route -> route.getScene() != null)
                .map(route -> score(route, scene, riskLevel))
                .filter(match -> match.sceneSpecificity() >= 0)
                .sorted(Comparator.comparingInt(RouteMatch::sceneSpecificity).reversed()
                        .thenComparing(Comparator.comparingInt(RouteMatch::riskSpecificity).reversed())
                        .thenComparing(Comparator.comparingInt((RouteMatch match) -> match.route().getPriority()).reversed())
                        .thenComparing(match -> normalize(match.route().getId())))
                .toList();
        if (!matches.isEmpty()) {
            RouteMatch match = matches.getFirst();
            return new MatchResult(match.route(), match.sceneSpecificity(), match.riskSpecificity());
        }

        RoutePolicyDefinition defaultRoute = properties.getDefaultRoute();
        if (defaultRoute != null && defaultRoute.isEnabled()) {
            return new MatchResult(defaultRoute, 0, 0);
        }
        return null;
    }

    public RoutePolicyDefinition match(ModelRoutingProperties properties, ModelRouteContext context) {
        MatchResult result = matchWithReason(properties, context);
        return result == null ? null : result.route();
    }

    private RouteMatch score(RoutePolicyDefinition route, String scene, String riskLevel) {
        String routeScene = normalize(route.getScene());
        boolean sceneMatches = "*".equals(routeScene) || routeScene.equals(scene);
        if (!sceneMatches) {
            return new RouteMatch(route, -1, -1);
        }
        String routeRisk = normalize(route.getRiskLevel());
        boolean riskMatches = riskLevel.isBlank() || "*".equals(routeRisk) || routeRisk.equals(riskLevel);
        if (!riskMatches) {
            return new RouteMatch(route, -1, -1);
        }
        int sceneSpecificity = "*".equals(routeScene) ? 0 : 1;
        int riskSpecificity = riskLevel.isBlank() || "*".equals(routeRisk) ? 0 : 1;
        return new RouteMatch(route, sceneSpecificity, riskSpecificity);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record MatchResult(RoutePolicyDefinition route, int sceneMatchLevel, int riskMatchLevel) {
    }

    private record RouteMatch(RoutePolicyDefinition route, int sceneSpecificity, int riskSpecificity) {
    }
}
