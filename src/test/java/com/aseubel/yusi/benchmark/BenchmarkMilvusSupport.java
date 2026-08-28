package com.aseubel.yusi.benchmark;

import com.aseubel.yusi.config.ai.properties.MilvusCollectionProperties;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.vector.request.InsertReq;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * benchmark 专用 Milvus 支撑：重置隔离集合（drop + 以与 MilvusConfig 相同的 schema 重建）并批量入库。
 * schema 与生产初始化保持一致（BM25 函数生成 text_sparse，稠密 HNSW/COSINE，稀疏 SPARSE_INVERTED_INDEX/BM25）。
 */
public final class BenchmarkMilvusSupport {

    /** 同 JVM 内已确认 loaded 的集合（ensureLoaded 的去重缓存；resetCollections 走裸 awaitLoaded）。 */
    private static final java.util.Set<String> ENSURED_LOADED =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    private BenchmarkMilvusSupport() {
    }

    /**
     * 兜底：确保三个业务隔离集合（embedding / mid_term_memory / match_profile）处于 loaded 状态。
     * Spring 的 MilvusConfig 只在"集合不存在"时创建（从不 load），而上次运行残留的
     * benchmark 集合会命中 hasCollection=true 直接跳过，导致后续 search/delete 报
     * "collection not loaded"。每个涉及 Milvus 的 runner 开头调用一次。
     */
    public static void ensureBusinessCollectionsLoaded(MilvusClientV2 client,
            MilvusCollectionProperties properties) {
        for (String name : List.of(properties.getEmbedding(), properties.getMidTermMemory(),
                properties.getMatchProfile())) {
            ensureLoaded(client, name);
        }
    }

    /** 幂等 ensure：同 JVM 已确认过的集合直接跳过。 */
    public static void ensureLoaded(MilvusClientV2 client, String collectionName) {
        if (!ENSURED_LOADED.add(collectionName)) {
            return;
        }
        Boolean loaded = client.getLoadState(
                io.milvus.v2.service.collection.request.GetLoadStateReq.builder()
                        .collectionName(collectionName).build());
        if (Boolean.TRUE.equals(loaded)) {
            return;
        }
        client.loadCollection(io.milvus.v2.service.collection.request.LoadCollectionReq.builder()
                .collectionName(collectionName).build());
        awaitLoaded(client, collectionName);
    }

    private static void awaitLoaded(MilvusClientV2 client, String collectionName) {
        long deadline = System.currentTimeMillis() + 120_000L;
        while (System.currentTimeMillis() < deadline) {
            // 该 SDK 版本 getLoadState 直接返回 Boolean
            Boolean state = client.getLoadState(
                    io.milvus.v2.service.collection.request.GetLoadStateReq.builder()
                            .collectionName(collectionName).build());
            if (Boolean.TRUE.equals(state)) {
                return;
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for collection load", e);
            }
        }
        throw new IllegalStateException("collection not loaded within 120s: " + collectionName);
    }

    /** drop 若存在再重建三个隔离集合，保证每次运行从空集开始；返回实际重建的集合名。 */
    public static List<String> resetCollections(MilvusClientV2 client,
            MilvusCollectionProperties properties, int dimension) {
        List<String> created = new ArrayList<>();
        for (String name : List.of(properties.getEmbedding(), properties.getMidTermMemory(),
                properties.getMatchProfile())) {
            if (Boolean.TRUE.equals(client.hasCollection(
                    HasCollectionReq.builder().collectionName(name).build()))) {
                client.dropCollection(DropCollectionReq.builder().collectionName(name).build());
            }
            createHybridCollection(client, name, dimension);
            created.add(name);
        }
        return created;
    }

    private static void createHybridCollection(MilvusClientV2 client, String collectionName, int dimension) {
        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                .build();
        schema.addField(AddFieldReq.builder().fieldName("id").dataType(DataType.VarChar)
                .maxLength(64).isPrimaryKey(true).autoID(false).build());
        schema.addField(AddFieldReq.builder().fieldName("text").dataType(DataType.VarChar)
                .maxLength(65535).enableAnalyzer(true).build());
        schema.addField(AddFieldReq.builder().fieldName("metadata").dataType(DataType.JSON).build());
        schema.addField(AddFieldReq.builder().fieldName("vector").dataType(DataType.FloatVector)
                .dimension(dimension).build());
        schema.addField(AddFieldReq.builder().fieldName("text_sparse")
                .dataType(DataType.SparseFloatVector).build());
        schema.addFunction(CreateCollectionReq.Function.builder()
                .name("bm25_text_func")
                .functionType(io.milvus.common.clientenum.FunctionType.BM25)
                .inputFieldNames(List.of("text"))
                .outputFieldNames(List.of("text_sparse"))
                .build());
        client.createCollection(CreateCollectionReq.builder()
                .collectionName(collectionName)
                .collectionSchema(schema)
                .build());

        IndexParam vectorIndex = IndexParam.builder()
                .fieldName("vector").indexType(IndexParam.IndexType.HNSW)
                .metricType(IndexParam.MetricType.COSINE).build();
        client.createIndex(CreateIndexReq.builder().collectionName(collectionName)
                .indexParams(List.of(vectorIndex)).build());
        IndexParam sparseIndex = IndexParam.builder()
                .fieldName("text_sparse").indexType(IndexParam.IndexType.SPARSE_INVERTED_INDEX)
                .metricType(IndexParam.MetricType.BM25).build();
        client.createIndex(CreateIndexReq.builder().collectionName(collectionName)
                .indexParams(List.of(sparseIndex)).build());

        // 显式 load 并轮询等待就绪：serverless 的 load 是异步的，立即 delete/query 会报
        // "collection not loaded"。上限 120s，超时抛异常如实失败（不得静默继续）。
        client.loadCollection(io.milvus.v2.service.collection.request.LoadCollectionReq.builder()
                .collectionName(collectionName).build());
        awaitLoaded(client, collectionName);
    }

    /**
     * 批量真实 embedding 后入库。
     *
     * @param docs     docId -> 正文
     * @param metadata docId -> metadata 字段（userId / memoryId 或 diaryId 等）
     * @return 返回 text -> docId 映射（检索结果按文本反查 id 用）
     */
    public static Map<String, String> insertDocs(MilvusClientV2 client, EmbeddingModel embeddingModel,
            String collectionName, Map<String, String> docs, Map<String, Map<String, String>> metadata) {
        List<String> ids = new ArrayList<>(docs.keySet());
        List<String> texts = new ArrayList<>();
        for (String id : ids) {
            texts.add(docs.get(id));
        }
        Embedding[] embeddings = embeddingModel.embedAll(
                texts.stream().map(dev.langchain4j.data.segment.TextSegment::from).toList())
                .content().toArray(new Embedding[0]);

        List<JsonObject> rows = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            JsonObject row = new JsonObject();
            row.addProperty("id", ids.get(i));
            row.addProperty("text", texts.get(i));
            JsonObject meta = new JsonObject();
            metadata.get(ids.get(i)).forEach(meta::addProperty);
            row.add("metadata", meta);
            JsonArray vector = new JsonArray();
            for (float v : embeddings[i].vector()) {
                vector.add(v);
            }
            row.add("vector", vector);
            rows.add(row);
        }
        client.insert(InsertReq.builder().collectionName(collectionName).data(rows).build());

        Map<String, String> textToId = new LinkedHashMap<>();
        docs.forEach((id, text) -> textToId.put(text, id));
        return textToId;
    }
}
