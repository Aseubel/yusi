package com.aseubel.yusi.pojo.constant;

/** Durable categories for background and source-triggered work. */
public enum TaskExecutionType {
    DIARY("DIARY"),
    PLAZA("PLAZA"),
    EMBEDDING("EMBEDDING"),
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
}
