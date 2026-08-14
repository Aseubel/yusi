package com.aseubel.yusi.pojo.constant;

/** Allow-listed metadata keys for security audit records. */
public final class SecurityAuditDetailKeys {

    public static final String FROM_STATUS = "fromStatus";
    public static final String TO_STATUS = "toStatus";
    public static final String ACTION = "action";
    public static final String REASON_CATEGORY = "reasonCategory";
    public static final String TASK_TYPE = "taskType";
    public static final String FAILURE_CATEGORY = "failureCategory";
    public static final String RETRY_COUNT = "retryCount";
    public static final String OPERATION = "operation";
    public static final String SOURCE_TYPE = "sourceType";
    public static final String VERSION = "version";
    public static final String COUNT = "count";
    public static final String AUDIENCE = "audience";

    private SecurityAuditDetailKeys() {
    }
}
