package com.aseubel.yusi.service.ai.model;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;

import static org.assertj.core.api.Assertions.assertThat;

class ModelInvocationErrorClassifierTest {

    @Test
    void classifiesRateLimitFromProviderMessage() {
        ModelInvocationException error = ModelInvocationErrorClassifier.classify(
                new RuntimeException("HTTP 429 Too Many Requests"), "openai-compatible", "qwen");

        assertThat(error.kind()).isEqualTo(ModelFailureKind.RATE_LIMITED);
        assertThat(error.provider()).isEqualTo("openai-compatible");
        assertThat(error.modelId()).isEqualTo("qwen");
    }

    @Test
    void classifiesContextLimitAndCancellationWithoutAllowingFallback() {
        ModelInvocationException contextError = ModelInvocationErrorClassifier.classify(
                new IllegalArgumentException("context length exceeded"), "openai-compatible", "qwen");
        ModelInvocationException cancelled = ModelInvocationErrorClassifier.classify(
                new CancellationException("cancelled"), "openai-compatible", "qwen");

        assertThat(contextError.kind()).isEqualTo(ModelFailureKind.CONTEXT_LIMIT);
        assertThat(contextError.isFallbackEligible(false)).isFalse();
        assertThat(cancelled.kind()).isEqualTo(ModelFailureKind.CANCELLED);
        assertThat(cancelled.isFallbackEligible(false)).isFalse();
    }
}
