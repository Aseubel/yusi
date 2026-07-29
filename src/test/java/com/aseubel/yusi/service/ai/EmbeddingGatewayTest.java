package com.aseubel.yusi.service.ai;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.embedding.response.EmbeddingResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingGatewayTest {

    @Mock
    private EmbeddingModel embeddingModel;

    @Test
    void embedAll_usesRequestResponseApiAndExposesBatchMetadata() {
        when(embeddingModel.embed(any(EmbeddingRequest.class))).thenReturn(EmbeddingResponse.builder()
                .embeddings(List.of(Embedding.from(new float[] {0.1f}), Embedding.from(new float[] {0.2f})))
                .metadata(EmbeddingResponseMetadata.builder()
                        .modelName("bge-m3")
                        .tokenUsage(new TokenUsage(12))
                        .build())
                .build());

        EmbeddingGateway gateway = new EmbeddingGateway(embeddingModel);
        EmbeddingGateway.EmbeddingBatchResult result = gateway.embedAll(List.of(
                TextSegment.from("第一段"), TextSegment.from("第二段")));

        ArgumentCaptor<EmbeddingRequest> captor = ArgumentCaptor.forClass(EmbeddingRequest.class);
        verify(embeddingModel).embed(captor.capture());
        assertEquals(List.of("第一段", "第二段"), captor.getValue().inputs().stream()
                .map(input -> input.text()).toList());
        assertEquals("bge-m3", result.modelName());
        assertEquals(12, result.inputTokenCount());
        assertEquals(2, result.inputCount());
        assertEquals(1, result.dimension());
    }
}
