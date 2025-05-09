package com.aseubel.yusi.config.ai;

import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Aseubel
 * @date 2025/5/9 下午5:54
 */
@Configuration
public class EmbeddingModelConfig {

    @Value("${embedding.qwen.apikey}")
    private String apiKey;

    @Bean(name = "embeddingModel")
    public EmbeddingModel embeddingModel() {
        return QwenEmbeddingModel.builder()
                .apiKey(apiKey)
                .build();
    }
}
