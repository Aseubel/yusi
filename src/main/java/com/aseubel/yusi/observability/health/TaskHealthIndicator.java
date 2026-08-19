package com.aseubel.yusi.observability.health;

import com.aseubel.yusi.observability.task.TaskHealthRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Readiness view over the fixed-name task registry. */
@Component("tasks")
public class TaskHealthIndicator implements HealthIndicator {

    private static final Duration STALE_AFTER = Duration.ofHours(2);

    private final TaskHealthRegistry registry;

    public TaskHealthIndicator(TaskHealthRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Health health() {
        long now = System.currentTimeMillis();
        int failed = 0;
        int stale = 0;
        for (TaskHealthRegistry.TaskState state : registry.snapshot().values()) {
            if ("FAILED".equals(state.status())) {
                failed++;
            }
            if (state.lastSuccessAt() > 0 && now - state.lastSuccessAt() > STALE_AFTER.toMillis()) {
                stale++;
            }
        }
        boolean available = failed == 0 && stale == 0;
        Health.Builder builder = available ? Health.up() : Health.down();
        return builder
                .withDetail("dependency", "tasks")
                .withDetail("classification", available ? "available" : "degraded")
                .build();
    }
}
