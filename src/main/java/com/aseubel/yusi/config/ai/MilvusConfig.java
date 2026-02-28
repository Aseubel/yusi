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
        return buildMilvusStore(properties, "yusi_embedding_collection");
    }

    @Bean(name = "midTermMemoryStore")
    public MilvusEmbeddingStore midTermMemoryStoreConfig(MilvusConfigProperties properties) {
        return buildMilvusStore(properties, "yusi_mid_term_memory");
    }

    private MilvusEmbeddingStore buildMilvusStore(MilvusConfigProperties properties, String collectionName) {
        MilvusEmbeddingStore.Builder builder = MilvusEmbeddingStore.builder();
        switch (properties.getMode()) {
            case 1:
                builder.host(properties.getHost())
                        .port(properties.getPort())
                        .username(properties.getUsername())
                        .password(properties.getPassword());
                break;
            case 2:
                builder.uri(properties.getUri())
                        .token(properties.getToken());
                break;
            default:
                throw new IllegalArgumentException("Invalid mode");
        }
        return builder
                .collectionName(collectionName) // Name of the collection
                .dimension(((EmbeddingModel) applicationContext.getBean("embeddingModel")).dimension())
                .indexType(IndexType.HNSW) // 它是目前内存索引中 查询速度 和 召回率 (Recall) 平衡最好的算法。
                .metricType(MetricType.COSINE) // Cosine 能最好地衡量“语义相似度”
                .consistencyLevel(ConsistencyLevelEnum.EVENTUALLY)
                .autoFlushOnInsert(false)
                .idFieldName("id")
                .textFieldName("text")
                .metadataFieldName("metadata")
                .vectorFieldName("vector")
                .build();
    }
}
