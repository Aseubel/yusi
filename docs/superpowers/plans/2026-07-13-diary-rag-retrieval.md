# Diary RAG Retrieval Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Return coherent, deduplicated diary contexts from RRF retrieval while indexing diary content with paragraph-first chunks and stable diary context.

**Architecture:** Add a small diary-only chunking component that owns paragraph handling, context construction, and deterministic chunk metadata. Keep Milvus responsible for dense/BM25 candidate recall; add a result assembler that converts the larger candidate set into at most five diary-level contexts by grouping on `diaryId` and merging only adjacent hit chunks.

**Tech Stack:** Java 21, Spring Boot, LangChain4j `DocumentSplitter`, Milvus Java SDK v2, JUnit 5, Mockito.

---

## File Structure

- Create: `src/main/java/com/aseubel/yusi/service/ai/DiaryChunker.java`
  - Builds a diary context header, splits text on paragraphs, and uses the existing recursive `DocumentSplitter` only for overlong paragraphs.
- Create: `src/main/java/com/aseubel/yusi/service/ai/DiaryRetrievalAssembler.java`
  - Parses Milvus hits, groups them by diary, and returns ordered merged contexts.
- Modify: `src/main/java/com/aseubel/yusi/service/ai/EmbeddingBatchService.java`
  - Replaces direct generic splitting with `DiaryChunker`; writes diary/chunk metadata.
- Modify: `src/main/java/com/aseubel/yusi/service/ai/DiarySearchTool.java`
  - Requests enough Milvus candidates and delegates final diary-level aggregation.
- Test: `src/test/java/com/aseubel/yusi/service/ai/DiaryChunkerTest.java`
  - Covers paragraph preservation, fallback splitting, context headers, and chunk metadata.
- Test: `src/test/java/com/aseubel/yusi/service/ai/DiaryRetrievalAssemblerTest.java`
  - Covers deduplication, adjacent merging, ordering, and malformed metadata.
- Test: `src/test/java/com/aseubel/yusi/service/ai/EmbeddingBatchServiceTest.java`
  - Captures the Milvus insert request and verifies persisted diary metadata and contextual text.
- Test: `src/test/java/com/aseubel/yusi/service/ai/DiarySearchToolTest.java`
  - Verifies request fields/candidate limit and the final aggregated returned contexts.

### Task 1: Add the Diary Chunking Boundary

**Files:**
- Create: `src/main/java/com/aseubel/yusi/service/ai/DiaryChunker.java`
- Test: `src/test/java/com/aseubel/yusi/service/ai/DiaryChunkerTest.java`

- [x] **Step 1: Write failing unit tests for paragraph-first chunking**

```java
@Test
void split_keepsShortParagraphsIntactAndAddsDiaryContext() {
    Diary diary = Diary.builder().diaryId("d-1").userId("u-1")
            .entryDate(LocalDate.of(2026, 7, 13)).title("海边散步")
            .placeName("青岛").emotion("平静").build();

    List<DiaryChunker.DiaryChunk> chunks = chunker.split(diary, "第一段。\n\n第二段。");

    assertThat(chunks).extracting(DiaryChunker.DiaryChunk::index)
            .containsExactly(0, 1);
    assertThat(chunks.get(0).text()).contains("日期：2026-07-13", "标题：海边散步", "第一段。");
    assertThat(chunks.get(1).text()).contains("地点：青岛", "情绪：平静", "第二段。");
}

@Test
void split_usesFallbackSplitterOnlyForOverlongParagraph() {
    DocumentSplitter fallback = mock(DocumentSplitter.class);
    when(fallback.split(any(Document.class))).thenReturn(List.of(
            TextSegment.from("长段前半"), TextSegment.from("长段后半")));
    DiaryChunker chunker = new DiaryChunker(fallback, text -> text.length() > 20);

    List<DiaryChunker.DiaryChunk> chunks = chunker.split(diary(), "短段。\n\n这是一个超过阈值的长段落。");

    assertThat(chunks).extracting(DiaryChunker.DiaryChunk::body)
            .containsExactly("短段。", "长段前半", "长段后半");
    verify(fallback).split(any(Document.class));
}
```

- [x] **Step 2: Run the focused test and verify it fails**

Run: `./mvnw -Dtest=DiaryChunkerTest test`

Expected: compilation failure because `DiaryChunker` does not exist.

- [x] **Step 3: Implement `DiaryChunker`**

```java
@Component
public class DiaryChunker {
    private final DocumentSplitter fallbackSplitter;

    public List<DiaryChunk> split(Diary diary, String plainContent) {
        String header = buildHeader(diary);
        List<String> bodies = splitParagraphsThenFallback(plainContent);
        return IntStream.range(0, bodies.size())
                .mapToObj(index -> new DiaryChunk(index, bodies.size(), header, bodies.get(index)))
                .toList();
    }

    public record DiaryChunk(int index, int count, String header, String body) {
        public String text() {
            return header + "\n\n" + body;
        }
    }
}
```

