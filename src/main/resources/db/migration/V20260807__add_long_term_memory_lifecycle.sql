-- ============================================================
-- V20260807__add_long_term_memory_lifecycle.sql
-- Phase 1: 长期记忆生命周期字段
-- ============================================================

ALTER TABLE `user_persona`
    ADD COLUMN `source_type` VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN'
        COMMENT '来源类型' AFTER `custom_instructions`,
    ADD COLUMN `source_id` VARCHAR(128) DEFAULT NULL
        COMMENT '来源记录ID' AFTER `source_type`,
    ADD COLUMN `confidence` DOUBLE NOT NULL DEFAULT 0.5
        COMMENT '可信度，范围 0 到 1' AFTER `source_id`,
    ADD COLUMN `match_allowed` TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '是否允许参与匹配' AFTER `confidence`,
    ADD COLUMN `hidden` TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '是否隐藏' AFTER `match_allowed`,
    ADD COLUMN `valid_until` DATETIME DEFAULT NULL
        COMMENT '有效期截止时间' AFTER `hidden`;

UPDATE `user_persona`
SET `match_allowed` = 1,
    `hidden` = 0,
    `confidence` = 0.5
WHERE 1 = 1;

CREATE INDEX `idx_user_persona_lifecycle`
    ON `user_persona` (`user_id`, `hidden`, `match_allowed`, `valid_until`);

ALTER TABLE `life_graph_entity`
    ADD COLUMN `confidence` DOUBLE NOT NULL DEFAULT 0.5
        COMMENT '可信度，范围 0 到 1' AFTER `props`,
    ADD COLUMN `match_allowed` TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '是否允许参与匹配' AFTER `confidence`,
    ADD COLUMN `hidden` TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '是否隐藏' AFTER `match_allowed`,
    ADD COLUMN `valid_until` DATETIME DEFAULT NULL
        COMMENT '有效期截止时间' AFTER `hidden`;

UPDATE `life_graph_entity`
SET `match_allowed` = 1,
    `hidden` = 0,
    `confidence` = 0.5
WHERE 1 = 1;

CREATE INDEX `idx_life_graph_entity_lifecycle`
    ON `life_graph_entity` (`user_id`, `hidden`, `match_allowed`, `valid_until`);

CREATE INDEX `idx_life_graph_entity_visible_type`
    ON `life_graph_entity` (`user_id`, `hidden`, `type`, `valid_until`, `mention_count`);
