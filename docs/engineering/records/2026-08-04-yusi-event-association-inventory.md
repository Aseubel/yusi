# Yusi 事件关联 ID 盘点

> **Status:** Complete v1; P1 connection slice implemented
> **Date:** 2026-08-04
> **Last Updated:** 2026-08-05
> **Related:** [Yusi Agent 产品与工程演进计划](../plans/2026-08-04-yusi-agent-product-roadmap.md)、[Yusi Agent 产品事件与评测基线](../specs/2026-08-04-yusi-agent-product-event-contract.md)

## 目的

本记录盘点当前系统中已经存在的实体 ID、任务 ID 和业务关联字段，并区分“能够定位一条记录”和“能够解释这条记录为什么产生”两类能力。

这次只补齐优先级最高的关联边界，不新建通用事件平台，也不要求所有历史数据立即拥有完整 Trace。

## 结论

1. 当前多数业务记录已经能通过主键或业务键定位；P0 已为日记变更触发的异步认知任务补上来源变更 ID，失败重试可以继续沿用同一来源。
2. `SoulMatch.id` 继续作为 `matchId` 使用；`SoulConnection.id` 已作为独立的
   `connectionId` 落地，一个匹配最多对应一个连接，不能把推荐记录直接等同于连接。
3. 情景室的 `code` 可以作为 `situationId`，报告目前内嵌在房间记录中；情景室仍保持独立小游戏，只有用户主动带入时才与对话或匹配建立一次性关联。
4. 当前聊天 `requestId` 已经作为用户可见的 `runId` 使用，并已持久化低敏生命周期摘要；它尚未与聊天记忆行、模型调用或完整工具调用 Trace 关联。

## 现状盘点

| 领域 | 现有记录/关联 | 当前可回答的问题 | 缺少的关联 |
| --- | --- | --- | --- |
| 聊天 AgentRun | `requestId` / `runId`、工具 `toolCallId`、`AgentRunTrace.id` | 一次流式任务有哪些公开阶段、工具活动、工具次数、耗时和终态 | `ChatMemoryMessage` 没有 `runId`；模型、检索和完整工具 Schema 未形成服务端 Trace |
| 聊天记忆 | `ChatMemoryMessage.id`、`memoryId`（当前为用户 ID） | 这条记忆行属于哪个用户、何时写入 | 由哪次对话请求产生；用户消息与回答的同一任务关联 |
| 日记变更 | `eventId`、`diaryId`、`userId`、`DiaryChangedEvent.Type` | 哪篇日记发生了什么类型的变更 | 变更事件本身尚未作为独立产品事件持久化 |
| Embedding 任务 | 任务 `id`、`diaryId`、`userId`、`triggerEventId`、状态和重试字段 | 任务是否成功、失败和重试，以及由哪次日记变更触发 | 任务状态尚未和统一服务端 Trace 汇总 |
| LifeGraph 任务 | 任务 `id`、`diaryId`、`userId`、`triggerEventId`、状态和重试字段 | 图谱任务是否成功、失败和重试，以及由哪次日记变更触发 | 任务状态尚未和统一服务端 Trace 汇总 |
| 认知冲突 | `CognitiveConflict.id`、`userId`、`source` | 用户有哪些未解决冲突 | 来源日记、对话或认知摄取记录的稳定 ID；触发冲突的运行 ID |
| 匹配推荐 | `SoulMatch.id` / `matchId`、`SoulConnection.id` / `connectionId`、`MatchProfile.version` | 推荐的是哪一对用户、当前双方动作、连接状态和画像版本 | 推荐生成的 `runId` 或事件 ID |
| 匹配反馈 | `MatchFeedback.id`、`matchId`、`connectionId`、`userId`、`action` | 用户对哪条匹配和连接做了什么反馈 | 反馈所对应的产品事件 ID或请求 ID |
| 灵魂聊天 | `SoulMessage.id`、`matchId`、发送方和接收方；发送时校验 `SoulConnection` | 消息属于哪条匹配，当前连接是否允许互动 | 消息行尚未持久化独立 `connectionId` 或请求 `runId` |
| 通知 | `id`、`notificationId`、`refType`、`refId`、`extraData` | 通知属于哪个用户和业务引用 | 触发通知的事件 ID；部分匹配/连接通知没有直接的结构化关联 |
| 情景室 | `SituationRoom.code`，可作为 `situationId`；报告内嵌 | 哪个情景、哪些成员、是否完成和是否已有报告 | 异步报告任务/运行 ID；用户主动带入对话或匹配的关联记录 |
| 情景室聊天 | `RoomMessage.id`、`roomCode` | 消息属于哪个情景室 | 报告运行 ID或上下文附加事件 ID |

## P0 实施结果

