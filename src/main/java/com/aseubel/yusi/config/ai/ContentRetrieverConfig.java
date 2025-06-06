package com.aseubel.yusi.config.ai;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * @author Aseubel
 * @date 2025/5/9 下午5:52
 */
@Configuration
public class ContentRetrieverConfig {

    @Autowired
    private ApplicationContext applicationContext;

    @Bean(name = "contentRetriever")
    public ContentRetriever contentRetriever() {
        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore((MilvusEmbeddingStore) applicationContext.getBean("milvusEmbeddingStore"))
                .embeddingModel((EmbeddingModel) applicationContext.getBean("embeddingModel"))
                .maxResults(3)
                .minScore(0.5)
                // 根据查询动态指定用户id
                .dynamicFilter(query -> {
                    String userId = (String) query.metadata().chatMemoryId();
                    return metadataKey("userId").isEqualTo(userId);
                })
                .build();
        return contentRetriever;
    }
}
