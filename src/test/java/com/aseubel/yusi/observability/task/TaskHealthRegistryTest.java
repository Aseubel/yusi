package com.aseubel.yusi.observability.task;

import org.junit.jupiter.api.Test;

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
}
