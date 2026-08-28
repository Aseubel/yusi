package com.aseubel.yusi.service.ai.model.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;

import java.time.Duration;
import java.util.Locale;

/**
 * DashScope 兼容适配：向 OpenAI 兼容请求体注入
 * <ul>
 *   <li>{@code enable_thinking}（按三态决策：tier 请求级覆盖 > 模型级配置 > 不注入；
 *       关闭 qwen3.x 默认思考链或显式开启）</li>
 *   <li>{@code max_tokens} 回填（SDK Responses 协议只发 {@code max_output_tokens}，
 *       而 DashScope 兼容端点按 {@code max_tokens} 限流输出，缺省会掉回默认 1024 导致
 *       GraphRAG 抽取 JSON 被截断 finish_reason=LENGTH）</li>
 * </ul>
 * 对声明 thinking-enabled/max-output-tokens 的模型启用；OpenAI 忽略未知字段，
 * DeepSeek 的严格 API 由外层 {@link DeepSeekResponsesHttpClientBuilder} 剥离不支持字段。
 */
final class DashScopeThinkingHttpClientBuilder implements HttpClientBuilder {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpClientBuilder delegate;

    /** 模型级思考模式配置；null 表示不干预（沿用服务端默认）。 */
    private final Boolean modelThinkingEnabled;

    /** 该模型允许的最大输出 tokens；null 表示不做 max_tokens 回填。 */
    private final Integer maxOutputTokens;

    DashScopeThinkingHttpClientBuilder(HttpClientBuilder delegate, Boolean modelThinkingEnabled,
            Integer maxOutputTokens) {
        this.delegate = delegate;
        this.modelThinkingEnabled = modelThinkingEnabled;
        this.maxOutputTokens = maxOutputTokens;
    }

    @Override
    public Duration connectTimeout() {
        return delegate.connectTimeout();
    }

    @Override
    public HttpClientBuilder connectTimeout(Duration timeout) {
        delegate.connectTimeout(timeout);
        return this;
    }

    @Override
    public Duration readTimeout() {
        return delegate.readTimeout();
    }

    @Override
    public HttpClientBuilder readTimeout(Duration timeout) {
        delegate.readTimeout(timeout);
        return this;
    }

    @Override
    public HttpClient build() {
        return new ThinkingOffHttpClient(delegate.build(), this);
    }

    private static boolean isCompatibleEndpoint(String url) {
        return url != null && (url.toLowerCase(Locale.ROOT).endsWith("/chat/completions")
                || url.toLowerCase(Locale.ROOT).endsWith("/responses"));
    }

    private HttpRequest patchBody(HttpRequest request) {
        // tier 请求级覆盖优先于模型级配置；两者皆空则不注入 enable_thinking
        Boolean thinkingEnabled = ThinkingRequestContext.override() != null
                ? ThinkingRequestContext.override() : modelThinkingEnabled;
        return inject(request, thinkingEnabled, maxOutputTokens);
    }

    private static HttpRequest inject(HttpRequest request, Boolean thinkingEnabled, Integer maxTokens) {
        if (request == null || request.body() == null || request.body().isBlank()
                || !isCompatibleEndpoint(request.url())) {
            return request;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(request.body());
            if (!(root instanceof ObjectNode object)) {
                return request;
            }
            boolean changed = false;
            if (thinkingEnabled != null && !object.has("enable_thinking")) {
                object.put("enable_thinking", thinkingEnabled);
                changed = true;
            }
            // DashScope 兼容端点只认 chat/completions 风格的 max_tokens；
            // Responses 协议 SDK 只发 max_output_tokens，缺了它输出被默认 1024 截断
            if (maxTokens != null && !object.has("max_tokens")
                    && object.path("max_output_tokens").asInt(0) == 0) {
                object.put("max_tokens", maxTokens);
                changed = true;
            }
            if (!changed) {
                return request;
            }
            return HttpRequest.builder()
                    .method(request.method())
                    .url(request.url())
                    .headers(request.headers())
                    .body(OBJECT_MAPPER.writeValueAsString(object))
                    .build();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to patch DashScope-compatible request", exception);
        }
    }

    private static final class ThinkingOffHttpClient implements HttpClient {
        private final HttpClient delegate;
        private final DashScopeThinkingHttpClientBuilder owner;

        private ThinkingOffHttpClient(HttpClient delegate, DashScopeThinkingHttpClientBuilder owner) {
            this.delegate = delegate;
            this.owner = owner;
        }

        @Override
        public SuccessfulHttpResponse execute(HttpRequest request) {
            return delegate.execute(owner.patchBody(request));
        }

        @Override
        public void execute(HttpRequest request, ServerSentEventParser parser,
                ServerSentEventListener listener) {
            delegate.execute(owner.patchBody(request), parser, listener);
        }
    }
}
