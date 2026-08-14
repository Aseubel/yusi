CREATE TABLE IF NOT EXISTS `task_execution` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `task_id` VARCHAR(64) NOT NULL,
    `task_type` VARCHAR(32) NOT NULL,
    `owner_user_id` VARCHAR(64) DEFAULT NULL,
    `source_type` VARCHAR(32) NOT NULL,
    `source_id` VARCHAR(255) NOT NULL,
    `source_version` VARCHAR(128) DEFAULT NULL,
    `trigger_event_id` VARCHAR(64) DEFAULT NULL,
    `run_id` VARCHAR(64) DEFAULT NULL,
    `idempotency_key` VARCHAR(191) NOT NULL,
    `status` VARCHAR(24) NOT NULL,
    `retry_count` INT NOT NULL DEFAULT 0,
    `max_retries` INT NOT NULL DEFAULT 5,
    `failure_category` VARCHAR(24) DEFAULT NULL,
    `checkpoint_json` VARCHAR(2048) DEFAULT NULL,
    `claimed_by` VARCHAR(128) DEFAULT NULL,
    `claimed_at` DATETIME DEFAULT NULL,
    `next_attempt_at` DATETIME DEFAULT NULL,
    `completed_at` DATETIME DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `version` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_execution_task_id` (`task_id`),
    UNIQUE KEY `uk_task_execution_idempotency` (`idempotency_key`),
    KEY `idx_task_execution_owner_status` (`owner_user_id`, `status`),
    KEY `idx_task_execution_source` (`source_type`, `source_id`),
    KEY `idx_task_execution_status_attempt` (`status`, `next_attempt_at`),
    KEY `idx_task_execution_trigger` (`trigger_event_id`),
    KEY `idx_task_execution_run` (`run_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

ALTER TABLE `embedding_task`
    ADD COLUMN `task_execution_id` VARCHAR(64) DEFAULT NULL COMMENT '跨领域任务执行账本ID' AFTER `trigger_event_id`,
    ADD KEY `idx_embedding_task_execution` (`task_execution_id`);

ALTER TABLE `life_graph_task`
    ADD COLUMN `task_execution_id` VARCHAR(64) DEFAULT NULL COMMENT '跨领域任务执行账本ID' AFTER `trigger_event_id`,
    ADD KEY `idx_life_graph_task_execution` (`task_execution_id`);
