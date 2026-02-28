package com.aseubel.yusi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云邮件推送配置
 *
 * @author Aseubel
 * @date 2026/02/28
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "aliyun.dm")
public class AliyunDmProperties {
    /**
     * AccessKey ID
     */
    private String accessKeyId;

    /**
     * AccessKey Secret
     */
    private String accessKeySecret;

    /**
     * 发信地址
     */
    private String accountName;

    /**
     * 发信人昵称
     */
    private String fromAlias = "Yusi";

    /**
     * 服务接入点
     */
    private String endpoint = "dm.aliyuncs.com";
}
