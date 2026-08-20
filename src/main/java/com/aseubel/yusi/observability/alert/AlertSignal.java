package com.aseubel.yusi.observability.alert;

import java.time.Instant;
import java.util.Set;

/** Immutable, already-normalized alert fact. */
public record AlertSignal(
        String category,
        String service,
        String operation,
        String level,
        String window,
        long count,
        double value,
        String classification,
        Instant observedAt,
        String state) {

    private static final Set<String> CATEGORIES = Set.of(
            "service_unavailable", "model_failure_rate", "task_backlog", "budget_denied");
    private static final Set<String> OPERATIONS = Set.of(
            "readiness", "db", "redis", "milvus", "model_gateway", "tasks", "model_call",
            "model_admission", "usage-sync", "memory-scan", "room-cleanup", "memory-fusion",
            "proactive-greeting", "embedding-cleanup", "lifegraph-cleanup",
            "task-execution-recovery", "security-audit-cleanup", "lifegraph-merge-suggestion",
            "weekly-report", "weekly-match", "embedding-worker", "lifegraph-worker",
            "model-state-sync");
    private static final Set<String> LEVELS = Set.of("warning", "critical");
    private static final Set<String> WINDOWS = Set.of("2m", "5m");
    private static final Set<String> CLASSIFICATIONS = Set.of(
            "available", "none", "timeout", "connection_failure", "unavailable", "validation",
            "rejected", "dependency", "admission_store_unavailable", "reservation_conflict",
            "limit_exceeded", "failure_rate", "budget_denied", "unknown");
    private static final Set<String> STATES = Set.of("firing", "recovered");

    public AlertSignal {
        if (!CATEGORIES.contains(category) || !"yusi-backend".equals(service)
                || !OPERATIONS.contains(operation) || !LEVELS.contains(level)
                || !WINDOWS.contains(window) || !CLASSIFICATIONS.contains(classification)
                || !STATES.contains(state)) {
            throw new IllegalArgumentException("unsupported alert classification");
        }
        if (isBlank(service) || isBlank(operation) || isBlank(window)
                || isBlank(classification) || observedAt == null || count < 0L
                || Double.isNaN(value) || Double.isInfinite(value) || value < 0D) {
            throw new IllegalArgumentException("invalid low-sensitivity alert signal");
        }
    }

    public String fingerprint() {
        return category + "|" + service + "|" + operation + "|" + level;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
