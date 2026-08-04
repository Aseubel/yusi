package com.aseubel.yusi.config.oss;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.credentials.StaticCredentialsProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
@EnableConfigurationProperties(OssProperties.class)
public class OssConfig {

    @Bean
    public OSSClient ossClient(OssProperties ossProperties) {
        var builder = OSSClient.newBuilder()
            .credentialsProvider(new StaticCredentialsProvider(
                ossProperties.getAccessKeyId(),
                ossProperties.getAccessKeySecret()))
            .region(ossProperties.getRegion());
        if (ossProperties.getEndpoint() != null && !ossProperties.getEndpoint().isBlank()) {
            builder.endpoint(ossProperties.getEndpoint());
        }
        return builder.build();
    }
}
