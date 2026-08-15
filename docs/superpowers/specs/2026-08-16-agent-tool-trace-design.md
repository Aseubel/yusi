# AgentToolTrace 低敏工具运行关联设计

## 目标

将聊天 AgentRun 中的本地工具、记忆召回工具和 MCP 工具调用持久化为低敏工具运行明细，
使一次 `AgentRun` 可以关联到模型调用和工具调用，同时不保存用户查询、工具参数、召回正文、
工具结果、Prompt 或密钥。

本设计只覆盖聊天流内部的 ToolCall 生命周期。外部 AI 通过 Yusi MCP 发起的调用仍属于外部
AgentRun；Yusi 只保留既有 MCP invocation 边界，不把它伪装成用户聊天 AgentRun。

## 已确认的边界

### AgentRun 与工具的关系

一个用户聊天请求仍然只有一个 `AgentRun`，工具调用是该运行的子明细，不创建新的 AgentRun：

```text
AgentRun(runId = requestId)
  -> ModelCallTrace
  -> AgentToolTrace(searchMemories / searchDiary / searchLifeGraph / MCP tool)
  -> final response
```

`agent_run_trace.tool_count` 的语义保持不变：它仍表示该 AgentRun 已完成的工具调用总数，
不是检索结果数量，也不是工具 Trace 行数。新增的 `AgentToolTrace` 不替换这个聚合字段；
工具成功或失败完成时仍按现有语义递增一次。

### toolCallId 兜底与关联

LangChain4j 1.18 的 `ToolExecutionRequest.id()` 在流式网关和 MCP 场景可能为空，因此不能
直接使用它作为数据库幂等键。

- `AgentToolTrace.toolCallId` 由 Yusi 在 `beforeToolExecution` 回调中生成本地唯一 ID，使用
  `IdUtil.fastSimpleUUID()`；每个开始回调都先生成 ID，再写入 `RUNNING` 记录。
- `ToolExecutionRequest.id()` 只写入可空的 `upstreamToolCallId`，作为诊断参考，不参与唯一约束。
- 本地 ID 同时作为 `tool.started` 与 `tool.completed` SSE 事件的 `toolCallId`，确保前端在
  上游 ID 为空时仍能关联开始和完成事件。
- 为了把完成回调关联回本地 ID，聊天请求生命周期维护一个临时的相关性注册表：优先按请求
  对象身份匹配，其次按非空上游 ID 匹配，最后按工具名的先入先出队列匹配。请求结束或 AgentRun
  进入终态后清理注册表；无法可靠匹配的完成回调不得修改其他工具记录。

数据库唯一约束为 `(user_id, run_id, tool_call_id)`。本地 ID 已经是每次调用唯一值，重复回调
通过本地 ID 幂等更新，不依赖上游协议实现。

### 终态收敛

工具执行完成回调不一定会在用户主动取消时触发，因此 `AgentToolTrace` 不能只依赖
`onToolExecuted` 收口。

`AgentRunTraceService.complete/fail/cancel` 三个 AgentRun 终态入口都必须调用工具 Trace
收敛服务，将同一 `userId + runId` 下仍为 `RUNNING` 的记录统一改为对应终态：

| AgentRun 终态 | AgentToolTrace 终态 | failureCategory |
| --- | --- | --- |
| `COMPLETED` | `COMPLETED` | 空 |
| `FAILED` | `FAILED` | `agent_error` |
| `CANCELLED` | `CANCELLED` | `cancelled` |

正常的 `onToolExecuted` 先独立完成工具记录，AgentRun 最终完成时不会重复改变已终态记录。
收敛操作必须幂等，并且不能因为 Trace 写入失败阻断聊天业务。

### failureCategory

`failureCategory` 只允许固定的低敏枚举分类，不保存异常 message，也不从工具结果文本中提取
失败原因。

- `TOOL_FAILED`：`ToolExecution.hasFailed()` 为 `true`。
- `AGENT_ERROR`：AgentRun 的错误回调或未分类运行异常收敛仍在运行的工具。
- `TIMEOUT`：调用链明确识别为超时异常时使用。
- `CANCELLED`：用户主动取消时使用。
- `UNKNOWN`：只有无法归类的异常类型才使用。

成功记录的 `failureCategory` 必须为空。工具结果文本、异常文本和异常堆栈只进入受控服务端
日志，不进入 `agent_tool_trace`。

## 数据模型

新增 `agent_tool_trace` 表和 `AgentToolTrace` 实体，字段如下：

