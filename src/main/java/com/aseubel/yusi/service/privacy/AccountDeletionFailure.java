package com.aseubel.yusi.service.privacy;

/** Failure with a fixed category and no user-controlled exception message. */
public final class AccountDeletionFailure extends RuntimeException {

    private final String category;

    public AccountDeletionFailure(String category) {
        super(category);
        this.category = category;
    }

    public AccountDeletionFailure(String category, Throwable cause) {
        super(category, cause);
        this.category = category;
    }

    public String category() {
        return category;
    }
}
