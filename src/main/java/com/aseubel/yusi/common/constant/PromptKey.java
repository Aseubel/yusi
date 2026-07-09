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
     * Soul Match Rerank Prompt
     */
    SOUL_MATCH("soul-match"),

    /**
     * Soul Match Recommendation Letter Prompt
     */
    SOUL_MATCH_LETTER("soul-match-letter"),

    /**
     * Emotion Analysis Prompt
     */
    EMOTION_ANALYSIS("emotion-analysis"),

    /**
     * Unified cognition routing prompt
     */
    COGNITION_ROUTING("cognition-routing"),

    /**
     * Soul weekly report generation prompt (F8.3)
     */
    SOUL_WEEKLY_REPORT("soul-weekly-report"),

    /**
     * Cognitive conflict detection prompt (F11.3)
     */
    COGNITIVE_CONFLICT("cognitive-conflict"),

    /**
     * Cross-source memory fusion prompt (F11.4)
     */
    MEMORY_FUSION("memory-fusion"),

    /**
     * Agent persona style prompt (F8.1) — gentle/lively/calm/rational
     */
    AGENT_PERSONA("agent-persona"),

    /**
     * Agent proactive greeting prompt (F8.2)
     */
    AGENT_PROACTIVE_GREETING("agent-proactive-greeting");

    private final String key;
}
