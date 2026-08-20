package com.aseubel.yusi.observability.alert;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;

/** Fixed semantic fields used by the transport-specific Feishu envelope. */
public record AlertMessage(
        String category,
        String service,
        String operation,
        String level,
        String window,
        String count,
        String value,
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
            "model-state-sync", "unknown");
    private static final Set<String> LEVELS = Set.of("warning", "critical");
    private static final Set<String> WINDOWS = Set.of("2m", "5m");
    private static final Set<String> STATES = Set.of("firing", "recovered");
    private static final Set<String> CLASSIFICATIONS = Set.of(
            "available", "none", "timeout", "connection_failure", "unavailable", "validation", "rejected",
            "dependency", "admission_store_unavailable", "reservation_conflict", "limit_exceeded",
            "failure_rate", "budget_denied", "unknown");

    public AlertMessage {
        category = normalize(category, CATEGORIES);
        service = "yusi-backend".equals(service) ? service : "unknown";
        operation = normalize(operation, OPERATIONS);
        level = normalize(level, LEVELS);
        window = normalize(window, WINDOWS);
        count = nonNegativeInteger(count);
        value = nonNegativeDecimal(value);
        classification = normalize(classification, CLASSIFICATIONS);
        observedAt = observedAt == null ? Instant.EPOCH : observedAt;
        state = normalize(state, STATES);
    }

    public static AlertMessage fromSignal(AlertSignal signal) {
        if (signal == null) {
            throw new IllegalArgumentException("alert signal is required");
        }
        return new AlertMessage(signal.category(), signal.service(), signal.operation(), signal.level(),
                signal.window(), Long.toString(signal.count()), Double.toString(signal.value()),
                signal.classification(), signal.observedAt(), signal.state());
    }

    private static String normalize(String value, Set<String> allowed) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return allowed.contains(normalized) ? normalized : "unknown";
    }

    private static String nonNegativeInteger(String value) {
        try {
            long parsed = Long.parseLong(value == null ? "0" : value.trim());
            return Long.toString(Math.max(0L, parsed));
        } catch (RuntimeException ignored) {
            return "0";
        }
    }

    private static String nonNegativeDecimal(String value) {
        try {
            double parsed = Double.parseDouble(value == null ? "0" : value.trim());
            return Double.isFinite(parsed) ? Double.toString(Math.max(0D, parsed)) : "0.0";
        } catch (RuntimeException ignored) {
            return "0.0";
        }
    }
}
