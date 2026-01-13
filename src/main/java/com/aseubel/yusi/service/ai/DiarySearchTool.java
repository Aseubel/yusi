package com.aseubel.yusi.service.ai;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.UserRepository;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

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
@RequiredArgsConstructor
public class DiarySearchTool {

    private final MilvusEmbeddingStore milvusEmbeddingStore;
    private final EmbeddingModel embeddingModel;
    private final UserRepository userRepository;

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
            @P("搜索查询，描述要查找的日记内容主题") String query,
            @P("开始日期，格式 YYYY-MM-DD，不指定时间范围则传 null") String startDate,
            @P("结束日期，格式 YYYY-MM-DD，不指定时间范围则传 null") String endDate) {

        // 安全检查：必须从 UserContext 获取当前用户 ID，防止 AI 幻觉访问其他用户数据
        String currentUserId = UserContext.getUserId();
        if (StrUtil.isEmpty(currentUserId)) {
            log.warn("DiarySearchTool: 无法获取当前用户ID，拒绝搜索请求");
            return List.of("无法验证用户身份，请重新登录后再试。");
        }

        // 隐私检查：CUSTOM 模式且未开启云端备份的用户不允许 RAG
        if (!isRagAllowed(currentUserId)) {
            log.info("DiarySearchTool: 用户 {} 使用最高隐私模式，RAG 功能不可用", currentUserId);
            return List.of("您当前使用的是最高隐私模式（自定义密钥且未开启云端备份），日记搜索功能不可用。如需使用此功能，请在设置中开启云端密钥备份。");
        }

        log.info("DiarySearchTool: 用户 {} 发起搜索，query='{}', startDate='{}', endDate='{}'",
                currentUserId, query, startDate, endDate);

        try {
            // 构建过滤条件
            Filter filter = buildFilter(currentUserId, startDate, endDate);

            // 生成查询的 Embedding
            Embedding queryEmbedding = embeddingModel.embed(query).content();

            // 构建搜索请求
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .filter(filter)
                    .maxResults(5)
                    .minScore(0.6) // 稍微降低阈值以获取更多相关结果
                    .build();

            // 执行搜索
            EmbeddingSearchResult<TextSegment> searchResult = milvusEmbeddingStore.search(searchRequest);

            // 提取并返回结果
            List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();

            if (matches.isEmpty()) {
                log.info("DiarySearchTool: 未找到匹配的日记内容");
                if (startDate != null || endDate != null) {
                    return List.of("在指定的时间范围内没有找到相关的日记记录。");
                }
                return List.of("没有找到与该主题相关的日记记录。");
            }

            List<String> results = matches.stream()
                    .map(match -> {
                        TextSegment segment = match.embedded();
                        String content = segment.text();
                        double score = match.score();
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
     * 构建 Milvus 过滤条件
     * 
     * 始终包含 userId 过滤（安全基线），可选添加日期范围过滤
     */
    private Filter buildFilter(String userId, String startDate, String endDate) {
        // 安全基线：必须过滤 userId
        Filter filter = metadataKey("userId").isEqualTo(userId);

        // 添加日期范围过滤（如果指定）
        if (startDate != null && !startDate.isEmpty()) {
            filter = Filter.and(filter, metadataKey("entryDate").isGreaterThanOrEqualTo(startDate));
        }

        if (endDate != null && !endDate.isEmpty()) {
            filter = Filter.and(filter, metadataKey("entryDate").isLessThanOrEqualTo(endDate));
        }

        log.debug("DiarySearchTool: 构建过滤条件 userId={}, startDate={}, endDate={}",
                userId, startDate, endDate);

        return filter;
    }
}
