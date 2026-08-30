-- ============================================================
-- V20260902__replace_valid_until_with_memory_decay.sql
-- 记忆遗忘机制改造：移除硬过期 valid_until，改为半衰期软衰减 +
-- 双重门槛完全遗忘（initial_importance 门槛 + forgotten_threshold）+ 命中强化
-- ============================================================

-- 1. mid_term_memory：新增衰减/遗忘相关列
ALTER TABLE `mid_term_memory`
    ADD COLUMN `initial_importance` DOUBLE DEFAULT NULL COMMENT '记忆创建时的初始重要性，完全遗忘判定的门槛基准' AFTER `importance`,
    ADD COLUMN `last_reinforced_at` DATETIME DEFAULT NULL COMMENT '最后一次被检索命中的时间，衰减时钟基准' AFTER `initial_importance`,
    ADD COLUMN `forgotten_at` DATETIME DEFAULT NULL COMMENT '完全遗忘时间（懒判定落库），NULL 表示仍可被检索' AFTER `last_reinforced_at`;

-- 2. 回填初始重要性：已有记忆的 initial_importance 取当前 importance
UPDATE `mid_term_memory` SET `initial_importance` = `importance` WHERE `initial_importance` IS NULL;

-- 3. mid_term_memory：移除硬过期列及索引，重建不含 valid_until 的索引
ALTER TABLE `mid_term_memory` DROP INDEX `idx_mid_term_memory_valid_until`;
ALTER TABLE `mid_term_memory` DROP INDEX `idx_mid_term_memory_lifecycle`;
ALTER TABLE `mid_term_memory` DROP COLUMN `valid_until`;
ALTER TABLE `mid_term_memory`
    ADD INDEX `idx_mid_term_memory_lifecycle` (`user_id`, `hidden`, `match_allowed`),
    ADD INDEX `idx_mid_term_memory_forgotten` (`user_id`, `forgotten_at`);

-- 4. user_persona：移除有效期控制
ALTER TABLE `user_persona` DROP INDEX `idx_user_persona_lifecycle`;
ALTER TABLE `user_persona` DROP COLUMN `valid_until`;
ALTER TABLE `user_persona`
    ADD INDEX `idx_user_persona_lifecycle` (`user_id`, `hidden`, `match_allowed`);

-- 5. life_graph_entity：移除有效期控制
ALTER TABLE `life_graph_entity` DROP INDEX `idx_life_graph_entity_lifecycle`;
ALTER TABLE `life_graph_entity` DROP INDEX `idx_life_graph_entity_visible_type`;
ALTER TABLE `life_graph_entity` DROP COLUMN `valid_until`;
ALTER TABLE `life_graph_entity`
    ADD INDEX `idx_life_graph_entity_lifecycle` (`user_id`, `hidden`, `match_allowed`),
    ADD INDEX `idx_life_graph_entity_visible_type` (`user_id`, `hidden`, `type`, `mention_count`);
