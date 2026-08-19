package com.aseubel.yusi.common.task;

import com.aseubel.yusi.observability.metrics.YusiMetrics;
import com.aseubel.yusi.observability.task.TaskHealthRegistry;
import com.aseubel.yusi.observability.trace.TraceIdSupport;
import com.aseubel.yusi.redis.service.IRedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.redisson.api.RLock;
import org.springframework.stereotype.Component;

/**
 * Runs a scheduled job on at most one application instance at a time.
 * The lock uses Redisson's watchdog so a long-running job is not cut off
 * by an arbitrarily short lease, while a crashed instance is still recovered.
 */
@Slf4j
@Component
public class DistributedJobRunner {

    private static final String LOCK_PREFIX = "yusi:scheduler:";

    private final IRedisService redisService;
    private final TaskHealthRegistry taskHealthRegistry;
    private final YusiMetrics metrics;

    public DistributedJobRunner(IRedisService redisService) {
        this(redisService, null, null);
    }

    @Autowired
    public DistributedJobRunner(IRedisService redisService,
            TaskHealthRegistry taskHealthRegistry,
            YusiMetrics metrics) {
        this.redisService = redisService;
        this.taskHealthRegistry = taskHealthRegistry;
        this.metrics = metrics;
    }

    public void runIfLeader(String jobName, Runnable task) {
        RLock lock = redisService.getLock(LOCK_PREFIX + jobName);
        boolean locked = false;
        String traceId = "job_" + normalizeTraceToken(jobName);
        try {
            locked = lock.tryLock();
            if (!locked) {
                log.debug("Skip scheduled job {} because another instance owns the lock", jobName);
                return;
            }
            if (taskHealthRegistry != null) {
                taskHealthRegistry.recordStart(jobName);
            }
            try (TraceIdSupport.Scope ignored = TraceIdSupport.open(traceId)) {
                task.run();
            }
            if (taskHealthRegistry != null) {
                taskHealthRegistry.recordSuccess(jobName);
            }
            if (metrics != null) {
                metrics.recordTask(jobName, "success");
            }
        } catch (Exception e) {
            if (taskHealthRegistry != null) {
                taskHealthRegistry.recordFailure(jobName, classify(e));
            }
            if (metrics != null) {
                metrics.recordTask(jobName, "failure");
            }
            log.error("Scheduled job failed: task={}, exceptionType={}", jobName,
                    e.getClass().getSimpleName());
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private String normalizeTraceToken(String jobName) {
        if (jobName == null || jobName.isBlank()) {
            return "unknown";
        }
        return jobName.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private String classify(Exception exception) {
        String type = exception.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
        if (type.contains("timeout")) {
            return "timeout";
        }
        if (type.contains("connect") || type.contains("redis")) {
            return "connection_failure";
        }
        return "unknown";
    }
}
