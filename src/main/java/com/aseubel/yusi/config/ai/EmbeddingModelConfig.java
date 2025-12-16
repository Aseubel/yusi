package com.aseubel.yusi.config.ai;

import com.aseubel.yusi.config.ai.properties.EmbeddingModelConfigProperties;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Aseubel
 * @date 2025/5/9 下午5:54
 */
@Configuration
@EnableConfigurationProperties(EmbeddingModelConfigProperties.class)
public class EmbeddingModelConfig {

    @Bean(name = "embeddingModel")
    public EmbeddingModel embeddingModel(EmbeddingModelConfigProperties properties) {
        return OpenAiEmbeddingModel.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(properties.getApikey())
                .modelName(properties.getModel())
                .build();
    }
}
