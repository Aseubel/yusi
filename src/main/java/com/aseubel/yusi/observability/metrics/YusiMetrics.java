package com.aseubel.yusi.observability.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
            "embedding-worker", "lifegraph-worker", "model-state-sync");
    private static final Set<String> RESULTS = Set.of(
            "success", "empty", "failure", "rejected", "unavailable", "unknown");
    private static final Set<String> FAILURE_CATEGORIES = Set.of(
            "none", "timeout", "connection_failure", "unavailable", "validation", "rejected",
            "dependency", "unknown");

    private final MeterRegistry registry;

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

    private String operationForTask(String taskName) {
        if (taskName == null) {
            return "unknown";
        }
        String normalized = taskName.trim().toLowerCase(Locale.ROOT);
        return OPERATIONS.contains(normalized) ? normalized : "unknown";
    }

    private String normalizeFailure(String value) {
        return normalize(value, FAILURE_CATEGORIES);
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
}
