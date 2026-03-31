package com.aseubel.yusi.service.ai;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.UserRepository;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.AnnSearchReq;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.request.data.EmbeddedText;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.ranker.RRFRanker;
import io.milvus.v2.service.vector.response.SearchResp;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 日记检索工具 - 使用 LangChain4j @Tool 注解实现 Agentic RAG
 * 
 * 该工具允许 LLM 根据语义查询和时间范围过滤来检索用户的日记内容。
 * 时间范围过滤使用 Milvus 的标量过滤功能（Scalar Filtering），
 * 可以精确地处理"上周"、"过去一个月"等相对时间查询。
 * 
 * 注意：RAG 功能仅对以下用户可用：
 * - 使用 DEFAULT 密钥模式的用户
 * - 使用 CUSTOM 密钥模式且开启云端备份的用户
 * 
 * @author Aseubel
 * @date 2025/12/31
 */
@Slf4j
@Component
public class DiarySearchTool {

    private final MilvusClientV2 milvusClientV2;
    private final EmbeddingModel embeddingModel;
    private final UserRepository userRepository;

    public DiarySearchTool(MilvusClientV2 milvusClientV2,
            EmbeddingModel embeddingModel,
            UserRepository userRepository) {
        this.milvusClientV2 = milvusClientV2;
        this.embeddingModel = embeddingModel;
        this.userRepository = userRepository;
    }

    /**
     * 检查用户是否允许 RAG 功能
     */
    private boolean isRagAllowed(String userId) {
        User user = userRepository.findByUserId(userId);
        if (user == null) {
            return false;
        }

        String keyMode = user.getKeyMode();
        if (keyMode == null || "DEFAULT".equals(keyMode)) {
            return true;
        }

        return Boolean.TRUE.equals(user.getHasCloudBackup());
    }

