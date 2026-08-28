# 用户级后台 AgentRun 与任务执行关联设计

## 状态

- 设计范围：认知摄取、LifeGraph、周报、主动问候的用户级后台 AI 运行关联
- 状态：待评审
- 日期：2026-08-14

## 背景

Phase 2 已经提供了 `task_execution` 任务账本，Phase 3 也已经提供了聊天
`agent_run_trace` 和 `model_call_trace`。当前后台工作流仍然没有把这三层记录串起来：

```text
后台入口 -> TaskExecution -> 模型调用
             各自记录，无法用一个 runId 还原完整工作流
```

具体表现为：

1. 认知摄取事件没有 `runId`，`AgentCognitionOrchestratorImpl` 也没有 AgentRun 生命周期。
2. LifeGraph 任务已经有 `TaskExecution`，但创建任务时没有设置 `runId`，worker 进入模型调用前也没有恢复运行上下文。
3. 周报使用 `generationRunId` 和 `TaskExecution.runId`，但当前一个扫描批次的多个用户共享同一个值，没有用户级 AgentRun Trace。
4. 主动问候只有扫描、冷却和通知逻辑，没有任务账本、运行状态或模型调用关联。
5. `ModelRouteContextHolder` 是线程局部上下文；异步任务线程不会自动继承用户级运行上下文，直接设置单次模型上下文时还可能覆盖外层运行信息。

这使得管理员只能看到“某次模型调用失败”或“某个任务失败”，不能可靠回答：

- 哪个用户的哪一次后台工作流触发了这次调用？
- 该工作流是否还有重试、已经完成，还是最终失败？
- 同一来源修改产生的任务、模型调用和派生结果是否属于同一次运行？

## 目标

- 为每个用户级后台 AI 工作流建立一个稳定的 `runId`。
- 让同一个 `runId` 贯穿 `TaskExecution`、`AgentRunTrace`、`ModelRouteContext` 和 `ModelCallTrace`。
- 覆盖以下用户级工作流：
  - 日记、聊天摘要和用户本人 Plaza 的认知摄取；
  - Diary / Plaza LifeGraph 任务；
  - 周报生成；
  - 主动问候生成与通知。
- 任务重试、进程重启和重复事件继续使用同一个逻辑 `runId`，不因重试生成新的运行记录。
- 运行记录只保存低敏生命周期和路由元数据，不保存原始文本、Prompt 正文、模型输出、思考内容、工具参数或工具结果。
- 保持现有业务表作为事实来源，不引入通用事件溯源平台，不新增用户可见功能。
- 让运行关联可以按用户安全查询，并为后续记忆召回、工具调用和评测回放留下稳定边界。

## 非目标

- 本切片不重写主动问候的触发条件。当前“不活跃天数、最近问候、静默时段、频率设置”逻辑继续保持；这里只补齐任务与运行记录。
- 本切片不把跨用户匹配批处理强行建模为用户级 AgentRun。
- 本切片不将所有异步服务改造成持久化 Agentic Runtime，也不增加暂停、人工确认或工作流编排 UI。
- 本切片不回填历史 `TaskExecution`、`SoulReport` 或 `ModelCallTrace` 的缺失 `runId`。
- 本切片不增加管理员读取用户原文或备份密钥的能力。

## 核心定义

### AgentRun 是一次用户级工作流

一次后台工作流有一个逻辑运行 ID：

```text
用户来源事件 / 定时用户候选
    -> 一个 user-scoped AgentRun(runId)
        -> 一个或多个 TaskExecution
            -> 一个或多个 ModelCallTrace
        -> 派生结果 / 通知
```

`runId` 表示这次用户级工作流的生命周期，不表示单次模型调用，也不表示整个跨用户调度批次。

对于同一任务的重试：

```text
第一次执行 ──失败等待重试──> 第二次执行 ──成功──> 同一个 runId 完成
```

模型调用的 `requestId`、`attemptId` 仍然按调用和 provider 尝试分别生成；它们不能替代 `runId`。

### 四个记录层的职责

