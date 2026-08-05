package com.aseubel.yusi.service.ai.runtime;

import com.aseubel.yusi.service.ai.model.ModelUsageSnapshot;

public record ModelCallAttemptEvent(
        String requestId,
        String attemptId,
        String runId,
        String userId,
        String scene,
        String language,
        String policyId,
        long policyVersion,
        String routeReason,
        String primaryTier,
        String selectedTier,
        String modelId,
        String provider,
        String modelName,
        ModelUsageSnapshot usage,
        long latencyMs,
        Long ttftMs,
        int retryIndex,
        boolean fallbackUsed,
        String status,
        String errorCode,
        String finishReason) {
}
