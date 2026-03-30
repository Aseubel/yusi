package com.aseubel.yusi.config.ai;

import com.aseubel.yusi.config.ai.properties.MilvusConfigProperties;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.index.request.CreateIndexReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Collections;

/**
 * @author Aseubel
 * @date 2025/5/7 上午10:43
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(MilvusConfigProperties.class)
public class MilvusConfig {

    @Autowired
    private ApplicationContext applicationContext;

    @Bean(name = "milvusClientV2")
    public MilvusClientV2 milvusClientV2(MilvusConfigProperties properties) {
        var builder = ConnectConfig.builder()
                .uri(properties.getUri())
                .token(properties.getToken())
                .username(properties.getUsername())
                .password(properties.getPassword());
        
        return new MilvusClientV2(builder.build());
    }

    @Bean(name = "milvusEmbeddingStore")
    @Primary
    public MilvusEmbeddingStore milvusEmbeddingStoreConfig(MilvusConfigProperties properties,
            MilvusClientV2 milvusClientV2) {
        String collectionName = "yusi_embedding_collection";
        initHybridCollection(milvusClientV2, collectionName);
        return buildMilvusStore(properties, collectionName);
    }

    @Bean(name = "midTermMemoryStore")
    public MilvusEmbeddingStore midTermMemoryStoreConfig(MilvusConfigProperties properties) {
        return buildMilvusStore(properties, "yusi_mid_term_memory");
    }

    private void initHybridCollection(MilvusClientV2 client, String collectionName) {
        Boolean hasCollection = client.hasCollection(HasCollectionReq.builder()
                .collectionName(collectionName)
                .build());

        if (!hasCollection) {
            log.info("初始化 Milvus 混合检索集合: {}", collectionName);
            int dimension = ((EmbeddingModel) applicationContext.getBean("embeddingModel")).dimension();

            CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder().build();
            schema.addField(AddFieldReq.builder().fieldName("id").dataType(DataType.VarChar).maxLength(36)
                    .isPrimaryKey(true).autoID(false).build());
            schema.addField(AddFieldReq.builder().fieldName("text").dataType(DataType.VarChar).maxLength(65535)
                    .enableAnalyzer(true).build());
            schema.addField(AddFieldReq.builder().fieldName("metadata").dataType(DataType.JSON).build());
            schema.addField(AddFieldReq.builder().fieldName("vector").dataType(DataType.FloatVector)
                    .dimension(dimension).build());
            schema.addField(
                    AddFieldReq.builder().fieldName("text_sparse").dataType(DataType.SparseFloatVector).build());

            schema.addFunction(CreateCollectionReq.Function.builder()
                    .name("bm25_text_func")
                    .functionType(io.milvus.common.clientenum.FunctionType.BM25)
                    .inputFieldNames(Collections.singletonList("text"))
                    .outputFieldNames(Collections.singletonList("text_sparse"))
                    .build());

            client.createCollection(CreateCollectionReq.builder()
                    .collectionName(collectionName)
                    .collectionSchema(schema)
                    .build());

            IndexParam indexParamForVectorField = IndexParam.builder()
                    .fieldName("vector")
                    .indexType(IndexParam.IndexType.HNSW)
                    .metricType(IndexParam.MetricType.COSINE)
                    .build();
            client.createIndex(CreateIndexReq.builder()
                    .collectionName(collectionName)
                    .indexParams(Collections.singletonList(indexParamForVectorField))
                    .build());

            IndexParam indexParamForSparseField = IndexParam.builder()
                    .fieldName("text_sparse")
                    .indexType(IndexParam.IndexType.SPARSE_INVERTED_INDEX)
                    .metricType(IndexParam.MetricType.BM25)
                    .build();
            client.createIndex(CreateIndexReq.builder()
                    .collectionName(collectionName)
                    .indexParams(Collections.singletonList(indexParamForSparseField))
                    .build());
            log.info("Milvus 混合检索集合 {} 初始化完成", collectionName);
        }
    }

    private MilvusEmbeddingStore buildMilvusStore(MilvusConfigProperties properties, String collectionName) {
        MilvusEmbeddingStore.Builder builder = MilvusEmbeddingStore.builder();

        return builder
                .username(properties.getUsername())
                .password(properties.getPassword())
                .uri(properties.getUri())
                .token(properties.getToken())
                .collectionName(collectionName) // Name of the collection
                .dimension(((EmbeddingModel) applicationContext.getBean("embeddingModel")).dimension())
                .indexType(IndexType.HNSW) // 它是目前内存索引中 查询速度 和 召回率 (Recall) 平衡最好的算法。
                .metricType(MetricType.COSINE) // Cosine 能最好地衡量“语义相似度”
                .consistencyLevel(ConsistencyLevelEnum.BOUNDED)
                .autoFlushOnInsert(true)
                .idFieldName("id")
                .textFieldName("text")
                .metadataFieldName("metadata")
                .vectorFieldName("vector")
                .build();
    }
}
