package com.aseubel.yusi.service.ai.model.provider;

import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.service.ai.model.ModelInvocationErrorClassifier;
import com.aseubel.yusi.service.ai.model.ModelInvocationException;
import com.aseubel.yusi.service.ai.model.ModelProtocol;
import com.aseubel.yusi.service.ai.model.constant.ModelProviderType;
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

    @Override
    public String providerId() {
        return ModelProviderType.OPENAI_COMPATIBLE.canonicalCode();
    }

    @Override
    public boolean supports(ModelRoutingProperties.ModelDefinition definition) {
        String provider = definition.getProvider() == null
                ? "" : definition.getProvider().trim().toLowerCase(Locale.ROOT);
        ModelProtocol protocol = ModelProtocol.normalize(definition.getProtocol());
        return ModelProviderType.OPENAI_COMPATIBLE.aliases().contains(provider)
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
        String baseUrl = normalizeBaseUrl(definition.getBaseurl(), protocol);
        if (protocol == ModelProtocol.RESPONSES) {
            // DashScope Responses 默认输出上限仅 1024 tokens，GraphRAG 抽取会被截断，必须显式放大
            Integer maxOutputTokens = definition.getMaxOutputTokens();
            var chatModelBuilder = OpenAiResponsesChatModel.builder()
                    .httpClientBuilder(httpClientBuilder(timeout, definition))
                    .baseUrl(baseUrl)
                    .apiKey(definition.getApikey())
                    .modelName(definition.getModel());
            var streamingBuilder = OpenAiResponsesStreamingChatModel.builder()
                    .httpClientBuilder(httpClientBuilder(timeout, definition))
                    .baseUrl(baseUrl)
                    .apiKey(definition.getApikey())
                    .modelName(definition.getModel());
            if (maxOutputTokens != null) {
                chatModelBuilder.maxOutputTokens(maxOutputTokens);
                streamingBuilder.maxOutputTokens(maxOutputTokens);
            }
            OpenAiResponsesChatModel chatModel = chatModelBuilder.build();
            OpenAiResponsesStreamingChatModel streamingChatModel = streamingBuilder.build();
            return new ProviderClientBundle(providerId(), chatModel, streamingChatModel);
        }

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(definition.getApikey())
                .modelName(definition.getModel())
                .timeout(timeout)
                .build();
        OpenAiStreamingChatModel streamingChatModel = OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(definition.getApikey())
                .modelName(definition.getModel())
                .timeout(timeout)
                .build();
        return new ProviderClientBundle(providerId(), chatModel, streamingChatModel);
    }

    private dev.langchain4j.http.client.HttpClientBuilder httpClientBuilder(Duration timeout,
            ModelRoutingProperties.ModelDefinition definition) {
        dev.langchain4j.http.client.HttpClientBuilder builder = HttpClientBuilderLoader.loadHttpClientBuilder()
                .connectTimeout(timeout)
                .readTimeout(timeout);
        if (definition.getThinkingEnabled() != null || definition.getMaxOutputTokens() != null) {
            // DeepSeek 过滤器必须包裹在 DashScope 装饰器之外（后执行 sanitize），
            // 才能剥掉 DashScope 装饰器注入的 enable_thinking/max_tokens；
            // 反之 patch 发生在 sanitize 之后，严格 API 会收到 DashScope 专属字段。
            if (isDeepSeekResponses(definition)) {
                builder = new DashScopeThinkingHttpClientBuilder(
                        new DeepSeekResponsesHttpClientBuilder(builder),
                        definition.getThinkingEnabled(), definition.getMaxOutputTokens());
            } else {
                builder = new DashScopeThinkingHttpClientBuilder(builder,
                        definition.getThinkingEnabled(), definition.getMaxOutputTokens());
            }
        }
        return builder;
    }

    private boolean isDeepSeekResponses(ModelRoutingProperties.ModelDefinition definition) {
        String provider = definition.getProvider() == null
                ? "" : definition.getProvider().trim().toLowerCase(Locale.ROOT);
        String model = definition.getModel() == null
                ? "" : definition.getModel().trim().toLowerCase(Locale.ROOT);
        return "deepseek".equals(provider) || model.startsWith("deepseek-");
    }

    private String normalizeBaseUrl(String configuredBaseUrl, ModelProtocol protocol) {
        if (configuredBaseUrl == null || configuredBaseUrl.isBlank()) {
            return configuredBaseUrl;
        }
        String value = configuredBaseUrl.trim().replaceFirst("/+$", "");
        String suffix = protocol == ModelProtocol.RESPONSES
                ? "/responses" : "/chat/completions";
        if (value.length() >= suffix.length()
                && value.regionMatches(true, value.length() - suffix.length(), suffix, 0, suffix.length())) {
            return value.substring(0, value.length() - suffix.length());
        }
        return value;
    }

    @Override
    public ModelInvocationException normalize(Throwable error, String modelId) {
        return ModelInvocationErrorClassifier.classify(error, providerId(), modelId);
    }
}
