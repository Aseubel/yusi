package com.aseubel.yusi.config.ai.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "model.chat", ignoreInvalidFields = true)
public class ChatModelConfigProperties {

    private String baseurl = "http://langchain4j.dev/demo/openai/v1";

    private String apikey = "demo";

    private String name = "gpt-4o-mini";
}
