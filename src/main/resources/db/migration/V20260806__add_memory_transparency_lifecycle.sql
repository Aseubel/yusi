-- ============================================================
-- V20260806__add_memory_transparency_lifecycle.sql
-- Phase 1: 记忆透明度与生命周期控制
-- ============================================================

ALTER TABLE `mid_term_memory`
    ADD COLUMN `source_type` VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN'
        COMMENT '记忆来源类型：CHAT_SUMMARY/DIARY/PLAZA/UNKNOWN' AFTER `user_id`,
    ADD COLUMN `source_id` VARCHAR(128) DEFAULT NULL
        COMMENT '来源记录ID' AFTER `source_type`,
    ADD COLUMN `confidence` DOUBLE NOT NULL DEFAULT 0.5
        COMMENT 'AI置信度，范围0到1' AFTER `importance`,
    ADD COLUMN `match_allowed` TINYINT(1) NOT NULL DEFAULT 1
        COMMENT '是否允许参与匹配' AFTER `confidence`,
    ADD COLUMN `hidden` TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '是否被用户隐藏' AFTER `match_allowed`;

UPDATE `mid_term_memory`
SET `confidence` = LEAST(GREATEST(`importance`, 0.0), 1.0)
WHERE `confidence` = 0.5;

CREATE INDEX `idx_mid_term_memory_lifecycle`
    ON `mid_term_memory` (`user_id`, `hidden`, `match_allowed`, `valid_until`);
