ALTER TABLE `model_call_trace`
    ADD COLUMN IF NOT EXISTS `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT '可选租户ID' AFTER `user_id`;
