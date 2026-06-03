-- ============================================================
-- V20260603__add_match_feedback.sql
-- v4.0 Phase 2: 匹配反馈循环 + 匹配后引导
-- ============================================================

-- 1. 创建匹配反馈表
CREATE TABLE IF NOT EXISTS `match_feedback` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `match_id` BIGINT NOT NULL COMMENT '关联的匹配记录ID',
    `user_id` VARCHAR(64) NOT NULL COMMENT '用户ID',
    `action` VARCHAR(16) NOT NULL COMMENT '反馈动作: ACCEPT/SKIP/INTERACT/REPORT',
    `interaction_depth` INT DEFAULT NULL COMMENT '互动深度（消息条数，仅INTERACT时有效）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '反馈时间',
    PRIMARY KEY (`id`),
    KEY `idx_match_feedback_user` (`user_id`),
    KEY `idx_match_feedback_match` (`match_id`),
    KEY `idx_match_feedback_action` (`user_id`, `action`),
    KEY `idx_match_feedback_created` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '匹配反馈记录表';

-- 2. 创建共鸣信号表（F9.2）
CREATE TABLE IF NOT EXISTS `resonance_signal` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `from_user_id` VARCHAR(64) NOT NULL COMMENT '发送方用户ID',
    `to_user_id` VARCHAR(64) NOT NULL COMMENT '接收方用户ID',
    `card_id` BIGINT DEFAULT NULL COMMENT '触发共鸣的广场帖子ID',
    `message` VARCHAR(200) DEFAULT NULL COMMENT '附言（匿名，不超过100字）',
    `is_read` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '接收方是否已读',
    `is_mutual` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否相互共鸣',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_resonance_signal_pair` (`from_user_id`, `to_user_id`),
    KEY `idx_resonance_signal_to_user` (`to_user_id`, `created_at`),
    KEY `idx_resonance_signal_from_user` (`from_user_id`),
    KEY `idx_resonance_signal_mutual` (`is_mutual`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '广场共鸣信号表';
