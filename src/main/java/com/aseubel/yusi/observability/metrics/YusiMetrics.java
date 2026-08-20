package com.aseubel.yusi.observability.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Low-cardinality metrics facade. Every public method normalizes labels before
 * touching Micrometer and never lets telemetry failures affect business code.
 */
@Component
public class YusiMetrics {

    private static final List<String> ALLOWED_SEARCH_TAGS = List.of(
            "tool", "operation", "result", "failure_category");
    private static final Set<String> TOOLS = Set.of(
            "diary", "mid_term_memory", "life_graph", "mcp", "system", "task", "unknown");
    private static final Set<String> OPERATIONS = Set.of(
            "search", "diary_search", "mid_term_memory_search", "fetch_recent_mid_term_memory",
            "life_graph_search", "mcp_diary_search", "mcp_memory_search", "model_call", "unknown",
            "usage-sync", "memory-scan", "room-cleanup", "memory-fusion", "proactive-greeting",
            "embedding-cleanup", "lifegraph-cleanup", "task-execution-recovery",
            "security-audit-cleanup", "lifegraph-merge-suggestion", "weekly-report", "weekly-match",
            "embedding-worker", "lifegraph-worker", "model-state-sync", "readiness", "db", "redis",
            "milvus", "model_gateway", "tasks", "model_admission");
    private static final Set<String> RATE_LIMIT_OPERATIONS = Set.of(
            "admin-user-permission", "admin-scenario-audit", "admin-suggestion-reply",
            "admin-suggestion-status", "admin-announcement-publish", "admin-embeddings-full-sync",
            "admin-user-deregister", "chatstream", "chat-cancel", "persona-config-update",
            "cognitive-conflict-resolve", "memory-fusion-run", "chat-inject-greeting",
            "developer-api-key-rotate", "developer-api-key-scopes", "developer-api-key-revoke",
            "diary-create", "diary-update", "diary-chat-deprecated", "image-upload",
            "image-upload-batch", "image-upload-check", "image-chunk-upload", "image-chunk-merge",
            "image-chunk-progress", "image-urls", "image-delete", "image-delete-batch",
            "key-settings-update", "key-reencrypt-diaries", "key-recovery-code", "key-recovery",
            "lifegraph-merge-accept", "lifegraph-merge-reject", "lifegraph-entity-create",
            "lifegraph-entity-update", "lifegraph-entity-delete", "lifegraph-relation-create",
            "lifegraph-relation-update", "lifegraph-relation-delete", "match-settings", "match-action",
            "match-feedback", "match-end", "match-report", "match-block", "memory-center-update",
            "memory-center-delete", "memory-persona-update", "memory-persona-delete",
            "memory-life-graph-update", "memory-life-graph-delete", "model-console-update",
            "model-route-preview", "notification-read", "notification-read-all", "notification-delete",
            "prompt-save", "prompt-update", "prompt-activate", "prompt-delete", "room-chat-send",
            "room-create", "room-join", "room-start", "room-scenario-submit", "room-scenario-update",
            "room-scenario-delete", "room-scenario-resubmit", "room-cancel", "room-vote-cancel",
            "room-submit", "soul-chat-send", "soul-chat-read", "plaza-submit", "plaza-feed",
            "plaza-update", "plaza-delete", "plaza-resonate", "plaza-signal", "plaza-signal-read",
            "suggestion-create", "user-register", "register-code", "login", "refresh",
            "forgot-password-code", "forgot-password-reset", "user-update", "user-logout",
            "location-create", "location-update", "location-delete", "geo-search", "geo-reverse",
            "platform-stats");
    private static final Set<String> RESULTS = Set.of(
            "success", "empty", "failure", "rejected", "unavailable", "unknown", "denied");
    private static final Set<String> DEPENDENCY_RESULTS = Set.of("up", "down", "unknown");
    private static final Set<String> TASK_RESULTS = Set.of(
            "running", "overdue", "on_time", "not_running", "unknown");
    private static final Set<String> TASK_OPERATIONS = Set.of(
            "usage-sync", "memory-scan", "room-cleanup", "memory-fusion", "proactive-greeting",
            "embedding-cleanup", "lifegraph-cleanup", "task-execution-recovery",
            "security-audit-cleanup", "lifegraph-merge-suggestion", "weekly-report", "weekly-match",
            "embedding-worker", "lifegraph-worker", "model-state-sync");
    private static final Set<String> FAILURE_CATEGORIES = Set.of(
            "none", "timeout", "connection_failure", "unavailable", "validation", "rejected",
            "dependency", "admission_store_unavailable", "reservation_conflict", "limit_exceeded",
            "unknown");

