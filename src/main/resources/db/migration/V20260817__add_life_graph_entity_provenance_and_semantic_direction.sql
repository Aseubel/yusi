ALTER TABLE life_graph_entity
    ADD COLUMN importance DOUBLE NOT NULL DEFAULT 0.5
        COMMENT '长期生活记忆价值，范围 0 到 1' AFTER confidence;

ALTER TABLE life_graph_relation
    ADD COLUMN semantic_source_id BIGINT NULL
        COMMENT '语义关系的主语实体ID' AFTER target_id,
    ADD COLUMN semantic_target_id BIGINT NULL
        COMMENT '语义关系的宾语实体ID' AFTER semantic_source_id;

UPDATE life_graph_relation
SET semantic_source_id = source_id,
    semantic_target_id = target_id
WHERE semantic_source_id IS NULL
   OR semantic_target_id IS NULL;

DROP INDEX uk_life_graph_relation_user_edge ON life_graph_relation;

ALTER TABLE life_graph_relation
    ADD UNIQUE KEY uk_life_graph_relation_user_semantic_edge
        (user_id, semantic_source_id, semantic_target_id, type),
    ADD KEY idx_life_graph_relation_user_semantic_source
        (user_id, semantic_source_id),
    ADD KEY idx_life_graph_relation_user_semantic_target
        (user_id, semantic_target_id);

CREATE TABLE life_graph_entity_evidence (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id VARCHAR(64) NOT NULL COMMENT '所属用户ID',
    entity_id BIGINT NOT NULL COMMENT '被来源贡献的实体ID',
    source_type VARCHAR(32) NOT NULL COMMENT '来源类型：DIARY/PLAZA/MANUAL/LEGACY',
    source_id VARCHAR(255) NOT NULL COMMENT '来源业务ID',
    occurrence_count INT NOT NULL DEFAULT 1 COMMENT '该来源的贡献次数',
    evidence_kind VARCHAR(64) DEFAULT NULL COMMENT '证据类别',
    snippet VARCHAR(1000) DEFAULT NULL COMMENT '短证据片段',
    entry_date DATE DEFAULT NULL COMMENT '来源日期',
    source_time DATETIME DEFAULT NULL COMMENT '来源时间',
    props JSON DEFAULT NULL COMMENT '来源扩展属性',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_life_graph_entity_evidence_source
        (user_id, entity_id, source_type, source_id),
    KEY idx_life_graph_entity_evidence_entity (user_id, entity_id),
    KEY idx_life_graph_entity_evidence_source (user_id, source_type, source_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '人生图谱实体来源证据表';
