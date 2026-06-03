-- ============================================================
-- V20260603__add_agent_persona_and_memory_ttl.sql
-- v4.0 Phase 1: Agent 人格配置表 + 中期记忆有效期
-- ============================================================

-- 1. 创建 Agent 人格配置表
CREATE TABLE IF NOT EXISTS `agent_persona_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` VARCHAR(64) NOT NULL COMMENT '用户ID',
    `personality_style` VARCHAR(32) NOT NULL DEFAULT 'gentle' COMMENT '人格风格: gentle/lively/calm/rational',
    `proactive_frequency` VARCHAR(16) NOT NULL DEFAULT 'low' COMMENT '主动问候频率: off/low/normal',
    `quiet_hours_start` VARCHAR(8) DEFAULT NULL COMMENT '静默时段开始(HH:mm)',
    `quiet_hours_end` VARCHAR(8) DEFAULT NULL COMMENT '静默时段结束(HH:mm)',
    `anniversary_reminder_enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '纪念日提醒开关',
    `weekly_report_enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '周报开关',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_persona_config_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'Agent 人格配置表';

-- 2. mid_term_memory 添加有效期列
ALTER TABLE `mid_term_memory`
    ADD COLUMN `valid_until` DATETIME DEFAULT NULL COMMENT '记忆有效期截止时间，过期后自动降低权重' AFTER `updated_at`;

-- 3. 为已有记录设置默认有效期（创建时间 + 30 天）
UPDATE `mid_term_memory`
    SET `valid_until` = DATE_ADD(`created_at`, INTERVAL 30 DAY)
    WHERE `valid_until` IS NULL;

-- 4. 添加有效期索引，加速过期过滤查询
ALTER TABLE `mid_term_memory`
    ADD INDEX `idx_mid_term_memory_valid_until` (`user_id`, `valid_until`);
