# Agent Tool 幂等账本与重复执行保护设计

## 目标

在现有 AgentRun 工具 Trace 和统一执行包装层之上，给明确声明为幂等写操作的工具增加持久化 claim 账本，避免同一个逻辑工具调用在超时、并发或流式回调重放时重复产生副作用。

本切片只处理后端执行边界和低敏账本，不增加用户确认页面，不改变 GraphRAG、MCP 服务内部业务逻辑，也不把工具参数、结果正文或异常文本写入数据库。

## 已确认的方案边界

本次采用方案 3：复用既有 `agent_tool_trace` 作为工具生命周期记录和幂等账本的共同承载。数据库中的 `tool_call_id` 同时是：

1. `AiController.beforeToolExecution` 生成的 `localToolCallId`；
2. SSE `tool.started` / `tool.completed` 使用的本地调用 ID；
3. `AgentToolInvocationContext.localToolCallId` 携带的逻辑调用标识；
4. `agent_tool_trace.tool_call_id` 的唯一关联键；
5. 幂等 claim 的账本 key。

账本不增加第二个 `idempotency_key`，也不生成独立的逻辑调用 ID。物理重试只复用同一个本地 ID；新的、明确的模型工具调用才生成新的本地 ID。

### 方案取舍

| 方案 | 做法 | 结论 |
| --- | --- | --- |
| 1. 新建独立幂等表并生成新 key | Trace 和 ledger 各自维护逻辑 ID，再通过映射关联 | 拒绝。两套 ID 容易在重试、回调迟到和清理时分叉，且增加跨表一致性负担 |
| 2. 只使用 Redis 锁 | 用短租约锁阻止并发执行，不持久化最终结果 | 拒绝。进程重启后无法区分已完成、失败和未知副作用，无法满足 UNKNOWN 不重放 |
| 3. 扩展 `agent_tool_trace` | 沿用 `tool_call_id`，增加声明模式、账本状态和生命周期时间字段 | 采用。已有唯一约束、用户/运行隔离和终态收敛可以直接复用 |

## 核心定义

### 逻辑调用与物理尝试

- 一个 `beforeToolExecution` 回调创建一个逻辑调用和一个本地 `tool_call_id`。
- 一个逻辑调用最多包含多个物理尝试，但本切片只允许 READ 工具进行一次超时重试；所有物理尝试复用同一个 `tool_call_id`。
- 对写工具，首次 claim 成功后不进行自动重试。超时、取消、线程提交失败或 AgentRun 在工具回调前进入终态时，不能证明副作用是否发生，账本必须进入 `UNKNOWN`。
- `agent_run_trace.tool_count` 语义不变：仍表示已完成的逻辑工具调用总数，按 `onToolExecuted` 的逻辑调用回调递增一次，不按物理尝试次数、claim 次数或账本行数递增。

### 能力声明

能力目录增加幂等声明：

- `AgentToolAccessMode`: `READ`、`WRITE`、`UNKNOWN`。
- `AgentToolIdempotencyMode`: `NONE`、`IDEMPOTENT_WRITE`。
- `AgentToolRetryPolicy`: 当前只有 READ 能力可以声明 `TIMEOUT_ONCE`；WRITE 和 UNKNOWN 强制 `DENY`。

默认值必须拒绝自动重试和幂等账本写入：未知工具为 `UNKNOWN + NONE + DENY`。本切片只有本地 `updateUserPersona` 声明为 `WRITE + IDEMPOTENT_WRITE + DENY`。其他写工具在显式声明前不进入账本。

`IDEMPOTENT_WRITE` 是“允许由平台用同一逻辑 key 做重复执行保护”的声明，不代表所有写工具都可以自动重试；写工具仍然禁止自动 retry。

## 数据模型

在 `agent_tool_trace` 现有字段上增加以下低敏字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `idempotency_mode` | `VARCHAR(24)` | `NONE` 或 `IDEMPOTENT_WRITE`；只有声明幂等的写工具使用账本 |
| `idempotency_status` | `VARCHAR(20)` | `CLAIMED`、`COMPLETED`、`FAILED`、`UNKNOWN`；`NONE` 时为空 |
| `idempotency_claimed_at` | `DATETIME` | 原子 claim 成功时间 |
| `idempotency_resolved_at` | `DATETIME` | 账本进入终态时间 |
| `idempotency_expires_at` | `DATETIME` | 幂等状态保留截止时间 |

