-- ============================================================
-- V20260819__add_phase2_correlation_ids.sql
-- Phase 2: domain record correlation IDs
-- ============================================================

ALTER TABLE `soul_match`
    ADD COLUMN `generation_run_id` VARCHAR(64) DEFAULT NULL COMMENT '生成本次推荐的运行 ID' AFTER `id`,
    ADD COLUMN `recommendation_event_id` VARCHAR(64) DEFAULT NULL COMMENT '推荐产品事件 ID' AFTER `generation_run_id`,
    ADD KEY `idx_soul_match_generation_run` (`generation_run_id`),
    ADD KEY `idx_soul_match_recommendation_event` (`recommendation_event_id`);

ALTER TABLE `soul_message`
    ADD COLUMN `connection_id` BIGINT DEFAULT NULL COMMENT '独立连接 ID' AFTER `match_id`,
    ADD COLUMN `run_id` VARCHAR(64) DEFAULT NULL COMMENT '关联的 AgentRun ID' AFTER `connection_id`,
    ADD COLUMN `source_event_id` VARCHAR(64) DEFAULT NULL COMMENT '来源产品事件 ID' AFTER `run_id`,
    ADD KEY `idx_soul_message_connection` (`connection_id`),
    ADD KEY `idx_soul_message_source_event` (`source_event_id`);

ALTER TABLE `chat_memory_message`
    ADD COLUMN `run_id` VARCHAR(64) DEFAULT NULL COMMENT '关联的 AgentRun ID' AFTER `memory_id`,
    ADD COLUMN `source_event_id` VARCHAR(64) DEFAULT NULL COMMENT '来源产品事件 ID' AFTER `run_id`,
    ADD KEY `idx_chat_memory_message_run` (`run_id`),
    ADD KEY `idx_chat_memory_message_source_event` (`source_event_id`);

ALTER TABLE `match_feedback`
    ADD COLUMN `source_event_id` VARCHAR(64) DEFAULT NULL COMMENT '来源产品事件 ID' AFTER `connection_id`,
    ADD COLUMN `idempotency_key` VARCHAR(128) DEFAULT NULL COMMENT '反馈写入幂等键' AFTER `source_event_id`,
    ADD KEY `idx_match_feedback_source_event` (`source_event_id`),
    ADD KEY `idx_match_feedback_idempotency` (`idempotency_key`);

ALTER TABLE `user_notification`
    ADD COLUMN `source_event_id` VARCHAR(64) DEFAULT NULL COMMENT '触发通知的产品事件 ID' AFTER `announcement_id`,
    ADD KEY `idx_notification_source_event` (`source_event_id`);

ALTER TABLE `soul_report`
    ADD COLUMN `generation_run_id` VARCHAR(64) DEFAULT NULL COMMENT '报告生成运行 ID' AFTER `id`,
    ADD COLUMN `task_execution_id` VARCHAR(64) DEFAULT NULL COMMENT '任务执行账本 ID' AFTER `generation_run_id`,
    ADD KEY `idx_soul_report_generation_run` (`generation_run_id`),
    ADD KEY `idx_soul_report_task_execution` (`task_execution_id`);
