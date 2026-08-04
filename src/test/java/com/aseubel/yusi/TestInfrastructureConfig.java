package com.aseubel.yusi;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aseubel.yusi.config.oss.OssProperties;
import io.milvus.v2.client.MilvusClientV2;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

/**
 * Test-only replacements for clients that would otherwise connect to external services.
 */
@TestConfiguration(proxyBeanMethods = false)
@EnableConfigurationProperties(OssProperties.class)
public class TestInfrastructureConfig {

    @Bean(name = "redissonClient")
    @Primary
    RedissonClient redissonClient() {
        return mock(RedissonClient.class, RETURNS_DEEP_STUBS);
    }

    @Bean
    @Primary
    StringRedisTemplate stringRedisTemplate() {
        return mock(StringRedisTemplate.class, RETURNS_DEEP_STUBS);
    }

    @Bean
    @Primary
    OSSClient ossClient() {
        return mock(OSSClient.class, RETURNS_DEEP_STUBS);
    }

    @Bean(name = "milvusClientV2")
    @Primary
    MilvusClientV2 milvusClientV2() {
        return mock(MilvusClientV2.class, RETURNS_DEEP_STUBS);
    }

}
