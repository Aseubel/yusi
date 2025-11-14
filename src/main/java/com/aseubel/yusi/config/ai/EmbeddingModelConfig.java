package com.aseubel.yusi.config.ai;

import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.aseubel.yusi.config.ai.properties.EmbeddingModelConfigProperties;

/**
 * @author Aseubel
 * @date 2025/5/9 下午5:54
 */
@Configuration
@EnableConfigurationProperties(EmbeddingModelConfigProperties.class)
public class EmbeddingModelConfig {

    @Bean(name = "embeddingModel")
    public EmbeddingModel embeddingModel(EmbeddingModelConfigProperties properties) {
        return QwenEmbeddingModel.builder()
                .apiKey(properties.getApikey())
                .build();
    }
}
