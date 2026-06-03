-- ============================================================
-- V20260603__add_memory_merged_into_id.sql
-- v4.0 Phase 3 (F11.4): mid_term_memory 跨源融合标记
-- ============================================================

ALTER TABLE `mid_term_memory`
    ADD COLUMN `merged_into_id` BIGINT DEFAULT NULL COMMENT '若被融合，指向幸存记忆的ID' AFTER `valid_until`;

ALTER TABLE `mid_term_memory`
    ADD INDEX `idx_mid_term_memory_merged` (`merged_into_id`);
