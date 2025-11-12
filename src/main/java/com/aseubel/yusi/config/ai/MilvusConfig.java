package com.aseubel.yusi.config.ai;

import com.aseubel.yusi.config.ai.properties.MilvusConfigProperties;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Aseubel
 * @date 2025/5/7 上午10:43
 */
@Configuration
@EnableConfigurationProperties(MilvusConfigProperties.class)
public class MilvusConfig {

    @Autowired
    private ApplicationContext applicationContext;

    @Bean(name = "milvusEmbeddingStore")
    public MilvusEmbeddingStore milvusEmbeddingStoreConfig(MilvusConfigProperties properties) {
        MilvusEmbeddingStore.Builder builder = MilvusEmbeddingStore.builder();
        switch (properties.getMode()) {
            case 1:
                builder.host("localhost")
                        .port(19530)
                        .username("username")
                        .password("password");
                break;
            case 2:
                builder.uri(properties.getUri())
                        .token(properties.getToken());
                break;
            default:
                throw new IllegalArgumentException("Invalid mode");
        }
        MilvusEmbeddingStore store = builder

                .collectionName("yusi_embedding_collection")      // Name of the collection
                .dimension(((EmbeddingModel) applicationContext.getBean("embeddingModel")).dimension())                            // Dimension of vectors
                .indexType(IndexType.FLAT)                 // Index type
                .metricType(MetricType.COSINE)             // Metric type
                .consistencyLevel(ConsistencyLevelEnum.EVENTUALLY)  // Consistency level
                .autoFlushOnInsert(true)                   // Auto flush after insert
                .idFieldName("id")                         // ID field name
                .textFieldName("text")                     // Text field name
                .metadataFieldName("metadata")             // Metadata field name
                .vectorFieldName("vector")                 // Vector field name
                .build();                                  // Build the MilvusEmbeddingStore instance
        return store;
    }
}
