package com.aseubel.yusi.common.ratelimit;

import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.common.exception.RateLimitException;
import com.aseubel.yusi.common.web.ClientIpResolver;
import com.aseubel.yusi.common.utils.LowSensitivityLogSummary;
import com.aseubel.yusi.observability.metrics.YusiMetrics;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RateLimiterConfig;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 限流切面（支持 Redis 故障降级到 Guava RateLimiter）
 */
@Aspect
@Component
@Order(2)
@Slf4j
public class RateLimiterAspect {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private ClientIpResolver clientIpResolver;

    @Autowired(required = false)
    private YusiMetrics metrics;

    @Autowired(required = false)
    private RateLimiterSubjectEncoder subjectEncoder = RateLimiterSubjectEncoder.fromEnvironment();

    private static final long MAX_LOCAL_LIMITERS = 10_000L;
    private static final long LOCAL_LIMITER_EXPIRE_MINUTES = 10L;
    private static final double LOCAL_FALLBACK_RATE_FACTOR = 0.25D;
    private static final double MAX_LOCAL_PERMITS_PER_SECOND = 1D;

    // 本地 Guava RateLimiter 缓存，用于 Redis 故障时降级。缓存必须有界，避免
    // 用户/IP 维度不断增长把故障实例的堆内存耗尽。
    private final Cache<String, LocalLimiter> localRateLimiters = CacheBuilder.newBuilder()
            .maximumSize(MAX_LOCAL_LIMITERS)
            .expireAfterAccess(LOCAL_LIMITER_EXPIRE_MINUTES, TimeUnit.MINUTES)
            .build();

    // Redis 故障标记
    private volatile boolean redisAvailable = true;

    // Redis 故障检测时间窗口（秒）
    private static final int REDIS_CHECK_INTERVAL = 30;

    // 最后一次 Redis 故障时间
    private volatile long lastRedisFailureTime = 0;

    @Before("@annotation(rateLimiter)")
    public void doBefore(JoinPoint point, RateLimiter rateLimiterAnnotation) {
        if (requiresSubject(rateLimiterAnnotation.limitType()) && !subjectEncoder.isConfigured()) {
            recordRateLimited(rateLimiterAnnotation.key(), "dependency");
            throw new RateLimitException();
        }

        // 检查是否需要重新探测 Redis 状态
        checkRedisAvailability();

        boolean allowed;
        String failureCategory;
        if (redisAvailable) {
            // 优先使用 Redis 分布式限流
            allowed = tryRedisRateLimit(rateLimiterAnnotation, point);
            failureCategory = redisAvailable ? "limit_exceeded" : "dependency";
        } else {
            // Redis 不可用时只使用有界本地桶，不能无限放行。
            allowed = tryLocalRateLimit(rateLimiterAnnotation, point);
            failureCategory = "dependency";
        }
        if (!allowed) {
            recordRateLimited(rateLimiterAnnotation.key(), failureCategory);
            throw new RateLimitException();
        }
    }

    /**
     * 尝试 Redis 限流
     */
    private boolean tryRedisRateLimit(RateLimiter rateLimiterAnnotation, JoinPoint point) {
        try {
            String combineKey = getCombineKey(rateLimiterAnnotation, point);
            int time = rateLimiterAnnotation.time();
            int count = rateLimiterAnnotation.count();

            if (time <= 0 || count <= 0) {
                return false;
            }

            RRateLimiter rRateLimiter = redissonClient.getRateLimiter(combineKey);
            ensureRedisRate(rRateLimiter, count, time);

            // 设置过期时间，避免 key 永久存在 (稍微长于限流窗口)
            rRateLimiter.expire(java.time.Duration.ofSeconds((long) time + 10L));

            boolean acquired = rRateLimiter.tryAcquire();

            // 成功访问 Redis，标记为可用
            redisAvailable = true;
            return acquired;

        } catch (Exception e) {
            log.warn("Rate limit backend unavailable: operation=rate_limit, failure_category=dependency, "
                    + "fallback=bounded_local, exceptionType={}", LowSensitivityLogSummary.exceptionType(e));
            redisAvailable = false;
            lastRedisFailureTime = System.currentTimeMillis();
            // 降级到本地限流
            return tryLocalRateLimit(rateLimiterAnnotation, point);
        }
    }

