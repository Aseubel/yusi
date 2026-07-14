package com.aseubel.yusi.service.ai;

import com.google.gson.JsonObject;
import io.milvus.v2.service.vector.response.SearchResp;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiaryRetrievalAssemblerTest {

    private final DiaryRetrievalAssembler assembler = new DiaryRetrievalAssembler();

    @Test
    void assemble_groupsOneDiaryAndMergesOnlyAdjacentChunks() {
        List<SearchResp.SearchResult> hits = List.of(
                hit("d-1", 2, 4, "日期：2026-07-13\n\nchunk two"),
                hit("d-2", 0, 1, "日期：2026-07-12\n\nother"),
                hit("d-1", 3, 4, "日期：2026-07-13\n\nchunk three"),
                hit("d-1", 0, 4, "日期：2026-07-13\n\nchunk zero"));

        assertEquals(List.of(
                "日期：2026-07-13\n\nchunk two\n\nchunk three",
                "日期：2026-07-12\n\nother"), assembler.assemble(hits, 5));
    }

    @Test
    void assemble_keepsMalformedMetadataAsStandaloneContext() {
        SearchResp.SearchResult malformed = SearchResp.SearchResult.builder()
                .entity(Map.of("text", "raw text", "metadata", Map.of("diaryId", "d-1")))
                .score(0.9f)
                .build();

        assertEquals(List.of("raw text"), assembler.assemble(List.of(malformed), 5));
    }

    @Test
    void assemble_readsJsonObjectMetadataReturnedByMilvus() {
        JsonObject firstMetadata = metadata("d-1", 0, 2);
        JsonObject secondMetadata = metadata("d-1", 1, 2);
        SearchResp.SearchResult first = SearchResp.SearchResult.builder()
                .entity(Map.of("text", "header\n\nfirst", "metadata", firstMetadata))
                .score(0.9f)
                .build();
        SearchResp.SearchResult second = SearchResp.SearchResult.builder()
                .entity(Map.of("text", "header\n\nsecond", "metadata", secondMetadata))
                .score(0.8f)
                .build();

        assertEquals(List.of("header\n\nfirst\n\nsecond"), assembler.assemble(List.of(first, second), 5));
    }

    private JsonObject metadata(String diaryId, int chunkIndex, int chunkCount) {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("diaryId", diaryId);
        metadata.addProperty("chunkIndex", chunkIndex);
        metadata.addProperty("chunkCount", chunkCount);
        return metadata;
    }

    private SearchResp.SearchResult hit(String diaryId, int chunkIndex, int chunkCount, String text) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("diaryId", diaryId);
        metadata.put("chunkIndex", chunkIndex);
        metadata.put("chunkCount", chunkCount);
        return SearchResp.SearchResult.builder()
                .entity(Map.of("text", text, "metadata", metadata))
                .score(0.9f)
                .build();
    }
}
