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
import java.util.Objects;

/**
 * 限流切面
 */
@Aspect
@Component
@Order(2)
@Slf4j
public class RateLimiterAspect {

    @Autowired
    private RedissonClient redissonClient;

    @Before("@annotation(rateLimiter)")
    public void doBefore(JoinPoint point, RateLimiter rateLimiter) {
        String key = rateLimiter.key();
        int time = rateLimiter.time();
        int count = rateLimiter.count();

        String combineKey = getCombineKey(rateLimiter, point);
        
        RRateLimiter rRateLimiter = redissonClient.getRateLimiter(combineKey);
        // 尝试设置速率，如果已存在则忽略
        // 注意：这里简单的使用 trySetRate，如果需要动态调整速率，可能需要先 delete 再 set，或者使用 expire
        rRateLimiter.trySetRate(RateType.OVERALL, count, time, RateIntervalUnit.SECONDS);
        
        // 设置过期时间，避免 key 永久存在 (稍微长于限流窗口)
        rRateLimiter.expire(java.time.Duration.ofSeconds(time + 10));

        if (!rRateLimiter.tryAcquire()) {
            throw new RateLimitException("访问过于频繁，请稍后再试");
        }
    }

    public String getCombineKey(RateLimiter rateLimiter, JoinPoint point) {
        StringBuilder stringBuffer = new StringBuilder("yusi:rateLimiter:");
        stringBuffer.append(rateLimiter.key()).append(":");

        if (rateLimiter.limitType() == LimitType.IP) {
            stringBuffer.append(getIpAddress()).append(":");
        } else if (rateLimiter.limitType() == LimitType.USER) {
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
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
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
