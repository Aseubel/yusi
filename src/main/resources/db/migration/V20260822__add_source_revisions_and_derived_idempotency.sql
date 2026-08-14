ALTER TABLE `diary`
    ADD COLUMN `source_revision` BIGINT DEFAULT NULL COMMENT '来源内容单调版本号' AFTER `update_time`;

ALTER TABLE `soul_card`
    ADD COLUMN `source_revision` BIGINT DEFAULT NULL COMMENT '来源内容单调版本号' AFTER `created_at`;

ALTER TABLE `embedding_task`
    ADD COLUMN `source_revision` BIGINT DEFAULT NULL COMMENT '来源内容单调版本号' AFTER `task_execution_id`,
    ADD UNIQUE KEY `uk_embedding_task_execution` (`task_execution_id`);

ALTER TABLE `life_graph_task`
    ADD COLUMN `source_revision` BIGINT DEFAULT NULL COMMENT '来源内容单调版本号' AFTER `task_execution_id`,
    ADD UNIQUE KEY `uk_life_graph_task_execution` (`task_execution_id`);

ALTER TABLE `life_graph_relation_evidence`
    ADD COLUMN `source_revision` BIGINT DEFAULT NULL COMMENT '来源内容单调版本号' AFTER `source_id`;

ALTER TABLE `life_graph_entity_evidence`
    ADD COLUMN `source_revision` BIGINT DEFAULT NULL COMMENT '来源内容单调版本号' AFTER `source_id`;

ALTER TABLE `life_graph_mention`
    ADD COLUMN `source_revision` BIGINT DEFAULT NULL COMMENT '来源内容单调版本号' AFTER `diary_id`;
