package com.aseubel.yusi.pojo.constant;

/** Lifecycle states for a durable task execution. */
public enum TaskExecutionStatus {
    PENDING,
    RUNNING,
    RETRY_WAIT,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
