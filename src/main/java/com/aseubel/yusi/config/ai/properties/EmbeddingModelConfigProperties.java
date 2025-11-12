package com.aseubel.yusi.config.ai.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "model.embedding", ignoreInvalidFields = true)
public class EmbeddingModelConfigProperties {
}