不新增 `idempotency_key` 字段，因为其值必须与 `tool_call_id` 完全相同；代码中使用 `localToolCallId` / `toolCallId` 贯穿 Controller、Context、Executor、Trace 和 ledger。

既有唯一约束 `(user_id, run_id, tool_call_id)` 保持不变，并继续作为跨用户隔离边界。普通工具 Trace 仍保留原有 `status`、`failure_category` 和 `attempt_count`；幂等账本状态与工具生命周期状态是两个有明确关系但不互相替代的状态字段。

### 保留与清理

- 幂等账本状态保留 30 天，从 `idempotency_resolved_at` 计算；没有终态的 `CLAIMED` 行使用 `idempotency_claimed_at` 计算。
- 过期清理只清理幂等账本字段（状态、claim/resolve/expire 时间），保留同一行的普通工具 Trace 生命周期，避免破坏 AgentRun 回顾和审计关联。
- 清理后的行不再承担旧逻辑调用的重放保护；新的模型调用仍会生成新的本地 `tool_call_id`。超过保留期的旧 ID 重放不属于本切片保证范围。
- 应用启动时扫描 `IDEMPOTENT_WRITE + CLAIMED` 且 `idempotency_claimed_at < now - 5 minutes` 的行，使用条件更新将其置为 `UNKNOWN`，写入 `idempotency_resolved_at` 和新的保留截止时间。扫描不接管这些行，也不自动执行工具。
- 运行时遇到已过租约但尚未被扫描的 `CLAIMED` 行，同样只返回 `IDEMPOTENCY_IN_PROGRESS` 或收敛为 `UNKNOWN` 后阻止，不允许接管执行。

5 分钟是 claim lease，不是自动恢复窗口；`UNKNOWN` 永远不自动重放。

## 账本状态机与原子 Claim

```text
                 claim
  no ledger ---------------> CLAIMED
                                |
             success ----------+----------> COMPLETED
             explicit failure -+----------> FAILED
             timeout/cancel/
             missing callback -+----------> UNKNOWN

  COMPLETED / FAILED / UNKNOWN  -- replay --> blocked(error)
  CLAIMED held by another call  -- replay --> blocked(error)
```

首次执行必须在 delegate 之前 claim。claim 使用当前 `(user_id, run_id, tool_call_id)` 行的条件更新，只有状态为空（或已过保留期并被明确清理）的一方可以把它写成 `CLAIMED`。更新结果为 0 时重新读取低敏状态并返回对应阻断决策；并发 claim 失败方快速失败，不能返回普通成功字符串，也不能继续调用 delegate。

账本状态的写入边界如下：

- delegate 返回普通结果：写 `COMPLETED`。不解析结果文本。
- `ToolExecutionResult.isError()` 或 `ToolExecution.hasFailed()` 明确为 true：写 `FAILED`，失败分类只使用固定枚举。
- `AgentToolTimeoutException`、`AgentToolCancelledException`、线程池拒绝、运行终态收敛或无法确认 delegate 是否产生副作用：写 `UNKNOWN`。
- 其他明确异常类型：写 `FAILED`，失败分类只能来自异常类型映射，不保存异常 message。
- 任何终态更新必须带 `WHERE idempotency_status = CLAIMED`，迟到回调不能把 `COMPLETED` / `FAILED` / `UNKNOWN` 重新打开。

## 重试与账本的重放决策矩阵

当前超时重试逻辑必须先判断访问模式和幂等账本状态，不能只看 `AgentToolTimeoutException`。尤其是写工具的超时重试必须被访问模式短路；否则“写工具可重试”和“UNKNOWN 不重放”会产生矛盾。

