ALTER TABLE `agent_tool_trace`
    ADD COLUMN `attempt_count` INT NOT NULL DEFAULT 1 COMMENT '物理工具尝试次数，仅记录数量' AFTER `capability_version`;
