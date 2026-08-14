ALTER TABLE `model_call_trace`
    ADD COLUMN `prompt_key` VARCHAR(64) DEFAULT NULL COMMENT 'Prompt稳定标识' AFTER `scene`,
    ADD COLUMN `prompt_version` VARCHAR(64) DEFAULT NULL COMMENT 'Prompt版本' AFTER `prompt_key`,
    ADD COLUMN `prompt_locale` VARCHAR(16) DEFAULT NULL COMMENT 'Prompt语言' AFTER `prompt_version`,
    ADD KEY `idx_model_call_trace_prompt_version` (`prompt_key`, `prompt_version`, `created_at`);
