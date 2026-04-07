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
     * 情景式分析 Prompt
     */
    LOGIC("logic"),

    /**
     * GraphRAG: Entity Extraction Prompt
     */
    GRAPHRAG_EXTRACT("graphrag-extract"),

    /**
     * GraphRAG: Entity Merge Suggestion Prompt
     */
    GRAPHRAG_MERGE_SUGGEST("graphrag-merge-suggest"),

    /**
     * Memory compression extraction Prompt
     */
    MEMORY_EXTRACT("memory-extract"),

    /**
     * Soul Match Prompt
     */
    SOUL_MATCH("soul-match"),

    /**
     * Emotion Analysis Prompt
     */
    EMOTION_ANALYSIS("emotion-analysis");

    private final String key;
}
