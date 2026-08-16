package com.aseubel.yusi.service.ai.model;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Function;

public final class ModelRouteContextHolder {

    private static final ThreadLocal<Deque<ModelRouteContext>> HOLDER = new ThreadLocal<>();

    private ModelRouteContextHolder() {
    }

    public static void set(ModelRouteContext context) {
        if (context == null) {
            return;
        }
        stack().push(context);
    }

    public static Scope open(ModelRouteContext context) {
        if (context == null) {
            return new Scope(false);
        }
        stack().push(context);
        return new Scope(true);
    }

    public static ModelRouteContext get() {
        Deque<ModelRouteContext> contexts = HOLDER.get();
        return contexts == null ? null : contexts.peek();
    }

    /**
     * Returns a call context enriched with the nearest values from outer scopes.
     * Call-specific scene and prompt fields remain the innermost values.
     */
    public static ModelRouteContext getEffective() {
        Deque<ModelRouteContext> contexts = HOLDER.get();
        if (contexts == null || contexts.isEmpty()) {
            return null;
        }

        ModelRouteContext nearest = contexts.peek();
        return ModelRouteContext.builder()
                .requestId(first(contexts, ModelRouteContext::getRequestId))
                .runId(first(contexts, ModelRouteContext::getRunId))
                .userId(first(contexts, ModelRouteContext::getUserId))
                .scene(first(contexts, ModelRouteContext::getScene))
                .promptKey(first(contexts, ModelRouteContext::getPromptKey))
                .promptVersion(first(contexts, ModelRouteContext::getPromptVersion))
                .promptLocale(first(contexts, ModelRouteContext::getPromptLocale))
                .riskLevel(first(contexts, ModelRouteContext::getRiskLevel))
                .estimatedInputTokens(first(contexts, ModelRouteContext::getEstimatedInputTokens))
                .reservedOutputTokens(first(contexts, ModelRouteContext::getReservedOutputTokens))
                .cancellationToken(first(contexts, ModelRouteContext::getCancellationToken))
                .maskSensitiveData(nearest.isMaskSensitiveData())
                .build();
    }

    public static void clear() {
        Deque<ModelRouteContext> contexts = HOLDER.get();
        if (contexts == null || contexts.isEmpty()) {
            HOLDER.remove();
            return;
        }
        contexts.pop();
        if (contexts.isEmpty()) {
            HOLDER.remove();
        }
    }

    private static Deque<ModelRouteContext> stack() {
        Deque<ModelRouteContext> contexts = HOLDER.get();
        if (contexts == null) {
            contexts = new ArrayDeque<>();
            HOLDER.set(contexts);
        }
        return contexts;
    }

    private static <T> T first(Deque<ModelRouteContext> contexts,
            Function<ModelRouteContext, T> extractor) {
        for (ModelRouteContext context : contexts) {
            T value = extractor.apply(context);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    public static final class Scope implements AutoCloseable {

        private final boolean ownsFrame;
        private boolean closed;

        private Scope(boolean ownsFrame) {
            this.ownsFrame = ownsFrame;
        }

        @Override
        public void close() {
            if (!closed && ownsFrame) {
                closed = true;
                ModelRouteContextHolder.clear();
            }
        }
    }
}
