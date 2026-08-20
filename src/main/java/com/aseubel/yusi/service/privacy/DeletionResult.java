package com.aseubel.yusi.service.privacy;

/** Low-sensitivity result of an account deletion request. */
public record DeletionResult(String requestId, Status status, String failureCategory) {

    public enum Status {
        COMPLETED,
        PENDING_RETRY
    }

    public boolean success() {
        return status == Status.COMPLETED;
    }

    public static DeletionResult completed(String requestId) {
        return new DeletionResult(requestId, Status.COMPLETED, null);
    }

    public static DeletionResult pendingRetry(String requestId, String failureCategory) {
        return new DeletionResult(requestId, Status.PENDING_RETRY, failureCategory);
    }
}
