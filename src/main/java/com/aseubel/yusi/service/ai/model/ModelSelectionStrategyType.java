package com.aseubel.yusi.service.ai.model;

public enum ModelSelectionStrategyType {
    ROUND_ROBIN,
    LEAST_LATENCY,
    WEIGHTED_RANDOM,
    FAIL_OVER
}
