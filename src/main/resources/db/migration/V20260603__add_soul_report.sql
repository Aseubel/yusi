-- ============================================================
-- V20260603__add_soul_report.sql
-- v4.0 Phase 3 (F8.3): 灵魂周报表
-- ============================================================

CREATE TABLE IF NOT EXISTS `soul_report` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` VARCHAR(64) NOT NULL COMMENT '用户ID',
    `report_type` VARCHAR(16) NOT NULL COMMENT '报告类型: WEEKLY/MONTHLY',
    `title` VARCHAR(200) NOT NULL COMMENT '报告标题',
    `content` TEXT NOT NULL COMMENT '报告正文（Markdown格式）',
    `period_start` DATE NOT NULL COMMENT '周期起始日期',
    `period_end` DATE NOT NULL COMMENT '周期结束日期',
    `notified` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已通知用户',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
    PRIMARY KEY (`id`),
    KEY `idx_soul_report_user` (`user_id`),
    KEY `idx_soul_report_user_type` (`user_id`, `report_type`),
    KEY `idx_soul_report_period` (`user_id`, `report_type`, `period_start`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '灵魂周报/月报表';
