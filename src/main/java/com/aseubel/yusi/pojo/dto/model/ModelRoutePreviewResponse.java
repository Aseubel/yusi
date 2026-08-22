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
public class ModelRoutePreviewResponse {
    private String policyId;
    private String primaryTier;
    @Builder.Default
    private List<Candidate> candidates = new ArrayList<>();
    private String routeReason;
    private ModelRouteReason routeReasonDetails;
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Candidate {
        private String tierId;
        private String modelId;
        private String provider;
        private String modelName;
        private boolean available;
        private String excludedReason;
        private String exclusionExplanation;
        private int rank;
        private boolean fallback;
        private String strategy;
        private int priority;
        private int weight;
        private double avgLatencyMs;
        private String phase;
        private boolean attemptable;
    }
}
