package com.aseubel.yusi.pojo.constant;

/** Safe, bounded failure categories; exception text is intentionally not persisted. */
public enum TaskFailureCategory {
    TRANSIENT,
    DEPENDENCY,
    VALIDATION,
    PERMISSION,
    CONFLICT,
    TIMEOUT,
    UNKNOWN
}
