#!/usr/bin/env bash
# 在本地 yusi 库补建 account_deletion_request 表（init.sql 已同步）
docker exec -i mysql mysql -uroot -proot123456 yusi 2>/dev/null <<'SQL'
CREATE TABLE IF NOT EXISTS `account_deletion_request` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `request_id` VARCHAR(64) NOT NULL COMMENT '注销请求 ID',
    `target_user_ref` VARCHAR(128) DEFAULT NULL COMMENT '目标用户引用',
    `requested_by_ref` VARCHAR(128) DEFAULT NULL COMMENT '发起人引用',
    `status` VARCHAR(24) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/RUNNING/PENDING_RETRY/SUPERSEDED/COMPLETED',
    `retry_count` INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    `failure_category` VARCHAR(48) DEFAULT NULL COMMENT '低敏失败分类',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `completed_at` DATETIME DEFAULT NULL COMMENT '完成时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_account_deletion_request_id` (`request_id`),
    KEY `idx_account_deletion_target_status` (`target_user_ref`, `status`),
    KEY `idx_account_deletion_status_updated` (`status`, `updated_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '账号注销请求台账';
SHOW TABLES LIKE 'account_deletion_request';
SQL
