package com.aseubel.yusi.service.ai.model.provider;

import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.service.ai.model.ModelInvocationErrorClassifier;
import com.aseubel.yusi.service.ai.model.ModelInvocationException;
import com.aseubel.yusi.service.ai.model.ModelProtocol;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiResponsesChatModel;
import dev.langchain4j.model.openai.OpenAiResponsesStreamingChatModel;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;

@Component
public class OpenAiCompatibleChatModelProvider implements ChatModelProviderAdapter {

    private static final Set<String> SUPPORTED_ALIASES = Set.of(
            "openai", "openai-compatible", "deepseek", "dashscope");

    @Override
    public String providerId() {
        return "openai-compatible";
    }

    @Override
    public boolean supports(ModelRoutingProperties.ModelDefinition definition) {
        String provider = definition.getProvider() == null
                ? "" : definition.getProvider().trim().toLowerCase(Locale.ROOT);
        ModelProtocol protocol = ModelProtocol.normalize(definition.getProtocol());
        return SUPPORTED_ALIASES.contains(provider)
                && (protocol == ModelProtocol.CHAT_COMPLETIONS || protocol == ModelProtocol.RESPONSES);
    }

    @Override
    public ProviderClientBundle create(ModelRoutingProperties.ModelDefinition definition) {
        if (!supports(definition)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "不支持的 Chat provider: " + definition.getProvider());
        }
        Duration timeout = Duration.ofSeconds(
                definition.getTimeoutSeconds() == null ? 60 : definition.getTimeoutSeconds());
        ModelProtocol protocol = ModelProtocol.normalize(definition.getProtocol());
        if (protocol == ModelProtocol.RESPONSES) {
            OpenAiResponsesChatModel chatModel = OpenAiResponsesChatModel.builder()
                    .httpClientBuilder(httpClientBuilder(timeout))
                    .baseUrl(definition.getBaseurl())
                    .apiKey(definition.getApikey())
                    .modelName(definition.getModel())
                    .build();
            OpenAiResponsesStreamingChatModel streamingChatModel = OpenAiResponsesStreamingChatModel.builder()
                    .httpClientBuilder(httpClientBuilder(timeout))
                    .baseUrl(definition.getBaseurl())
                    .apiKey(definition.getApikey())
                    .modelName(definition.getModel())
                    .build();
            return new ProviderClientBundle(providerId(), chatModel, streamingChatModel);
        }

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .baseUrl(definition.getBaseurl())
                .apiKey(definition.getApikey())
                .modelName(definition.getModel())
                .timeout(timeout)
                .build();
        OpenAiStreamingChatModel streamingChatModel = OpenAiStreamingChatModel.builder()
                .baseUrl(definition.getBaseurl())
                .apiKey(definition.getApikey())
                .modelName(definition.getModel())
                .timeout(timeout)
                .build();
        return new ProviderClientBundle(providerId(), chatModel, streamingChatModel);
    }

    private dev.langchain4j.http.client.HttpClientBuilder httpClientBuilder(Duration timeout) {
        return HttpClientBuilderLoader.loadHttpClientBuilder()
                .connectTimeout(timeout)
                .readTimeout(timeout);
    }

    @Override
    public ModelInvocationException normalize(Throwable error, String modelId) {
        return ModelInvocationErrorClassifier.classify(error, providerId(), modelId);
    }
}
