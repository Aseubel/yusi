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
import java.util.Set;

/** Removes OpenAI-only Responses fields rejected by DeepSeek's strict API. */
final class DeepSeekResponsesHttpClientBuilder implements HttpClientBuilder {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> UNSUPPORTED_FIELDS = Set.of(
            "store",
            "previous_response_id",
            "include",
            "truncation",
            "service_tier",
            "safety_identifier",
            "prompt_cache_key",
            "prompt_cache_retention",
            "stream_options");

    private final HttpClientBuilder delegate;

    DeepSeekResponsesHttpClientBuilder(HttpClientBuilder delegate) {
        this.delegate = delegate;
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
        return new FilteringHttpClient(delegate.build());
    }

    private static HttpRequest sanitize(HttpRequest request) {
        if (request == null || request.body() == null || request.body().isBlank()
                || request.url() == null
                || !request.url().toLowerCase(Locale.ROOT).endsWith("/responses")) {
            return request;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(request.body());
            if (!(root instanceof ObjectNode object)) {
                return request;
            }
            boolean changed = UNSUPPORTED_FIELDS.stream().anyMatch(object::has);
            if (!changed) {
                return request;
            }
            UNSUPPORTED_FIELDS.forEach(object::remove);
            return HttpRequest.builder()
                    .method(request.method())
                    .url(request.url())
                    .headers(request.headers())
                    .body(OBJECT_MAPPER.writeValueAsString(object))
                    .build();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sanitize DeepSeek Responses request", exception);
        }
    }

    private static final class FilteringHttpClient implements HttpClient {
        private final HttpClient delegate;

        private FilteringHttpClient(HttpClient delegate) {
            this.delegate = delegate;
        }

        @Override
        public SuccessfulHttpResponse execute(HttpRequest request) {
            return delegate.execute(sanitize(request));
        }

        @Override
        public void execute(HttpRequest request, ServerSentEventParser parser,
                ServerSentEventListener listener) {
            delegate.execute(sanitize(request), parser, listener);
        }
    }
}