    /**
     * 尝试本地 Guava RateLimiter 限流
     */
    private boolean tryLocalRateLimit(com.aseubel.yusi.common.ratelimit.RateLimiter rateLimiterAnnotation, JoinPoint point) {
        try {
            String combineKey = getCombineKey(rateLimiterAnnotation, point);
            int time = rateLimiterAnnotation.time();
            int count = rateLimiterAnnotation.count();

            if (time <= 0 || count <= 0) {
                return false;
            }

            // 降级实例只承担保守的单机保护，不能把分布式窗口额度原样复制到每个实例。
            double permitsPerSecond = Math.min(
                    ((double) count / time) * LOCAL_FALLBACK_RATE_FACTOR,
                    MAX_LOCAL_PERMITS_PER_SECOND);
            LocalLimiter localLimiter = localRateLimiters.asMap().compute(combineKey,
                    (key, current) -> current == null || !current.matches(count, time)
                            ? new LocalLimiter(count, time,
                                    com.google.common.util.concurrent.RateLimiter.create(permitsPerSecond))
                            : current);
            return localLimiter.limiter.tryAcquire();

        } catch (Exception e) {
            log.warn("Local rate limit failed: operation=rate_limit, failure_category=dependency, exceptionType={}",
                    LowSensitivityLogSummary.exceptionType(e));
            // 限流失败时，为了安全起见，默认拒绝
            return false;
        }
    }

    /**
     * 检查 Redis 可用性
     * 定期探测 Redis 是否恢复
     */
    private void checkRedisAvailability() {
        if (redissonClient == null) {
            redisAvailable = false;
            return;
        }
        if (!redisAvailable) {
            long currentTime = System.currentTimeMillis();
            // 每隔 REDIS_CHECK_INTERVAL 秒尝试探测一次
            if (currentTime - lastRedisFailureTime > REDIS_CHECK_INTERVAL * 1000) {
                try {
                    // 尝试访问 Redis，检测是否恢复（通过创建一个临时 key 来测试）
                    RRateLimiter testLimiter = redissonClient.getRateLimiter("yusi:rateLimiter:test:connection");
                    testLimiter.trySetRate(RateType.OVERALL, 1, 1, RateIntervalUnit.SECONDS);
                    testLimiter.expire(java.time.Duration.ofSeconds(1));
                    redisAvailable = true;
                    log.info("Rate limit backend recovered: operation=rate_limit");
                } catch (Exception e) {
                    // 仍然不可用
                    log.debug("Rate limit backend remains unavailable: operation=rate_limit, "
                            + "fallback=bounded_local, exceptionType={}", LowSensitivityLogSummary.exceptionType(e));
                }
            }
        }
    }

    public String getCombineKey(RateLimiter rateLimiterAnnotation, JoinPoint point) {
        StringBuilder stringBuffer = new StringBuilder("yusi:rateLimiter:");
        stringBuffer.append(rateLimiterAnnotation.key()).append(":");

        if (rateLimiterAnnotation.limitType() == LimitType.IP) {
            stringBuffer.append("ip:")
                    .append(subjectEncoder.encode("ip:" + getIpAddress())).append(":");
        } else if (rateLimiterAnnotation.limitType() == LimitType.USER) {
            String userId = UserContext.getUserId();
            stringBuffer.append("u:")
                    .append(subjectEncoder.encode("user:" + (userId == null ? "unknown" : userId)))
                    .append(":");
        }

        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        Class<?> targetClass = method.getDeclaringClass();
        stringBuffer.append(targetClass.getName()).append(":").append(method.getName());

        return stringBuffer.toString();
    }

    private String getIpAddress() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
                    .getRequestAttributes();
            if (attributes != null) {
                return clientIpResolver.resolve(attributes.getRequest());
            }
        } catch (Exception e) {
            log.warn("Client subject resolution failed: operation=rate_limit, failure_category=dependency, "
                    + "exceptionType={}", LowSensitivityLogSummary.exceptionType(e));
        }
        return "unknown";
    }

    private boolean requiresSubject(LimitType limitType) {
        return limitType == LimitType.USER || limitType == LimitType.IP;
    }

    private void recordRateLimited(String operation, String failureCategory) {
        if (metrics != null) {
            metrics.recordRateLimited(operation, failureCategory);
        }
    }

    private void ensureRedisRate(RRateLimiter limiter, int count, int time) {
        long intervalMillis = (long) time * 1000L;
        RateLimiterConfig current = limiter.getConfig();
        if (current == null) {
            limiter.trySetRate(RateType.OVERALL, count, time, RateIntervalUnit.SECONDS);
            current = limiter.getConfig();
        }
        if (!matches(current, count, intervalMillis)) {
            // setRate also clears the old permit state, so a changed annotation
            // takes effect immediately instead of inheriting stale capacity.
            limiter.setRate(RateType.OVERALL, count, time, RateIntervalUnit.SECONDS);
        }
    }

    private boolean matches(RateLimiterConfig config, int count, long intervalMillis) {
        return config != null
                && config.getRateType() == RateType.OVERALL
                && Long.valueOf(count).equals(config.getRate())
                && Long.valueOf(intervalMillis).equals(config.getRateInterval());
    }

    private static final class LocalLimiter {
        private final int count;
        private final int time;
        private final com.google.common.util.concurrent.RateLimiter limiter;

        private LocalLimiter(int count, int time,
                com.google.common.util.concurrent.RateLimiter limiter) {
            this.count = count;
            this.time = time;
            this.limiter = limiter;
        }

        private boolean matches(int expectedCount, int expectedTime) {
            return count == expectedCount && time == expectedTime;
        }
    }
}