    private final MeterRegistry registry;
    private final Map<String, AtomicReference<Double>> gaugeValues = new ConcurrentHashMap<>();

    public YusiMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public static List<String> allowedSearchTags() {
        return ALLOWED_SEARCH_TAGS;
    }

    public void recordToolSearch(String tool, String operation, String result,
            String failureCategory, long durationMs, int resultCount) {
        try {
            String normalizedTool = normalize(tool, TOOLS);
            String normalizedOperation = normalize(operation, OPERATIONS);
            String normalizedResult = normalize(result, RESULTS);
            String normalizedFailure = normalizeFailure(failureCategory);
            String[] tags = tags(normalizedTool, normalizedOperation, normalizedResult, normalizedFailure);

            Counter total = Counter.builder("tool_search_total")
                    .description("Tool search operations")
                    .tags(tags)
                    .register(registry);
            total.increment();

            Counter failure = Counter.builder("tool_search_failure_total")
                    .description("Failed tool search operations")
                    .tags(tags)
                    .register(registry);
            failure.increment(0D);
            if ("failure".equals(normalizedResult) || !"none".equals(normalizedFailure)) {
                failure.increment();
            }

            Timer.builder("tool_search_latency")
                    .description("Tool search latency")
                    .tags(tags)
                    .register(registry)
                    .record(Duration.ofMillis(Math.max(0L, durationMs)));
            DistributionSummary.builder("tool_search_results")
                    .description("Tool search result count")
                    .tags(tags)
                    .register(registry)
                    .record(Math.max(0, resultCount));
        } catch (RuntimeException ignored) {
            // Metrics must never change the result or failure behavior of a tool.
        }
    }

    public void recordModelCall(String result, String failureCategory, long latencyMs) {
        try {
            String normalizedResult = normalize(result, RESULTS);
            String normalizedFailure = normalizeFailure(failureCategory);
            String[] tags = tags("system", "model_call", normalizedResult, normalizedFailure);
            Counter total = Counter.builder("model_call_total").tags(tags).register(registry);
            total.increment();
            Counter failure = Counter.builder("model_call_failure_total").tags(tags).register(registry);
            failure.increment(0D);
            if ("failure".equals(normalizedResult) || !"none".equals(normalizedFailure)) {
                failure.increment();
            }
            Timer.builder("model_call_latency")
                    .tags(tags)
                    .register(registry)
                    .record(Duration.ofMillis(Math.max(0L, latencyMs)));
        } catch (RuntimeException ignored) {
            // Metrics are best effort.
        }
    }

    public void recordTask(String taskName, String status) {
        try {
            String normalizedTask = normalize(operationForTask(taskName), OPERATIONS);
            String normalizedStatus = normalize(status, RESULTS);
            recordToolSearch("task", normalizedTask, normalizedStatus, "none", 0L, 0);
        } catch (RuntimeException ignored) {
            // Metrics are best effort.
        }
    }

    public void recordDependencyHealth(String dependency, String result, String failureCategory,
            boolean available) {
        try {
            String normalizedDependency = normalizeDependency(dependency);
            String normalizedResult = normalize(result, DEPENDENCY_RESULTS);
            if ("unknown".equals(normalizedResult)) {
                normalizedResult = available ? "up" : "down";
            }
            String normalizedFailure = normalizeFailure(failureCategory);
            recordGauge("dependency_health",
                    tags("system", normalizedDependency, normalizedResult, normalizedFailure),
                    available && "up".equals(normalizedResult) ? 1D : 0D, null);
        } catch (RuntimeException ignored) {
            // Metrics are best effort.
        }
    }

