package com.aseubel.yusi.service.ai.model.provider;

import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ChatModelProviderRegistry {

    private static final Map<String, String> PROVIDER_ALIASES = Map.of(
            "openai", "openai-compatible",
            "openai-compatible", "openai-compatible",
            "deepseek", "openai-compatible",
            "dashscope", "openai-compatible",
            "anthropic", "anthropic");

    private final Map<String, ChatModelProviderAdapter> adapters;

    public ChatModelProviderRegistry(List<ChatModelProviderAdapter> adapters) {
        this.adapters = adapters.stream().collect(Collectors.toUnmodifiableMap(
                adapter -> adapter.providerId().toLowerCase(Locale.ROOT),
                Function.identity()));
    }

    public ChatModelProviderAdapter.ProviderClientBundle create(
            ModelRoutingProperties.ModelDefinition definition) {
        String configuredProvider = definition.getProvider() == null
                ? "" : definition.getProvider().trim().toLowerCase(Locale.ROOT);
        String providerId = PROVIDER_ALIASES.get(configuredProvider);
        ChatModelProviderAdapter adapter = providerId == null ? null : adapters.get(providerId);
        if (adapter == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "不支持的 Chat provider: " + definition.getProvider());
        }
        if (!adapter.supports(definition)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Chat provider " + definition.getProvider() + " 不支持协议: " + definition.getProtocol());
        }
        log.debug("Creating Chat model client through provider adapter: provider={}, modelId={}",
                providerId, definition.getId());
        return adapter.create(definition);
    }
}
