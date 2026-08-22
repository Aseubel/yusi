package com.aseubel.yusi.service.ai.model;

import dev.langchain4j.exception.HttpException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

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

    @Test
    void classifiesProviderHttpStatusesAndPreservesStatus() {
        ModelInvocationException authentication = ModelInvocationErrorClassifier.classify(
                new HttpException(401, "unauthorized"), "openai-compatible", "qwen");
        ModelInvocationException modelNotFound = ModelInvocationErrorClassifier.classify(
                new HttpException(404, "model not found"), "openai-compatible", "qwen");
        ModelInvocationException invalidRequest = ModelInvocationErrorClassifier.classify(
                new HttpException(422, "invalid request"), "openai-compatible", "qwen");
        ModelInvocationException serverError = ModelInvocationErrorClassifier.classify(
                new HttpException(500, "server error"), "openai-compatible", "qwen");

        assertThat(authentication.kind()).isEqualTo(ModelFailureKind.AUTHENTICATION);
        assertThat(authentication.httpStatus()).isEqualTo(401);
        assertThat(modelNotFound.kind()).isEqualTo(ModelFailureKind.MODEL_NOT_FOUND);
        assertThat(modelNotFound.httpStatus()).isEqualTo(404);
        assertThat(invalidRequest.kind()).isEqualTo(ModelFailureKind.INVALID_REQUEST);
        assertThat(invalidRequest.httpStatus()).isEqualTo(422);
        assertThat(serverError.kind()).isEqualTo(ModelFailureKind.SERVER_ERROR);
        assertThat(serverError.httpStatus()).isEqualTo(500);
    }

    @Test
    void classifiesHttpExceptionThroughAnArbitraryCauseChain() {
        ModelInvocationException error = ModelInvocationErrorClassifier.classify(
                new CompletionException(new IllegalStateException(
                        "provider wrapper", new HttpException(403, "forbidden"))),
                "openai-compatible", "qwen");

        assertThat(error.kind()).isEqualTo(ModelFailureKind.AUTHENTICATION);
        assertThat(error.httpStatus()).isEqualTo(403);
    }
}
