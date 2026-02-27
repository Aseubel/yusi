package com.aseubel.yusi.redis.aspect;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.aseubel.yusi.common.utils.CompressUtils;
import com.aseubel.yusi.common.utils.SpelResolverHelper;
import com.aseubel.yusi.redis.service.IRedisService;
import com.aseubel.yusi.redis.annotation.QueryCache;
import com.aseubel.yusi.redis.annotation.UpdateCache;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RScript;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;

import static com.aseubel.yusi.redis.common.CacheConstants.*;

/**
 * 更新缓存切面，<a href="https://mp.weixin.qq.com/s/OENxcvRSGtkPtNzjWwK6KQ">转转技术</a>
 *
 * @author Aseubel
 * @date 2025/8/7 上午11:00
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class CacheAspect {

    @Value("${spring.cache.redis.key-prefix:yusi:}")
    private String keyPrefix;
    @Value("${spring.cache.redis.time-to-live:1s}")
    private Duration ttl;
    private static final long lockTime = 10000;

    private final ThreadPoolTaskExecutor threadPoolExecutor;
    private final SpelResolverHelper spelResolverHelper;
    private final IRedisService redisService;
    private final ObjectMapper objectMapper;

    private static final String SET_SH = "local key = KEYS[1]\n"
            + "local value = ARGV[1]\n"
            + "local owner = ARGV[2]\n"
            + "local ttl = tonumber(ARGV[3])\n"
            + "local lockOwner = redis.call('HGET', key, '" + OWNER + "')\n"
            + "local lockInfo = redis.call('HGET', key, '" + LOCK_INFO + "')\n"
            + "if lockOwner and lockOwner == owner then\n"
            + "    redis.call('HMSET', key, '" + LOCK_INFO + "', 'unlocked', '" + VALUE + "', value)\n"
            + "    redis.call('HDEL', key, '" + UNLOCK_TIME + "')\n"
            + "    if ttl and ttl > 0 then redis.call('EXPIRE', key, ttl) end\n"
            + "    return 0\n"
            + "end\n"
            + "return 1";

    /**
     * 释放锁脚本（用于查询结果为空时释放锁，避免锁永久卡住）
     */
    private static final String RELEASE_LOCK_SH = "local key = KEYS[1]\n"
            + "local owner = ARGV[1]\n"
            + "local lockOwner = redis.call('HGET', key, '" + OWNER + "')\n"
            + "if lockOwner and lockOwner == owner then\n"
            + "    redis.call('HDEL', key, '" + OWNER + "', '" + UNLOCK_TIME + "', '" + LOCK_INFO + "')\n"
            + "end\n"
            + "return 0";

    private static final String GET_SH = "local key = KEYS[1]\n"
            + "local newUnlockTime = ARGV[1]\n"
            + "local owner = ARGV[2]\n"
            + "local currentTime = tonumber(ARGV[3])\n"
            + "local value = redis.call('HGET', key, '" + VALUE + "')\n"
            + "local unlockTime = redis.call('HGET', key, '" + UNLOCK_TIME + "')\n"
            + "local lockOwner = redis.call('HGET', key, '" + OWNER + "')\n"
            + "local lockInfo = redis.call('HGET', key, '" + LOCK_INFO + "')\n"
            + "if unlockTime and currentTime > tonumber(unlockTime or 0) then\n"
            + "    redis.call('HMSET', key, '" + LOCK_INFO + "', 'locked', '" + UNLOCK_TIME + "', newUnlockTime, '"
            + OWNER + "', owner)\n"
            + "    return {value, '" + NEED_QUERY + "'}\n"
            + "end\n"
            + "if not value or value == '' then\n"
            + "    if lockOwner and lockOwner ~= owner then\n"
            + "        return {value, '" + NEED_WAIT + "'}\n"
            + "    end\n"
            + "    redis.call('HMSET', key, '" + LOCK_INFO + "', 'locked', '" + UNLOCK_TIME + "', newUnlockTime, '"
            + OWNER + "', owner)\n"
            + "    return {value, '" + NEED_QUERY + "'}\n"
            + "end\n"
            + "if lockInfo and lockInfo == 'locked' then \n"
            + "    return {value, '" + SUCCESS_NEED_QUERY + "'}\n"
            + "end\n"
            + "return {value , '" + SUCCESS + "'}";

    private static final String SET_SH_SHA = DigestUtil.sha1Hex(SET_SH);
    private static final String GET_SH_SHA = DigestUtil.sha1Hex(GET_SH);
    private static final String RELEASE_LOCK_SH_SHA = DigestUtil.sha1Hex(RELEASE_LOCK_SH);

    @Around("@annotation(updateCache)")
    public Object updateCache(ProceedingJoinPoint joinPoint, UpdateCache updateCache) throws Throwable {
        return processUpdateCache(joinPoint, updateCache);
    }

    /**
     * 处理多个 @UpdateCache 注解（通过 @Repeatable 支持）
     * 先清除所有缓存，再执行方法一次
     */
    @Around("@annotation(updateCaches)")
    public Object updateCaches(ProceedingJoinPoint joinPoint, UpdateCache.Container updateCaches) throws Throwable {
        // 先清除所有缓存
        for (UpdateCache updateCache : updateCaches.value()) {
            processEvictOnly(updateCache, joinPoint);
        }
        // 执行方法一次
        return joinPoint.proceed();
    }

    /**
     * 仅处理缓存失效（不执行方法）
     */
    private void processEvictOnly(UpdateCache updateCache, ProceedingJoinPoint joinPoint) throws Throwable {
        String key = keyPrefix + spelResolverHelper.resolveSpel(joinPoint, updateCache.key());
        if (key.contains("*")) {
            redisService.removeByPattern(key);
        } else {
            redisService.remove(key);
        }
    }

    /**
     * 处理单个 @UpdateCache 注解: 使用淘汰策略保障一致性
     */
    private Object processUpdateCache(ProceedingJoinPoint joinPoint, UpdateCache updateCache) throws Throwable {
        String key = keyPrefix + spelResolverHelper.resolveSpel(joinPoint, updateCache.key());

        // 无论是哪种模式，更新时为了一致性直接清除缓存（延迟双删模式）
        if (key.contains("*")) {
            redisService.removeByPattern(key);
        } else {
            redisService.remove(key);
        }

        Object result = joinPoint.proceed();

        // 延迟双删保证数据一致性（防止在 DB 更新期间，其他线程将旧数据重新载入缓存）
        if (key.contains("*")) {
            redisService.removeByPattern(key);
        } else {
            redisService.remove(key);
        }

        return result;
    }

    /**
     * 如果数据为空且锁已过期: 则锁定缓存，返回 NEED_QUERY，同步执行"取数据"并返回结果
     * 如果数据为空且被锁定: 则返回 NEED_WAIT，休眠100ms并再次查询
     * 如果数据不为空且被锁定: 则立即返回SUCCESS_NEED_QUERY和缓存数据，异步执行"取数据"
     * 如果数据不为空且未锁定: 则立即返回SUCCESS和缓存数据
     */
    @Around("@annotation(queryCache)")
    public Object queryCache(ProceedingJoinPoint joinPoint, QueryCache queryCache) throws Throwable {

        String key = keyPrefix + spelResolverHelper.resolveSpel(joinPoint, queryCache.key());
        String newUnlockTime = String.valueOf(System.currentTimeMillis() + lockTime);
        // 使用UUID替换线程名称，防止多台机器相同线程名称覆盖锁
        String owner = java.util.UUID.randomUUID().toString();
        String currentTime = String.valueOf(System.currentTimeMillis());

        // 计算TTL
        long effectiveTtl = queryCache.ttl() > 0 ? queryCache.ttl() : ttl.getSeconds();
        boolean compress = queryCache.compress();

        List<Object> result = redisService.execute(GET_SH_SHA, GET_SH, RScript.ReturnType.MULTI, List.of(key),
                newUnlockTime, owner, currentTime);

        String sign = (String) result.get(1);
        long maxWaitTime = System.currentTimeMillis() + 1500; // 最多等待1.5秒
        while (sign.equals(NEED_WAIT) && System.currentTimeMillis() < maxWaitTime) {
            Thread.sleep(100); // 缩短休眠间隔，提高响应性
            result = redisService.execute(GET_SH_SHA, GET_SH, RScript.ReturnType.MULTI, List.of(key), newUnlockTime,
                    owner, currentTime);
            sign = (String) result.get(1);
        }

        if (sign.equals(NEED_WAIT)) {
            // 超时未获取到锁，直接查询源数据（兜底），避免大面积 5xx
            log.warn("Cache fallback: Lock wait timeout for key [{}], querying db directly.", key);
            return joinPoint.proceed();
        }

        // 从返回结果中获取原始的、未经处理的业务数据字符串
        String valueStr = (String) result.get(0);
        // 如果启用压缩，解压缩数据
        if (compress && valueStr != null) {
            valueStr = CompressUtils.decompress(valueStr);
        }
        // 提前获取目标方法的返回类型（包含泛型信息），用于后续的反序列化
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        JavaType returnType = objectMapper.getTypeFactory().constructType(method.getGenericReturnType());

        switch (sign) {
            case NEED_QUERY:
                // 缓存未命中，直接查询源数据并返回
                return queryData(joinPoint, key, effectiveTtl, compress, owner);
            case SUCCESS_NEED_QUERY:
                // 缓存命中，但数据陈旧，需要异步更新（当前项目已配置 ThreadPoolTaskExecutor 复制了 MDC 和 UserContext
                // 等上下文，可安全异步执行）
                threadPoolExecutor.execute(() -> {
                    try {
                        // 异步刷新的时候由于生成了新的调用对象，最好生成一个新的 owner 继续加锁，或者复用但已不再占用当前请求的主流程
                        queryData(joinPoint, key, effectiveTtl, compress, owner);
                    } catch (Throwable e) {
                        log.error("Async cache refresh failed for key: {}", key, e);
                    }
                });
                if (valueStr == null)
                    return null;
                // 空缓存防穿透值处理 (如果写入的是特殊值)
                if ("\"\"".equals(valueStr) || "null".equals(valueStr)) {
                    return null;
                }
                return objectMapper.readValue(valueStr, returnType);
            case SUCCESS:
                if (valueStr == null)
                    return null;
                if ("\"\"".equals(valueStr) || "null".equals(valueStr)) {
                    return null;
                }
                return objectMapper.readValue(valueStr, returnType);
        }
        return joinPoint.proceed();
    }

    private Object queryData(ProceedingJoinPoint joinPoint, String key, long ttl, boolean compress, String owner)
            throws Throwable {
        try {
            Object value = joinPoint.proceed();
            // ObjectUtil.isNotEmpty 会将空集合判定为空！导致空集合查库结果无法缓存（从而被穿透）。
            // 修改为仅阻挡业务真实 null。如果需要存特殊空值防穿透，这里可以转换。
            if (value != null) {
                // 这样确保了存入 Redis 的是标准、可反序列化的 JSON
                String valueAsJson = objectMapper.writeValueAsString(value);
                // 如果启用压缩，压缩数据后再存储
                if (compress) {
                    valueAsJson = CompressUtils.compress(valueAsJson);
                }
                redisService.execute(SET_SH_SHA, SET_SH, RScript.ReturnType.INTEGER, List.of(key), valueAsJson,
                        owner, ttl);
            } else {
                // 解决缓存穿透问题，缓存一个特殊的标识值防止反复穿透数据库。这里存为特殊的JSON null "" (看业务需要，这里存 "null" 也可以)
                redisService.execute(SET_SH_SHA, SET_SH, RScript.ReturnType.INTEGER, List.of(key), "null",
                        owner, 60); // 空值缓存时间短一点，60秒即可
            }
            return value;
        } catch (Throwable e) {
            log.error("Failed to query data for cache key: {}", key, e);
            // 异常时也要释放锁
            try {
                redisService.execute(RELEASE_LOCK_SH_SHA, RELEASE_LOCK_SH, RScript.ReturnType.INTEGER, List.of(key),
                        owner);
            } catch (Exception ex) {
                log.warn("Failed to release lock for key: {}", key, ex);
            }
            throw e;
        }
    }

}