| 记录 | 事实职责 | 关键关联 |
| --- | --- | --- |
| `AgentRunTrace` | 用户级工作流的开始、阶段、终态、耗时和低敏失败分类 | `user_id + run_id` |
| `TaskExecution` | 任务来源、幂等、领取、重试、checkpoint 和最终任务状态 | `task_id + run_id` |
| `ModelCallTrace` | 单次模型请求的路由、模型、Prompt 身份、用量、耗时和错误 | `user_id + run_id + request_id + attempt_id` |
| 业务结果表 | 日记认知、LifeGraph、周报或通知的实际业务结果 | 使用已有来源 ID / 任务 ID / 运行 ID |

不让 `AgentRunTrace` 取代 `TaskExecution`，也不把模型调用状态反写成用户事实。三个记录层可以独立失败，写 Trace 失败不能阻断用户业务结果；但任务的最终状态必须按业务写入结果处理。

## 运行边界

### 认知摄取

每个 `CognitionIngestCommand` 对应一个独立的认知摄取运行。它可以包含图片理解、认知路由、画像更新、中期记忆写入、LifeGraph bridge 和匹配画像刷新，但不把后续独立的 Diary LifeGraph 任务隐式合并进来。

原因是这两个工作流有不同的幂等和重试边界：认知路由失败不应阻止 LifeGraph 任务重试，LifeGraph 抽取重试也不应重复写入画像。

运行入口：

```text
DiaryCognitionIngestEvent
ChatCognitionIngestEvent
EmotionPlazaCognitionIngestEvent
    -> AgentCognitionOrchestratorImpl
        -> COGNITION_INGEST TaskExecution
        -> AgentRunTrace(scene=cognition_ingest)
```

`CognitionIngestCommand` 增加可选 `runId`。生产事件初始没有该字段时，由编排入口生成；测试、同步调用或已有调用方传入的非空值必须复用。认知任务的幂等键使用用户、来源类型、来源 ID 和来源版本，不使用随机 `runId`，从而重复事件可以取回原有任务和运行。

认知编排成功写入所有必要的业务派生结果后完成 AgentRun；空文本、无图片且无可处理内容属于可解释的跳过，不创建模型调用，但任务和运行仍以完成状态结束。未捕获异常导致任务失败时，运行记录进入失败状态。

### LifeGraph

Diary / Plaza 的 LifeGraph 任务继续由现有 `LifeGraphTask` 作为领域 worker 投影，由对应的 `TaskExecution` 作为通用任务账本：

```text
DiaryChangedEvent / PlazaCardChangedEvent
    -> LifeGraphTaskCreator / PlazaLifeGraphListener
        -> LIFE_GRAPH TaskExecution(runId)
        -> AgentRunTrace(scene=life_graph)
        -> LifeGraphTask worker
            -> ModelRouteContext(runId)
            -> ModelCallTrace(runId)
```

创建任务时生成 `runId`；如果幂等键已经存在，必须使用已存在 `TaskExecution.runId`，不能为重复事件生成新运行。这样来源版本替换和重复投递仍然只对应一个逻辑任务运行。

worker 领取任务后，从 `TaskExecution` 恢复 `runId`，在调用 `LifeGraphBuildService` 前打开运行上下文，并在 `finally` 中清理。删除、空内容和被新版本取代的任务没有模型调用，但仍应完成对应的 AgentRun。

瞬时错误进入任务重试等待时，AgentRun 保持 `RUNNING`，阶段更新为 `retry_wait`；只有任务最终成功或重试耗尽失败时，AgentRun 才进入终态。进程重启后，恢复的任务继续使用原 `runId`。历史缺少 `runId` 的任务保持兼容处理，不做批量回填；如确实需要执行模型，worker 可以为该旧任务补写一个新的运行关联并保留其原任务 ID，但这属于兼容分支，不改变新任务规则。

### 周报

周报调度是跨用户扫描，但每个实际生成的用户报告必须是独立的用户级运行：

```text
weekly-report scheduler batch
    -> user A: WEEKLY_REPORT TaskExecution + AgentRun
    -> user B: WEEKLY_REPORT TaskExecution + AgentRun
```

现有 `generationRunId` 对新生成的 `SoulReport` 改为保存该用户的 `AgentRun.runId`，与 `TaskExecution.runId` 一致；调度器本身的批次 ID 只用于日志和本轮扫描统计，不作为用户运行 ID。历史报告中的批次值保留，不回填 Trace。

周报任务的幂等键必须由用户和报告周期稳定生成，不能再包含每次调度随机产生的批次 ID。这样同一周重复扫描会取回已有任务，不会因为新的扫描批次重复生成报告。没有日记和对话活动的用户不创建模型调用；已有的周期检查行为保持。