`splitParagraphsThenFallback` must split on blank-line runs, retain every nonblank paragraph unchanged when the existing recursive splitter returns one segment, and flatten fallback segments in source order. `buildHeader` must include only nonblank fields in this fixed order: date, title, place, emotion. Use the existing `documentSplitter` bean as `fallbackSplitter`; do not change the splitting behavior of any non-diary flow.

- [x] **Step 4: Run the focused test and verify it passes**

Run: `./mvnw -Dtest=DiaryChunkerTest test`

Expected: `Tests run: 2`, `Failures: 0`.

### Task 2: Persist Diary-Level Chunk Metadata

**Files:**
- Modify: `src/main/java/com/aseubel/yusi/service/ai/EmbeddingBatchService.java:118-220`
- Test: `src/test/java/com/aseubel/yusi/service/ai/EmbeddingBatchServiceTest.java`

- [x] **Step 1: Write the failing insert-payload test**

```java
@Test
void processPendingTasks_writesDiaryChunkMetadataAndContextualText() {
    stubOnePendingUpsert("task-1", "diary-1", "user-1");
    when(diaryRepository.findByDiaryId("diary-1")).thenReturn(diaryWithParagraphs());
    when(embeddingModel.embedAll(anyList())).thenReturn(embeddingResponseFor(2));

    service.processPendingTasks();

    ArgumentCaptor<InsertReq> captor = ArgumentCaptor.forClass(InsertReq.class);
    verify(milvusClientV2).insert(captor.capture());
    List<JsonObject> rows = captor.getValue().getData();
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).getAsJsonObject("metadata").get("diaryId").getAsString()).isEqualTo("diary-1");
    assertThat(rows.get(0).getAsJsonObject("metadata").get("chunkIndex").getAsInt()).isEqualTo(0);
    assertThat(rows.get(1).getAsJsonObject("metadata").get("chunkCount").getAsInt()).isEqualTo(2);
    assertThat(rows.get(0).get("text").getAsString()).contains("日期：2026-07-13", "标题：测试日记");
}
```

- [x] **Step 2: Run the focused test and verify it fails**

Run: `./mvnw -Dtest=EmbeddingBatchServiceTest test`

Expected: compilation failure or assertion failure because `diaryId`, `chunkIndex`, and `chunkCount` are absent.

- [x] **Step 3: Replace generic segment preparation with `DiaryChunker` output**

In `processUpsertBatch`, inject `DiaryChunker`, call `diaryChunker.split(diary, text)`, and create embeddings from `chunk.text()`. Preserve the current delete-before-insert, batch embedding call, retry handling, and deterministic primary key format. Write these values into the JSON `metadata` object for each row:

```java
metadata.addProperty("userId", diary.getUserId());
metadata.addProperty("entryDate", diary.getEntryDate().toString());
metadata.addProperty("diaryId", diary.getDiaryId());
metadata.addProperty("chunkIndex", chunk.index());
metadata.addProperty("chunkCount", chunk.count());
```

Only write `entryDate` when it is non-null, matching the current filtering contract.

- [x] **Step 4: Run the focused test and verify it passes**

Run: `./mvnw -Dtest=EmbeddingBatchServiceTest test`

Expected: `Tests run: 1`, `Failures: 0`.

### Task 3: Assemble Milvus Hits into Diary Contexts

**Files:**
- Create: `src/main/java/com/aseubel/yusi/service/ai/DiaryRetrievalAssembler.java`
- Test: `src/test/java/com/aseubel/yusi/service/ai/DiaryRetrievalAssemblerTest.java`

- [x] **Step 1: Write failing aggregation tests**

```java
@Test
void assemble_groupsOneDiaryAndMergesOnlyAdjacentChunks() {
    List<SearchResult> hits = List.of(
            hit("d-1", 2, 4, 0.91f, "header\n\nchunk two"),
            hit("d-2", 0, 1, 0.89f, "other header\n\nother"),
            hit("d-1", 3, 4, 0.80f, "header\n\nchunk three"),
            hit("d-1", 0, 4, 0.70f, "header\n\nchunk zero"));

    assertThat(assembler.assemble(hits, 5)).containsExactly(
            "header\n\nchunk two\n\nchunk three",
            "other header\n\nother");
}

@Test
void assemble_keepsMalformedMetadataAsStandaloneContext() {
    assertThat(assembler.assemble(List.of(hitWithoutChunkMetadata("raw text")), 5))
            .containsExactly("raw text");
}
```

- [x] **Step 2: Run the focused test and verify it fails**

Run: `./mvnw -Dtest=DiaryRetrievalAssemblerTest test`

Expected: compilation failure because `DiaryRetrievalAssembler` does not exist.

- [x] **Step 3: Implement deterministic diary-level aggregation**

