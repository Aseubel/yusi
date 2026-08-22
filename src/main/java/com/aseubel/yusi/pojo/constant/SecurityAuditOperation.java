package com.aseubel.yusi.pojo.constant;

public enum SecurityAuditOperation {
    CREATE,
    /** Retained so historical audit details remain readable; no new events use it. */
    @Deprecated
    READ,
    UPDATE,
    DELETE,
    ACTIVATE,
    REVIEW,
    REPLY,
    STATUS_CHANGE,
    PUBLISH,
    FULL_SYNC,
    DEREGISTER,
    PARTICIPANT_CHECK,
    RESET
}
