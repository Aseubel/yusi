ALTER TABLE `agent_tool_trace`
    ADD COLUMN `idempotency_mode` VARCHAR(24) NOT NULL DEFAULT 'NONE'
        COMMENT '幂等声明模式: NONE/IDEMPOTENT_WRITE' AFTER `attempt_count`,
    ADD COLUMN `idempotency_status` VARCHAR(20) DEFAULT NULL
        COMMENT '幂等账本状态: CLAIMED/COMPLETED/FAILED/UNKNOWN' AFTER `idempotency_mode`,
    ADD COLUMN `idempotency_claimed_at` DATETIME DEFAULT NULL COMMENT '账本claim时间' AFTER `idempotency_status`,
    ADD COLUMN `idempotency_resolved_at` DATETIME DEFAULT NULL COMMENT '账本终态时间' AFTER `idempotency_claimed_at`,
    ADD COLUMN `idempotency_expires_at` DATETIME DEFAULT NULL COMMENT '账本保留截止时间' AFTER `idempotency_resolved_at`,
    ADD KEY `idx_agent_tool_trace_idempotency_state`
        (`idempotency_mode`, `idempotency_status`, `idempotency_expires_at`);
