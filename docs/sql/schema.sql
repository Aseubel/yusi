SET NAMES utf8mb4;

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `user`;

CREATE TABLE `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` VARCHAR(255) DEFAULT NULL COMMENT '用户业务ID',
    `username` VARCHAR(255) DEFAULT NULL COMMENT '用户名',
    `password` VARCHAR(255) DEFAULT NULL COMMENT '密码',
    `email` VARCHAR(255) DEFAULT NULL COMMENT '邮箱',
    `is_match_enabled` TINYINT(1) DEFAULT NULL COMMENT '是否开启匹配',
    `match_intent` VARCHAR(255) DEFAULT NULL COMMENT '匹配意图',
    `permission_level` INT NOT NULL DEFAULT 0 COMMENT '权限等级',
    `key_mode` VARCHAR(255) DEFAULT NULL COMMENT '密钥模式: DEFAULT(默认服务端密钥) / CUSTOM(用户自定义密钥)',
    `has_cloud_backup` TINYINT(1) DEFAULT NULL COMMENT '是否开启云端密钥备份（仅CUSTOM模式有效）',
    `encrypted_backup_key` VARCHAR(1024) DEFAULT NULL COMMENT '云端备份的加密密钥（使用管理员公钥加密）',
    `key_salt` VARCHAR(255) DEFAULT NULL COMMENT '密钥派生盐值（用于PBKDF2/Argon2）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_user_id` (`user_id`),
    UNIQUE KEY `uk_user_username` (`username`),
    KEY `idx_user_permission_level` (`permission_level`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户信息表';

DROP TABLE IF EXISTS `diary`;

CREATE TABLE `diary` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `diary_id` VARCHAR(255) DEFAULT NULL COMMENT '日记业务ID',
    `user_id` VARCHAR(255) DEFAULT NULL COMMENT '用户ID',
    `title` VARCHAR(255) DEFAULT NULL COMMENT '标题',
    `content` TEXT COMMENT '日记内容（前端加密后的密文）',
    `client_encrypted` TINYINT(1) DEFAULT NULL COMMENT '是否为客户端加密内容',
    `visibility` TINYINT(1) DEFAULT NULL COMMENT '可见性',
    `entry_date` DATE DEFAULT NULL COMMENT '日记日期',
    `ai_analysis_status` INT DEFAULT NULL COMMENT 'AI分析状态',
    `ai_response` VARCHAR(1000) DEFAULT NULL COMMENT 'AI回复',
    `emotion` VARCHAR(32) DEFAULT NULL COMMENT '情感分析结果',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `latitude` DOUBLE DEFAULT NULL COMMENT '纬度',
    `longitude` DOUBLE DEFAULT NULL COMMENT '经度',
    `address` VARCHAR(255) DEFAULT NULL COMMENT '详细地址',
    `place_name` VARCHAR(255) DEFAULT NULL COMMENT '地点名称（如：星巴克咖啡、公司）',
    `place_id` VARCHAR(255) DEFAULT NULL COMMENT '地图 POI ID（用于去重和关联）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_diary_diary_id` (`diary_id`),
    KEY `idx_diary_user_id` (`user_id`),
    KEY `idx_diary_entry_date` (`entry_date`),
    KEY `idx_diary_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户日记表';

DROP TABLE IF EXISTS `interface_daily_usage`;

CREATE TABLE `interface_daily_usage` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` VARCHAR(64) NOT NULL COMMENT '用户ID',
    `ip` VARCHAR(64) NOT NULL COMMENT 'IP地址',
    `interface_name` VARCHAR(128) NOT NULL COMMENT '接口名称',
    `usage_date` DATE NOT NULL COMMENT '使用日期',
    `request_count` BIGINT NOT NULL COMMENT '请求次数',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_ip_interface_date` (
        `user_id`,
        `ip`,
        `interface_name`,
        `usage_date`
    ),
    KEY `idx_interface_daily_usage_date` (`usage_date`),
    KEY `idx_interface_daily_usage_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户接口每日调用统计';

DROP TABLE IF EXISTS `prompt_template`;

CREATE TABLE `prompt_template` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name` VARCHAR(255) NOT NULL COMMENT '模板唯一名称',
    `template` TEXT NOT NULL COMMENT '模板内容',
    `version` VARCHAR(64) NOT NULL DEFAULT 'v1' COMMENT '版本号',
    `active` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否激活',
    `scope` VARCHAR(64) NOT NULL DEFAULT 'global' COMMENT '适用范围: diary/match/room/plaza/admin/global',
    `locale` VARCHAR(16) NOT NULL DEFAULT 'zh-CN' COMMENT '语言区域，如 zh-CN/en-US',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '描述',
    `tags` VARCHAR(255) DEFAULT NULL COMMENT '标签，逗号分隔',
    `is_default` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为该scope默认模板',
    `priority` INT NOT NULL DEFAULT 0 COMMENT '匹配优先级（越大越优先）',
    `updated_by` VARCHAR(64) DEFAULT NULL COMMENT '最后更新人',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_prompt_template_name_locale` (`name`, `locale`),
    KEY `idx_prompt_scope_active_default` (
        `scope`,
        `active`,
        `is_default`
    ),
    KEY `idx_prompt_updated_at` (`updated_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'Prompt模板表';

DROP TABLE IF EXISTS `room_message`;

CREATE TABLE `room_message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `room_code` VARCHAR(32) NOT NULL COMMENT '房间代码',
    `sender_id` VARCHAR(64) NOT NULL COMMENT '发送者ID',
    `sender_name` VARCHAR(64) DEFAULT NULL COMMENT '发送者昵称',
    `content` TEXT NOT NULL COMMENT '消息内容',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_room_code` (`room_code`),
    KEY `idx_created_at` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '房间临时聊天消息';

DROP TABLE IF EXISTS `situation_scenario`;

CREATE TABLE `situation_scenario` (
    `id` VARCHAR(32) NOT NULL COMMENT '剧本ID',
    `title` VARCHAR(100) DEFAULT NULL COMMENT '标题',
    `description` TEXT COMMENT '描述',
    `submitter_id` VARCHAR(255) DEFAULT NULL COMMENT '提交者ID',
    `reject_reason` TEXT COMMENT '拒绝理由',
    `status` INT DEFAULT 0 COMMENT '状态: 0-待审核/1-人工拒绝/2-AI 审核拒绝/3-AI 审核通过/4-人工通过',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '情境剧本表';

DROP TABLE IF EXISTS `situation_room`;

CREATE TABLE `situation_room` (
    `code` VARCHAR(32) NOT NULL COMMENT '房间代码',
    `status` VARCHAR(20) DEFAULT NULL COMMENT '房间状态',
    `owner_id` VARCHAR(255) DEFAULT NULL COMMENT '房主ID',
    `scenario_id` VARCHAR(255) DEFAULT NULL COMMENT '剧本ID',
    `members` TEXT COMMENT '成员列表',
    `submissions` TEXT COMMENT '提交内容',
    `submission_visibility` TEXT COMMENT '提交可见性',
    `cancel_votes` TEXT COMMENT '取消投票',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `report` TEXT COMMENT '情境报告',
    PRIMARY KEY (`code`),
    KEY `idx_situation_room_owner_id` (`owner_id`),
    KEY `idx_situation_room_status` (`status`),
    KEY `idx_situation_room_created_at` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '情境房间表';

DROP TABLE IF EXISTS `soul_card`;

CREATE TABLE `soul_card` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `content` TEXT COMMENT '卡片内容',
    `origin_id` VARCHAR(255) DEFAULT NULL COMMENT '来源ID (Diary ID or Situation Room Code)',
    `user_id` VARCHAR(255) DEFAULT NULL COMMENT '作者ID',
    `type` VARCHAR(255) DEFAULT NULL COMMENT '卡片类型',
    `emotion` VARCHAR(255) DEFAULT NULL COMMENT 'AI分析的主要情感/主题',
    `resonance_count` INT DEFAULT NULL COMMENT '共鸣次数',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_soul_card_user_id` (`user_id`),
    KEY `idx_soul_card_created_at` (`created_at`),
    KEY `idx_soul_card_emotion` (`emotion`),
    KEY `idx_soul_card_origin_id` (`origin_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '心灵卡片表';

DROP TABLE IF EXISTS `soul_match`;

CREATE TABLE `soul_match` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_a_id` VARCHAR(255) DEFAULT NULL COMMENT '用户A ID',
    `user_b_id` VARCHAR(255) DEFAULT NULL COMMENT '用户B ID',
    `letter_a_to_b` VARCHAR(2000) DEFAULT NULL COMMENT 'A写给B的信',
    `letter_b_to_a` VARCHAR(2000) DEFAULT NULL COMMENT 'B写给A的信',
    `status_a` INT DEFAULT NULL COMMENT '用户A状态 (0: Pending, 1: Interested, 2: Skipped)',
    `status_b` INT DEFAULT NULL COMMENT '用户B状态 (0: Pending, 1: Interested, 2: Skipped)',
    `is_matched` TINYINT(1) DEFAULT NULL COMMENT '是否匹配成功 (True if both Interested)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_soul_match_user_a` (`user_a_id`),
    KEY `idx_soul_match_user_b` (`user_b_id`),
    KEY `idx_soul_match_is_matched` (`is_matched`),
    KEY `idx_soul_match_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '灵魂匹配表';

DROP TABLE IF EXISTS `soul_message`;

CREATE TABLE `soul_message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `match_id` BIGINT DEFAULT NULL COMMENT '匹配ID',
    `sender_id` VARCHAR(255) DEFAULT NULL COMMENT '发送者ID',
    `receiver_id` VARCHAR(255) DEFAULT NULL COMMENT '接收者ID',
    `content` VARCHAR(1000) DEFAULT NULL COMMENT '消息内容',
    `is_read` TINYINT(1) DEFAULT NULL COMMENT '是否已读',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_soul_message_match_id` (`match_id`),
    KEY `idx_soul_message_receiver_id` (`receiver_id`),
    KEY `idx_soul_message_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '匹配用户聊天消息表';

DROP TABLE IF EXISTS `soul_resonance`;

CREATE TABLE `soul_resonance` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `card_id` BIGINT DEFAULT NULL COMMENT '卡片ID',
    `user_id` VARCHAR(255) DEFAULT NULL COMMENT '用户ID',
    `type` VARCHAR(255) DEFAULT NULL COMMENT '共鸣类型',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_soul_resonance_card_user` (`card_id`, `user_id`),
    KEY `idx_soul_resonance_user_id` (`user_id`),
    KEY `idx_soul_resonance_created_at` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '心灵共鸣表';

DROP TABLE IF EXISTS `user_location`;

CREATE TABLE `user_location` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `location_id` VARCHAR(255) DEFAULT NULL COMMENT '地点业务ID',
    `user_id` VARCHAR(255) DEFAULT NULL COMMENT '用户ID',
    `name` VARCHAR(255) DEFAULT NULL COMMENT '用户自定义名称（如：家、公司）',
    `latitude` DOUBLE DEFAULT NULL COMMENT '纬度',
    `longitude` DOUBLE DEFAULT NULL COMMENT '经度',
    `address` VARCHAR(255) DEFAULT NULL COMMENT '详细地址',
    `place_id` VARCHAR(255) DEFAULT NULL COMMENT '地图 POI ID',
    `location_type` VARCHAR(255) DEFAULT NULL COMMENT '地点类型：FREQUENT（常用地点）/ IMPORTANT（重要地点）',
    `icon` VARCHAR(255) DEFAULT NULL COMMENT '图标标识',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_location_location_id` (`location_id`),
    KEY `idx_user_location_user_id` (`user_id`),
    KEY `idx_user_location_place_id` (`place_id`),
    KEY `idx_user_location_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户保存的地点表';

DROP TABLE IF EXISTS `embedding_task`;
-- Milvus Embedding 任务表
-- 用于可靠的异步批量处理日记向量化
-- 任务与日记保存在同一事务中，确保不会丢失
CREATE TABLE `embedding_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `diary_id` VARCHAR(255) NOT NULL COMMENT '关联的日记业务ID',
    `user_id` VARCHAR(255) NOT NULL COMMENT '关联的用户ID',
    `task_type` VARCHAR(32) NOT NULL COMMENT '任务类型: UPSERT(新增/修改) / DELETE(删除)',
    `status` VARCHAR(32) NOT NULL COMMENT '任务状态: PENDING/PROCESSING/COMPLETED/FAILED',
    `retry_count` INT DEFAULT 0 COMMENT '重试次数',
    `max_retries` INT DEFAULT 5 COMMENT '最大重试次数',
    `error_message` VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `next_retry_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '下次重试时间',
    PRIMARY KEY (`id`),
    KEY `idx_embedding_task_status` (`status`),
    KEY `idx_embedding_task_diary_id` (`diary_id`),
    KEY `idx_embedding_task_next_retry` (`next_retry_at`),
    KEY `idx_embedding_task_created` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'Milvus Embedding 任务表';

DROP TABLE IF EXISTS `life_graph_entity`;
-- Life Graph (GraphRAG) - 用户人生图谱
CREATE TABLE `life_graph_entity` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` VARCHAR(64) NOT NULL COMMENT '用户ID',
    `type` VARCHAR(32) NOT NULL COMMENT '实体类型: Person/Event/Place/Emotion/Topic/Item/User',
    `name_norm` VARCHAR(255) NOT NULL COMMENT '归一化名称（用于去重与消歧）',
    `display_name` VARCHAR(255) NOT NULL COMMENT '展示名称',
    `mention_count` INT NOT NULL DEFAULT 0 COMMENT '提及次数',
    `relation_count` INT DEFAULT 0 COMMENT '关系数量',
    `summary` VARCHAR(512) DEFAULT NULL COMMENT '实体摘要',
    `first_mention_date` DATE DEFAULT NULL COMMENT '首次出现日期',
    `last_mention_at` DATETIME DEFAULT NULL COMMENT '最后一次出现时间',
    `props` JSON DEFAULT NULL COMMENT '扩展属性(JSON): emotion/frequency/coordinates/address/last_interaction 等',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_life_graph_entity_user_type_norm` (
        `user_id`,
        `type`,
        `name_norm`
    ),
    KEY `idx_life_graph_entity_user_type` (`user_id`, `type`),
    KEY `idx_life_graph_entity_user_mentions` (`user_id`, `mention_count`),
    KEY `idx_life_graph_entity_updated` (`updated_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '人生图谱实体表';

DROP TABLE IF EXISTS `life_graph_entity_alias`;

CREATE TABLE `life_graph_entity_alias` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` VARCHAR(64) NOT NULL COMMENT '用户ID',
    `entity_id` BIGINT NOT NULL COMMENT '实体ID',
    `alias_norm` VARCHAR(255) NOT NULL COMMENT '归一化别名（唯一）',
    `alias_display` VARCHAR(255) NOT NULL COMMENT '展示别名',
    `confidence` DECIMAL(4, 3) NOT NULL DEFAULT 0.800 COMMENT '别名映射置信度(0-1)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_life_graph_alias_user_norm` (`user_id`, `alias_norm`),
    KEY `idx_life_graph_alias_user_entity` (`user_id`, `entity_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '人生图谱实体别名表';

DROP TABLE IF EXISTS `life_graph_relation`;
-- 关系边表：逻辑上视为无向边，写入时建议保持 source_id < target_id 用于去重；查询时通过 UNION ALL 做双向展开
CREATE TABLE `life_graph_relation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` VARCHAR(64) NOT NULL COMMENT '用户ID',
    `source_id` BIGINT NOT NULL COMMENT '实体ID（较小）',
    `target_id` BIGINT NOT NULL COMMENT '实体ID（较大）',
    `type` VARCHAR(64) NOT NULL COMMENT '关系类型: RELATED_TO/HAPPENED_AT/TRIGGERED/PARTICIPATED/MENTIONED_IN 等',
    `confidence` DECIMAL(4, 3) NOT NULL DEFAULT 0.800 COMMENT '关系置信度(0-1): LLM 0.8, 人工 1.0',
    `weight` INT NOT NULL DEFAULT 1 COMMENT '关系权重（共现/重复次数累积）',
    `first_seen` DATETIME DEFAULT NULL COMMENT '首次出现时间',
    `last_seen` DATETIME DEFAULT NULL COMMENT '最后出现时间',
    `evidence_diary_id` VARCHAR(255) DEFAULT NULL COMMENT '证据日记业务ID（可为空）',
    `props` JSON DEFAULT NULL COMMENT '扩展属性(JSON): emotion/coordinates/cause 等',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_life_graph_relation_user_edge` (
        `user_id`,
        `source_id`,
        `target_id`,
        `type`
    ),
    KEY `idx_life_graph_relation_user_source` (`user_id`, `source_id`),
    KEY `idx_life_graph_relation_user_target` (`user_id`, `target_id`),
    KEY `idx_life_graph_relation_user_type` (`user_id`, `type`),
    KEY `idx_life_graph_relation_updated` (`updated_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '人生图谱关系表';

DROP TABLE IF EXISTS `life_graph_mention`;

CREATE TABLE `life_graph_mention` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` VARCHAR(64) NOT NULL COMMENT '用户ID',
    `entity_id` BIGINT NOT NULL COMMENT '实体ID',
    `diary_id` VARCHAR(255) NOT NULL COMMENT '日记业务ID',
    `entry_date` DATE DEFAULT NULL COMMENT '日记日期',
    `snippet` VARCHAR(1000) DEFAULT NULL COMMENT '证据片段（截断）',
    `props` JSON DEFAULT NULL COMMENT '扩展属性(JSON): offsets 等',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_life_graph_mention_user_entity` (`user_id`, `entity_id`),
    KEY `idx_life_graph_mention_user_diary` (`user_id`, `diary_id`),
    KEY `idx_life_graph_mention_entry_date` (`entry_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '人生图谱实体证据表';

DROP TABLE IF EXISTS `life_graph_task`;

CREATE TABLE `life_graph_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `diary_id` VARCHAR(255) NOT NULL COMMENT '关联的日记业务ID',
    `user_id` VARCHAR(64) NOT NULL COMMENT '关联的用户ID',
    `task_type` VARCHAR(32) NOT NULL COMMENT '任务类型: UPSERT/DELETE',
    `status` VARCHAR(32) NOT NULL COMMENT '任务状态: PENDING/PROCESSING/COMPLETED/FAILED',
    `retry_count` INT DEFAULT 0 COMMENT '重试次数',
    `max_retries` INT DEFAULT 5 COMMENT '最大重试次数',
    `error_message` VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `next_retry_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '下次重试时间',
    PRIMARY KEY (`id`),
    KEY `idx_life_graph_task_status` (`status`),
    KEY `idx_life_graph_task_diary_id` (`diary_id`),
    KEY `idx_life_graph_task_next_retry` (`next_retry_at`),
    KEY `idx_life_graph_task_created` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '人生图谱抽取任务表';

SET FOREIGN_KEY_CHECKS = 1;