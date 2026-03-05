CREATE TABLE IF NOT EXISTS `model_runtime_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `config_key` VARCHAR(64) NOT NULL DEFAULT 'active' COMMENT '配置标识（默认 active）',
    `config_json` LONGTEXT NOT NULL COMMENT '模型治理运行时全量配置(JSON)',
    `version` BIGINT NOT NULL DEFAULT 1 COMMENT '配置版本号',
    `operator_id` VARCHAR(64) DEFAULT NULL COMMENT '操作人ID',
    `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_model_runtime_config_key` (`config_key`),
    KEY `idx_model_runtime_config_updated_at` (`updated_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '模型治理运行时配置表';

CREATE TABLE IF NOT EXISTS `model_config_change_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `change_id` VARCHAR(64) NOT NULL COMMENT '变更唯一标识',
    `operator_id` VARCHAR(64) DEFAULT NULL COMMENT '操作人ID',
    `action` VARCHAR(32) NOT NULL COMMENT '变更动作: UPDATE_CONFIG/SWITCH_STRATEGY/ROLLBACK',
    `group_name` VARCHAR(64) DEFAULT NULL COMMENT '分组名(策略切换时使用)',
    `before_json` JSON DEFAULT NULL COMMENT '变更前快照(JSON)',
    `after_json` JSON DEFAULT NULL COMMENT '变更后快照(JSON)',
    `success` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否成功: 1-成功,0-失败',
    `error_message` VARCHAR(512) DEFAULT NULL COMMENT '失败原因',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_model_config_change_log_change_id` (`change_id`),
    KEY `idx_model_config_change_log_operator` (`operator_id`),
    KEY `idx_model_config_change_log_action` (`action`),
    KEY `idx_model_config_change_log_created_at` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '模型治理配置变更日志表';
