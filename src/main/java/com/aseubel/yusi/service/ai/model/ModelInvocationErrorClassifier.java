package com.aseubel.yusi.service.ai.model;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.RateLimitException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CancellationException;

public final class ModelInvocationErrorClassifier {

    private ModelInvocationErrorClassifier() {
    }

    public static ModelInvocationException classify(Throwable error, String provider, String modelId) {
        Throwable root = unwrap(error);
        Integer httpStatus = findHttpStatus(error);
        ModelFailureKind kind = classifyKind(error, httpStatus);
        return new ModelInvocationException(kind, provider, modelId, retryAfterMillis(error), httpStatus, root);
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error == null ? new IllegalStateException("Unknown model invocation failure") : error;
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (current.getCause() != null && seen.add(current)) {
            current = current.getCause();
        }
        return current;
    }

    private static ModelFailureKind classifyKind(Throwable error, Integer httpStatus) {
        if (containsType(error, CancellationException.class) || containsType(error, InterruptedException.class)) {
            return ModelFailureKind.CANCELLED;
        }
        if (httpStatus != null) {
            return classifyHttpStatus(httpStatus);
        }
        if (containsType(error, AuthenticationException.class)) {
            return ModelFailureKind.AUTHENTICATION;
        }
        if (containsType(error, ModelNotFoundException.class)) {
            return ModelFailureKind.MODEL_NOT_FOUND;
        }
        if (containsType(error, ContentFilteredException.class)) {
            return ModelFailureKind.SAFETY_REFUSAL;
        }
        if (containsType(error, InvalidRequestException.class)) {
            return ModelFailureKind.INVALID_REQUEST;
        }
        if (containsType(error, RateLimitException.class)) {
            return ModelFailureKind.RATE_LIMITED;
        }
        if (containsType(error, InternalServerException.class)) {
            return ModelFailureKind.SERVER_ERROR;
        }
        if (containsType(error, dev.langchain4j.exception.TimeoutException.class)
                || containsType(error, SocketTimeoutException.class)
                || containsType(error, ConnectException.class)
                || containsType(error, java.util.concurrent.TimeoutException.class)
                || containsType(error, IOException.class)) {
            return ModelFailureKind.TRANSIENT_NETWORK;
        }

        String message = messages(error);
        if (containsAny(message, "429", "rate limit", "rate_limit", "too many requests", "throttl")) {
            return ModelFailureKind.RATE_LIMITED;
        }
        if (containsAny(message, "401", "403", "unauthorized", "forbidden", "authentication failed",
                "authentication error", "invalid api key", "invalid_api_key")) {
            return ModelFailureKind.AUTHENTICATION;
        }
        if (containsAny(message, "404", "model not found", "model_not_found", "no such model")) {
            return ModelFailureKind.MODEL_NOT_FOUND;
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
        if (containsAny(message, "invalid request", "bad request", "http 400", "status code: 400",
                "http 413", "status code: 413", "http 422", "status code: 422")) {
            return ModelFailureKind.INVALID_REQUEST;
        }
        if (containsAny(message, "http 5", "server error", "service unavailable", "bad gateway",
                "gateway timeout", "status code: 5")) {
            return ModelFailureKind.SERVER_ERROR;
        }
        if (containsAny(message, "timeout", "timed out", "connection reset", "connection refused",
                "broken pipe", "network")) {
            return ModelFailureKind.TRANSIENT_NETWORK;
        }
        return ModelFailureKind.UNKNOWN;
    }

    private static ModelFailureKind classifyHttpStatus(int status) {
        return switch (status) {
            case 401, 403 -> ModelFailureKind.AUTHENTICATION;
            case 404 -> ModelFailureKind.MODEL_NOT_FOUND;
            case 408 -> ModelFailureKind.TRANSIENT_NETWORK;
            case 413, 422 -> ModelFailureKind.INVALID_REQUEST;
            case 429 -> ModelFailureKind.RATE_LIMITED;
            default -> {
                if (status >= 500) {
                    yield ModelFailureKind.SERVER_ERROR;
                }
                if (status >= 400) {
                    yield ModelFailureKind.INVALID_REQUEST;
                }
                yield ModelFailureKind.UNKNOWN;
            }
        };
    }

    private static Integer findHttpStatus(Throwable error) {
        for (Throwable current : causes(error)) {
            if (current instanceof HttpException httpException) {
                return httpException.statusCode();
            }
        }
        return null;
    }

    private static String messages(Throwable error) {
        StringBuilder messages = new StringBuilder();
        for (Throwable current : causes(error)) {
            if (current.getMessage() != null) {
                if (messages.length() > 0) {
                    messages.append(' ');
                }
                messages.append(current.getMessage().toLowerCase(Locale.ROOT));
            }
        }
        return messages.toString();
    }

    private static boolean containsType(Throwable error, Class<?> type) {
        for (Throwable current : causes(error)) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    private static Iterable<Throwable> causes(Throwable error) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        java.util.List<Throwable> causes = new java.util.ArrayList<>();
        Throwable current = error == null ? new IllegalStateException("Unknown model invocation failure") : error;
        while (current != null && seen.add(current)) {
            causes.add(current);
            current = current.getCause();
        }
        return causes;
    }

    private static Long retryAfterMillis(Throwable error) {
        for (Throwable current : causes(error)) {
            String message = current.getMessage();
            if (message == null) {
                continue;
            }
            String lower = message.toLowerCase(Locale.ROOT);
            int marker = lower.indexOf("retry-after");
            if (marker < 0) {
                continue;
            }
            String suffix = lower.substring(marker).replaceAll("[^0-9]", " ").trim();
            if (suffix.isBlank()) {
                continue;
            }
            try {
                return Long.parseLong(suffix.split("\\s+")[0]) * 1000L;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
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
