# Diary RAG Retrieval Design

## Goal

Improve diary-memory retrieval without depending on production feedback loops or
introducing model-based reranking. The retrieval context delivered to the Agent
must avoid duplicate overlapping chunks and preserve a coherent diary event or
paragraph where possible.

## Scope

This change applies only to the raw-diary collection
`yusi_embedding_collection`.

`yusi_mid_term_memory` remains one vector per generated memory summary.
`yusi_match_profile` remains one vector per match profile. Neither collection
uses the diary document splitter and neither is changed here.

## Ingestion

Replace the shared fixed-window diary splitting behavior with a diary-specific
splitter.

1. Build a stable context header from available diary fields: entry date,
   title, place name, and emotion.
2. Split the diary body at paragraph boundaries first. A paragraph is kept as
   one chunk whenever it is within the configured maximum size.
3. Split an overlong paragraph recursively at sentence boundaries, using a
   small overlap only for this fallback path.
4. Prepend the stable context header to the stored and embedded chunk text so
   it contributes to both dense retrieval and BM25.
5. Persist the following metadata for every chunk: `userId`, `entryDate`,
   `diaryId`, `chunkIndex`, and `chunkCount`.

Chunk identifiers remain deterministic and scoped to a diary. Re-indexing an
updated diary deletes all of its old chunks before inserting its replacement
chunks.

## Retrieval

Milvus continues to retrieve dense and sparse candidates and combines them
with RRF. This only fuses the same primary key across the two result lists; it
does not deduplicate distinct overlapping chunks from the same diary.

After hybrid search, the application will:

1. Request the diary chunk metadata with each result.
2. Group hits by `diaryId`.
3. Retain the highest-ranked hit as the anchor for each diary.
4. Include directly adjacent hit chunks from the same diary in chunk-index
   order, merging them into one diary context.
5. Return at most five merged diary contexts to the Agent, ordered by the
   anchor rank.

This is deliberately conservative: it does not fetch unhit neighbor chunks,
does not apply recency decay, does not use MMR, and does not call a
cross-encoder or LLM reranker.

## Data Migration

Existing vectors lack the metadata required for correct diary-level grouping.
After deployment, use the existing administrator full-sync endpoint to clear
and rebuild `yusi_embedding_collection` from diary records. The service must
remain correct while the rebuild is in progress: unavailable or incomplete
vector results produce the existing empty-result behavior rather than leaking
unfiltered data.

## Error Handling

Malformed or missing chunk metadata must not merge unrelated chunks. Such a
result is treated as an individual context and remains subject to the existing
user and date filters. A retrieval exception retains the current error path.

## Verification

Add focused tests covering:

- paragraph-first splitting and fallback splitting of an overlong paragraph;
- context headers and persisted chunk metadata;
- grouping chunks from one diary into one returned context;
- ordered merging of adjacent hit chunks;
- no merge across diary IDs or non-adjacent chunk indexes;
- unchanged behavior for empty results and user/date filtering.
