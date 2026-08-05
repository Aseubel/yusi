package com.aseubel.yusi.service.ai.model;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public final class ModelInvocationErrorClassifier {

    private ModelInvocationErrorClassifier() {
    }

    public static ModelInvocationException classify(Throwable error, String provider, String modelId) {
        Throwable root = unwrap(error);
        ModelFailureKind kind = classifyKind(root);
        return new ModelInvocationException(kind, provider, modelId, retryAfterMillis(root), root);
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error == null ? new IllegalStateException("Unknown model invocation failure") : error;
        while ((current instanceof CompletionException || current instanceof ExecutionException
                || current instanceof InvocationTargetException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static ModelFailureKind classifyKind(Throwable error) {
        if (error instanceof CancellationException || error instanceof InterruptedException) {
            return ModelFailureKind.CANCELLED;
        }
        if (error instanceof SocketTimeoutException || error instanceof ConnectException
                || error instanceof TimeoutException || error instanceof IOException) {
            return ModelFailureKind.TRANSIENT_NETWORK;
        }

        String message = error.getMessage() == null ? "" : error.getMessage().toLowerCase(Locale.ROOT);
        if (containsAny(message, "429", "rate limit", "rate_limit", "too many requests", "throttl")) {
            return ModelFailureKind.RATE_LIMITED;
        }
        if (containsAny(message, "context length", "context window", "maximum context", "max context",
                "prompt is too long", "token limit", "too many tokens")) {
            return ModelFailureKind.CONTEXT_LIMIT;
        }
        if (containsAny(message, "safety", "content policy", "refus", "moderation")) {
            return ModelFailureKind.SAFETY_REFUSAL;
        }
        if (containsAny(message, "structured output", "json schema", "invalid json", "tool call parse",
                "failed to parse")) {
            return ModelFailureKind.STRUCTURED_OUTPUT;
        }
        if (containsAny(message, "invalid request", "bad request", "http 400", "status code: 400")) {
            return ModelFailureKind.INVALID_REQUEST;
        }
        if (containsAny(message, "http 5", "server error", "service unavailable", "bad gateway",
                "gateway timeout")) {
            return ModelFailureKind.SERVER_ERROR;
        }
        if (containsAny(message, "timeout", "timed out", "connection reset", "connection refused",
                "broken pipe", "network")) {
            return ModelFailureKind.TRANSIENT_NETWORK;
        }
        return ModelFailureKind.UNKNOWN;
    }

    private static Long retryAfterMillis(Throwable error) {
        String message = error.getMessage();
        if (message == null) {
            return null;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        int marker = lower.indexOf("retry-after");
        if (marker < 0) {
            return null;
        }
        String suffix = lower.substring(marker).replaceAll("[^0-9]", " ").trim();
        if (suffix.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(suffix.split("\\s+")[0]) * 1000L;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            if (value.contains(term)) {
                return true;
            }
        }
        return false;
    }
}
