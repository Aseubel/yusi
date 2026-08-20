package com.aseubel.yusi.observability.task;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TaskHealthRegistryTest {

    @Test
    void recordsOnlyFixedTaskNamesAndLowSensitivityStatuses() {
        TaskHealthRegistry registry = new TaskHealthRegistry();

        registry.recordStart("weekly-match");
        registry.recordSuccess("weekly-match");
        registry.recordFailure("fixture-query-task", "fixture-error-message");

        TaskHealthRegistry.TaskState state = registry.snapshot().get("weekly-match");
        assertThat(state).isNotNull();
        assertThat(state.status()).isEqualTo("SUCCESS");
        assertThat(state.failureCategory()).isEqualTo("none");
        assertThat(registry.snapshot().keySet()).doesNotContain("fixture-query-task");
        assertThat(registry.snapshot().toString()).doesNotContain("fixture-error-message");
    }

    @Test
    void snapshotIsImmutableAndUnknownCategoriesAreNormalized() {
        TaskHealthRegistry registry = new TaskHealthRegistry();
        registry.recordFailure("weekly-match", "fixture-error-category");

        assertThat(registry.snapshot().get("weekly-match").failureCategory()).isEqualTo("unknown");
        assertThat(registry.snapshot()).isUnmodifiable();
    }

    @Test
    void scheduleSnapshotComputesDueGapAndRunningLagWithoutTaskInput() {
        Instant startedAt = Instant.parse("2026-08-12T12:00:00Z");
        Instant now = startedAt.plus(Duration.ofMinutes(20));
        TaskHealthRegistry registry = new TaskHealthRegistry();

        registry.recordStart("weekly-match", startedAt);
        Map<String, TaskHealthRegistry.TaskTiming> timing = registry.timingSnapshot(
                now, new TaskScheduleCatalog());

        TaskHealthRegistry.TaskTiming state = timing.get("weekly-match");
        assertThat(state).isNotNull();
        assertThat(state.sampleAvailable()).isTrue();
        assertThat(state.lagMinutes()).isEqualTo(20D);
        assertThat(state.dueGapMinutes()).isZero();
        assertThat(state.result()).isEqualTo("running");
        assertThat(state.toString()).doesNotContain(
                "fixture-user-alert", "fixture-query-alert", "fixture-content-alert");
    }

    @Test
    void noTaskSampleIsUnknownAndDoesNotPretendToBeZero() {
        TaskHealthRegistry registry = new TaskHealthRegistry();

        TaskHealthRegistry.TaskTiming state = registry.timingSnapshot(
                Instant.parse("2026-08-20T12:00:00Z"), new TaskScheduleCatalog())
                .get("weekly-match");

        assertThat(state).isNotNull();
        assertThat(state.sampleAvailable()).isFalse();
        assertThat(state.result()).isEqualTo("unknown");
        assertThat(state.dueGapMinutes()).isNaN();
        assertThat(state.lagMinutes()).isNaN();
    }
}
