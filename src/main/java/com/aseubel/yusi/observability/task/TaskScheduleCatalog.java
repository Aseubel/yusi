package com.aseubel.yusi.observability.task;

import java.time.Duration;
import java.util.Map;

/** Fixed scheduler cadence catalog used for low-sensitivity backlog and health facts. */
public class TaskScheduleCatalog {

    private static final Duration MINIMUM_STALE_AFTER = Duration.ofHours(2);

    private static final Map<String, Duration> EXPECTED_INTERVALS = Map.ofEntries(
            Map.entry("usage-sync", Duration.ofMinutes(30)),
            Map.entry("memory-scan", Duration.ofMinutes(10)),
            Map.entry("room-cleanup", Duration.ofMinutes(1)),
            Map.entry("memory-fusion", Duration.ofDays(1)),
            Map.entry("proactive-greeting", Duration.ofHours(1)),
            Map.entry("embedding-cleanup", Duration.ofHours(1)),
            Map.entry("lifegraph-cleanup", Duration.ofHours(1)),
            Map.entry("task-execution-recovery", Duration.ofMinutes(1)),
            Map.entry("security-audit-cleanup", Duration.ofDays(1)),
            Map.entry("lifegraph-merge-suggestion", Duration.ofDays(1)),
            Map.entry("weekly-report", Duration.ofDays(7)),
            Map.entry("weekly-match", Duration.ofDays(7)),
            Map.entry("embedding-worker", Duration.ofSeconds(1)),
            Map.entry("lifegraph-worker", Duration.ofSeconds(2)),
            Map.entry("model-state-sync", Duration.ofSeconds(30)));

    public Duration expectedInterval(String taskName) {
        String normalized = TaskHealthRegistry.normalizeTaskName(taskName);
        return normalized == null ? null : EXPECTED_INTERVALS.get(normalized);
    }

    public Duration staleAfter(String taskName) {
        Duration interval = expectedInterval(taskName);
        if (interval == null) {
            return null;
        }
        Duration graceWindow = interval.multipliedBy(2);
        return graceWindow.compareTo(MINIMUM_STALE_AFTER) < 0
                ? MINIMUM_STALE_AFTER : graceWindow;
    }
}
