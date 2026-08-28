package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.service.ai.model.constant.ModelRouteExclusionReason;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record ModelRouteDecision(
        String requestId,
        String policyId,
        long policyVersion,
        String primaryTier,
        List<String> fallbackTiers,
        List<ModelRouteCandidate> candidates,
        String routeReason,
        ModelRouteParameters routeParameters,
        RouteReason routeReasonDetails) {

    public ModelRouteDecision {
        fallbackTiers = fallbackTiers == null ? List.of() : List.copyOf(fallbackTiers);
        candidates = candidates == null ? List.of() : List.copyOf(new ArrayList<>(candidates));
        routeParameters = routeParameters == null
                ? new ModelRouteParameters(null, null, null, null, null, null, Map.of())
                : routeParameters;
        routeReasonDetails = routeReasonDetails == null
                ? new RouteReason(policyId, 0, 0, 0, primaryTier, fallbackTiers, List.of())
                : routeReasonDetails;
    }

    public ModelRouteDecision(String requestId, String policyId, long policyVersion,
            String primaryTier, List<String> fallbackTiers, List<ModelRouteCandidate> candidates,
            String routeReason) {
        this(requestId, policyId, policyVersion, primaryTier, fallbackTiers, candidates,
                routeReason, null);
    }

    public ModelRouteDecision(String requestId, String policyId, long policyVersion,
            String primaryTier, List<String> fallbackTiers, List<ModelRouteCandidate> candidates,
            String routeReason, ModelRouteParameters routeParameters) {
        this(requestId, policyId, policyVersion, primaryTier, fallbackTiers, candidates,
                routeReason, routeParameters, null);
    }

    public List<ModelRouteCandidate> attemptCandidates() {
        List<ModelRouteCandidate> primary = candidates.stream()
                .filter(ModelRouteCandidate::primaryEligible)
                .toList();
        List<ModelRouteCandidate> fallback = candidates.stream()
                .filter(candidate -> candidate.available()
                        && ModelRouteExclusionReason.FALLBACK_TIER.code().equals(candidate.excludedReason()))
                .toList();
        if (!primary.isEmpty()) {
            return concat(primary, fallback);
        }
        return fallback;
    }

    private List<ModelRouteCandidate> concat(List<ModelRouteCandidate> first,
            List<ModelRouteCandidate> second) {
        List<ModelRouteCandidate> result = new ArrayList<>(first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return List.copyOf(result);
    }

    public record RouteReason(
            String routeId,
            int sceneMatchLevel,
            int riskMatchLevel,
            int routePriority,
            String primaryTier,
            List<String> fallbackTierOrder,
            List<String> strategyOrder) {

        public RouteReason {
            fallbackTierOrder = fallbackTierOrder == null ? List.of() : List.copyOf(fallbackTierOrder);
            strategyOrder = strategyOrder == null ? List.of() : List.copyOf(strategyOrder);
        }
    }
}
