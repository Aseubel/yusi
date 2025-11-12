package com.aseubel.yusi.config.ai.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "model.chat", ignoreInvalidFields = true)
public class ChatModelConfigProperties {
}
