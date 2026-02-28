package com.aseubel.yusi.service.ai;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

import com.aseubel.yusi.repository.MidTermMemoryRepository;
import com.aseubel.yusi.pojo.entity.MidTermMemory;

@Slf4j
@Service
public class MidTermMemorySearchService {

    private final MilvusEmbeddingStore midTermMemoryStore;
    private final EmbeddingModel embeddingModel;
    private final MidTermMemoryRepository midTermMemoryRepository;

    public MidTermMemorySearchService(
            @Qualifier("midTermMemoryStore") MilvusEmbeddingStore midTermMemoryStore,
            EmbeddingModel embeddingModel,
            MidTermMemoryRepository midTermMemoryRepository) {
        this.midTermMemoryStore = midTermMemoryStore;
        this.embeddingModel = embeddingModel;
        this.midTermMemoryRepository = midTermMemoryRepository;
    }

    /**
     * 搜索中期记忆（向量检索）
     *
     * @param userId 用户的 ID
     * @param query  搜索的查询词
     * @param topK   返回结果数量
     * @return 匹配的记忆文本列表
     */
    public List<String> searchMidTermMemory(String userId, String query, int topK) {
        log.info("Searching mid-term memory for user: {}, query: {}", userId, query);

        try {
            Filter filter = metadataKey("userId").isEqualTo(userId);

            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(embeddingModel.embed(query).content())
                    .filter(filter)
                    .maxResults(topK)
                    .minScore(0.5)
                    .build();

            EmbeddingSearchResult<TextSegment> searchResult = midTermMemoryStore.search(searchRequest);
            List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();

            if (matches.isEmpty()) {
                log.info("No matching mid-term memory found.");
                return Collections.emptyList();
            }

            return matches.stream()
                    .map(match -> match.embedded().text())
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
