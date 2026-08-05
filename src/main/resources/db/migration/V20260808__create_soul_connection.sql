-- ============================================================
-- V20260808__create_soul_connection.sql
-- Phase 2: 独立连接生命周期与安全反馈关联
-- ============================================================

CREATE TABLE IF NOT EXISTS `soul_connection` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '连接 ID',
    `match_id` BIGINT NOT NULL COMMENT '原始匹配 ID',
    `user_a_id` VARCHAR(64) NOT NULL COMMENT '用户 A ID',
    `user_b_id` VARCHAR(64) NOT NULL COMMENT '用户 B ID',
    `status` VARCHAR(32) NOT NULL COMMENT '连接状态',
    `started_at` DATETIME DEFAULT NULL COMMENT '开始互动时间',
    `ended_at` DATETIME DEFAULT NULL COMMENT '结束或阻断时间',
    `last_action` VARCHAR(32) DEFAULT NULL COMMENT '最近一次连接动作',
    `last_action_by` VARCHAR(64) DEFAULT NULL COMMENT '最近动作操作者',
    `reason_category` VARCHAR(64) DEFAULT NULL COMMENT '结束或安全原因类别',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_soul_connection_match_id` (`match_id`),
    KEY `idx_soul_connection_user_a` (`user_a_id`),
    KEY `idx_soul_connection_user_b` (`user_b_id`),
    KEY `idx_soul_connection_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '灵魂连接生命周期表';

ALTER TABLE `match_feedback`
    ADD COLUMN `connection_id` BIGINT DEFAULT NULL COMMENT '独立连接 ID' AFTER `match_id`,
    MODIFY COLUMN `action` VARCHAR(32) NOT NULL COMMENT '反馈动作或连接反馈类别',
    ADD KEY `idx_match_feedback_connection_user` (`connection_id`, `user_id`, `action`);

INSERT INTO `soul_connection`
    (`match_id`, `user_a_id`, `user_b_id`, `status`, `started_at`, `ended_at`, `last_action`, `created_at`, `updated_at`)
SELECT
    `id`,
    `user_a_id`,
    `user_b_id`,
    CASE
        WHEN `is_matched` = 1 OR (`status_a` = 1 AND `status_b` = 1) THEN 'STARTED'
        WHEN `status_a` = 2 OR `status_b` = 2 THEN 'DECLINED'
        ELSE 'WAITING_REPLY'
    END,
    CASE WHEN `is_matched` = 1 OR (`status_a` = 1 AND `status_b` = 1) THEN COALESCE(`update_time`, `create_time`) END,
    CASE WHEN `status_a` = 2 OR `status_b` = 2 THEN COALESCE(`update_time`, `create_time`) END,
    CASE
        WHEN `is_matched` = 1 OR (`status_a` = 1 AND `status_b` = 1) THEN 'MIGRATED_MATCH'
        WHEN `status_a` = 2 OR `status_b` = 2 THEN 'MIGRATED_DECLINE'
        ELSE 'MIGRATED_ACCEPT'
    END,
    COALESCE(`create_time`, CURRENT_TIMESTAMP),
    COALESCE(`update_time`, CURRENT_TIMESTAMP)
FROM `soul_match`
WHERE (`status_a` <> 0 OR `status_b` <> 0 OR `is_matched` = 1)
  AND NOT EXISTS (
      SELECT 1 FROM `soul_connection` c WHERE c.`match_id` = `soul_match`.`id`
  );

UPDATE `match_feedback` f
JOIN `soul_connection` c ON c.`match_id` = f.`match_id`
SET f.`connection_id` = c.`id`
WHERE f.`connection_id` IS NULL;
