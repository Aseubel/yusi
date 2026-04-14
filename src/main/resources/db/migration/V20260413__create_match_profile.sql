CREATE TABLE IF NOT EXISTS `match_profile` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` VARCHAR(64) NOT NULL COMMENT '用户ID',
    `profile_text` TEXT NULL COMMENT '匹配画像全文',
    `life_graph_summary` TEXT NULL COMMENT '长期结构摘要',
    `persona_summary` TEXT NULL COMMENT '稳定偏好摘要',
    `mid_memory_summary` TEXT NULL COMMENT '近期状态摘要',
    `version` BIGINT NOT NULL DEFAULT 0 COMMENT '画像版本号',
    `updated_at` DATETIME NULL COMMENT '最近更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_match_profile_user_id` (`user_id`),
    KEY `idx_match_profile_updated_at` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一Agent匹配画像表';