```java
public List<String> assemble(List<SearchResp.SearchResult> hits, int resultLimit) {
    LinkedHashMap<String, List<DiaryHit>> hitsByDiary = parseHitsInRankOrder(hits);
    List<String> contexts = new ArrayList<>();
    for (List<DiaryHit> diaryHits : hitsByDiary.values()) {
        DiaryHit anchor = diaryHits.getFirst();
        List<DiaryHit> adjacent = diaryHits.stream()
                .filter(hit -> Math.abs(hit.chunkIndex() - anchor.chunkIndex()) <= 1)
                .sorted(comparingInt(DiaryHit::chunkIndex))
                .toList();
        contexts.add(mergeHeaderOnce(adjacent));
        if (contexts.size() == resultLimit) break;
    }
    return contexts;
}
```

Parse `metadata` defensively as a `Map<String, Object>`. For valid metadata, a diary's first hit in Milvus rank order is its anchor. Include only that anchor and distinct hits at `anchorIndex - 1` or `anchorIndex + 1`; discard non-adjacent hits from the same diary. `mergeHeaderOnce` must retain the first chunk's header and append only the body of subsequent chunks, preventing repeated headers. When metadata is missing, malformed, or has a negative chunk index, return its `text` unchanged as a standalone context; never merge it with another result.

- [x] **Step 4: Run the focused test and verify it passes**

Run: `./mvnw -Dtest=DiaryRetrievalAssemblerTest test`

Expected: `Tests run: 2`, `Failures: 0`.

### Task 4: Wire Retrieval to Use the Assembler

**Files:**
- Modify: `src/main/java/com/aseubel/yusi/service/ai/DiarySearchTool.java:45-190`
- Test: `src/test/java/com/aseubel/yusi/service/ai/DiarySearchToolTest.java`

- [x] **Step 1: Write failing tool-level tests**

```java
@Test
void searchDiary_requestsMetadataAndReturnsDiaryLevelContexts() {
    when(userRepository.findByUserId("u-1")).thenReturn(ragAllowedUser());
    when(embeddingModel.embed("海边")).thenReturn(queryEmbeddingResponse());
    when(milvusClientV2.hybridSearch(any(HybridSearchReq.class)))
            .thenReturn(searchResponse(hit("d-1", 1, 3, 0.9f, "header\n\nfirst"),
                    hit("d-1", 2, 3, 0.8f, "header\n\nsecond")));

    assertThat(tool.searchDiary("u-1", "海边", null, null))
            .containsExactly("header\n\nfirst\n\nsecond");

    ArgumentCaptor<HybridSearchReq> captor = ArgumentCaptor.forClass(HybridSearchReq.class);
    verify(milvusClientV2).hybridSearch(captor.capture());
    assertThat(captor.getValue().getOutFields()).contains("text", "metadata");
    assertThat(captor.getValue().getLimit()).isEqualTo(20);
}
```

- [x] **Step 2: Run the focused test and verify it fails**

Run: `./mvnw -Dtest=DiarySearchToolTest test`

Expected: assertion failure because the current request asks only for `text`, limits hybrid results to five, and returns raw chunks.

- [x] **Step 3: Delegate final results to `DiaryRetrievalAssembler`**

Inject `DiaryRetrievalAssembler` into `DiarySearchTool`. Retain the existing user/date filter and RRF configuration. Change both dense and sparse candidate limits and the hybrid limit to `20`, request `List.of("text", "metadata")`, then replace direct stream-to-text mapping with:

```java
List<String> results = retrievalAssembler.assemble(searchResults.getFirst(), 5);
```

Keep the existing no-results and exception responses. If all candidate metadata is malformed, `DiaryRetrievalAssembler` returns the raw texts as standalone contexts, so retrieval still degrades safely.

- [x] **Step 4: Run the focused test and verify it passes**

Run: `./mvnw -Dtest=DiarySearchToolTest test`

Expected: `Tests run: 1`, `Failures: 0`.

### Task 5: Run Regression Verification and Rebuild Operationally

**Files:**
- Modify: `docs/superpowers/specs/2026-07-13-diary-rag-retrieval-design.md`
  - Record the completed migration procedure only after implementation and test results are known.

- [x] **Step 1: Run all focused tests together**

Run: `./mvnw -Dtest=DiaryChunkerTest,EmbeddingBatchServiceTest,DiaryRetrievalAssemblerTest,DiarySearchToolTest test`

Expected: all four test classes pass.

- [x] **Step 2: Run the repository-required Maven test suite**

Run: `./mvnw test`

Expected: Maven exits with code `0`.

- [ ] **Step 3: Rebuild the deployed diary index after approval**

Call the existing super-admin endpoint `POST /admin/embeddings/full-sync`, implemented in `AdminController.fullSyncEmbeddings()` and `EmbeddingBatchService.fullSync()`. Verify logs report the collection clear, task reset, and subsequent embedding-task completion before treating diary-level grouping as available for all historical data.

- [ ] **Step 4: Obtain explicit user approval before any commit**

Do not run `git commit` or `git push` in this task. Present the test/build output and changed files to the user; commit only after the user explicitly requests it, per `.agents/AGENTS.md`.
