-- Account deletion retry ledger. This file is a change record for deployment
-- tooling; the application does not include Flyway runtime wiring.
CREATE TABLE IF NOT EXISTS `account_deletion_request` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `request_id` VARCHAR(64) NOT NULL,
    `target_user_ref` VARCHAR(128) DEFAULT NULL,
    `requested_by_ref` VARCHAR(128) DEFAULT NULL,
    `status` VARCHAR(24) NOT NULL,
    `retry_count` INT NOT NULL DEFAULT 0,
    `failure_category` VARCHAR(48) DEFAULT NULL,
    `created_at` DATETIME NOT NULL,
    `updated_at` DATETIME NOT NULL,
    `completed_at` DATETIME DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_account_deletion_request_id` (`request_id`),
    KEY `idx_account_deletion_target_status` (`target_user_ref`, `status`),
    KEY `idx_account_deletion_status_updated` (`status`, `updated_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
