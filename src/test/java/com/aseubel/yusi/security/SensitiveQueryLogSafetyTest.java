package com.aseubel.yusi.security;

import com.aseubel.yusi.grpc.McpGrpcServiceImpl;
import com.aseubel.yusi.grpc.mcp.SearchDiaryRequest;
import com.aseubel.yusi.grpc.mcp.SearchDiaryResponse;
import com.aseubel.yusi.grpc.mcp.SearchMemoryRequest;
import com.aseubel.yusi.grpc.mcp.SearchMemoryResponse;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.ChatMemoryMessageRepository;
import com.aseubel.yusi.repository.DiaryExtensionRepository;
import com.aseubel.yusi.repository.MidTermMemoryRepository;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.service.ai.rag.DiaryRetrievalAssembler;
import com.aseubel.yusi.service.ai.tool.LifeGraphTool;
import com.aseubel.yusi.service.ai.tool.MemorySearchTool;
import com.aseubel.yusi.service.developer.DeveloperConfigService;
import com.aseubel.yusi.service.diary.DiaryService;
import com.aseubel.yusi.service.lifegraph.LifeGraphQueryService;
import com.aseubel.yusi.service.memory.MidTermMemorySearchService;
import com.aseubel.yusi.service.ai.tool.DiarySearchTool;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.grpc.stub.StreamObserver;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.response.SearchResp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.read.ListAppender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SensitiveQueryLogSafetyTest {

    private static final String QUERY_SENTINEL = "fixture-log-sensitive-query-7f3c";
    private static final String USER_ID = "fixture-user-log-safety";

    @Mock
    private MilvusClientV2 milvusClientV2;
    @Mock
    private EmbeddingModel embeddingModel;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DiaryRetrievalAssembler retrievalAssembler;
    @Mock
    private LifeGraphQueryService lifeGraphQueryService;
    @Mock
    private MidTermMemoryRepository midTermMemoryRepository;
    @Mock
    private DiaryService diaryService;
    @Mock
    private DiaryExtensionRepository diaryExtensionRepository;
    @Mock
    private MemorySearchTool memorySearchTool;
    @Mock
    private DeveloperConfigService developerConfigService;
    @Mock
    private ChatMemoryMessageRepository chatMemoryMessageRepository;

    private final List<AttachedLogger> attachedLoggers = new ArrayList<>();

    @AfterEach
    void detachLoggers() {
        attachedLoggers.forEach(attached -> {
            attached.logger().detachAppender(attached.appender());
            attached.appender().stop();
            attached.logger().setLevel(attached.originalLevel());
        });
        attachedLoggers.clear();
    }

    @Test
    void diarySearchKeepsResultAndDoesNotLogQueryOrDates() {
        DiarySearchTool tool = new DiarySearchTool(milvusClientV2, embeddingModel, userRepository,
                retrievalAssembler);
        SearchResp.SearchResult hit = SearchResp.SearchResult.builder()
                .entity(Collections.singletonMap("text", "fixture-diary-result"))
                .score(0.9f)
                .build();
        when(userRepository.findByUserId(USER_ID)).thenReturn(User.builder().keyMode("DEFAULT").build());
        when(embeddingModel.embed(QUERY_SENTINEL)).thenReturn(Response.from(Embedding.from(new float[] { 0.1f })));
        when(milvusClientV2.hybridSearch(any()))
                .thenReturn(SearchResp.builder().searchResults(List.of(List.of(hit))).build());
        when(retrievalAssembler.assemble(List.of(hit), 5)).thenReturn(List.of("fixture-diary-result"));

        ListAppender<ILoggingEvent> appender = attach(DiarySearchTool.class);
        List<String> result = tool.searchDiary(USER_ID, QUERY_SENTINEL, "2026-08-01", "2026-08-19");

        String rendered = rendered(appender);
        assertEquals(List.of("fixture-diary-result"), result);
        assertFalse(rendered.contains(QUERY_SENTINEL));
        assertFalse(rendered.contains("2026-08-01"));
        assertFalse(rendered.contains("2026-08-19"));
        assertFalse(rendered.contains("metadata[\"userId\"]"));
        assertTrue(rendered.contains("queryLengthBucket"));
    }

    @Test
    void diarySearchKeepsFallbackAndDoesNotLogExceptionMessage() {
        DiarySearchTool tool = new DiarySearchTool(milvusClientV2, embeddingModel, userRepository,
                retrievalAssembler);
        when(userRepository.findByUserId(USER_ID)).thenReturn(User.builder().keyMode("DEFAULT").build());
        when(embeddingModel.embed(QUERY_SENTINEL))
                .thenThrow(new IllegalStateException(QUERY_SENTINEL));

        ListAppender<ILoggingEvent> appender = attach(DiarySearchTool.class);
        List<String> result = tool.searchDiary(USER_ID, QUERY_SENTINEL, null, null);

        String rendered = rendered(appender);
        assertEquals(List.of("搜索日记时遇到了一些问题，请稍后再试。"), result);
        assertFalse(rendered.contains(QUERY_SENTINEL));
        assertTrue(rendered.contains("exceptionType") && rendered.contains("IllegalStateException"));
    }

    @Test
    void lifeGraphSearchKeepsResultAndDoesNotLogQuery() {
        LifeGraphTool tool = new LifeGraphTool(lifeGraphQueryService);
        when(lifeGraphQueryService.localSearch(USER_ID, QUERY_SENTINEL, 3, 30, 5))
                .thenReturn("fixture-graph-result");

        ListAppender<ILoggingEvent> appender = attach(LifeGraphTool.class);
        String result = tool.searchLifeGraph(USER_ID, QUERY_SENTINEL);

        String rendered = rendered(appender);
        assertTrue(result.contains("fixture-graph-result"));
        assertFalse(rendered.contains(QUERY_SENTINEL));
        assertTrue(rendered.contains("queryLengthBucket"));
    }

    @Test
    void lifeGraphSearchKeepsFallbackAndDoesNotLogExceptionMessage() {
        LifeGraphTool tool = new LifeGraphTool(lifeGraphQueryService);
        when(lifeGraphQueryService.localSearch(USER_ID, QUERY_SENTINEL, 3, 30, 5))
                .thenThrow(new IllegalStateException(QUERY_SENTINEL));

        ListAppender<ILoggingEvent> appender = attach(LifeGraphTool.class);
        String result = tool.searchLifeGraph(USER_ID, QUERY_SENTINEL);

        String rendered = rendered(appender);
        assertEquals("搜索图谱时发生错误。现在请直接用你的语气回答用户的问题。", result);
        assertFalse(rendered.contains(QUERY_SENTINEL));
        assertTrue(rendered.contains("exceptionType") && rendered.contains("IllegalStateException"));
    }

    @Test
    void midTermSearchKeepsEmptyResultAndDoesNotLogQuery() {
        MidTermMemorySearchService service = new MidTermMemorySearchService(
                milvusClientV2, embeddingModel, midTermMemoryRepository);
        when(embeddingModel.embed(QUERY_SENTINEL)).thenReturn(Response.from(Embedding.from(new float[] { 0.1f })));
        when(milvusClientV2.hybridSearch(any()))
                .thenReturn(SearchResp.builder().searchResults(List.of(List.of())).build());

        ListAppender<ILoggingEvent> appender = attach(MidTermMemorySearchService.class);
        List<String> result = service.searchMidTermMemory(USER_ID, QUERY_SENTINEL, 3);

        String rendered = rendered(appender);
        assertEquals(List.of(), result);
        assertFalse(rendered.contains(QUERY_SENTINEL));
        assertTrue(rendered.contains("queryLengthBucket"));
    }

    @Test
    void midTermSearchKeepsFallbackAndDoesNotLogExceptionMessage() {
        MidTermMemorySearchService service = new MidTermMemorySearchService(
                milvusClientV2, embeddingModel, midTermMemoryRepository);
        when(embeddingModel.embed(QUERY_SENTINEL))
                .thenThrow(new IllegalStateException(QUERY_SENTINEL));

        ListAppender<ILoggingEvent> appender = attach(MidTermMemorySearchService.class);
        List<String> result = service.searchMidTermMemory(USER_ID, QUERY_SENTINEL, 3);

        String rendered = rendered(appender);
        assertEquals(List.of(), result);
        assertFalse(rendered.contains(QUERY_SENTINEL));
        assertTrue(rendered.contains("exceptionType") && rendered.contains("IllegalStateException"));
    }

    @Test
    void recentMidTermMemoriesKeepFallbackAndDoNotLogExceptionMessage() {
        MidTermMemorySearchService service = new MidTermMemorySearchService(
                milvusClientV2, embeddingModel, midTermMemoryRepository);
        when(midTermMemoryRepository.findAvailableByUserId(anyString(), any(Pageable.class)))
                .thenThrow(new IllegalStateException(QUERY_SENTINEL));

        ListAppender<ILoggingEvent> appender = attach(MidTermMemorySearchService.class);
        String result = service.getRecentMemories(USER_ID, 3);

        String rendered = rendered(appender);
        assertEquals("", result);
        assertFalse(rendered.contains(QUERY_SENTINEL));
        assertTrue(rendered.contains("exceptionType") && rendered.contains("IllegalStateException"));
    }

    @Test
    void mcpDiarySearchKeepsResponseCompletionAndDoesNotLogKeywordOrDates() {
        when(developerConfigService.authorize("fixture-api-key", "MEMORY_READ")).thenReturn(USER_ID);
        when(diaryExtensionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        McpGrpcServiceImpl service = new McpGrpcServiceImpl(diaryService, diaryExtensionRepository,
                memorySearchTool, developerConfigService, chatMemoryMessageRepository);
        RecordingObserver<SearchDiaryResponse> observer = new RecordingObserver<>();
        ListAppender<ILoggingEvent> appender = attach(McpGrpcServiceImpl.class);

        service.searchDiary(SearchDiaryRequest.newBuilder()
                .setApiKey("fixture-api-key")
                .setKeyword(QUERY_SENTINEL)
                .setStartTime("2026-08-01 00:00:00")
                .setEndTime("2026-08-19 23:59:59")
                .build(), observer);

        String rendered = rendered(appender);
        assertEquals(1, observer.values().size());
        assertTrue(observer.completed());
        assertEquals(0, observer.values().get(0).getResultsCount());
        assertFalse(rendered.contains(QUERY_SENTINEL));
        assertFalse(rendered.contains("2026-08-01 00:00:00"));
        assertFalse(rendered.contains("2026-08-19 23:59:59"));
        assertTrue(rendered.contains("keywordLengthBucket"));
    }

    @Test
    void mcpDiarySearchKeepsErrorResponseAndDoesNotLogExceptionMessage() {
        when(developerConfigService.authorize("fixture-api-key", "MEMORY_READ")).thenReturn(USER_ID);
        when(diaryExtensionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenThrow(new IllegalStateException(QUERY_SENTINEL));
        McpGrpcServiceImpl service = new McpGrpcServiceImpl(diaryService, diaryExtensionRepository,
                memorySearchTool, developerConfigService, chatMemoryMessageRepository);
        RecordingObserver<SearchDiaryResponse> observer = new RecordingObserver<>();
        ListAppender<ILoggingEvent> appender = attach(McpGrpcServiceImpl.class);

        service.searchDiary(SearchDiaryRequest.newBuilder()
                .setApiKey("fixture-api-key")
                .setKeyword(QUERY_SENTINEL)
                .build(), observer);

        String rendered = rendered(appender);
        assertEquals(1, observer.values().size());
        assertTrue(observer.completed());
        assertEquals(QUERY_SENTINEL, observer.values().get(0).getErrorMessage());
        assertFalse(rendered.contains(QUERY_SENTINEL));
        assertTrue(rendered.contains("exceptionType") && rendered.contains("IllegalStateException"));
    }

    @Test
    void mcpMemorySearchKeepsResultsAndDoesNotLogQuery() {
        when(developerConfigService.authorize("fixture-api-key", "MEMORY_READ")).thenReturn(USER_ID);
        when(memorySearchTool.searchMemories(USER_ID, QUERY_SENTINEL, null, null))
                .thenReturn("fixture-long-term-result");
        when(chatMemoryMessageRepository.findByMemoryIdOrderByCreatedAtDesc(anyString(), any(Pageable.class)))
                .thenReturn(List.of());
        McpGrpcServiceImpl service = new McpGrpcServiceImpl(diaryService, diaryExtensionRepository,
                memorySearchTool, developerConfigService, chatMemoryMessageRepository);
        RecordingObserver<SearchMemoryResponse> observer = new RecordingObserver<>();
        ListAppender<ILoggingEvent> appender = attach(McpGrpcServiceImpl.class);

        service.searchMemory(SearchMemoryRequest.newBuilder()
                .setApiKey("fixture-api-key")
                .setQuery(QUERY_SENTINEL)
                .setMaxResults(3)
                .build(), observer);

        String rendered = rendered(appender);
        assertEquals(1, observer.values().size());
        assertTrue(observer.completed());
        assertEquals(1, observer.values().get(0).getResultsCount());
        assertFalse(rendered.contains(QUERY_SENTINEL));
        assertTrue(rendered.contains("queryLengthBucket"));
    }

    @Test
    void mcpMemorySearchKeepsErrorResponseAndDoesNotLogExceptionMessage() {
        when(developerConfigService.authorize("fixture-api-key", "MEMORY_READ")).thenReturn(USER_ID);
        when(memorySearchTool.searchMemories(USER_ID, QUERY_SENTINEL, null, null))
                .thenThrow(new IllegalStateException(QUERY_SENTINEL));
        McpGrpcServiceImpl service = new McpGrpcServiceImpl(diaryService, diaryExtensionRepository,
                memorySearchTool, developerConfigService, chatMemoryMessageRepository);
        RecordingObserver<SearchMemoryResponse> observer = new RecordingObserver<>();
        ListAppender<ILoggingEvent> appender = attach(McpGrpcServiceImpl.class);

        service.searchMemory(SearchMemoryRequest.newBuilder()
                .setApiKey("fixture-api-key")
                .setQuery(QUERY_SENTINEL)
                .setMaxResults(3)
                .build(), observer);

        String rendered = rendered(appender);
        assertEquals(1, observer.values().size());
        assertTrue(observer.completed());
        assertEquals(QUERY_SENTINEL, observer.values().get(0).getErrorMessage());
        assertFalse(rendered.contains(QUERY_SENTINEL));
        assertTrue(rendered.contains("exceptionType") && rendered.contains("IllegalStateException"));
    }

    private ListAppender<ILoggingEvent> attach(Class<?> type) {
        Logger logger = (Logger) LoggerFactory.getLogger(type);
        Level originalLevel = logger.getLevel();
        logger.setLevel(Level.ALL);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        attachedLoggers.add(new AttachedLogger(logger, appender, originalLevel));
        return appender;
    }

    private String rendered(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream()
                .map(event -> event.getFormattedMessage() + " " + throwableText(event.getThrowableProxy()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String throwableText(IThrowableProxy proxy) {
        if (proxy == null) {
            return "";
        }
        StringBuilder text = new StringBuilder(proxy.getClassName());
        text.append(':').append(proxy.getMessage());
        for (StackTraceElementProxy frame : proxy.getStackTraceElementProxyArray()) {
            text.append(' ').append(frame.getStackTraceElement());
        }
        return text.toString();
    }

    private record AttachedLogger(Logger logger, ListAppender<ILoggingEvent> appender, Level originalLevel) {
    }

    private static final class RecordingObserver<T> implements StreamObserver<T> {
        private final List<T> values = new ArrayList<>();
        private boolean completed;

        @Override
        public void onNext(T value) {
            values.add(value);
        }

        @Override
        public void onError(Throwable throwable) {
            throw new AssertionError("unexpected observer error", throwable);
        }

        @Override
        public void onCompleted() {
            completed = true;
        }

        List<T> values() {
            return values;
        }

        boolean completed() {
            return completed;
        }
    }
}