报告写入成功后，运行完成。通知失败不回滚报告，也不把已经成功的模型运行标记为失败；通知本身沿用现有日志和通知错误处理，后续另行补通知任务的运行关联。

### 主动问候

主动问候扫描本身是跨用户批处理，不创建一个全局 AgentRun。只有通过现有候选规则的用户才创建用户级任务：

```text
proactive greeting scan
    -> eligible user
        -> PROACTIVE_GREETING TaskExecution
        -> AgentRunTrace(scene=proactive_greeting)
        -> model call / fallback
        -> notification
```

新增 `TaskExecutionType.PROACTIVE_GREETING`，来源类型使用稳定的主动问候来源代码。幂等键至少包含用户和当前问候冷却窗口的稳定日期桶；同一小时重复扫描不能产生两条任务或两条通知。原有通知查询仍是业务冷却保护，任务唯一键负责并发扫描下的第二道保护。

动态问候模型调用失败但模板 fallback 成功发送时，用户级工作流视为完成，模型失败只留在 `ModelCallTrace`；通知创建失败或任务不可恢复时，任务和 AgentRun 才进入失败终态。这样运行终态表达用户工作流是否完成，而不是单个模型 provider 是否成功。

## `runId` 传播设计

### 统一运行协调器

新增轻量的后台运行协调能力，职责限制为：

1. 生成或复用 `runId`；
2. 幂等地调用 `AgentRunTraceService.start`；
3. 在当前异步线程安装带 `runId` 和 `userId` 的基础运行元数据；
4. 在工作流结束时完成、失败或更新重试阶段；
5. 使用 `try/finally` 清理线程局部状态。

它不负责业务事务、模型路由、任务领取或用户通知。可以在现有 `AgentRunTraceService` 上增加 scope API，也可以增加同包的 `AgentRunScopeService`；最终只保留一套进入/退出约定，避免四个工作流各自复制生命周期代码。

### 模型调用上下文

现有直接模型调用需要保留每个调用自己的 `scene` 和 Prompt 快照，同时继承外层的 `runId`、`userId` 和必要的 `requestId`：

```text
后台 scope: { runId, userId }
    -> cognition call: { scene, prompt snapshot, runId, userId }
    -> image call:     { scene, prompt snapshot, runId, userId }
    -> LifeGraph call: { scene, prompt snapshot, runId, userId }
```

不得用一个“全局当前 Prompt”覆盖运行 ID，也不得让单次调用结束时的 `clear()` 清掉外层运行上下文。建议让 `ModelRouteContextHolder` 支持可恢复的嵌套 scope：调用级 scope 退出后恢复后台基础 scope，最外层异步任务退出才清理全部状态。所有现有直接模型调用都必须使用同一套 scope API。

异步线程池不会自动传播请求线程中的模型上下文。每个 `@Async` listener、定时任务用户分支和 LifeGraph worker 都必须显式打开 scope；禁止依赖 MDC 或 ThreadLocal 的隐式跨线程继承。

### 重试与终态

| TaskExecution 状态 | AgentRun 状态 | 处理 |
| --- | --- | --- |
| `PENDING` / `RUNNING` | `RUNNING` | 任务已创建或正在执行 |
| `RETRY_WAIT` | `RUNNING` | 更新阶段为 `retry_wait`，保留同一 `runId` |
| `SUCCEEDED` | `COMPLETED` | 幂等完成，重复完成不改变终态 |
| `FAILED` | `FAILED` | 写入低敏失败分类和耗尽重试信息 |
| `CANCELLED` | `CANCELLED` | 仅在未来支持显式取消的工作流使用 |

如果任务状态已经是终态而 AgentRun 尚未更新，补偿路径可以重复调用完成或失败方法；AgentRun 终态更新必须幂等。模型调用 Trace 的失败不自动决定任务终态，业务 worker 根据 fallback、重试策略和结果写入决定。

## 数据模型与数据库变更

### 复用现有字段

- `agent_run_trace.user_id`、`run_id`、`scene`、`status`、`current_stage`、`failure_category`：不新增重复运行表。
- `task_execution.run_id`：所有本切片新建的用户级任务必须填写；原有字段已经有索引。
- `model_call_trace.run_id`、`user_id`：由模型路由上下文自动写入；Prompt key/version/locale 继续沿用上一切片的快照身份。
- `SoulReport.generation_run_id`：新报告保存用户级 AgentRun ID；历史值保持兼容。

