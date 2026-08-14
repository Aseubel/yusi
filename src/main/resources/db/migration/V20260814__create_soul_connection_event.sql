-- ============================================================
-- V20260814__create_soul_connection_event.sql
-- Phase 2: 连接生命周期产品事件 ID 与状态变更历史
-- ============================================================

CREATE TABLE IF NOT EXISTS `soul_connection_event` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `event_id` VARCHAR(64) NOT NULL COMMENT '服务端生成的产品事件 ID',
    `event_name` VARCHAR(64) NOT NULL COMMENT '稳定产品事件名',
    `schema_version` INT NOT NULL DEFAULT 1 COMMENT '事件结构版本',
    `connection_id` BIGINT NOT NULL COMMENT '独立连接 ID',
    `match_id` BIGINT NOT NULL COMMENT '原始匹配 ID',
    `actor_user_id` VARCHAR(64) DEFAULT NULL COMMENT '操作者用户 ID',
    `from_status` VARCHAR(32) DEFAULT NULL COMMENT '变更前连接状态',
    `to_status` VARCHAR(32) NOT NULL COMMENT '变更后连接状态',
    `action` VARCHAR(32) NOT NULL COMMENT '连接动作',
    `reason_category` VARCHAR(64) DEFAULT NULL COMMENT '低敏原因类别',
    `occurred_at` DATETIME NOT NULL COMMENT '事件发生时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_soul_connection_event_event_id` (`event_id`),
    KEY `idx_soul_connection_event_connection_time` (`connection_id`, `occurred_at`),
    KEY `idx_soul_connection_event_match_time` (`match_id`, `occurred_at`),
    KEY `idx_soul_connection_event_actor_time` (`actor_user_id`, `occurred_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '灵魂连接产品事件历史';
