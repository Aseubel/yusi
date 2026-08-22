# 模型治理控制面重构记录

日期：2026-08-23

## 变更

- 统一生产路由和管理预览的候选规划，明确 route priority、模型 priority、weight、latency 和 tier strategy 的生效范围。
- `riskLevel` 参与 route 匹配；模型运行态没有 Redis 记录时在控制台显示 `UNKNOWN`，生产首请求仍按冷启动候选处理。
- 模型 `weight` 允许为 0；`WEIGHTED_RANDOM` tier 必须至少有一个启用、能力匹配且权重大于 0 的成员。
- 已发布模型 ID 作为稳定身份，普通治理更新不能删除或改名；需要替换时新增模型并迁移 tier 成员后停用旧模型。
- 预览接受当前 schema v2 草稿投影，不创建 provider client，不接收 API key，并返回结构化 route reason、候选 rank 和排除解释。
- 管理控制台按总览/模型与 Tier/场景路由/调用活动组织，并提供单模型和全部模型短期运行态 reset。

## Reset 语义

reset 只清理 Redis Hash 中的模型短期健康状态并发布 `ModelStateEvent(action=RESET)`，不会修改治理配置、调用轨迹、Prometheus/Micrometer 指标或其他 Redis key。Hash 写入先于 Pub/Sub 发布；发布失败时接口返回已写入且节点收敛待完成，实例下一次状态同步仍可从 Hash 收敛。

管理员动作通过 `security_audit_event` 记录，details 只允许 operation、scope、count 等低敏字段。

## 数据库

执行 `src/main/resources/db/migration/V20260831__add_model_management_read_indexes.sql` 后，存量 `model_call_trace` 支持按模型/场景和创建时间查询。新环境的 `src/main/resources/db/init.sql` 同步包含两个索引。运行态不新增数据库表。

## 安全边界

控制面和日志不保存或展示 API key、prompt、回答、思考过程、图片 URL、工具参数或完整上游响应体；上游失败仍只保留归一化类别、HTTP 状态和低敏摘要。
