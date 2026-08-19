package com.aseubel.yusi.observability.task;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory task state used by readiness checks. It deliberately stores only
 * fixed names, bounded statuses, timestamps, and failure categories.
 */
public class TaskHealthRegistry {

    private static final Set<String> ALLOWED_TASKS = Set.of(
            "usage-sync",
            "memory-scan",
            "room-cleanup",
            "memory-fusion",
            "proactive-greeting",
            "embedding-cleanup",
            "lifegraph-cleanup",
            "task-execution-recovery",
            "security-audit-cleanup",
            "lifegraph-merge-suggestion",
            "weekly-report",
            "weekly-match",
            "embedding-worker",
            "lifegraph-worker",
            "model-state-sync");

    private static final Set<String> ALLOWED_FAILURE_CATEGORIES = Set.of(
            "none",
            "timeout",
            "connection_failure",
            "unavailable",
            "rejected",
            "dependency",
            "unknown");

    private final Map<String, TaskState> states = new ConcurrentHashMap<>();

    public static List<String> allowedTaskNames() {
        return ALLOWED_TASKS.stream().sorted().toList();
    }

    public void recordStart(String taskName) {
        String normalizedName = normalizeTaskName(taskName);
        if (normalizedName == null) {
            return;
        }
        long now = System.currentTimeMillis();
        states.compute(normalizedName, (ignored, previous) -> new TaskState(
                normalizedName,
                "RUNNING",
                now,
                previous == null ? 0L : previous.lastSuccessAt(),
                "none"));
    }

    public void recordSuccess(String taskName) {
        String normalizedName = normalizeTaskName(taskName);
        if (normalizedName == null) {
            return;
        }
        long now = System.currentTimeMillis();
        states.compute(normalizedName, (ignored, previous) -> new TaskState(
                normalizedName,
                "SUCCESS",
                previous == null ? now : previous.startedAt(),
                now,
                "none"));
    }

    public void recordFailure(String taskName, String category) {
        String normalizedName = normalizeTaskName(taskName);
        if (normalizedName == null) {
            return;
        }
        long now = System.currentTimeMillis();
        states.compute(normalizedName, (ignored, previous) -> new TaskState(
                normalizedName,
                "FAILED",
                previous == null ? now : previous.startedAt(),
                previous == null ? 0L : previous.lastSuccessAt(),
                normalizeFailureCategory(category)));
    }

    public Map<String, TaskState> snapshot() {
        List<String> names = new ArrayList<>(states.keySet());
        names.sort(String::compareTo);
        Map<String, TaskState> snapshot = new LinkedHashMap<>();
        for (String name : names) {
            snapshot.put(name, states.get(name));
        }
        return Map.copyOf(snapshot);
    }

    public static String normalizeTaskName(String taskName) {
        if (taskName == null) {
            return null;
        }
        String normalized = taskName.trim().toLowerCase(java.util.Locale.ROOT);
        return ALLOWED_TASKS.contains(normalized) ? normalized : null;
    }

    public static String normalizeFailureCategory(String category) {
        if (category == null || category.isBlank()) {
            return "unknown";
        }
        String normalized = category.trim().toLowerCase(java.util.Locale.ROOT);
        return ALLOWED_FAILURE_CATEGORIES.contains(normalized) ? normalized : "unknown";
    }

    public record TaskState(
            String taskName,
            String status,
            long startedAt,
            long lastSuccessAt,
            String failureCategory) {
    }
}
