package com.aseubel.yusi.service.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelRuntimeState {
    private String instanceId;
    private String modelName;
    private boolean available;
    private double healthScore;
    private double qps;
    private double avgLatencyMs;
    private double errorRate;
    private long totalRequests;
    private long successRequests;
    private long failureRequests;
    private int consecutiveFailures;
    private int consecutiveSuccesses;
    private long lastUpdatedAt;
    private long nextProbeAt;
    private String phase;
    private String lastError;
}
