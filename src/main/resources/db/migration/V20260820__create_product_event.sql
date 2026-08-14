-- ============================================================
-- V20260820__create_product_event.sql
-- Phase 2/3 shared foundation: durable product event envelope
-- ============================================================

CREATE TABLE IF NOT EXISTS `product_event` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `event_id` VARCHAR(64) NOT NULL COMMENT '服务端生成的产品事件 ID',
    `event_name` VARCHAR(64) NOT NULL COMMENT '稳定产品事件名',
    `schema_version` INT NOT NULL DEFAULT 1 COMMENT '事件结构版本',
    `user_id` VARCHAR(64) NOT NULL COMMENT '事件所属用户',
    `actor_user_id` VARCHAR(64) DEFAULT NULL COMMENT '操作者用户 ID',
    `session_id` VARCHAR(64) DEFAULT NULL COMMENT '会话 ID',
    `run_id` VARCHAR(64) DEFAULT NULL COMMENT 'AgentRun 或任务运行 ID',
    `match_id` BIGINT DEFAULT NULL COMMENT '匹配记录 ID',
    `connection_id` BIGINT DEFAULT NULL COMMENT '独立连接 ID',
    `situation_id` VARCHAR(64) DEFAULT NULL COMMENT '情景 ID',
    `source` VARCHAR(32) NOT NULL COMMENT '事件来源域',
    `sensitivity` VARCHAR(16) NOT NULL COMMENT '敏感级别: LOW/RESTRICTED/SECURITY',
    `idempotency_key` VARCHAR(160) NOT NULL COMMENT '业务幂等键',
    `payload_json` JSON NOT NULL COMMENT '低敏结构化载荷，不保存正文',
    `occurred_at` DATETIME NOT NULL COMMENT '服务端记录的发生时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_event_event_id` (`event_id`),
    UNIQUE KEY `uk_product_event_idempotency` (`idempotency_key`),
    KEY `idx_product_event_user_time` (`user_id`, `occurred_at`),
    KEY `idx_product_event_source_time` (`source`, `occurred_at`),
    KEY `idx_product_event_match` (`match_id`, `occurred_at`),
    KEY `idx_product_event_connection` (`connection_id`, `occurred_at`),
    KEY `idx_product_event_run` (`run_id`, `occurred_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '低敏产品事件信封';

CREATE TABLE IF NOT EXISTS `product_event_scope` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `event_id` VARCHAR(64) NOT NULL COMMENT '产品事件 ID',
    `user_id` VARCHAR(64) NOT NULL COMMENT '允许读取该事件的用户',
    `scope_role` VARCHAR(32) NOT NULL COMMENT '访问范围角色',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_event_scope_event_user` (`event_id`, `user_id`),
    KEY `idx_product_event_scope_user_event` (`user_id`, `event_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '产品事件用户访问范围';
