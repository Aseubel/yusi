ALTER TABLE `soul_match`
    ADD COLUMN `reason` VARCHAR(1000) NULL COMMENT '本次匹配的共鸣原因' AFTER `letter_b_to_a`,
    ADD COLUMN `timing_reason` VARCHAR(1000) NULL COMMENT '本次匹配的时机原因' AFTER `reason`,
    ADD COLUMN `ice_breaker` VARCHAR(1000) NULL COMMENT '用于破冰的建议语' AFTER `timing_reason`,
    ADD COLUMN `score` INT NULL COMMENT '本次匹配精排分数' AFTER `ice_breaker`;
