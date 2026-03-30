package com.aseubel.yusi.config.ai.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "milvus", ignoreInvalidFields = true)
public class MilvusConfigProperties {

    private String uri = "";

    private String token = "";

    private String host = "127.0.0.1";

    private int port = 19530;

    private String username = "";

    private String password = "";
}
