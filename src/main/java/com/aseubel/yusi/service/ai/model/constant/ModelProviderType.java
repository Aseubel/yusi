package com.aseubel.yusi.service.ai.model.constant;

import java.util.Locale;
import java.util.Set;

/** Model provider aliases and their adapter identifiers. */
public enum ModelProviderType {
    OPENAI_COMPATIBLE("openai-compatible", Set.of(
            "openai", "openai-compatible", "deepseek", "dashscope", "glm", "zhipuai", "bigmodel")),
    ANTHROPIC("anthropic", Set.of("anthropic"));

    private final String canonicalCode;
    private final Set<String> aliases;

    ModelProviderType(String canonicalCode, Set<String> aliases) {
        this.canonicalCode = canonicalCode;
        this.aliases = aliases;
    }

    public String canonicalCode() {
        return canonicalCode;
    }

    public Set<String> aliases() {
        return aliases;
    }

    public static ModelProviderType fromAlias(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (ModelProviderType provider : values()) {
            if (provider.aliases.contains(normalized)) {
                return provider;
            }
        }
        return null;
    }
}
