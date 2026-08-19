package com.aseubel.yusi.security;

import com.aseubel.yusi.observability.metrics.YusiMetrics;
import com.aseubel.yusi.observability.task.TaskHealthRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilitySensitiveDataTest {

    @Test
    void rejectedInputsNeverBecomeHealthOrMetricDimensions() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        YusiMetrics metrics = new YusiMetrics(registry);
        metrics.recordToolSearch("fixture-user-observability", "fixture-query-observability",
                "fixture-prompt-observability", "fixture-response-observability", 1L, 1);

        TaskHealthRegistry tasks = new TaskHealthRegistry();
        tasks.recordFailure("fixture-cache-key-observability", "fixture-sql-observability");

        String meters = registry.getMeters().toString();
        String states = tasks.snapshot().toString();
        assertThat(meters).doesNotContain("fixture-user-observability", "fixture-query-observability",
                "fixture-prompt-observability", "fixture-response-observability");
        assertThat(states).doesNotContain("fixture-cache-key-observability", "fixture-sql-observability");
    }
}
