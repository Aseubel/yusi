package com.aseubel.yusi.service.ai.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * LangChain4j Embedding request/response API adapter.
 * Keeps provider metadata and batch invariants out of business services.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingGateway {

    private final EmbeddingModel embeddingModel;

    public EmbeddingBatchResult embedAll(List<TextSegment> textSegments) {
        if (textSegments == null || textSegments.isEmpty()) {
            throw new IllegalArgumentException("Embedding batch must contain at least one text segment");
        }

        long startedAt = System.nanoTime();
        EmbeddingResponse response = embeddingModel.embed(EmbeddingRequest.builder()
                .textSegments(textSegments)
                .build());
        long latencyMillis = (System.nanoTime() - startedAt) / 1_000_000;

        if (response == null || response.embeddings() == null
                || response.embeddings().size() != textSegments.size()) {
            int actualCount = response == null || response.embeddings() == null
                    ? 0 : response.embeddings().size();
            throw new IllegalStateException("Embedding response count does not match request: expected "
                    + textSegments.size() + ", actual " + actualCount);
        }

        List<Embedding> embeddings = List.copyOf(response.embeddings());
        String modelName = response.modelName() != null ? response.modelName() : embeddingModel.modelName();
        Integer inputTokenCount = response.tokenUsage() == null
                ? null : response.tokenUsage().inputTokenCount();
        int dimension = embeddings.isEmpty() ? 0 : embeddings.get(0).vector().length;

        log.info("embedding.batch model={} inputs={} dimensions={} inputTokens={} latencyMs={}",
                modelName, textSegments.size(), dimension, inputTokenCount, latencyMillis);

        return new EmbeddingBatchResult(embeddings, modelName, inputTokenCount,
                textSegments.size(), dimension, latencyMillis);
    }

    public record EmbeddingBatchResult(
            List<Embedding> embeddings,
            String modelName,
            Integer inputTokenCount,
            int inputCount,
            int dimension,
            long latencyMillis) {
    }
}
