package com.aseubel.yusi.common.ratelimit;

import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.common.exception.RateLimitException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
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
import java.util.concurrent.ConcurrentHashMap;

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

    // 本地 Guava RateLimiter 缓存，用于 Redis 故障时降级
    private final ConcurrentHashMap<String, com.google.common.util.concurrent.RateLimiter> localRateLimiters = new ConcurrentHashMap<>();

    // Redis 故障标记
    private volatile boolean redisAvailable = true;

    // Redis 故障检测时间窗口（秒）
    private static final int REDIS_CHECK_INTERVAL = 30;

    // 最后一次 Redis 故障时间
    private volatile long lastRedisFailureTime = 0;

    @Before("@annotation(rateLimiter)")
    public void doBefore(JoinPoint point, RateLimiter rateLimiterAnnotation) {
        // 检查是否需要重新探测 Redis 状态
        checkRedisAvailability();

        if (redisAvailable) {
            // 优先使用 Redis 分布式限流
            if (!tryRedisRateLimit(rateLimiterAnnotation, point)) {
                throw new RateLimitException("访问过于频繁，请稍后再试");
            }
        } else {
            // Redis 不可用，降级到本地 Guava RateLimiter
            if (!tryLocalRateLimit(rateLimiterAnnotation, point)) {
                throw new RateLimitException("访问过于频繁，请稍后再试 (本地限流)");
            }
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

            RRateLimiter rRateLimiter = redissonClient.getRateLimiter(combineKey);
            // 尝试设置速率，如果已存在则忽略
            rRateLimiter.trySetRate(RateType.OVERALL, count, time, RateIntervalUnit.SECONDS);

            // 设置过期时间，避免 key 永久存在 (稍微长于限流窗口)
            rRateLimiter.expire(java.time.Duration.ofSeconds(time + 10));

            boolean acquired = rRateLimiter.tryAcquire();
            
            // 成功访问 Redis，标记为可用
            redisAvailable = true;
            return acquired;
            
        } catch (Exception e) {
            log.error("Redis 限流失败，降级到本地限流", e);
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

            // 计算每秒许可数
            double permitsPerSecond = (double) count / time;

            // 获取或创建本地 RateLimiter
            com.google.common.util.concurrent.RateLimiter localLimiter = localRateLimiters.computeIfAbsent(combineKey, 
                k -> com.google.common.util.concurrent.RateLimiter.create(permitsPerSecond));

            // 动态调整速率（如果配置变化）
            // 注意：Guava RateLimiter 不支持动态调整，这里只是简单处理
            // 如果需要精确控制，可以考虑重新创建或使用其他库

            return localLimiter.tryAcquire();
            
        } catch (Exception e) {
            log.error("本地限流失败", e);
            // 限流失败时，为了安全起见，默认拒绝
            return false;
        }
    }

    /**
     * 检查 Redis 可用性
     * 定期探测 Redis 是否恢复
     */
    private void checkRedisAvailability() {
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
                    log.info("Redis 连接已恢复");
                } catch (Exception e) {
                    // 仍然不可用
                    log.debug("Redis 仍然不可用，继续使用本地限流");
                }
            }
        }
    }

    public String getCombineKey(RateLimiter rateLimiterAnnotation, JoinPoint point) {
        StringBuilder stringBuffer = new StringBuilder("yusi:rateLimiter:");
        stringBuffer.append(rateLimiterAnnotation.key()).append(":");

        if (rateLimiterAnnotation.limitType() == LimitType.IP) {
            stringBuffer.append(getIpAddress()).append(":");
        } else if (rateLimiterAnnotation.limitType() == LimitType.USER) {
            String userId = UserContext.getUserId();
            if (userId != null) {
                stringBuffer.append(userId).append(":");
            }
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
                HttpServletRequest request = attributes.getRequest();
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("Proxy-Client-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("WL-Proxy-Client-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getRemoteAddr();
                }
                return ip;
            }
        } catch (Exception e) {
            log.error("获取IP地址失败", e);
        }
        return "unknown";
    }
}
