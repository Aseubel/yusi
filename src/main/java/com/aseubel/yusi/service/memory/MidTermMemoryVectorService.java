package com.aseubel.yusi.service.memory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.aseubel.yusi.pojo.entity.MidTermMemory;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** 维护中期记忆在 Milvus 中的可检索副本。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MidTermMemoryVectorService {

    private final com.aseubel.yusi.config.ai.properties.MilvusCollectionProperties collectionProperties;
    private final MilvusClientV2 milvusClientV2;
    private final EmbeddingModel embeddingModel;

    public void upsert(MidTermMemory memory) {
        if (memory == null || memory.getId() == null || memory.getSummary() == null
                || memory.getSummary().isBlank()) {
            return;
        }

        delete(memory.getId());

        Embedding embedding = embeddingModel.embed(memory.getSummary()).content();
        JsonObject metadata = new JsonObject();
        metadata.addProperty("userId", memory.getUserId());
        metadata.addProperty("memoryId", String.valueOf(memory.getId()));
        metadata.addProperty("matchAllowed", Boolean.TRUE.equals(memory.getMatchAllowed()));
        metadata.addProperty("hidden", Boolean.TRUE.equals(memory.getHidden()));

        JsonArray vectorArray = new JsonArray();
        for (float value : embedding.vector()) {
            vectorArray.add(value);
        }

        JsonObject row = new JsonObject();
        row.addProperty("id", UUID.randomUUID().toString());
        row.addProperty("text", memory.getSummary());
        row.add("vector", vectorArray);
        row.add("metadata", metadata);

        milvusClientV2.insert(InsertReq.builder()
                .collectionName(collectionProperties.getMidTermMemory())
                .data(List.of(row))
                .build());
    }

    public void delete(Long memoryId) {
        if (memoryId == null) {
            return;
        }
        milvusClientV2.delete(DeleteReq.builder()
                .collectionName(collectionProperties.getMidTermMemory())
                .filter("metadata[\"memoryId\"] == '" + memoryId + "'")
                .build());
    }
}
