package com.aseubel.yusi.service.ai.prompt;

/**
 * Immutable prompt identity and content selected for one model call.
 *
 * <p>The template is request-scoped data. Trace events must copy only the
 * identity fields and never persist this record as a whole.</p>
 */
public record PromptSnapshot(
        String key,
        String version,
        String locale,
        String template) {
}