2026-08-04 已完成日记变更到异步任务的第一条关联链：

```text
DiaryChangedEvent.eventId
        -> EmbeddingTask.triggerEventId
        -> LifeGraphTask.triggerEventId
```

- 新事件默认由服务端生成 UUID，也允许测试或可靠消息边界传入已有事件 ID。
- Embedding 和 LifeGraph 的任务创建都使用同一个变更事件 ID。
- 任务重试和任务回收只更新状态、错误和时间，不生成新的 `triggerEventId`。
- 新字段允许为空，历史任务不回填虚假来源；数据库迁移和初始化 schema 都已更新。
- 日志只记录低敏 ID，不记录日记正文、Prompt、工具参数或令牌。

## ID 分层

后续实现使用下面的命名边界，避免把不同含义的 ID 混用：

| ID | 含义 | 示例 | 生命周期 |
| --- | --- | --- | --- |
| 记录 ID | 数据库行或业务对象身份 | `diaryId`、`matchId`、`situationId` | 随对象存在 |
| 事件 ID | 一次业务事实的唯一身份 | `eventId`、`triggerEventId` | 写入后不可复用，用于去重和审计 |
| AgentRun ID | 一次用户任务或长流程运行 | 当前聊天 `requestId` | 从运行开始到终态 |
| ToolCall ID | 一次工具执行 | `toolCallId` | 只属于一个 AgentRun |
| Connection ID | 一次匹配后的连接关系 | `SoulConnection.id` | 从接受/开始互动到结束或阻断 |

一个任务可以同时拥有多个不同层级的 ID。例如 Embedding 任务自身有 `task.id`，由日记变更 `triggerEventId` 触发，但不应把任务 ID冒充成 AgentRun ID。

## 补齐优先级

### P0：日记变更到异步任务

- 为 `DiaryChangedEvent` 生成稳定 `eventId`。
- 将 `eventId` 作为 `triggerEventId` 写入 Embedding 和 LifeGraph 任务。
- 为新增字段提供可回滚的数据库迁移；旧任务允许为空，历史记录不回填虚假 ID。
- 重试沿用原任务的 `triggerEventId`，不为同一次任务重试生成新的来源事件。

这条链可以首先支持：日记变更 -> 异步任务 -> 重试/失败原因的定位，并且不会把用户日记原文写入事件或日志。

### P1：匹配到连接（第一版已完成）

- [x] 保留 `matchId` 表示一次推荐记录，并以 `SoulConnection` 独立保存连接生命周期。
- [x] 接受动作创建或更新 `connectionId`；双方接受后进入 `STARTED`，单方接受时为
      `WAITING_REPLY`，双方深度互动反馈后进入 `MUTUAL_RESONANCE`。
- [x] 后续反馈、结束、举报和拉黑均通过 `connectionId` 关联；连接状态限制聊天发送。
- [x] 迁移已有已接受匹配，并回填历史 `match_feedback.connection_id`。
- [ ] 匹配推荐生成的 `runId`/产品事件 ID，以及通知、聊天消息的完整事件关联仍待补齐。

### P1：聊天 AgentRun 到记忆

- 在聊天记忆行增加可空 `runId`，先覆盖用户消息和 Agent 最终回答。
- 工具结果和原始思考不写入聊天记忆的产品事件字段。
- 对历史行保留为空，不用迁移脚本猜测关联关系。

### P2：情景室报告和主动带入

- 报告生成需要独立的 `reportRunId` 或报告任务记录，用于异步失败、重试和幂等。
- 用户主动将情景带入一次对话或匹配时，记录短期 `contextAttached` 关联和过期时间。
- 不因情景完成自动写入长期画像或匹配画像。

### P2：通知和冲突来源

- 通知增加结构化 `sourceEventId`，逐步替代仅依赖 `extraData` 或 `refType/refId` 的关联。
- 认知冲突增加 `sourceType/sourceId`，让冲突可回到日记、对话或认知摄取记录。

## 实施顺序与验收

1. P0 事件 ID 和两类异步任务的关联字段已完成，并保留任务重试的同一 `triggerEventId`。
2. P1 连接模型已完成；下一步为连接状态变化补充产品事件 ID，并逐步关联通知、聊天消息和
   安全审计，而不是把 `matchId` 继续当作连接身份。
3. 聊天记忆 `runId`、模型/检索 Trace 和情景室报告关联仍按后续阶段推进，避免一次引入过多表结构。

P0 完成后，至少应能通过 `triggerEventId` 查询或日志定位：

- 哪篇日记的哪种变更触发了任务；
- 同一次变更创建了哪些 Embedding/LifeGraph 任务；
- 任务是否经历重试、最终成功或失败；
- 该关联没有暴露日记正文、Prompt、工具参数或令牌。
