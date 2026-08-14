package com.aseubel.yusi.pojo.constant;

/** Stable, low-sensitivity security audit actions. */
public enum SecurityAuditAction {
    CONNECTION_REPORTED("connection.reported"),
    CONNECTION_BLOCKED("connection.blocked"),
    MEMORY_CREATED("memory.created"),
    MEMORY_UPDATED("memory.updated"),
    MEMORY_DELETED("memory.deleted"),
    LIFE_GRAPH_UPDATED("life_graph.updated"),
    LIFE_GRAPH_DELETED("life_graph.deleted"),
    PERSONA_UPDATED("persona.updated"),
    PERSONA_DELETED("persona.deleted"),
    TASK_FAILED("task.failed"),
    ACCESS_DENIED("access.denied");

    private final String code;

    SecurityAuditAction(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
