# SQL 脚本说明

## 脚本清单

- `init.sql`：全量初始化脚本，适用于新环境一次性建库建表
- `update_developer_config.sql`：开发者配置增量脚本
- `update_model_management.sql`：模型治理增量脚本（新增运行时配置与变更日志表）

## 执行建议

1. 新环境优先执行 `init.sql`
2. 存量环境按功能分批执行 `update_*.sql`
3. 执行增量脚本前先做数据库备份

## 模型治理相关验证

- 表检查：
  - `model_runtime_config`
  - `model_config_change_log`
- 索引检查：
  - `uk_model_runtime_config_key`
  - `uk_model_config_change_log_change_id`
