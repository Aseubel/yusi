package com.aseubel.yusi.pojo.constant;

/** Durable categories for background and source-triggered work. */
public enum TaskExecutionType {
    DIARY("DIARY"),
    PLAZA("PLAZA"),
    EMBEDDING("EMBEDDING"),
    /**
     * @deprecated retained only for reading task_execution rows written by an older release;
     * new embedding tasks must use {@link #EMBEDDING}.
     */
    @Deprecated
    DIARY_EMBEDDING("DIARY_EMBEDDING"),
    LIFE_GRAPH("LIFE_GRAPH"),
    PERSONA("PERSONA"),
    WEEKLY_REPORT("WEEKLY_REPORT"),
    MATCHING("MATCHING"),
    COGNITION_INGEST("COGNITION_INGEST"),
    PROACTIVE_GREETING("PROACTIVE_GREETING");

    private final String code;

    TaskExecutionType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public TaskExecutionType canonical() {
        return this == DIARY_EMBEDDING ? EMBEDDING : this;
    }
}