    /**
     * 搜索用户日记
     * 
     * 当用户询问关于过去经历的问题时，使用此工具检索相关的日记内容。
     * 如果用户提到了特定的时间范围（如"上周"、"去年圣诞节"、"最近三天"），
     * 你需要计算出准确的日期范围并传入 startDate 和 endDate 参数。
     * 
     * @param memoryId  用户ID（由 LangChain4j 自动注入）
     * @param query     语义搜索查询，描述要查找的内容主题（如"工作压力"、"和朋友的聚会"）
     * @param startDate 可选，日期范围的开始日期，格式必须为 YYYY-MM-DD（如 2024-12-01）。
     *                  如果用户没有指定时间范围，则传入 null
     * @param endDate   可选，日期范围的结束日期，格式必须为 YYYY-MM-DD（如 2024-12-31）。
     *                  如果用户没有指定时间范围，则传入 null
     * @return 匹配的日记内容列表，按相关性排序
     */
    @Tool(name = "searchDiary", value = """
            搜索用户的日记历史。当用户询问关于自己过去经历、回忆或特定时间段发生的事情时使用此工具。

            参数说明：
            - query: 要搜索的内容主题，用自然语言描述（必填）
            - startDate: 日期范围开始，格式 YYYY-MM-DD（可选，用户提到时间时需要计算）
            - endDate: 日期范围结束，格式 YYYY-MM-DD（可选，用户提到时间时需要计算）

            时间计算示例（假设今天是 2025-12-31）：
            - "上周" -> startDate: 2025-12-23, endDate: 2025-12-29
            - "这个月" -> startDate: 2025-12-01, endDate: 2025-12-31
            - "去年" -> startDate: 2024-01-01, endDate: 2024-12-31
            - "最近三天" -> startDate: 2025-12-28, endDate: 2025-12-31
            """)
    public List<String> searchDiary(
            @ToolMemoryId String memoryId,
            @P("搜索查询，描述要查找的日记内容主题") String query,
            @P("开始日期，格式 YYYY-MM-DD，不指定时间范围则传 null") String startDate,
            @P("结束日期，格式 YYYY-MM-DD，不指定时间范围则传 null") String endDate) {

        String currentUserId = memoryId;
        if (StrUtil.isEmpty(currentUserId)) {
            log.warn("DiarySearchTool: 未绑定用户ID，拒绝搜索请求");
            return List.of("无法验证用户身份，请重新登录后再试。");
        }

        // 检查权限
        if (!isRagAllowed(currentUserId)) {
            log.info("DiarySearchTool: 用户 {} 使用最高隐私模式，RAG 功能不可用", currentUserId);
            return List.of("您当前使用的是最高隐私模式（自定义密钥且未开启云端备份），日记搜索功能不可用。如需使用此功能，请在设置中开启云端密钥备份。");
        }

        log.info("DiarySearchTool: 用户 {} 发起搜索，query='{}', startDate='{}', endDate='{}'",
                currentUserId, query, startDate, endDate);

        try {
            // 构建过滤条件字符串 (Milvus expr 格式)
            String expr = buildMilvusExpr(currentUserId, startDate, endDate);

            // 生成查询的 Embedding
            Embedding queryEmbedding = embeddingModel.embed(query).content();

            // 1. 构建稠密向量搜索请求
            AnnSearchReq denseReq = AnnSearchReq.builder()
                    .vectorFieldName("vector")
                    .vectors(Collections.singletonList(new FloatVec(queryEmbedding.vector())))
                    .params("{\"metric_type\": \"COSINE\"}")
                    .limit(10) // 增加TopK以供Rerank
                    .filter(expr)
                    .build();

            // 2. 构建稀疏向量搜索请求 (使用Milvus直接文本搜索能力进行BM25检索)
            AnnSearchReq sparseReq = AnnSearchReq.builder()
                    .vectorFieldName("text_sparse")
                    .vectors(Collections.singletonList(new EmbeddedText(query)))
                    .params("{\"metric_type\": \"BM25\"}")
                    .limit(10)
                    .filter(expr)
                    .build();

            // 3. 构建混合搜索请求
            HybridSearchReq hybridSearchReq = HybridSearchReq.builder()
                    .collectionName("yusi_embedding_collection")
                    .searchRequests(Arrays.asList(denseReq, sparseReq))
                    .ranker(RRFRanker.builder().k(60).build()) // RRF重排序，60为常用的平滑参数k
                    .limit(5) // 最终返回Top 5
                    .outFields(Collections.singletonList("text"))
                    .build();

            // 4. 执行混合搜索
            SearchResp searchResp = milvusClientV2.hybridSearch(hybridSearchReq);
            List<List<SearchResp.SearchResult>> searchResults = searchResp.getSearchResults();

            if (searchResults == null || searchResults.isEmpty() || searchResults.get(0).isEmpty()) {
                log.info("DiarySearchTool: 未找到匹配的日记内容");
                if (startDate != null || endDate != null) {
                    return List.of("在指定的时间范围内没有找到相关的日记记录。现在请直接用你的语气回答用户的问题。");
                }
                return List.of("没有找到与该主题相关的日记记录。现在请直接用你的语气回答用户的问题。");
            }

            // 提取并返回结果
            List<String> results = searchResults.get(0).stream()
                    .map(result -> {
                        Map<String, Object> entity = result.getEntity();
                        String content = entity.containsKey("text") ? entity.get("text").toString() : "";
                        double score = result.getScore();
                        log.debug("DiarySearchTool: 匹配结果 score={}, content='{}'", score,
                                content.substring(0, Math.min(50, content.length())));
                        return content;
                    })
                    .collect(Collectors.toList());

            log.info("DiarySearchTool: 找到 {} 条匹配结果", results.size());
            return results;

        } catch (Exception e) {
            log.error("DiarySearchTool: 搜索过程中发生错误", e);
            return List.of("搜索日记时遇到了一些问题，请稍后再试。");
        }
    }

    /**
     * 构建 Milvus 查询表达式 (Expr)
     * 
     * 始终包含 userId 过滤（安全基线），可选添加日期范围过滤
     */
    private String buildMilvusExpr(String userId, String startDate, String endDate) {
        StringBuilder expr = new StringBuilder(String.format("metadata[\"userId\"] == '%s'", userId));

        if (startDate != null && !startDate.isEmpty()) {
            expr.append(String.format(" and metadata[\"entryDate\"] >= '%s'", startDate));
        }

        if (endDate != null && !endDate.isEmpty()) {
            expr.append(String.format(" and metadata[\"entryDate\"] <= '%s'", endDate));
        }

        log.debug("DiarySearchTool: 构建过滤条件 expr={}", expr.toString());
        return expr.toString();
    }
}
