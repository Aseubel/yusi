package com.aseubel.yusi.pojo.constant;

public enum SecurityAuditResourceType {
    USER,
    CONNECTION,
    SITUATION_SCENARIO,
    SUGGESTION,
    ANNOUNCEMENT,
    EMBEDDING_SYNC,
    PROMPT_TEMPLATE,
    MODEL_GOVERNANCE,
    /** Retained so historical JPA audit rows remain readable; no new events use it. */
    @Deprecated
    USER_BACKUP_KEY,
    MID_TERM_MEMORY,
    LIFE_GRAPH_ENTITY,
    PERSONA,
    TASK_EXECUTION,
    RESOURCE
}
