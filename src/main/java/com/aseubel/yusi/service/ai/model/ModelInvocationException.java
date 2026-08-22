package com.aseubel.yusi.service.ai.model;

import lombok.Getter;

@Getter
public class ModelInvocationException extends RuntimeException {

    private final ModelFailureKind kind;
    private final String provider;
    private final String modelId;
    private final Long retryAfterMs;
    private final Integer httpStatus;
    private final String errorSummary;

    public ModelInvocationException(ModelFailureKind kind, String provider, String modelId,
            Long retryAfterMs, Throwable cause) {
        this(kind, provider, modelId, retryAfterMs, null, cause);
    }

    public ModelInvocationException(ModelFailureKind kind, String provider, String modelId,
            Long retryAfterMs, Integer httpStatus, Throwable cause) {
        super(buildMessage(kind, provider, modelId, cause, httpStatus), cause);
        this.kind = kind == null ? ModelFailureKind.UNKNOWN : kind;
        this.provider = provider;
        this.modelId = modelId;
        this.retryAfterMs = retryAfterMs;
        this.httpStatus = httpStatus;
        this.errorSummary = ModelErrorSummary.summarize(cause, httpStatus);
    }

    public boolean isFallbackEligible(boolean outputEmitted) {
        if (outputEmitted) {
            return false;
        }
        return kind == ModelFailureKind.TRANSIENT_NETWORK
                || kind == ModelFailureKind.RATE_LIMITED
                || kind == ModelFailureKind.SERVER_ERROR
                || kind == ModelFailureKind.STRUCTURED_OUTPUT;
    }

    public ModelFailureKind kind() {
        return kind;
    }

    public String provider() {
        return provider;
    }

    public String modelId() {
        return modelId;
    }

    public Long retryAfterMs() {
        return retryAfterMs;
    }

    public Integer httpStatus() {
        return httpStatus;
    }

    public String errorSummary() {
        return errorSummary;
    }

    private static String buildMessage(ModelFailureKind kind, String provider, String modelId,
            Throwable cause, Integer httpStatus) {
        String detail = ModelErrorSummary.summarize(cause, httpStatus);
        return "Model invocation failed: kind=" + (kind == null ? ModelFailureKind.UNKNOWN : kind)
                + ", provider=" + provider + ", model=" + modelId
                + (detail == null || detail.isBlank() ? "" : ", detail=" + detail);
    }
}