| 字段 | 说明 |
| --- | --- |
| `id` | 数据库主键 |
| `user_id` | 用户 scope |
| `run_id` | 所属 AgentRun |
| `tool_call_id` | Yusi 生成的本地调用 ID，非空 |
| `upstream_tool_call_id` | LangChain4j/MCP 上游 ID，可空，仅供参考 |
| `tool_name` | 工具稳定名称 |
| `tool_source` | `local`、`mcp` 或其他固定来源分类 |
| `status` | `RUNNING`、`COMPLETED`、`FAILED`、`CANCELLED` |
| `failure_category` | 固定低敏失败分类，可空 |
| `started_at` | 工具开始时间 |
| `completed_at` | 工具终态时间，可空 |
| `duration_ms` | 工具耗时，可空 |
| `created_at` / `updated_at` | 审计和维护时间 |

索引和约束：

- 唯一约束 `(user_id, run_id, tool_call_id)`。
- 查询索引 `(user_id, run_id, created_at)`。
- 状态恢复索引 `(status, updated_at)`，供未来清理或补偿使用。

不增加 JSON 参数列，不增加 query、arguments、result、snippet、Prompt、模型输出或密钥列。

## 代码边界

### 纳入

- [AiController.java](../../../src/main/java/com/aseubel/yusi/controller/AiController.java) 的
  工具回调实际位置为 L342-L372：`beforeToolExecution` 生成本地 ID 并开始记录，
  `onToolExecuted` 完成记录并保留现有 SSE 行为。
- `AgentToolTrace`、Repository、Service 及短生命周期工具相关性注册表。
- `AgentRunTraceService` 的 `complete/fail/cancel` 终态收敛调用。
- 工具名称、工具来源和失败分类的集中常量。
- 数据库增量迁移和 `src/main/resources/db/init.sql`。
- 相关单元测试和 roadmap 记录。

### 显式排除

本切片**不修改** `McpGrpcServiceImpl` 的原始 `keyword/query` 日志。它属于外部 MCP invocation
日志边界，不是聊天 AgentRun 的 ToolCall 生命周期；该文件中的日志收敛另立安全日志切片处理。
本切片只确保聊天 AgentRun 的持久化 Trace 不保存这些内容。

`MemorySearchTool`、`DiarySearchTool`、`LifeGraphTool` 和 `MidTermMemorySearchService` 的
检索算法、返回结构、GraphRAG 多跳深度和权限判断不改变。它们不直接写工具 Trace；由
`AiController` 的统一 ToolCall 回调记录调用元数据。

## 生命周期

```text
beforeToolExecution
  -> 生成本地 toolCallId
  -> 保存 AgentToolTrace.RUNNING
  -> 注册 request identity / upstream id / tool name 关联
  -> 发送既有 tool.started（使用本地 toolCallId）

onToolExecuted
  -> 解析本地 toolCallId
  -> hasFailed ? FAILED : COMPLETED
  -> 只写固定 failureCategory
  -> 发送既有 tool.completed
  -> AgentRun.tool_count 按现有语义递增一次

AgentRun complete/fail/cancel
  -> 收敛同一 userId + runId 下的 RUNNING 工具记录
  -> 清理本次聊天的内存相关性注册表
```

Trace 的数据库异常只记录低敏服务端日志并继续 SSE/聊天流程；业务错误仍由 AgentRun 原有
生命周期记录。重复终态回调、重复工具完成回调和取消后的迟到回调都不能重新打开或覆盖已
完成记录。

## 验收标准

1. 上游 `ToolExecutionRequest.id()` 为 null 时，开始和完成仍使用同一个后端生成的本地
   `toolCallId`，并能完成唯一更新。
2. 上游 ID 非空时，数据库唯一键仍只使用本地 ID，上游值只保存在可空参考列。
3. AgentRun 的完成、失败、取消三个终态都会收敛孤儿 `RUNNING` 工具记录。
4. `agent_run_trace.tool_count` 仍表示完成工具调用总数，既有测试和行为不改变。
5. `failureCategory` 只能来自固定枚举；工具结果文本和异常 message 不落库。
6. Trace 表不包含用户 query、工具参数、召回文本、工具结果、Prompt 或密钥。
7. `McpGrpcServiceImpl` 本次没有变更，后续日志收敛范围在 roadmap 中明确记录。
8. 不启动服务，不依赖远程模型、Milvus 或跨域环境；后端测试和 `git diff --check` 通过。
