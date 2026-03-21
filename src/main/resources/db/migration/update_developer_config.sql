CREATE TABLE IF NOT EXISTS `developer_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` VARCHAR(255) NOT NULL COMMENT '用户业务ID',
    `api_key` VARCHAR(255) DEFAULT NULL COMMENT '生成的API Key',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_developer_config_user_id` (`user_id`),
    UNIQUE KEY `uk_developer_config_api_key` (`api_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '开发者配置表';
