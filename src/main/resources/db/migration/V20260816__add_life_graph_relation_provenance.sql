ALTER TABLE `life_graph_relation`
    ADD COLUMN `origin` VARCHAR(16) NOT NULL DEFAULT 'MANUAL'
        COMMENT '关系来源：AUTO 可随来源撤销，MANUAL 保留人工关系' AFTER `evidence_diary_id`;

ALTER TABLE `life_graph_relation`
    ADD COLUMN `manual_weight` INT NOT NULL DEFAULT 0
        COMMENT '人工关系权重基线，不随日记来源撤销' AFTER `weight`;

ALTER TABLE `life_graph_entity`
    ADD COLUMN `origin` VARCHAR(16) NOT NULL DEFAULT 'MANUAL'
        COMMENT '实体来源：AUTO/MANUAL' AFTER `hidden`;

UPDATE `life_graph_entity` e
SET e.`origin` = 'AUTO'
WHERE EXISTS (
    SELECT 1 FROM `life_graph_mention` m
    WHERE m.`user_id` = e.`user_id` AND m.`entity_id` = e.`id`
);

UPDATE `life_graph_relation`
SET `origin` = CASE WHEN `evidence_diary_id` IS NULL THEN 'MANUAL' ELSE 'AUTO' END;

UPDATE `life_graph_relation`
SET `manual_weight` = CASE WHEN `origin` = 'MANUAL' THEN `weight` ELSE 0 END;

CREATE TABLE `life_graph_relation_evidence` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` VARCHAR(64) NOT NULL COMMENT '所属用户ID',
    `relation_id` BIGINT NOT NULL COMMENT '聚合关系ID',
    `source_type` VARCHAR(32) NOT NULL COMMENT '来源类型：DIARY/LEGACY',
    `source_id` VARCHAR(255) NOT NULL COMMENT '来源业务ID或迁移标识',
    `occurrence_count` INT NOT NULL DEFAULT 1 COMMENT '该来源对关系权重的贡献次数',
    `evidence_snippet` VARCHAR(1000) DEFAULT NULL COMMENT '短证据片段',
    `confidence` DECIMAL(4,3) DEFAULT NULL COMMENT '来源证据置信度',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_life_graph_relation_evidence_source` (`user_id`, `relation_id`, `source_type`, `source_id`),
    KEY `idx_life_graph_relation_evidence_relation` (`user_id`, `relation_id`),
    KEY `idx_life_graph_relation_evidence_source` (`user_id`, `source_type`, `source_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '人生图谱关系来源证据表';

INSERT INTO `life_graph_relation_evidence`
    (`user_id`, `relation_id`, `source_type`, `source_id`, `occurrence_count`, `confidence`, `created_at`, `updated_at`)
SELECT `user_id`, `id`, 'DIARY', `evidence_diary_id`, 1, `confidence`, `created_at`, `updated_at`
FROM `life_graph_relation`
WHERE `evidence_diary_id` IS NOT NULL;

INSERT INTO `life_graph_relation_evidence`
    (`user_id`, `relation_id`, `source_type`, `source_id`, `occurrence_count`, `confidence`, `created_at`, `updated_at`)
SELECT `user_id`, `id`, 'LEGACY', CONCAT('relation:', `id`), `weight` - 1, `confidence`, `created_at`, `updated_at`
FROM `life_graph_relation`
WHERE `evidence_diary_id` IS NOT NULL
  AND `weight` > 1;

INSERT INTO `life_graph_relation_evidence`
    (`user_id`, `relation_id`, `source_type`, `source_id`, `occurrence_count`, `confidence`, `created_at`, `updated_at`)
SELECT `user_id`, `id`, 'LEGACY', CONCAT('relation:', `id`), `weight`, `confidence`, `created_at`, `updated_at`
FROM `life_graph_relation`
WHERE `evidence_diary_id` IS NULL
  AND `weight` > 0;
