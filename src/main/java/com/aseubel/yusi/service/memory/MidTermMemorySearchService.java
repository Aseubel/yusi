package com.aseubel.yusi.service.memory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.aseubel.yusi.common.utils.LowSensitivityLogSummary;
import com.aseubel.yusi.observability.metrics.YusiMetrics;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.AnnSearchReq;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.request.data.EmbeddedText;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.ranker.RRFRanker;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

import com.aseubel.yusi.repository.MidTermMemoryRepository;
import com.aseubel.yusi.pojo.entity.MidTermMemory;

@Slf4j
@Service
public class MidTermMemorySearchService {

    private final MilvusClientV2 milvusClientV2;
    private final EmbeddingModel embeddingModel;
    private final MidTermMemoryRepository midTermMemoryRepository;
    private final YusiMetrics metrics;
    private final com.aseubel.yusi.config.ai.properties.MilvusCollectionProperties collectionProperties;

    public MidTermMemorySearchService(MilvusClientV2 milvusClientV2,
            EmbeddingModel embeddingModel,
            MidTermMemoryRepository midTermMemoryRepository) {
        this(milvusClientV2, embeddingModel, midTermMemoryRepository, null,
                new com.aseubel.yusi.config.ai.properties.MilvusCollectionProperties());
    }

    @Autowired
    public MidTermMemorySearchService(MilvusClientV2 milvusClientV2,
            EmbeddingModel embeddingModel,
            MidTermMemoryRepository midTermMemoryRepository,
            YusiMetrics metrics,
            com.aseubel.yusi.config.ai.properties.MilvusCollectionProperties collectionProperties) {
        this.milvusClientV2 = milvusClientV2;
        this.embeddingModel = embeddingModel;
        this.midTermMemoryRepository = midTermMemoryRepository;
        this.metrics = metrics;
        this.collectionProperties = collectionProperties;
    }

    /**
     * 搜索中期记忆（向量检索 + 稀疏检索的混合检索）
     *
     * @param userId 用户的 ID
     * @param query  搜索的查询词
     * @param topK   返回结果数量
     * @return 匹配的记忆文本列表
     */
    public List<String> searchMidTermMemory(String userId, String query, int topK) {
        log.info("MidTermMemory search started: userId={}, queryLengthBucket={}, topK={}",
                userId, LowSensitivityLogSummary.lengthBucket(query), topK);
        long startedAt = System.nanoTime();

        try {
            String expr = String.format("metadata[\"userId\"] == '%s'", userId);

            // 生成查询的 Embedding
            Embedding queryEmbedding = embeddingModel.embed(query).content();

            // 1. 构建稠密向量搜索请求
            AnnSearchReq denseReq = AnnSearchReq.builder()
                    .vectorFieldName("vector")
                    .vectors(Collections.singletonList(new FloatVec(queryEmbedding.vector())))
                    .params("{\"metric_type\": \"COSINE\"}")
                    .limit(topK * 2) // 增加TopK以供Rerank
                    .filter(expr)
                    .build();

            // 2. 构建稀疏向量搜索请求 (使用Milvus直接文本搜索能力进行BM25检索)
            AnnSearchReq sparseReq = AnnSearchReq.builder()
                    .vectorFieldName("text_sparse")
                    .vectors(Collections.singletonList(new EmbeddedText(query)))
                    .params("{\"metric_type\": \"BM25\"}")
                    .limit(topK * 2)
                    .filter(expr)
                    .build();

            // 3. 构建混合搜索请求
            HybridSearchReq hybridSearchReq = HybridSearchReq.builder()
                    .collectionName(collectionProperties.getMidTermMemory())
                    .searchRequests(Arrays.asList(denseReq, sparseReq))
                    .ranker(RRFRanker.builder().k(60).build()) // RRF重排序，60为常用的平滑参数k
                    .limit(Math.max(topK * 3, topK)) // 过滤隐藏/过期记忆后仍尽量填满结果
                    .outFields(Arrays.asList("text", "metadata"))
                    .build();

            // 4. 执行混合搜索
            SearchResp searchResp = milvusClientV2.hybridSearch(hybridSearchReq);
            List<List<SearchResp.SearchResult>> searchResults = searchResp.getSearchResults();

            if (searchResults == null || searchResults.isEmpty() || searchResults.get(0).isEmpty()) {
                log.info("No matching mid-term memory found.");
                recordSearch("empty", 0, startedAt);
                return Collections.emptyList();
            }

            LocalDateTime now = LocalDateTime.now();
            List<String> results = searchResults.get(0).stream()
                    .filter(result -> isAvailable(result, userId, now))
                    .map(result -> {
                        Map<String, Object> entity = result.getEntity();
                        return entity.containsKey("text") ? entity.get("text").toString() : "";
                    })
                    .filter(text -> !text.isBlank())
                    .limit(topK)
                    .collect(Collectors.toList());
            recordSearch(results.isEmpty() ? "empty" : "success", results.size(), startedAt);
            return results;

        } catch (Exception e) {
            log.error("MidTermMemory search failed: userId={}, operation=search_mid_term_memory, exceptionType={}",
                    userId, LowSensitivityLogSummary.exceptionType(e));
            recordSearch("failure", 0, startedAt);
            return Collections.emptyList();
        }
    }