    public void recordTaskBacklog(String taskName, double dueGapMinutes, double lagMinutes,
            String result, String failureCategory) {
        try {
            String normalizedTask = normalize(operationForTask(taskName), OPERATIONS);
            String normalizedResult = normalize(result, TASK_RESULTS);
            if (!TASK_OPERATIONS.contains(normalizedTask) || "unknown".equals(normalizedResult)
                    || !Double.isFinite(dueGapMinutes) || !Double.isFinite(lagMinutes)) {
                return;
            }
            String normalizedFailure = normalizeFailure(failureCategory);
            String[] tags = tags("task", normalizedTask, normalizedResult, normalizedFailure);
            recordGauge("task_due_gap", tags, finiteNonNegative(dueGapMinutes), "minutes");
            recordGauge("task_lag", tags, finiteNonNegative(lagMinutes), "minutes");
        } catch (RuntimeException ignored) {
            // Metrics are best effort.
        }
    }

    public void recordBudgetDenied(String reason) {
        try {
            String normalizedFailure = normalizeBudgetReason(reason);
            Counter counter = Counter.builder("budget_denied_total")
                    .description("Denied model budget admissions")
                    .tags(tags("system", "model_admission", "denied", normalizedFailure))
                    .register(registry);
            counter.increment();
        } catch (RuntimeException ignored) {
            // Metrics are best effort.
        }
    }

    public void recordRateLimited(String operation, String failureCategory) {
        try {
            String normalizedOperation = normalize(operation, RATE_LIMIT_OPERATIONS);
            String normalizedFailure = normalizeRateLimitFailure(failureCategory);
            Counter counter = Counter.builder("rate_limited_total")
                    .description("Rejected rate-limited operations")
                    .tags(tags("system", normalizedOperation, "rejected", normalizedFailure))
                    .register(registry);
            counter.increment();
        } catch (RuntimeException ignored) {
            // Metrics are best effort and must not affect a rejection response.
        }
    }

    private String operationForTask(String taskName) {
        if (taskName == null) {
            return "unknown";
        }
        String normalized = taskName.trim().toLowerCase(Locale.ROOT);
        return OPERATIONS.contains(normalized) ? normalized : "unknown";
    }

    private String normalizeDependency(String dependency) {
        if (dependency == null || dependency.isBlank()) {
            return "unknown";
        }
        String normalized = dependency.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "mysql", "database" -> "db";
            case "modelgateway", "model-gateway" -> "model_gateway";
            default -> normalize(normalized, OPERATIONS);
        };
    }

    private String normalizeFailure(String value) {
        return normalize(value, FAILURE_CATEGORIES);
    }

    private String normalizeRateLimitFailure(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "limit_exceeded", "dependency" -> normalized;
            default -> "unknown";
        };
    }

    public static String normalizeBudgetReason(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("ADMISSION_STORE_UNAVAILABLE".equals(normalized)
                || "ADMISSION_STORE_UNAVAILABLE".equals(value.trim())) {
            return "admission_store_unavailable";
        }
        if ("RESERVATION_CONFLICT".equals(normalized)
                || "RESERVATION_CONFLICT".equals(value.trim())) {
            return "reservation_conflict";
        }
        if ("LIMIT_EXCEEDED".equals(normalized)
                || normalized.startsWith("LIMIT_EXCEEDED:")) {
            return "limit_exceeded";
        }
        return "unknown";
    }

    private String normalize(String value, Set<String> allowed) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return allowed.contains(normalized) ? normalized : "unknown";
    }

    private String[] tags(String tool, String operation, String result, String failureCategory) {
        return new String[] {
                "tool", tool,
                "operation", operation,
                "result", result,
                "failure_category", failureCategory
        };
    }

    private void recordGauge(String name, String[] tags, double value, String baseUnit) {
        String key = name + "|" + String.join("|", tags);
        AtomicReference<Double> reference = gaugeValues.computeIfAbsent(key,
                ignored -> new AtomicReference<>(value));
        reference.set(value);
        Gauge.Builder<AtomicReference<Double>> builder = Gauge.builder(name, reference,
                current -> current.get()).tags(tags);
        if (baseUnit != null) {
            builder.baseUnit(baseUnit);
        }
        builder.register(registry);
    }

    private double finiteNonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0D, value) : 0D;
    }
}
