CREATE TABLE IF NOT EXISTS `web_access_policy` (
    `id` BIGINT NOT NULL COMMENT '固定单例主键，始终为 1',
    `development_mode_enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否开启本地开发 Origin 模式',
    `development_mode_expires_at` DATETIME DEFAULT NULL COMMENT '开发模式自动失效时间',
    `allowed_origins` LONGTEXT NOT NULL COMMENT '额外允许的浏览器 Origin JSON 数组',
    `blocked_origins` LONGTEXT NOT NULL COMMENT '拒绝的浏览器 Origin JSON 数组',
    `allowed_ip_rules` LONGTEXT NOT NULL COMMENT '允许的 IP/CIDR JSON 数组',
    `blocked_ip_rules` LONGTEXT NOT NULL COMMENT '拒绝的 IP/CIDR JSON 数组',
    `version` BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `updated_by` VARCHAR(64) DEFAULT NULL COMMENT '最后操作管理员',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '运行时 Web Origin/IP 访问策略';
