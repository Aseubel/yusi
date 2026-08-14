CREATE TABLE IF NOT EXISTS `security_audit_event` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `event_id` VARCHAR(64) NOT NULL,
    `action` VARCHAR(64) NOT NULL,
    `actor_type` VARCHAR(16) NOT NULL,
    `actor_user_id` VARCHAR(64) DEFAULT NULL,
    `subject_user_id` VARCHAR(64) DEFAULT NULL,
    `resource_type` VARCHAR(32) NOT NULL,
    `resource_id` VARCHAR(255) DEFAULT NULL,
    `outcome` VARCHAR(16) NOT NULL,
    `reason_code` VARCHAR(64) DEFAULT NULL,
    `details_json` VARCHAR(1024) NOT NULL,
    `occurred_at` DATETIME NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_security_audit_event_id` (`event_id`),
    KEY `idx_security_audit_actor_time` (`actor_user_id`, `occurred_at`),
    KEY `idx_security_audit_subject_time` (`subject_user_id`, `occurred_at`),
    KEY `idx_security_audit_resource_time` (`resource_type`, `resource_id`, `occurred_at`),
    KEY `idx_security_audit_occurred_at` (`occurred_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '低敏感度安全审计事件';

CREATE TABLE IF NOT EXISTS `security_audit_event_scope` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `audit_event_id` BIGINT NOT NULL,
    `user_id` VARCHAR(64) NOT NULL,
    `scope_role` VARCHAR(32) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_security_audit_scope_event_user` (`audit_event_id`, `user_id`),
    KEY `idx_security_audit_scope_user_event` (`user_id`, `audit_event_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '安全审计用户可见范围';
