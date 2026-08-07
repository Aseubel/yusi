package com.aseubel.yusi.service.ai.model;

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
        ModelRouteParameters routeParameters) {

    public ModelRouteDecision {
        fallbackTiers = fallbackTiers == null ? List.of() : List.copyOf(fallbackTiers);
        candidates = candidates == null ? List.of() : List.copyOf(new ArrayList<>(candidates));
        routeParameters = routeParameters == null
                ? new ModelRouteParameters(null, null, null, null, null, Map.of())
                : routeParameters;
    }

    public ModelRouteDecision(String requestId, String policyId, long policyVersion,
            String primaryTier, List<String> fallbackTiers, List<ModelRouteCandidate> candidates,
            String routeReason) {
        this(requestId, policyId, policyVersion, primaryTier, fallbackTiers, candidates,
                routeReason, null);
    }

    public List<ModelRouteCandidate> attemptCandidates() {
        List<ModelRouteCandidate> primary = candidates.stream()
                .filter(ModelRouteCandidate::primaryEligible)
                .toList();
        List<ModelRouteCandidate> fallback = candidates.stream()
                .filter(candidate -> candidate.available()
                        && "fallback-tier".equals(candidate.excludedReason()))
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
}
