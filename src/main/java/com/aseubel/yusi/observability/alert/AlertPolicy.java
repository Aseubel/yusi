package com.aseubel.yusi.observability.alert;

import java.time.Duration;
import java.util.Locale;

/** Initial, production-tunable alert thresholds. */
public record AlertPolicy(
        Duration readinessDownAfter,
        Duration modelWindow,
        double modelFailureRateThreshold,
        int modelMinimumCalls,
        double taskWarningMinutes,
        double taskCriticalMinutes,
        Duration taskSustainAfter,
        Duration budgetWindow,
        int budgetMinimumDenials,
        Duration suppressionWindow,
        int maxDeliveryAttempts) {

    public static AlertPolicy initial() {
        return new AlertPolicy(
                Duration.ofMinutes(2),
                Duration.ofMinutes(5),
                0.20D,
                20,
                15D,
                60D,
                Duration.ofMinutes(5),
                Duration.ofMinutes(5),
                10,
                Duration.ofMinutes(30),
                3);
    }

    public static String normalizeBudgetReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "unknown";
        }
        String normalized = reason.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("ADMISSION_STORE_UNAVAILABLE")) {
            return "admission_store_unavailable";
        }
        if (normalized.equals("RESERVATION_CONFLICT")) {
            return "reservation_conflict";
        }
        if (normalized.equals("LIMIT_EXCEEDED") || normalized.startsWith("LIMIT_EXCEEDED:")) {
            return "limit_exceeded";
        }
        return "unknown";
    }
}