    private boolean isAvailable(SearchResp.SearchResult result, String userId, LocalDateTime now) {
        String memoryId = extractMemoryId(result);
        if (memoryId == null) {
            return false;
        }
        try {
            Long id = Long.valueOf(memoryId);
            return midTermMemoryRepository.findByIdAndUserId(id, userId)
                    .filter(memory -> !Boolean.TRUE.equals(memory.getHidden()))
                    .filter(memory -> memory.getMergedIntoId() == null)
                    .filter(memory -> memory.getValidUntil() == null || memory.getValidUntil().isAfter(now))
                    .isPresent();
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private String extractMemoryId(SearchResp.SearchResult result) {
        Object metadataValue = result.getEntity().get("metadata");
        if (metadataValue instanceof Map<?, ?> metadata) {
            Object memoryId = metadata.get("memoryId");
            return memoryId == null ? null : memoryId.toString();
        }
        if (metadataValue instanceof JsonObject metadata && metadata.has("memoryId")) {
            JsonElement memoryId = metadata.get("memoryId");
            return memoryId.isJsonPrimitive() ? memoryId.getAsString() : null;
        }
        return null;
    }

    /**
     * 获取用户近期的中期记忆（最新摘要）
     * 按照创建时间降序获取
     *
     * @param userId 用户的 ID
     * @param limit  获取的最大数量
     * @return 格式化后的近期记忆文本
     */
    public String getRecentMemories(String userId, int limit) {
        log.info("Fetching recent mid-term memories for user: {}, limit: {}", userId, limit);
        long startedAt = System.nanoTime();
        try {
            List<MidTermMemory> recentMemories = midTermMemoryRepository.findAvailableByUserId(
                    userId, java.time.LocalDateTime.now(), org.springframework.data.domain.PageRequest.of(0, limit));

            if (recentMemories.isEmpty()) {
                recordRecent("empty", 0, startedAt);
                return "";
            }

            String result = recentMemories.stream()
                    .map((MidTermMemory mem) -> String.format("- %s (Score: %.2f)", mem.getSummary(),
                            mem.getImportance()))
                    .collect(Collectors.joining("\n"));
            recordRecent(result.isBlank() ? "empty" : "success", recentMemories.size(), startedAt);
            return result;

        } catch (Exception e) {
            log.error("MidTermMemory recent fetch failed: userId={}, operation=fetch_recent_mid_term_memory, exceptionType={}",
                    userId, LowSensitivityLogSummary.exceptionType(e));
            recordRecent("failure", 0, startedAt);
            return "";
        }
    }

    private void recordSearch(String result, int resultCount, long startedAt) {
        if (metrics == null) {
            return;
        }
        metrics.recordToolSearch("mid_term_memory", "mid_term_memory_search", result,
                "failure".equals(result) ? "unknown" : "none", elapsedMillis(startedAt), resultCount);
    }

    private void recordRecent(String result, int resultCount, long startedAt) {
        if (metrics == null) {
            return;
        }
        metrics.recordToolSearch("mid_term_memory", "fetch_recent_mid_term_memory", result,
                "failure".equals(result) ? "unknown" : "none", elapsedMillis(startedAt), resultCount);
    }

    private long elapsedMillis(long startedAt) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
