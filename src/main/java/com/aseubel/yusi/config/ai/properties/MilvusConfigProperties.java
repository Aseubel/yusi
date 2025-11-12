package com.aseubel.yusi.config.ai.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "milvus", ignoreInvalidFields = true)
public class MilvusConfigProperties {
    // 连接认证，1为使用host:port + 用户名密码，2为使用uri + token
    private int mode = 1;

    private String uri = "";

    private String token = "";

    private String host = "127.0.0.1";

    private int port = 19530;

    private String username = "";

    private String password = "";
}
