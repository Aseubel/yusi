package com.aseubel.yusi.service.ai.model;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

/**
 * Wire protocol used by a chat model endpoint.
 */
public enum ModelProtocol {
    CHAT_COMPLETIONS,
    RESPONSES,
    ANTHROPIC_MESSAGES;

    @JsonCreator
    public static ModelProtocol fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    public static ModelProtocol normalize(ModelProtocol protocol) {
        return protocol == null ? CHAT_COMPLETIONS : protocol;
    }
}
