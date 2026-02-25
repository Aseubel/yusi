package com.aseubel.yusi.grpc;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.grpc.mcp.DiaryResult;
import com.aseubel.yusi.grpc.mcp.McpExtensionServiceGrpc;
import com.aseubel.yusi.grpc.mcp.QueryLifeGraphRequest;
import com.aseubel.yusi.grpc.mcp.QueryLifeGraphResponse;
import com.aseubel.yusi.grpc.mcp.SearchDiaryRequest;
import com.aseubel.yusi.grpc.mcp.SearchDiaryResponse;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.repository.DeveloperConfigRepository;
import com.aseubel.yusi.repository.DiaryExtensionRepository;
import com.aseubel.yusi.pojo.entity.DeveloperConfig;
import com.aseubel.yusi.service.diary.DiaryService;
import com.aseubel.yusi.service.lifegraph.LifeGraphQueryService;
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
    private final LifeGraphQueryService lifeGraphQueryService;
    private final DeveloperConfigRepository developerConfigRepository;

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
    public void queryLifeGraph(QueryLifeGraphRequest request, StreamObserver<QueryLifeGraphResponse> responseObserver) {
        try {
            String apiKey = request.getApiKey();
            String userId = getUserIdByApiKey(apiKey);
            if (userId == null) {
                throw new IllegalArgumentException("Invalid API Key");
            }

            String query = request.getQuery();
            log.info("MCP Ext: Querying life graph for user {}, query: '{}'", userId, query);

            // Local search pulls context nodes with high score relative to the query
            String graphResult = lifeGraphQueryService.localSearch(userId, query, 5, 50, 5);

            QueryLifeGraphResponse response = QueryLifeGraphResponse.newBuilder()
                    .setResultJson(graphResult != null ? graphResult : "")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("MCP Ext: Error querying life graph", e);
            responseObserver.onNext(QueryLifeGraphResponse.newBuilder()
                    .setErrorMessage(e.getMessage() != null ? e.getMessage() : "Unknown error")
                    .build());
            responseObserver.onCompleted();
        }
    }
}
