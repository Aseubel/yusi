package com.aseubel.yusi.service.ai.model;

/**
 * Raised only when the gateway cannot reserve the configured admission budget.
 * It is not a provider health failure.
 */
public class ModelAdmissionDeniedException extends ModelInvocationException {

    private final String reason;

    public ModelAdmissionDeniedException(String provider, String modelId, String reason) {
        super(ModelFailureKind.RATE_LIMITED, provider, modelId, null,
                new IllegalStateException("Model admission rejected: " + reason));
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
