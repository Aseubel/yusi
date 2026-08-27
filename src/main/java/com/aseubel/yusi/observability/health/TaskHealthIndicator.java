package com.aseubel.yusi.observability.health;

import com.aseubel.yusi.observability.task.TaskHealthRegistry;
import com.aseubel.yusi.observability.task.TaskScheduleCatalog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Readiness view over the fixed-name task registry. */
@Component("tasks")
public class TaskHealthIndicator implements HealthIndicator {

    private final TaskHealthRegistry registry;
    private final TaskScheduleCatalog scheduleCatalog;

    public TaskHealthIndicator(TaskHealthRegistry registry) {
        this(registry, new TaskScheduleCatalog());
    }

    @Autowired
    public TaskHealthIndicator(TaskHealthRegistry registry, TaskScheduleCatalog scheduleCatalog) {
        this.registry = registry;
        this.scheduleCatalog = scheduleCatalog;
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
            Duration staleAfter = scheduleCatalog.staleAfter(state.taskName());
            long lastActivityAt = "RUNNING".equals(state.status())
                    ? state.startedAt() : state.lastSuccessAt();
            if (staleAfter != null && lastActivityAt > 0
                    && now - lastActivityAt > staleAfter.toMillis()) {
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
