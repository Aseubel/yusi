package com.aseubel.yusi.observability.health;

import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/** Read-only Redisson probe using a fixed, non-user key. */
@Component("redis")
@ConditionalOnBean(RedissonClient.class)
public class RedisHealthIndicator implements HealthIndicator {

    private static final String HEALTH_BUCKET = "yusi:health:probe";

    private final RedissonClient redissonClient;

    public RedisHealthIndicator(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public Health health() {
        try {
            RBucket<String> bucket = redissonClient.getBucket(HEALTH_BUCKET);
            bucket.isExists();
            return Health.up()
                    .withDetail("dependency", "redis")
                    .withDetail("classification", "available")
                    .build();
        } catch (RuntimeException exception) {
            return Health.down()
                    .withDetail("dependency", "redis")
                    .withDetail("classification", classify(exception))
                    .build();
        }
    }

    private String classify(RuntimeException exception) {
        String type = exception.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
        if (type.contains("timeout")) {
            return "timeout";
        }
        if (type.contains("connect") || type.contains("redis")) {
            return "connection_failure";
        }
        return "unavailable";
    }
}
