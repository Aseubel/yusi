package com.aseubel.yusi.config.oss;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "yusi.oss")
public class OssProperties {

    private String region;

    private String bucketName;

    private String endpoint;

    private Integer urlExpireSeconds = 3600;

    private Long maxFileSize = 10485760L;

    private String imageFolder = "images/";
}
