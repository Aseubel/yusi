package com.aseubel.yusi.pojo.constant;

/** Source namespaces used by the task ledger. */
public enum TaskExecutionSourceType {
    DIARY("DIARY"),
    PLAZA("PLAZA"),
    EMBEDDING("EMBEDDING"),
    LIFE_GRAPH("LIFE_GRAPH"),
    PERSONA("PERSONA"),
    WEEKLY_REPORT("WEEKLY_REPORT"),
    MATCHING("MATCHING"),
    PROACTIVE_GREETING("PROACTIVE_GREETING");

    private final String code;

    TaskExecutionSourceType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
