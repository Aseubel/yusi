package com.aseubel.yusi.service.ai;

import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.UserRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.response.SearchResp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiarySearchToolTest {

    @Mock
    private MilvusClientV2 milvusClientV2;
    @Mock
    private EmbeddingModel embeddingModel;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DiaryRetrievalAssembler retrievalAssembler;

    @Test
    void searchDiary_requestsMetadataAndReturnsDiaryLevelContexts() {
        DiarySearchTool tool = new DiarySearchTool(milvusClientV2, embeddingModel, userRepository, retrievalAssembler);
        SearchResp.SearchResult hit = SearchResp.SearchResult.builder()
                .entity(Map.of("text", "原始 chunk"))
                .score(0.9f)
                .build();
        SearchResp response = SearchResp.builder().searchResults(List.of(List.of(hit))).build();
        when(userRepository.findByUserId("u-1")).thenReturn(User.builder().keyMode("DEFAULT").build());
        when(embeddingModel.embed("海边")).thenReturn(Response.from(Embedding.from(new float[] { 0.1f })));
        when(milvusClientV2.hybridSearch(any())).thenReturn(response);
        when(retrievalAssembler.assemble(List.of(hit), 5)).thenReturn(List.of("合并后的日记上下文"));

        assertEquals(List.of("合并后的日记上下文"), tool.searchDiary("u-1", "海边", null, null));

        ArgumentCaptor<HybridSearchReq> captor = ArgumentCaptor.forClass(HybridSearchReq.class);
        verify(milvusClientV2).hybridSearch(captor.capture());
        assertEquals(20, captor.getValue().getLimit());
        assertTrue(captor.getValue().getOutFields().containsAll(List.of("text", "metadata")));
        verify(retrievalAssembler).assemble(List.of(hit), 5);
    }
}
