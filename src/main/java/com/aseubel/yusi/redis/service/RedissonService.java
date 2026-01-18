package com.aseubel.yusi.redis.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.*;
import org.redisson.client.RedisException;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Redis 服务 - Redisson
 */
@Slf4j
@Service("redissonService")
public class RedissonService implements IRedisService {

    @Resource
    private RedissonClient redissonClient;

    public <T> void setValue(String key, T value) {
        redissonClient.<T>getBucket(key).set(value);
    }

    @Override
    public <T> void setValue(String key, T value, long expired) {
        RBucket<T> bucket = redissonClient.getBucket(key);
        bucket.set(value, Duration.ofMillis(expired));
    }

    public <T> T getValue(String key) {
        return redissonClient.<T>getBucket(key).get();
    }

    @Override
    public <T> RQueue<T> getQueue(String key) {
        return redissonClient.getQueue(key);
    }

    @Override
    public <T> RBlockingQueue<T> getBlockingQueue(String key) {
        return redissonClient.getBlockingQueue(key);
    }

    @Override
    public <T> RDelayedQueue<T> getDelayedQueue(RBlockingQueue<T> rBlockingQueue) {
        return redissonClient.getDelayedQueue(rBlockingQueue);
    }

    @Override
    public void setAtomicLong(String key, long value) {
        redissonClient.getAtomicLong(key).set(value);
    }

    @Override
    public Long getAtomicLong(String key) {
        return redissonClient.getAtomicLong(key).get();
    }

    @Override
    public long incr(String key) {
        return redissonClient.getAtomicLong(key).incrementAndGet();
    }

    @Override
    public long incrBy(String key, long delta) {
        return redissonClient.getAtomicLong(key).addAndGet(delta);
    }

    @Override
    public long decr(String key) {
        return redissonClient.getAtomicLong(key).decrementAndGet();
    }

    @Override
    public long decrBy(String key, long delta) {
        return redissonClient.getAtomicLong(key).addAndGet(-delta);
    }

    @Override
    public void remove(String key) {
        redissonClient.getBucket(key).delete();
    }

    @Override
    public void removeByPattern(String pattern) {
        redissonClient.getKeys().deleteByPattern(pattern);
    }

    @Override
    public boolean isExists(String key) {
        return redissonClient.getBucket(key).isExists();
    }

    public void addToSet(String key, String value) {
        RSet<String> set = redissonClient.getSet(key);
        set.add(value);
    }

    public void removeFromSet(String key, String value) {
        RSet<String> set = redissonClient.getSet(key);
        set.remove(value);
    }

    public Set<String> getSetMembers(String key) {
        RSet<String> set = redissonClient.getSet(key);
        return set.readAll();
    }

    public boolean isSetMember(String key, String value) {
        RSet<String> set = redissonClient.getSet(key);
        return set.contains(value);
    }

    public void setSetExpired(String key, long expired) {
        RSet<String> set = redissonClient.getSet(key);
        set.expire(Duration.ofMillis(expired));
    }

    public void addToList(String key, String value) {
        RList<String> list = redissonClient.getList(key);
        list.add(value);
    }

    public String getFromList(String key, int index) {
        RList<String> list = redissonClient.getList(key);
        return list.get(index);
    }

    @Override
    public <K, V> RMap<K, V> getMap(String key) {
        return redissonClient.getMap(key);
    }

    public <T> void addToMap(String key, String field, T value) {
        RMap<String, T> map = redissonClient.getMap(key);
        map.put(field, value);
    }

    public void addToMap(String key, String field, String value) {
        RMap<String, String> map = redissonClient.getMap(key);
        map.put(field, value);
    }

    public void setMapExpired(String key, long expired) {
        RMap<String, String> map = redissonClient.getMap(key);
        map.expire(Duration.ofMillis(expired));
    }

    public Long getMapExpired(String key) {
        RMap<String, String> map = redissonClient.getMap(key);
        return map.remainTimeToLive();
    }

    public Map<String, String> getMapToJavaMap(String key) {
        RMap<String, String> map = redissonClient.getMap(key);
        return map.readAllMap();
    }

    public void removeFromMap(String key, String field) {
        RMap<String, String> map = redissonClient.getMap(key);
        map.remove(field);
    }

    @Override
    public <K, V> V getFromMap(String key, K field) {
        return redissonClient.<K, V>getMap(key).get(field);
    }

    public void addToSortedSet(String key, String value) {
        RSortedSet<String> sortedSet = redissonClient.getSortedSet(key);
        sortedSet.add(value);
    }

    @Override
    public RLock getLock(String key) {
        return redissonClient.getLock(key);
    }

    @Override
    public void unLock(String key) {
        redissonClient.getLock(key).unlock();
    }

    @Override
    public RLock getFairLock(String key) {
        return redissonClient.getFairLock(key);
    }

    @Override
    public RReadWriteLock getReadWriteLock(String key) {
        return redissonClient.getReadWriteLock(key);
    }

    @Override
    public RSemaphore getSemaphore(String key) {
        return redissonClient.getSemaphore(key);
    }

    @Override
    public RPermitExpirableSemaphore getPermitExpirableSemaphore(String key) {
        return redissonClient.getPermitExpirableSemaphore(key);
    }

    @Override
    public RCountDownLatch getCountDownLatch(String key) {
        return redissonClient.getCountDownLatch(key);
    }

    @Override
    public <T> RBloomFilter<T> getBloomFilter(String key) {
        return redissonClient.getBloomFilter(key);
    }

    @Override
    public Boolean setNx(String key) {
        return redissonClient.getBucket(key).setIfAbsent("lock");
    }

    @Override
    public Boolean setNx(String key, Duration expired) {
        return redissonClient.getBucket(key).setIfAbsent("lock", expired);
    }

    @Override
    public <T> T execute(String shaDigest, String luaScript, RScript.ReturnType returnType, List<Object> keys,
            Object... values) {
        try {
            // 在获取脚本对象时，为其指定 StringCodec
            // 这会覆盖客户端默认的 JsonJacksonCodec，仅对本次操作有效
            RScript script = redissonClient.getScript(StringCodec.INSTANCE);

            // 优先尝试使用 EVALSHA 执行，效率最高
            return script.evalSha(RScript.Mode.READ_WRITE, shaDigest, returnType, keys, values);

        } catch (RedisException e) {
            // 捕获 Redis 异常，并检查是不是 NOSCRIPT 错误
            if (e.getMessage().startsWith("NOSCRIPT")) {
                log.warn("Lua script with SHA {} not found, falling back to EVAL.", shaDigest);

                // 如果是 NOSCRIPT 错误，同样使用带 StringCodec 的脚本对象执行 EVAL
                RScript script = redissonClient.getScript(StringCodec.INSTANCE);
                return script.eval(RScript.Mode.READ_WRITE, luaScript, returnType, keys, values);
            }
            // 如果是其他类型的 Redis 异常，则直接向上抛出
            throw e;
        }
    }

    @Override
    public <T> T execute(String luaScript, RScript.ReturnType returnType, List<Object> keys, Object... values) {
        return redissonClient.getScript().eval(RScript.Mode.READ_WRITE, luaScript, returnType, keys, values);
    }

    @Override
    public void incrMap(String key, String field, int delta) {
        redissonClient.getMap(key).addAndGet(field, delta);
    }

}