| Access mode | Idempotency mode | 账本状态 | 首次/重放决策 | 超时 retry | 模型可见结果 |
| --- | --- | --- | --- | --- | --- |
| `READ` | `NONE` | 无账本 | 执行 | 最多 1 次，受逻辑总 deadline 限制 | delegate 结果或原有超时错误 |
| `WRITE` | `NONE` | 无账本 | 允许一次明确调用 | 禁止；即使错误配置 `TIMEOUT_ONCE` 也由 Executor 拒绝 | delegate 结果或原有错误 |
| `WRITE` | `IDEMPOTENT_WRITE` | 空 | 原子 claim，成功后执行 | 禁止 | delegate 结果 |
| `WRITE` | `IDEMPOTENT_WRITE` | 当前调用刚 claim 的 `CLAIMED` | 只允许当前一次 delegate | 禁止；timeout/cancel 立即写 `UNKNOWN` | 超时/取消错误，不发起下一次 |
| `WRITE` | `IDEMPOTENT_WRITE` | 其他调用持有 `CLAIMED` | 快速阻断 | 禁止 | `TOOL_EXECUTION_BLOCKED: IDEMPOTENCY_IN_PROGRESS; operation was not executed.` |
| `WRITE` | `IDEMPOTENT_WRITE` | `COMPLETED` | 阻断重放 | 不适用 | `TOOL_EXECUTION_BLOCKED: IDEMPOTENCY_ALREADY_COMPLETED; operation was not repeated.` |
| `WRITE` | `IDEMPOTENT_WRITE` | `FAILED` | 阻断自动重放，要求新的明确调用 | 禁止 | `TOOL_EXECUTION_BLOCKED: IDEMPOTENCY_PREVIOUS_FAILURE; operation requires a new explicit invocation.` |
| `WRITE` | `IDEMPOTENT_WRITE` | `UNKNOWN` | 永久阻断自动重放，避免重复副作用 | 禁止 | `TOOL_EXECUTION_BLOCKED: IDEMPOTENCY_UNKNOWN; operation was not replayed to avoid duplicate side effects.` |
| `UNKNOWN` | 任意 | 无账本或未知状态 | 不因未知能力扩展写入 | 禁止 | 初次调用沿用工具错误语义；重放不自动执行 |

如果缺少 `AgentToolInvocationContext`，即使工具已声明 `IDEMPOTENT_WRITE` 也必须 fail closed，返回：

`TOOL_EXECUTION_BLOCKED: IDEMPOTENCY_CONTEXT_MISSING; operation was not executed.`

`execute(...)` 返回上述稳定文本；`executeWithContext(...)` 返回相同文本且 `ToolExecutionResult.isError() == true`。因此并发 claim 失败、已完成、已失败、未知和 Context 缺失都不会被模型误认为工具执行成功。

## Context 注入技术验证

### 首选机制：AgentToolInvocationContext

新增低敏不可变 Context：

```text
AgentToolInvocationContext
  userId
  runId
  localToolCallId       // 与 tool_call_id 同值
  toolName
  toolSource
  accessMode
  idempotencyMode
  capabilityVersion
```

`AiController.beforeToolExecution` 将这个 Context 和 request object identity 注册到现有的 `AgentToolExecutionAttemptRegistry`；Registry 同时实现 `AgentToolInvocationContextProvider`，不复制 request arguments 或结果。`AgentToolExecutionPolicyExecutor` 在 caller 线程取得 Context，并在提交专用 worker 时显式捕获它。worker 在调用 delegate 前设置 `AgentToolInvocationContextHolder`，在 `finally` 中清理。

首个实现任务必须先用单元测试验证：Context 能从 Controller 注册的 request identity 传播到专用 worker，`UserPersonaTool` 能在实际 delegate 执行期间读取相同的 `userId` 和 `localToolCallId`。只有该测试通过，才能接入账本 claim。

### 退回机制：ModelRouteContextHolder

如果技术验证证明 LangChain4j 的工具回调对象在实际执行边界无法稳定映射，保留相同 Context 字段但改为把它作为 `ModelRouteContext` 的调用级扩展，由 `ModelRouteContextHolder` 在提交 worker 时显式携带。该退回机制仍使用原有 `localToolCallId`，不创建第二套 key；缺少 `localToolCallId` 时依旧 fail closed。不能通过 ThreadLocal 的隐式继承或全局静态变量绕过显式捕获。

## 执行流程

```text
AiController.beforeToolExecution
  -> 生成 localToolCallId
  -> 读取 capability(access/idempotency/retry)
  -> 写 AgentToolTrace.RUNNING + 同一 tool_call_id
  -> 注册 InvocationContext(request identity)
  -> 发送 tool.started(localToolCallId)

AgentToolExecutionPolicyExecutor
  -> 按 request identity 取得 Context
  -> WRITE + IDEMPOTENT_WRITE: claim ledger
       -> blocked: 返回标准 error，不调用 delegate
       -> claimed: 进入一次 delegate
  -> worker 设置 InvocationContextHolder
  -> 执行 delegate
  -> 按返回值/异常类型 resolve ledger
  -> READ 才允许 timeout retry；重试前再次检查 access mode、取消和 deadline

onToolExecuted / AgentRun complete/fail/cancel
  -> 使用同一个 localToolCallId 完成普通 Trace
  -> 终态回调统一关闭仍为 RUNNING 的工具 Trace
  -> 仍为 CLAIMED 的幂等账本改 UNKNOWN
  -> 清理 request identity Context 和 attempt registry
```