### 必要代码字段和枚举

- `CognitionIngestCommand` 增加可选 `runId`。
- `TaskExecutionType` 增加 `COGNITION_INGEST`、`PROACTIVE_GREETING`。
- 如需要区分来源，`TaskExecutionSourceType` 增加对应稳定来源代码；来源字段仍表达实际业务来源，不能把用户 ID 当作泛化来源类型。
- `ModelCallTraceQuery` 增加 `runId` 过滤，管理员查询必须继续受现有权限和用户 scope 限制。

### 索引

现有 `agent_run_trace(user_id, run_id)` 和 `task_execution(run_id)` 已满足主要关联。为模型轨迹查询增加：

```sql
KEY idx_model_call_trace_user_run_created (user_id, run_id, created_at)
```

不增加跨用户的裸 `run_id` 外键或全局唯一约束。`runId` 不是用户可见秘密，但查询和审计必须始终带用户 scope，避免同一字符串跨用户串读。

### 不新增表

本切片不新增 AgentRun、工作流实例或通用事件表。若未来需要工具调用明细、记忆召回摘要或人工确认步骤，再以独立低敏记录定义其职责，不把本切片的 `AgentRunTrace` 变成万能 JSON 容器。

## 业务流程

### 一般用户级后台流程

```text
入口
  -> 计算稳定幂等键
  -> createOrGet TaskExecution(runId)
  -> 若任务已成功，直接跳过
  -> start AgentRunTrace(userId, runId, scene)
  -> claim / 执行业务步骤
       -> open run scope
       -> 每次模型调用继承 runId 并记录 ModelCallTrace
       -> close call scope
  -> 写入业务结果
  -> TaskExecution 成功 + AgentRun 完成
```

异常时：

```text
瞬时失败 -> TaskExecution.retry -> AgentRun 保持 RUNNING
最终失败 -> TaskExecution.failed -> AgentRun FAILED
线程退出 -> finally 清理 ModelRouteContextHolder
```

业务结果写入和任务状态更新继续遵循现有事务边界；不在持有长数据库事务时调用 LLM。

### 认知与 LifeGraph 的来源关系

同一个 Diary 修改可能产生两个独立的用户级运行：

```text
DiaryChangedEvent
  ├─ cognition ingest run  -> persona / mid-memory / bridge
  └─ life graph task run    -> LifeGraph extraction / provenance replacement
```

它们可以共享同一个 `triggerEventId` 或来源版本，但不能因为来源相同就复用同一个 `runId`。这样两个流程的失败、重试和完成状态不会互相覆盖。

## 安全与隐私边界

- `AgentRunTrace` 只保存用户、场景、阶段、终态、耗时、工具计数和低敏错误分类。
- `TaskExecution.checkpointJson` 继续限制长度和内容，不放入日记正文、Prompt、模型输出或密钥。
- `ModelCallTrace` 只保存模型路由和用量元数据，不保存原始请求、响应或思考内容。
- 管理端按现有安全审计和用户 scope 查询运行记录；不能通过 `runId` 反查其他用户。
- 不把用户自定义秘钥、云端备份秘钥或解密后的 Diary 内容写入任何运行记录。
- Trace 写入失败应记录服务端低敏日志并继续业务流程；业务失败仍必须由 `TaskExecution` 和 AgentRun 记录可解释终态。

## 兼容与失败处理

### 重复事件与并发

- `TaskExecution` 以稳定幂等键为第一道保护。
- `AgentRunTraceService.start` 对同一 `userId + runId` 幂等；并发插入遇到唯一键冲突时回查已有记录。
- 已存在任务的 `runId` 不得被新事件覆盖。
- 已完成任务不重新发送通知、不重复写入派生结果；已有领域服务的来源版本和证据幂等继续生效。

### 旧任务

历史任务的 `run_id` 可以为空。新代码读取时必须允许为空，不因为旧任务无法关联 Trace 而阻断任务处理。默认不做全量回填；仅当旧任务实际进入模型 worker 且需要追踪时，在任务级别补建一个新的运行关联，并在日志中区分 `legacy_run_assigned`。

### 失败分类

