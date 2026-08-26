package com.aseubel.yusi.redis;

import com.aseubel.yusi.redis.service.RedissonService;
import org.junit.jupiter.api.Test;
import org.redisson.api.RKeys;
import org.redisson.api.RMap;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedissonServiceUsageCleanupTest {

    private static final String TARGET_USER = "fixture-usage-delete-target";
    private static final String USAGE_KEY = "yusi:usage:2026-08-26";

    @Test
    void usageCleanupScansRawHashFieldsWithoutDecodingHashValues() throws Exception {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RKeys keys = mock(RKeys.class);
        @SuppressWarnings("unchecked")
        RMap<Object, Object> usageMap = mock(RMap.class);
        String encodedTargetField = "\"" + TARGET_USER + "\u0001fixture-ip\u0001fixture-interface\"";
        String rawTargetField = TARGET_USER + "\u0001fixture-legacy-ip\u0001fixture-legacy-interface";
        String otherField = "\"fixture-other\u0001fixture-ip\u0001fixture-interface\"";

        when(redissonClient.getKeys()).thenReturn(keys);
        when(keys.getKeysByPattern("yusi:usage:*")).thenReturn(List.of(USAGE_KEY));
        when(redissonClient.getMap(eq(USAGE_KEY), same(StringCodec.INSTANCE))).thenReturn(usageMap);
        when(usageMap.keySet()).thenReturn(Set.of(encodedTargetField, rawTargetField, otherField));

        RedissonService service = new RedissonService();
        ReflectionTestUtils.setField(service, "redissonClient", redissonClient);

        Method cleanup = assertDoesNotThrow(
                () -> RedissonService.class.getMethod("removeUsageFields", String.class));
        cleanup.invoke(service, TARGET_USER);

        verify(redissonClient).getMap(USAGE_KEY, StringCodec.INSTANCE);
        verify(usageMap).remove(encodedTargetField);
        verify(usageMap).remove(rawTargetField);
        verify(usageMap, never()).remove(otherField);
    }

    @Test
    void stringValueReadsUseStringCodecForStringRedisTemplateKeys() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        @SuppressWarnings("unchecked")
        RBucket<String> bucket = mock(RBucket.class);
        String key = "yusi:chunk:" + TARGET_USER + ":fixture:totalChunks";

        when(redissonClient.<String>getBucket(eq(key), same(StringCodec.INSTANCE))).thenReturn(bucket);
        when(bucket.get()).thenReturn("2");

        RedissonService service = new RedissonService();
        ReflectionTestUtils.setField(service, "redissonClient", redissonClient);

        assertEquals("2", service.getStringValue(key));
        verify(redissonClient).getBucket(key, StringCodec.INSTANCE);
        verify(bucket).get();
    }
}
