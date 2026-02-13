package com.aseubel.yusi.common.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Prompt Key Constants
 * 
 * @author Aseubel
 * @date 2026/02/10
 */
@Getter
@RequiredArgsConstructor
public enum PromptKey {

    /**
     * Chat Assistant System Prompt
     */
    CHAT("chat"),

    /**
     * GraphRAG: Entity Extraction Prompt
     */
    GRAPHRAG_EXTRACT("graphrag-extract"),

    /**
     * GraphRAG: Entity Merge Suggestion Prompt
     */
    GRAPHRAG_MERGE_SUGGEST("graphrag-merge-suggest"),

    /**
     * GraphRAG: QA Prompt (Multi-hop reasoning)
     */
    GRAPHRAG_QA("graphrag-qa");

    private final String key;
}
