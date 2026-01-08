package com.aseubel.yusi.service.ai.impl;

import com.aseubel.yusi.service.ai.AiLockService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis-based implementation of AI lock service
 * Uses Redis for distributed locking across multiple instances
 */
@Service
@Slf4j
public class AiLockServiceImpl implements AiLockService {

    @Autowired
    private RedissonClient redissonClient;

    private static final String AI_LOCK_KEY = "ai:lock:";
    // Lock timeout in seconds - prevents deadlock if request fails without cleanup
    private static final long LOCK_TIMEOUT_SECONDS = 180; // 3 minutes, same as SSE timeout

    @Override
    public boolean tryAcquireLock(String userId) {
        RBucket<String> bucket = redissonClient.getBucket(AI_LOCK_KEY + userId);
        boolean acquired = bucket.setIfAbsent("locked", Duration.ofSeconds(LOCK_TIMEOUT_SECONDS));
        if (acquired) {
            log.debug("AI lock acquired for user {}", userId);
        } else {
            log.debug("AI lock already held for user {}", userId);
        }
        return acquired;
    }

    @Override
    public void releaseLock(String userId) {
        RBucket<String> bucket = redissonClient.getBucket(AI_LOCK_KEY + userId);
        bucket.delete();
        log.debug("AI lock released for user {}", userId);
    }

    @Override
    public boolean isLocked(String userId) {
        RBucket<String> bucket = redissonClient.getBucket(AI_LOCK_KEY + userId);
        return bucket.isExists();
    }
}
