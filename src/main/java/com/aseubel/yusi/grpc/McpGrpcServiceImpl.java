package com.aseubel.yusi.grpc;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.common.constant.ChatMessageRole;
import com.aseubel.yusi.common.constant.DeveloperScope;
import com.aseubel.yusi.grpc.constant.McpMemoryResultType;
import com.aseubel.yusi.grpc.mcp.DiaryResult;
import com.aseubel.yusi.grpc.mcp.McpExtensionServiceGrpc;
import com.aseubel.yusi.grpc.mcp.QueryLifeGraphRequest;
import com.aseubel.yusi.grpc.mcp.QueryLifeGraphResponse;
import com.aseubel.yusi.grpc.mcp.SearchDiaryRequest;
import com.aseubel.yusi.grpc.mcp.SearchDiaryResponse;
import com.aseubel.yusi.grpc.mcp.SearchMemoryRequest;
import com.aseubel.yusi.grpc.mcp.SearchMemoryResponse;
import com.aseubel.yusi.grpc.mcp.MemoryResult;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.repository.DiaryExtensionRepository;
import com.aseubel.yusi.service.developer.DeveloperConfigService;
import com.aseubel.yusi.service.diary.DiaryService;
import com.aseubel.yusi.service.ai.tool.MemorySearchTool;
import com.aseubel.yusi.repository.ChatMemoryMessageRepository;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Exposing core functions via gRPC to the external MCP Server.
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class McpGrpcServiceImpl extends McpExtensionServiceGrpc.McpExtensionServiceImplBase {

    private final DiaryService diaryService;
    private final DiaryExtensionRepository diaryExtensionRepository;
    private final MemorySearchTool memorySearchTool;
    private final DeveloperConfigService developerConfigService;
    private final ChatMemoryMessageRepository chatMemoryMessageRepository;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DIARY_PAGE_SIZE = 100;
    private static final int MAX_DIARY_SCAN = 1000;
    private static final int MAX_DIARY_RESULTS = 100;
    private static final int MAX_MEMORY_RESULTS = 50;

    @Override
    public void searchDiary(SearchDiaryRequest request, StreamObserver<SearchDiaryResponse> responseObserver) {
        try {
            String apiKey = request.getApiKey();
            String userId = developerConfigService.authorize(apiKey, DeveloperScope.MEMORY_READ.code());
            if (userId == null) {
                throw new IllegalArgumentException("Invalid API Key");
            }

            String keyword = request.getKeyword();
            String startTimeStr = request.getStartTime();
            String endTimeStr = request.getEndTime();

            log.info("MCP Ext: Searching diary for user {}, keyword: '{}', time: {} - {}",
                    userId, keyword, startTimeStr, endTimeStr);

            Specification<Diary> spec = (root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();
                predicates.add(cb.equal(root.get("userId"), userId));

                if (StrUtil.isNotBlank(startTimeStr)) {
                    LocalDateTime start = LocalDateTime.parse(startTimeStr, FORMATTER);
                    predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), start));
                }
                if (StrUtil.isNotBlank(endTimeStr)) {
                    LocalDateTime end = LocalDateTime.parse(endTimeStr, FORMATTER);
                    predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), end));
                }
                return cb.and(predicates.toArray(new Predicate[0]));
            };

            List<DiaryResult> results = new ArrayList<>();
            int scanned = 0;
            for (int page = 0; scanned < MAX_DIARY_SCAN && results.size() < MAX_DIARY_RESULTS; page++) {
                Page<Diary> diaryPage = diaryExtensionRepository.findAll(spec,
                        PageRequest.of(page, DIARY_PAGE_SIZE,
                                Sort.by(Sort.Direction.DESC, "createTime")));
                for (Diary diary : diaryPage.getContent()) {
                    scanned++;
                    // Decrypt only the bounded page in memory to match the keyword.
                    String decryptedContent = diaryService.decryptDiaryContent(diary);

                    if (StrUtil.isNotBlank(decryptedContent)
                            && (StrUtil.isBlank(keyword) || decryptedContent.contains(keyword))) {
                        results.add(DiaryResult.newBuilder()
                                .setDiaryId(diary.getDiaryId() != null ? diary.getDiaryId() : "")
                                .setDate(diary.getCreateTime() != null ? diary.getCreateTime().format(FORMATTER) : "")
                                .setContent(decryptedContent)
                                .setEmotion(diary.getEmotion() != null ? diary.getEmotion() : "")
                                .build());
                    }

                    if (results.size() >= MAX_DIARY_RESULTS || scanned >= MAX_DIARY_SCAN) {
                        break;
                    }
                }

                if (!diaryPage.hasNext()) {
                    break;
                }
            }

            SearchDiaryResponse response = SearchDiaryResponse.newBuilder()
                    .addAllResults(results)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("MCP Ext: Error searching diary", e);
            responseObserver.onNext(SearchDiaryResponse.newBuilder()
                    .setErrorMessage(e.getMessage() != null ? e.getMessage() : "Unknown error")
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void searchMemory(SearchMemoryRequest request, StreamObserver<SearchMemoryResponse> responseObserver) {
        try {
            String apiKey = request.getApiKey();
            String userId = developerConfigService.authorize(apiKey, DeveloperScope.MEMORY_READ.code());
            if (userId == null) {
                throw new IllegalArgumentException("Invalid API Key");
            }

            String query = request.getQuery();
            int requestedResults = request.getMaxResults() > 0 ? request.getMaxResults() : 10;
            int maxResults = Math.min(Math.max(requestedResults, 1), MAX_MEMORY_RESULTS);

            log.info("MCP Ext: Searching memory for user {}, query: '{}', maxResults: {}", userId, query, maxResults);

            List<MemoryResult> results = new ArrayList<>();

            // 1. Long-term Memory Search (Graph + Diary + MidTerm)
            String longTermMemory = memorySearchTool.searchMemories(userId, query, null, null);
            if (StrUtil.isNotBlank(longTermMemory)) {
                results.add(MemoryResult.newBuilder()
                        .setType(McpMemoryResultType.LONG_TERM_MEMORY.code())
                        .setContent(longTermMemory)
                        .setScore(1.0)
                        .build());
            }

            // 2. ShortTerm Context (Recent Messages)
            int shortTermCount = maxResults - results.size();
            // If longTermMemory takes 1 slot, we have maxResults - 1 slots left for shortTermCount
            // Assuming maxResults is reasonable (e.g. 10), we will fetch some recent messages.
            
            if (shortTermCount > 0) {
                List<com.aseubel.yusi.pojo.entity.ChatMemoryMessage> recentMessages =
                        chatMemoryMessageRepository.findByMemoryIdOrderByCreatedAtDesc(userId,
                                org.springframework.data.domain.PageRequest.of(0, shortTermCount));

                // Reverse order so latest messages are at the end
                java.util.Collections.reverse(recentMessages);

                for (int i = 0; i < recentMessages.size(); i++) {
                    com.aseubel.yusi.pojo.entity.ChatMemoryMessage msg = recentMessages.get(i);
                    if (!ChatMessageRole.SYSTEM.code().equals(msg.getRole())) {
                        results.add(MemoryResult.newBuilder()
                                .setType(McpMemoryResultType.SHORT_TERM_CONTEXT.code())
                                .setContent(msg.getRole() + ": " + msg.getContent())
                                .setSourceId(msg.getId() != null ? String.valueOf(msg.getId()) : "")
                                .setScore(0.8 - i * 0.03)
                                .setCreatedAt(msg.getCreatedAt() != null ? msg.getCreatedAt().format(FORMATTER) : "")
                                .build());
                    }
                }
            }

            SearchMemoryResponse response = SearchMemoryResponse.newBuilder()
                    .addAllResults(results)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("MCP Ext: Error searching memory", e);
            responseObserver.onNext(SearchMemoryResponse.newBuilder()
                    .setErrorMessage(e.getMessage() != null ? e.getMessage() : "Unknown error")
                    .build());
            responseObserver.onCompleted();
        }
    }
}
