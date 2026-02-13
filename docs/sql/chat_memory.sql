-- Chat Memory Storage
CREATE TABLE IF NOT EXISTS `chat_memory_message` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `memory_id` varchar(64) NOT NULL COMMENT '记忆ID (通常为 userId)',
  `role` varchar(20) NOT NULL COMMENT '消息角色 (USER, AI, SYSTEM, TOOL_EXECUTION, TOOL_RESULT)',
  `content` text NOT NULL COMMENT '消息内容',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_memory_id_created` (`memory_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话记忆存储表';
