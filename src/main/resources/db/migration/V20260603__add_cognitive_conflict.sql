-- ============================================================
-- V20260603__add_cognitive_conflict.sql
-- v4.0 Phase 3 (F11.3): 认知冲突检测表
-- ============================================================

CREATE TABLE IF NOT EXISTS `cognitive_conflict` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` VARCHAR(64) NOT NULL COMMENT '用户ID',
    `description` TEXT NOT NULL COMMENT '冲突描述（自然语言）',
    `existing_belief` TEXT COMMENT '已有认知',
    `new_observation` TEXT COMMENT '新观察（矛盾的新输入）',
    `source` VARCHAR(16) NOT NULL COMMENT '来源: PERSONA/LIFEGRAPH',
    `resolved` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已澄清',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_cognitive_conflict_user` (`user_id`),
    KEY `idx_cognitive_conflict_resolved` (`user_id`, `resolved`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '认知冲突标记表';
