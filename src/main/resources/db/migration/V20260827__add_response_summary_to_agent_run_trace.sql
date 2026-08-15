ALTER TABLE `agent_run_trace`
    ADD COLUMN `response_char_count` BIGINT NOT NULL DEFAULT 0
        COMMENT '助手响应 Unicode code point 数量，不保存响应内容'
        AFTER `tool_count`;
