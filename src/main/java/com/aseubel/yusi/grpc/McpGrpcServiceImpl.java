package com.aseubel.yusi.grpc;

import cn.hutool.core.util.StrUtil;
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
import com.aseubel.yusi.repository.DeveloperConfigRepository;
import com.aseubel.yusi.repository.DiaryExtensionRepository;
import com.aseubel.yusi.pojo.entity.DeveloperConfig;
import com.aseubel.yusi.service.diary.DiaryService;
import com.aseubel.yusi.service.ai.MemorySearchTool;
import com.aseubel.yusi.repository.ChatMemoryMessageRepository;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
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
    private final DeveloperConfigRepository developerConfigRepository;
    private final ChatMemoryMessageRepository chatMemoryMessageRepository;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String getUserIdByApiKey(String apiKey) {
        if (StrUtil.isBlank(apiKey)) {
            return null;
        }
        return developerConfigRepository.findByApiKey(apiKey)
                .map(DeveloperConfig::getUserId)
                .orElse(null);
    }

    @Override
    public void searchDiary(SearchDiaryRequest request, StreamObserver<SearchDiaryResponse> responseObserver) {
        try {
            String apiKey = request.getApiKey();
            String userId = getUserIdByApiKey(apiKey);
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

            List<Diary> diaries = diaryExtensionRepository.findAll(spec);
            List<DiaryResult> results = new ArrayList<>();

            for (Diary diary : diaries) {
                // Decrypt into memory to match keyword
                String decryptedContent = diaryService.decryptDiaryContent(diary);

                // If content exists and matches keyword (or no keyword provided)
                if (StrUtil.isNotBlank(decryptedContent) &&
                        (StrUtil.isBlank(keyword) || decryptedContent.contains(keyword))) {

                    results.add(DiaryResult.newBuilder()
                            .setDiaryId(diary.getDiaryId() != null ? diary.getDiaryId() : "")
                            .setDate(diary.getCreateTime() != null ? diary.getCreateTime().format(FORMATTER) : "")
                            .setContent(decryptedContent)
                            .setEmotion(diary.getEmotion() != null ? diary.getEmotion() : "")
                            .build());
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
            String userId = getUserIdByApiKey(apiKey);
            if (userId == null) {
                throw new IllegalArgumentException("Invalid API Key");
            }

            String query = request.getQuery();
            int maxResults = request.getMaxResults() > 0 ? request.getMaxResults() : 10;

            log.info("MCP Ext: Searching memory for user {}, query: '{}', maxResults: {}", userId, query, maxResults);

            List<MemoryResult> results = new ArrayList<>();

            // 1. Long-term Memory Search (Graph + Diary + MidTerm)
            String longTermMemory = memorySearchTool.searchMemories(userId, query, null, null);
            if (StrUtil.isNotBlank(longTermMemory)) {
                results.add(MemoryResult.newBuilder()
                        .setType("LONG_TERM_MEMORY")
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
                    if (!"SYSTEM".equals(msg.getRole())) {
                        results.add(MemoryResult.newBuilder()
                                .setType("SHORT_TERM_CONTEXT")
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
