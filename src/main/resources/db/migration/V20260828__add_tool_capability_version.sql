ALTER TABLE `agent_tool_trace`
    ADD COLUMN `capability_version` VARCHAR(32) DEFAULT NULL COMMENT '工具能力契约版本' AFTER `tool_source`;