专用线程池仍使用 `agent.tool.execution.pool-size` 和 `agent.tool.execution.queue-capacity`，默认 8 个 worker、16 个队列槽位。单次 READ 超时重试期间，超时后未响应中断的旧 delegate 可能继续占用一个 worker，重试的新 delegate 可能再占用一个 worker，因此一次逻辑调用最多造成两个同时占用的 worker；本切片不扩大池、不建立第二个重试池。写工具无自动 retry，不产生额外的 retry worker。逻辑总 deadline 最大 30 秒，明显小于 SSE 180 秒生命周期。

## `updateUserPersona` 端到端验收

`updateUserPersona` 是本切片唯一声明 `WRITE + IDEMPOTENT_WRITE` 的真实消费方，验收不得只验证能力目录或空包装：

1. 从实际 `UserPersonaTool` 生成本地 ToolSpecification，能力目录返回 `WRITE`、`IDEMPOTENT_WRITE`、`DENY`。
2. 用 `beforeToolExecution` 生成的本地 ID 注册 Context，调用统一包装后的 executor；Context Holder 中的用户 ID 被 `UserPersonaTool` 使用，`UserPersonaService.updateUserPersona` 只被调用一次。
3. 对同一个 `(userId, runId, localToolCallId)` 再发起一次执行，第二次不进入 `UserPersonaService`，返回 `isError == true` 的 `IDEMPOTENCY_ALREADY_COMPLETED`。
4. 并发用同一 key 执行时，只有 claim 成功方可以进入工具，失败方快速返回 `IDEMPOTENCY_IN_PROGRESS` 且明确为 error。
5. 让第一次执行超时，验证没有第二次工具调用，账本变为 `UNKNOWN`；再次用同一 key 执行返回 `IDEMPOTENCY_UNKNOWN`。
6. `agent_run_trace.tool_count` 在一次成功逻辑调用中仍只增加一次，物理尝试和 claim 不增加该字段。

## 失败分类与安全边界

`failureCategory` 只能来自固定枚举：异常类型明确是 timeout/cancel 时分别使用 `TIMEOUT` / `CANCELLED`；`ToolExecution.hasFailed()` 或 `ToolExecutionResult.isError()` 为 true 时使用 `TOOL_FAILED`；AgentRun 终态收敛使用 `AGENT_ERROR` 或 `CANCELLED`；无法分类才使用 `UNKNOWN`。禁止从工具返回文本、异常 message、参数、query、Prompt 或模型输出中提取失败原因落库。

`McpGrpcServiceImpl` 原始 `keyword/query` 日志不在本切片范围内，另立日志安全切片处理。幂等账本只保存用户、运行、工具调用、能力声明、状态和时间等低敏字段。

## 非目标

- 不为所有工具自动声明幂等；未声明写工具不进入账本。
- 不给写工具增加自动重试、用户确认、暂停/恢复或补偿执行。
- 不把 `UNKNOWN` 转为可重放状态，不通过模型结果文本猜测副作用是否发生。
- 不限制 GraphRAG 查询深度，不改变 MCP 服务和具体业务工具的内部幂等实现。
- 不改变 `agent_run_trace.tool_count` 的既有含义。

## 验收标准

1. 重试决策先判断 access mode 和 ledger status；WRITE/UNKNOWN 不自动 timeout retry，UNKNOWN ledger 永不重放。
2. 并发 claim 失败方和所有重放阻断方都返回稳定 error 响应，不静默成功。
3. 全链路只使用 Controller 生成的 `localToolCallId/tool_call_id`，不存在第二套独立 ledger key。
4. 账本只由 `IDEMPOTENT_WRITE` 声明的写工具使用；启动扫描 5 分钟以上孤儿 `CLAIMED` 为 `UNKNOWN`，账本状态保留 30 天。
5. Context 注入先有传播测试；失败时只允许显式退回 `ModelRouteContextHolder`，不能依赖隐式 ThreadLocal 继承。
6. `updateUserPersona` 端到端证明 claim、阻断、Context 消费和真实副作用保护有效。
7. 全量后端测试通过，`git diff --check` 通过，不启动服务、不依赖远程模型或跨域环境。
