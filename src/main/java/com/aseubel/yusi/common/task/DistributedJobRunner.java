package com.aseubel.yusi.common.task;

import com.aseubel.yusi.redis.service.IRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.stereotype.Component;

/**
 * Runs a scheduled job on at most one application instance at a time.
 * The lock uses Redisson's watchdog so a long-running job is not cut off
 * by an arbitrarily short lease, while a crashed instance is still recovered.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedJobRunner {

    private static final String LOCK_PREFIX = "yusi:scheduler:";

    private final IRedisService redisService;

    public void runIfLeader(String jobName, Runnable task) {
        RLock lock = redisService.getLock(LOCK_PREFIX + jobName);
        boolean locked = false;
        try {
            locked = lock.tryLock();
            if (!locked) {
                log.debug("Skip scheduled job {} because another instance owns the lock", jobName);
                return;
            }
            task.run();
        } catch (Exception e) {
            log.error("Scheduled job {} failed", jobName, e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
