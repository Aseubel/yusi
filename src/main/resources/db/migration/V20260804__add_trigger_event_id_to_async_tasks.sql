ALTER TABLE `embedding_task`
    ADD COLUMN `trigger_event_id` VARCHAR(64) DEFAULT NULL COMMENT '触发任务的日记变更事件ID' AFTER `user_id`,
    ADD KEY `idx_embedding_task_trigger_event` (`trigger_event_id`);

ALTER TABLE `life_graph_task`
    ADD COLUMN `trigger_event_id` VARCHAR(64) DEFAULT NULL COMMENT '触发任务的日记变更事件ID' AFTER `user_id`,
    ADD KEY `idx_life_graph_task_trigger_event` (`trigger_event_id`);
