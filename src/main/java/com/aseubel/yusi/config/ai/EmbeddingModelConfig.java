package com.aseubel.yusi.config.ai;

import com.aseubel.yusi.config.ai.properties.EmbeddingModelConfigProperties;
import dev.langchain4j.community.model.dashscope.QwenTokenCountEstimator;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * @author Aseubel
 * @date 2025/5/9 下午5:54
 */
@Configuration
@EnableConfigurationProperties(EmbeddingModelConfigProperties.class)
public class EmbeddingModelConfig {

    @Autowired
    private ApplicationContext applicationContext;

    @Bean(name = "embeddingModel")
    public EmbeddingModel embeddingModel(EmbeddingModelConfigProperties properties) {
        return OpenAiEmbeddingModel.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(properties.getApikey())
                .modelName(properties.getModel())
                .build();
    }

    @Bean(name = "contentRetriever")
    public ContentRetriever contentRetriever() {
        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore((MilvusEmbeddingStore) applicationContext.getBean("milvusEmbeddingStore"))
                .embeddingModel((EmbeddingModel) applicationContext.getBean("embeddingModel"))
                .maxResults(16)
                .minScore(0.3)
                // 根据查询动态指定用户id
                .dynamicFilter(query -> {
                    String userId = (String) query.metadata().chatMemoryId();
                    return metadataKey("userId").isEqualTo(userId);
                })
                .build();
        return contentRetriever;
    }

    @Bean(name = "tokenCountEstimator")
    public TokenCountEstimator tokenCountEstimator() {
        TokenCountEstimator tokenCountEstimator = new OpenAiTokenCountEstimator("gpt-4o-mini");
        return tokenCountEstimator;
    }

    @Bean(name = "documentSplitter")
    public DocumentSplitter documentSplitter() {
        DocumentSplitter documentSplitter = DocumentSplitters.recursive(500, 50);
        return documentSplitter;
    }

}
