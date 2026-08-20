package com.aseubel.yusi.observability.task;

import java.time.Duration;
import java.time.Instant;
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
        recordStart(taskName, Instant.now());
    }

    public void recordStart(String taskName, Instant startedAt) {
        String normalizedName = normalizeTaskName(taskName);
        if (normalizedName == null || startedAt == null) {
            return;
        }
        long now = startedAt.toEpochMilli();
        states.compute(normalizedName, (ignored, previous) -> new TaskState(
                normalizedName,
                "RUNNING",
                now,
                previous == null ? 0L : previous.lastSuccessAt(),
                "none"));
    }

    public void recordSuccess(String taskName) {
        recordSuccess(taskName, Instant.now());
    }

    public void recordSuccess(String taskName, Instant completedAt) {
        String normalizedName = normalizeTaskName(taskName);
        if (normalizedName == null || completedAt == null) {
            return;
        }
        long now = completedAt.toEpochMilli();
        states.compute(normalizedName, (ignored, previous) -> new TaskState(
                normalizedName,
                "SUCCESS",
                previous == null ? now : previous.startedAt(),
                now,
                "none"));
    }

    public void recordFailure(String taskName, String category) {
        recordFailure(taskName, category, Instant.now());
    }

    public void recordFailure(String taskName, String category, Instant failedAt) {
        String normalizedName = normalizeTaskName(taskName);
        if (normalizedName == null || failedAt == null) {
            return;
        }
        long now = failedAt.toEpochMilli();
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

    public Map<String, TaskTiming> timingSnapshot(Instant now, TaskScheduleCatalog catalog) {
        if (now == null || catalog == null) {
            return Map.of();
        }
        Map<String, TaskTiming> result = new LinkedHashMap<>();
        for (String taskName : allowedTaskNames()) {
            TaskState state = states.get(taskName);
            Duration interval = catalog.expectedInterval(taskName);
            if (state == null || interval == null) {
                result.put(taskName, TaskTiming.unknown(taskName));
                continue;
            }
            double lagMinutes = 0D;
            double dueGapMinutes = 0D;
            String resultLabel;
            if ("RUNNING".equals(state.status())) {
                lagMinutes = minutesBetween(state.startedAt(), now.toEpochMilli());
                resultLabel = "running";
            } else if (state.lastSuccessAt() <= 0L) {
                result.put(taskName, TaskTiming.unknown(taskName));
                continue;
            } else {
                long dueAt = state.lastSuccessAt() + interval.toMillis();
                dueGapMinutes = Math.max(0D, minutesBetween(dueAt, now.toEpochMilli()));
                resultLabel = dueGapMinutes > 0D ? "overdue" : "on_time";
            }
            result.put(taskName, new TaskTiming(taskName, true, dueGapMinutes, lagMinutes,
                    resultLabel, state.failureCategory()));
        }
        return Map.copyOf(result);
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

    public record TaskTiming(
            String taskName,
            boolean sampleAvailable,
            double dueGapMinutes,
            double lagMinutes,
            String result,
            String failureCategory) {

        private static TaskTiming unknown(String taskName) {
            return new TaskTiming(taskName, false, Double.NaN, Double.NaN,
                    "unknown", "unknown");
        }
    }

    private static double minutesBetween(long earlierMillis, long laterMillis) {
        return Math.max(0D, (laterMillis - earlierMillis) / 60000D);
    }
}