AgentRun 的 `failure_category` 只使用低敏类别，例如 `validation`、`dependency`、`model_error`、`notification_error`、`timeout`、`unknown`；禁止把异常 message、Prompt 或用户文本直接写入 Trace。

## 实现文件边界

预计修改位置：

- `src/main/java/com/aseubel/yusi/pojo/dto/cognition/CognitionIngestCommand.java`
- `src/main/java/com/aseubel/yusi/service/cognition/impl/AgentCognitionOrchestratorImpl.java`
- `src/main/java/com/aseubel/yusi/service/lifegraph/LifeGraphTaskCreator.java`
- `src/main/java/com/aseubel/yusi/service/lifegraph/LifeGraphTaskBatchService.java`
- `src/main/java/com/aseubel/yusi/service/lifegraph/PlazaLifeGraphListener.java`
- `src/main/java/com/aseubel/yusi/service/report/SoulReportGenerator.java`
- `src/main/java/com/aseubel/yusi/service/agent/impl/AgentProactiveServiceImpl.java`
- `src/main/java/com/aseubel/yusi/service/ai/runtime/AgentRunTraceService.java`
- `src/main/java/com/aseubel/yusi/service/ai/model/ModelRouteContextHolder.java` 及其直接调用点
- `src/main/java/com/aseubel/yusi/service/task/TaskExecutionService.java`（只补充读取/状态协作 API）
- `src/main/java/com/aseubel/yusi/pojo/constant/TaskExecutionType.java`
- `src/main/java/com/aseubel/yusi/pojo/constant/TaskExecutionSourceType.java`
- `src/main/java/com/aseubel/yusi/pojo/constant/TaskExecutionKeys.java`
- `src/main/java/com/aseubel/yusi/pojo/dto/model/ModelCallTraceQuery.java`
- `src/main/java/com/aseubel/yusi/service/ai/model/ModelManagementService.java`
- `src/main/resources/db/migration/V20260825__add_model_trace_run_scope_index.sql`
- `src/main/resources/db/init.sql`（保持新安装 schema 与增量迁移一致）

不修改匹配批处理的 `generationRunId` 语义，不把 `MatchServiceImpl` 纳入本切片。匹配需要独立的跨用户批运行模型，后续另行设计。

## 测试边界

实现时先写失败测试，再写生产代码。至少覆盖：

1. `AgentRunTraceService` 对相同用户和运行 ID 的 start/complete/fail 幂等行为。
2. 认知摄取为没有 `runId` 的命令生成运行 ID，并将相同 ID 传入任务和模型上下文；重复事件复用已有任务。
3. Diary / Plaza LifeGraph 任务创建时写入 `TaskExecution.runId`，worker 重试时保持不变，模型上下文在退出后被清理。
4. 周报每个用户拥有独立运行 ID，重复调度不因批次 ID变化而重复生成，同一报告的任务与结果使用同一个 ID。
5. 主动问候并发扫描对同一用户只创建一个任务和一条通知；模型 fallback 成功时运行完成，通知失败时运行失败。
6. `ModelCallTrace` 的查询可以按 `userId + runId` 筛选，不能跨用户读取同名运行 ID。
7. 瞬时任务失败进入 `RETRY_WAIT` 时 AgentRun 不提前终止，重试耗尽才标记失败。
8. `ModelRouteContextHolder` 的嵌套 scope 恢复外层运行上下文，异步任务结束后无 ThreadLocal 泄漏。

不启动服务，不依赖远程模型、Milvus 或跨域环境；使用 Mockito、Spring slice 或现有测试替身验证关联和状态转换。

## 验收标准

- 对认知、LifeGraph、周报和主动问候的任一新后台运行，可以按 `userId + runId` 找到 AgentRun、任务执行和该运行的模型调用。
- 同一任务的重复事件和重试不会创建新的逻辑运行，不重复生成业务结果或通知。
- 每个异步边界都显式设置并清理运行上下文；线程复用不会串用户或串运行。
- AgentRun 终态与用户级工作流结果一致，单次模型失败不会误报整个 fallback 成功的问候失败。
- 匹配批处理仍保持现有 `generationRunId`，没有被错误纳入用户级 AgentRun。
- 运行与模型 Trace 不包含用户原文、模型输出、Prompt 正文或秘钥。
- 新增迁移、初始化 schema、单元测试和 roadmap 记录全部完成后，`mvn -q test` 通过。
