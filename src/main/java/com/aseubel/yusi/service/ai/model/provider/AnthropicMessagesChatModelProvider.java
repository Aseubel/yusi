package com.aseubel.yusi.service.ai.model.provider;

import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.service.ai.model.ModelInvocationErrorClassifier;
import com.aseubel.yusi.service.ai.model.ModelInvocationException;
import com.aseubel.yusi.service.ai.model.ModelProtocol;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;

@Component
public class AnthropicMessagesChatModelProvider implements ChatModelProviderAdapter {

    private static final Set<String> SUPPORTED_ALIASES = Set.of("anthropic");

    @Override
    public String providerId() {
        return "anthropic";
    }

    @Override
    public boolean supports(ModelRoutingProperties.ModelDefinition definition) {
        String provider = definition.getProvider() == null
                ? "" : definition.getProvider().trim().toLowerCase(Locale.ROOT);
        return SUPPORTED_ALIASES.contains(provider)
                && ModelProtocol.normalize(definition.getProtocol()) == ModelProtocol.ANTHROPIC_MESSAGES;
    }

    @Override
    public ProviderClientBundle create(ModelRoutingProperties.ModelDefinition definition) {
        if (!supports(definition)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Anthropic provider 必须使用 ANTHROPIC_MESSAGES 协议");
        }
        Duration timeout = Duration.ofSeconds(
                definition.getTimeoutSeconds() == null ? 60 : definition.getTimeoutSeconds());
        AnthropicChatModel chatModel = AnthropicChatModel.builder()
                .baseUrl(definition.getBaseurl())
                .apiKey(definition.getApikey())
                .modelName(definition.getModel())
                .timeout(timeout)
                .build();
        AnthropicStreamingChatModel streamingChatModel = AnthropicStreamingChatModel.builder()
                .baseUrl(definition.getBaseurl())
                .apiKey(definition.getApikey())
                .modelName(definition.getModel())
                .timeout(timeout)
                .build();
        return new ProviderClientBundle(providerId(), chatModel, streamingChatModel);
    }

    @Override
    public ModelInvocationException normalize(Throwable error, String modelId) {
        return ModelInvocationErrorClassifier.classify(error, providerId(), modelId);
    }
}
