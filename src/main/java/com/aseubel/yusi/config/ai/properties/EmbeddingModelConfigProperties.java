package com.aseubel.yusi.config.ai.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "model.embedding", ignoreInvalidFields = true)
public class EmbeddingModelConfigProperties {

    private String baseUrl;
    private String apikey;
    private String model;
    
    /**
     * 请求超时时间（秒），默认60秒
     */
    private Integer timeoutSeconds = 60;
    
    /**
     * 连接超时时间（秒），默认10秒
     */
    private Integer connectTimeoutSeconds = 10;
}
