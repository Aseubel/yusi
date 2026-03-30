package com.aseubel.yusi.service.ai;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.AnnSearchReq;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.request.data.EmbeddedText;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.ranker.RRFRanker;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.aseubel.yusi.repository.MidTermMemoryRepository;
import com.aseubel.yusi.pojo.entity.MidTermMemory;

@Slf4j
@Service
@RequiredArgsConstructor
public class MidTermMemorySearchService {

    private final MilvusClientV2 milvusClientV2;
    private final EmbeddingModel embeddingModel;
    private final MidTermMemoryRepository midTermMemoryRepository;

    /**
     * 搜索中期记忆（向量检索 + 稀疏检索的混合检索）
     *
     * @param userId 用户的 ID
     * @param query  搜索的查询词
     * @param topK   返回结果数量
     * @return 匹配的记忆文本列表
     */
    public List<String> searchMidTermMemory(String userId, String query, int topK) {
        log.info("Searching mid-term memory for user: {}, query: {}", userId, query);

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
                    .collectionName("yusi_mid_term_memory")
                    .searchRequests(Arrays.asList(denseReq, sparseReq))
                    .ranker(RRFRanker.builder().k(60).build()) // RRF重排序，60为常用的平滑参数k
                    .limit(topK) // 最终返回TopK
                    .outFields(Collections.singletonList("text"))
                    .build();

            // 4. 执行混合搜索
            SearchResp searchResp = milvusClientV2.hybridSearch(hybridSearchReq);
            List<List<SearchResp.SearchResult>> searchResults = searchResp.getSearchResults();

            if (searchResults == null || searchResults.isEmpty() || searchResults.get(0).isEmpty()) {
                log.info("No matching mid-term memory found.");
                return Collections.emptyList();
            }

            return searchResults.get(0).stream()
                    .map(result -> {
                        Map<String, Object> entity = result.getEntity();
                        return entity.containsKey("text") ? entity.get("text").toString() : "";
                    })
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error searching mid-term memory for user: {}", userId, e);
            return Collections.emptyList();
        }
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
        try {
            List<MidTermMemory> recentMemories = midTermMemoryRepository.findByUserIdOrderByCreatedAtDesc(
                    userId, org.springframework.data.domain.PageRequest.of(0, limit));

            if (recentMemories.isEmpty()) {
                return "";
            }

            return recentMemories.stream()
                    .map((MidTermMemory mem) -> String.format("- %s (Score: %.2f)", mem.getSummary(),
                            mem.getImportance()))
                    .collect(Collectors.joining("\n"));

        } catch (Exception e) {
            log.error("Error fetching recent mid-term memories for user: {}", userId, e);
            return "";
        }
    }
}
