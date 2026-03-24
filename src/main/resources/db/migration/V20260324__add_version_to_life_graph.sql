-- 添加乐观锁版本号以支持并发更新控制
ALTER TABLE `life_graph_entity`
ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号' AFTER `props`;

ALTER TABLE `life_graph_relation`
ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号' AFTER `props`;
