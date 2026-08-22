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

    public String endpointSuffix() {
        return switch (this) {
            case CHAT_COMPLETIONS -> "/chat/completions";
            case RESPONSES -> "/responses";
            case ANTHROPIC_MESSAGES -> "/messages";
        };
    }

    public String resolveEndpoint(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        String normalized = baseUrl.trim().replaceFirst("/+$", "");
        if (normalized.isBlank()) {
            return null;
        }
        String suffix = endpointSuffix();
        if (normalized.length() >= suffix.length()
                && normalized.regionMatches(true, normalized.length() - suffix.length(),
                suffix, 0, suffix.length())) {
            return normalized;
        }
        return normalized + suffix;
    }
}
