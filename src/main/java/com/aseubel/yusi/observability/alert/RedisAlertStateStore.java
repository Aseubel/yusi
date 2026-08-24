package com.aseubel.yusi.observability.alert;

import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/** Redis-backed alert state using only fixed alert fingerprints and TTLs. */
@Component
@Profile("!test")
@ConditionalOnBean(RedissonClient.class)
public class RedisAlertStateStore implements AlertStateStore {

    private static final String PREFIX = "yusi:alert:dedup:";
    private static final String ROOT_KEY = PREFIX + "root:service_unavailable";
    private static final Set<String> CATEGORIES = Set.of(
            "service_unavailable", "model_failure_rate", "task_backlog", "budget_denied");
    private static final Set<String> OPERATIONS = Set.of(
            "readiness", "db", "redis", "milvus", "model_gateway", "tasks", "model_call",
            "model_admission", "usage-sync", "memory-scan", "room-cleanup", "memory-fusion",
            "proactive-greeting", "embedding-cleanup", "lifegraph-cleanup",
            "task-execution-recovery", "security-audit-cleanup", "lifegraph-merge-suggestion",
            "weekly-report", "weekly-match", "embedding-worker", "lifegraph-worker",
            "model-state-sync");
    private static final Set<String> LEVELS = Set.of("warning", "critical");

    private final RedissonClient redissonClient;
    private final InMemoryAlertStateStore fallback = new InMemoryAlertStateStore(256);
    private volatile String lastFailureCategory = "none";

    public RedisAlertStateStore(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public boolean claim(String fingerprint, Instant now, Duration suppressionWindow) {
        validate(fingerprint, now, suppressionWindow);
        try {
            boolean claimed = bucket(key(fingerprint) + ":claim").setIfAbsent("claimed", suppressionWindow);
            lastFailureCategory = "none";
            return claimed;
        } catch (RuntimeException exception) {
            lastFailureCategory = "dedup_store_unavailable";
            return fallback.claim(fingerprint, now, suppressionWindow);
        }
    }

    @Override
    public void markFiring(String fingerprint, Instant now) {
        validate(fingerprint, now, Duration.ofMinutes(1));
        try {
            bucket(key(fingerprint) + ":active").set("active", Duration.ofHours(2));
            if (fingerprint.startsWith("service_unavailable|")) {
                bucket(ROOT_KEY).set("active", Duration.ofHours(2));
            }
        } catch (RuntimeException ignored) {
            lastFailureCategory = "dedup_store_unavailable";
            fallback.markFiring(fingerprint, now);
            // Notification state must never affect readiness.
        }
    }

    @Override
    public boolean markRecovered(String fingerprint, Instant now) {
        validate(fingerprint, now, Duration.ofMinutes(1));
        try {
            RBucket<String> state = bucket(key(fingerprint) + ":active");
            if (!state.isExists()) {
                return false;
            }
            state.delete();
            if (fingerprint.startsWith("service_unavailable|")) {
                bucket(ROOT_KEY).delete();
            }
            return true;
        } catch (RuntimeException exception) {
            lastFailureCategory = "dedup_store_unavailable";
            return fallback.markRecovered(fingerprint, now);
        }
    }

    @Override
    public boolean isRootSuppressionActive(Instant now) {
        try {
            return bucket(ROOT_KEY).isExists();
        } catch (RuntimeException exception) {
            lastFailureCategory = "dedup_store_unavailable";
            return fallback.isRootSuppressionActive(now);
        }
    }

    public String lastFailureCategory() {
        return lastFailureCategory;
    }

    private RBucket<String> bucket(String key) {
        return redissonClient.getBucket(key);
    }

    private String key(String fingerprint) {
        String[] parts = fingerprint.split("\\|", -1);
        return PREFIX + parts[0] + ":" + parts[1] + ":" + parts[2] + ":" + parts[3];
    }

    private void validate(String fingerprint, Instant now, Duration duration) {
        if (fingerprint == null || now == null || duration == null || duration.isNegative()
                || duration.isZero()) {
            throw new IllegalArgumentException("invalid alert state request");
        }
        String[] parts = fingerprint.split("\\|", -1);
        if (parts.length != 4 || !CATEGORIES.contains(parts[0])
                || !"yusi-backend".equals(parts[1]) || !OPERATIONS.contains(parts[2])
                || !LEVELS.contains(parts[3])) {
            throw new IllegalArgumentException("unsupported alert fingerprint");
        }
    }
}
