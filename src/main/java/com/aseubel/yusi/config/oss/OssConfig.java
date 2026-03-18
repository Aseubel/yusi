package com.aseubel.yusi.config.oss;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.credentials.StaticCredentialsProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OssProperties.class)
public class OssConfig {

    @Bean
    public OSSClient ossClient(OssProperties ossProperties) {
        return OSSClient.newBuilder()
            .credentialsProvider(new StaticCredentialsProvider(
                ossProperties.getAccessKeyId(),
                ossProperties.getAccessKeySecret()))
            .region(ossProperties.getRegion())
            .build();
    }
}
