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
    ADMIN_PERMISSION_UPDATED("admin.permission.updated"),
    ADMIN_USER_DEREGISTERED("admin.user.deregistered"),
    SCENARIO_REVIEWED("scenario.reviewed"),
    SUGGESTION_REPLIED("suggestion.replied"),
    SUGGESTION_STATUS_UPDATED("suggestion.status.updated"),
    ANNOUNCEMENT_PUBLISHED("announcement.published"),
    EMBEDDINGS_FULL_SYNC("embeddings.full_sync"),
    PROMPT_CREATED("prompt.created"),
    PROMPT_UPDATED("prompt.updated"),
    PROMPT_ACTIVATED("prompt.activated"),
    PROMPT_DELETED("prompt.deleted"),
    MODEL_GOVERNANCE_UPDATED("model.governance.updated"),
    MODEL_RUNTIME_STATE_RESET("model.runtime_state.reset"),
    /**
     * Retained so historical JPA audit rows remain readable; no new events use it.
     */
    @Deprecated
    BACKUP_KEY_ACCESSED("backup_key.accessed"),
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
