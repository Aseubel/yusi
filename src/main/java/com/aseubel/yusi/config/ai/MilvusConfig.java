package com.aseubel.yusi.config.ai;

import com.aseubel.yusi.config.ai.properties.MilvusCollectionProperties;
import com.aseubel.yusi.config.ai.properties.MilvusConfigProperties;
import com.aseubel.yusi.config.ai.properties.EmbeddingModelConfigProperties;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.index.request.CreateIndexReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Collections;

/**
 * @author Aseubel
 * @date 2025/5/7 上午10:43
 */
@Slf4j
@Configuration
@Profile("!test")
@EnableConfigurationProperties({ MilvusConfigProperties.class, EmbeddingModelConfigProperties.class })
public class MilvusConfig {

        private static boolean hasText(String value) {
                return value != null && !value.isBlank();
        }

        @Bean(name = "milvusClientV2")
        public MilvusClientV2 milvusClientV2(MilvusConfigProperties properties,
                        EmbeddingModelConfigProperties embeddingProperties,
                        MilvusCollectionProperties collectionProperties) {
                var builder = ConnectConfig.builder()
                                .uri(properties.getUri())
                                .token(properties.getToken());
                // 无鉴权的本地实例必须省略 username/password，SDK 对非 null 的用户名做非空校验
                if (hasText(properties.getUsername())) {
                        builder.username(properties.getUsername());
                }
                if (hasText(properties.getPassword())) {
                        builder.password(properties.getPassword());
                }

                MilvusClientV2 client = new MilvusClientV2(builder.build());
                initHybridCollection(client, collectionProperties.getEmbedding(),
                                embeddingProperties.getDimension());
                initHybridCollection(client, collectionProperties.getMidTermMemory(),
                                embeddingProperties.getDimension());
                initHybridCollection(client, collectionProperties.getMatchProfile(),
                                embeddingProperties.getDimension());
                return client;
        }

        private void initHybridCollection(MilvusClientV2 client, String collectionName, int dimension) {
                Boolean hasCollection = client.hasCollection(HasCollectionReq.builder()
                                .collectionName(collectionName)
                                .build());

                if (!hasCollection) {
                        log.info("初始化 Milvus 混合检索集合: {}", collectionName);

                        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                                        .build();
                        schema.addField(AddFieldReq.builder().fieldName("id").dataType(DataType.VarChar).maxLength(36)
                                        .isPrimaryKey(true).autoID(false).build());
                        schema.addField(AddFieldReq.builder().fieldName("text").dataType(DataType.VarChar)
                                        .maxLength(65535)
                                        .enableAnalyzer(true).build());
                        schema.addField(AddFieldReq.builder().fieldName("metadata").dataType(DataType.JSON).build());
                        schema.addField(AddFieldReq.builder().fieldName("vector").dataType(DataType.FloatVector)
                                        .dimension(dimension).build());
                        schema.addField(
                                        AddFieldReq.builder().fieldName("text_sparse")
                                                        .dataType(DataType.SparseFloatVector).build());

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
}
