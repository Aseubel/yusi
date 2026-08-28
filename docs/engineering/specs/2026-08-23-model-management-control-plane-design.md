# 模型治理控制面重构设计

日期：2026-08-23

状态：待用户审阅

## 1. 背景与调研结论

当前模型治理已经具备 schema v2、物理模型注册表、tier、场景路由、运行时健康状态和调用轨迹，但控制台没有把这些对象之间的生效关系表达清楚。

本次调研确认了以下事实：

- 模型 `priority` 已存在，但只在模型编辑抽屉中出现，模型列表、tier 候选顺序和路由预览均未展示它。
- 路由规则 `priority`、模型 `priority` 和模型 `weight` 是三个不同字段。路由规则 priority 决定同场景规则匹配顺序；模型 priority 只由 `FAIL_OVER` 使用；weight 只由 `WEIGHTED_RANDOM` 使用。
- 生产配置中 DeepSeek 的模型 priority 为 1，Qwen 为 2，因此所有包含这两个模型的 `FAIL_OVER` tier 都先尝试 DeepSeek。当前 `INVALID_REQUEST` 不允许 fallback，400 会直接失败。
- `riskLevel` 可以编辑，但当前路由匹配器没有使用它。
- tier 策略属于 tier，但页面只通过当前路由的主 tier 修改策略，fallback tier 的策略不可见且不可直接管理。
- 健康页面只展示已经产生运行态的模型；无状态模型会被隐藏，部分 tier 统计却会把无状态模型视为健康。
- 路由预览读取已发布配置，不读取当前未保存草稿，因此预览可能与编辑器内容不一致。
- 状态中心已经通过 Redis Hash 和 Pub/Sub 同步运行态，但没有管理员 reset 接口。

## 2. 目标与边界

### 2.1 目标

1. 让管理员能从总览直接判断模型是否可用、为什么不可用、正在被哪些场景使用以及下一步应该执行什么操作。
2. 让模型注册、tier 成员、tier 策略和场景路由各自拥有清晰的责任边界。
3. 让 priority、weight、latency 和 fallback 顺序在界面与后端实际算法中保持一致。
4. 让路由预览基于当前草稿计算，并显示实际排序依据和排除原因。
5. 提供单模型 reset 和全部模型 reset，不修改治理配置、历史调用轨迹或历史指标。
6. 保持低敏观测边界，不保存或展示 prompt、回答、思考内容、图片 URL、工具参数和 API key。

### 2.2 不做兼容保留

本次允许直接重构现有控制台语义和内部 DTO，不保留旧版页面行为、旧版预览语义或重复的兼容分支。schema v2 仍作为唯一治理配置格式，但控制台只维护一套新的读写和预览路径。

不新增数据库表保存短期运行状态。Redis 仍是运行时健康状态的事实来源，现有安全审计表用于记录管理员 reset 动作。

## 3. 领域模型与生效规则

控制面固定为四层：

```text
物理模型 Model
    -> 逻辑层级 Tier
        -> 场景路由 RoutePolicy
            -> 请求级候选链 ModelRouteDecision
                -> Provider attempt
```

### 3.1 物理模型

物理模型只负责供应商连接和模型自身属性：

- provider、protocol、endpoint、真实模型 ID、密钥配置状态；
- capabilities、scenes、上下文窗口和价格快照；
- `priority`、`weight` 两个调度属性；
- enabled 和运行时健康状态。

已注册模型的内部 ID 在编辑时不可修改。需要更换内部 ID 时，先新建模型并迁移 tier 成员，避免旧成员关系残留。

### 3.2 Tier

Tier 是一组具有共同能力边界和共同选择策略的模型成员：

- `members` 决定候选集合；
- `strategy` 决定候选排序；
- `capabilities` 和模型 capabilities 共同决定成员是否有资格参与；
- `enabled` 决定整个 tier 是否可以被路由；
- 模型的 priority 和 weight 保留在模型层，不复制到 tier。

### 3.3 RoutePolicy

路由规则只负责把场景和风险上下文映射到主 tier 与有序 fallback tier：

- 先按场景匹配：精确场景优先于 `*`；
- 如果请求带有 risk level，精确 risk 优先于通配 risk；请求没有 risk 时不限制 risk；
- 在相同场景和 risk 匹配级别内，route priority 数值越大越优先；
- 最后以 route ID 做稳定排序；
- route priority 不参与 tier 内模型选择。

`riskLevel` 从展示字段改为真实匹配条件。已有只有一个同场景规则的配置，其行为保持为该场景规则；不再保留一个“可编辑但不生效”的字段。

### 3.4 Tier 选择策略

| 策略 | 实际行为 | 使用字段 | 不使用字段 |
|---|---|---|---|
| `ROUND_ROBIN` | 在可用成员之间按 tier 独立游标轮转；不可用成员排在候选尾部 | 健康状态、tier 游标 | priority、weight、latency |
| `LEAST_LATENCY` | 优先选择有采样且平均延迟最低的可用成员；无采样成员作为冷启动候选排在有采样成员之前；同值按模型 ID 稳定排序 | 健康状态、平均延迟、采样情况 | priority、weight |
| `WEIGHTED_RANDOM` | 在可用且 weight 大于 0 的成员中按权重随机；零权重成员不进入选择 | 健康状态、weight | priority、latency |
| `FAIL_OVER` | 先按可用性，再按模型 priority 升序，最后按模型 ID 稳定排序 | 健康状态、priority | weight、latency |

模型 weight 允许为 0，但当一个 `WEIGHTED_RANDOM` tier 没有正权重成员时，配置校验失败。这样 `ZERO_WEIGHT` 会成为真实可达的排除原因，而不是被全局正数校验提前掩盖。

路由 fallback 只在主 tier 的候选链耗尽且错误属于可回退类型时触发。`INVALID_REQUEST`、协议不匹配、能力不匹配等确定性错误不触发 fallback，控制台必须显示该结论。

## 4. 后端设计

### 4.1 控制面快照

现有治理快照改为面向控制台的完整投影，保留 schema v2 配置作为保存载荷，但增加可读运行信息：

- 模型：priority、weight、protocol、capabilities、scenes、tier IDs、使用它的 route IDs、当前运行态、最近错误和最近更新时间；
- tier：策略、能力要求、成员总数、健康/探测中/不可用/未采样数量、按当前策略计算的候选顺序；
- route：场景、risk、route priority、主 tier、fallback tier 顺序、每个 tier 的策略和总体可用性；
- summary：启用模型数、可用/探测中/不可用/未采样模型数、无可用主 tier 的 route 数、fallback 比例、最近失败。

运行态没有记录的模型不再被伪装成健康，统一以 `UNKNOWN`/“未采样”展示。路由运行时仍将未采样模型视为可尝试候选，但控制面必须明确标记这是冷启动状态。

### 4.2 路由预览

路由预览请求包含：

- scene、risk level、估算输入 token、预留输出 token；
- 当前治理草稿的模型、tier、route 和 default route 投影。

预览使用独立的纯规划服务，根据配置定义和 Redis 运行态计算候选链，不创建 Provider client，不读取或接收 API key。未保存模型也可以参与排序预览，但会标记为“尚未发布”。

每个候选返回以下低敏字段：

- tier、模型 ID、provider、真实模型名；
- strategy、priority、weight、平均延迟和运行阶段；
- rank、available、attemptable；
- 稳定排除原因和人类可读解释；
- 是否属于 fallback tier。

`routeReason` 改为结构化字段，不再依赖前端解析分号拼接字符串。

### 4.3 运行态 reset

新增两个管理员接口：

```text
POST /api/model/states/{modelId}/reset
POST /api/model/states/reset
```

单模型 reset 和全部 reset 都只操作短期运行态。每个被 reset 的模型执行以下重置：

- phase = `UP`，available = true；
- total/success/failure requests = 0；
- consecutive failures/successes = 0；
- avg latency、error rate、qps = 0；
- lastError = null；
- nextProbeAt = 0；
- 保留 instanceId 和 modelName。

reset 使用比当前状态更大的单调 `lastUpdatedAt`，写入 Redis Hash 并发布 `ModelStateEvent(action=RESET)`。所有实例的 Pub/Sub listener 必须立即合并 reset 到本地窗口；Redis Hash 仍作为事件丢失后的最终收敛来源。

全部 reset 的目标集合是 Redis 状态 Hash、当前实例本地窗口和当前配置中已出现的运行态 ID 的并集。没有状态记录的模型不需要写入一条空状态。

Redis Hash 写入失败时接口返回失败；Pub/Sub 发布失败但 Hash 已写入时，接口返回“已写入、等待节点收敛”的明确结果，并记录低敏告警日志，不能返回 `unknown`。

管理员 reset 通过现有 `security_audit_event` 记录：新增 `MODEL_RUNTIME_STATE_RESET` action，resource type 使用 `MODEL_GOVERNANCE`，resource ID 为模型 ID 或 `all`，details 只包含 operation、scope、count 等 allow-list 字段。

### 4.4 配置校验

服务端校验继续是最终边界，前端只提供提前反馈。新增或收敛以下规则：

- 模型 ID、tier ID、route ID 唯一且不可通过普通编辑改名；
- priority 必须大于等于 0；weight 必须大于等于 0；
- `WEIGHTED_RANDOM` tier 至少有一个正权重、启用且能力匹配的成员；
- route 的主 tier 与 fallback tier 都必须存在、启用且有场景匹配成员；
- risk level 统一使用 `LOW/MEDIUM/HIGH/*`；
- route priority、token 限制和生成参数继续执行范围校验；
- provider/protocol/capability 组合必须可由 Provider adapter 支持。

校验错误返回稳定的 field path 和 error code，前端可以定位到具体模型、tier 或 route，不再只显示一段总错误文本。

## 5. 前端信息架构

### 5.1 总览与健康

总览首屏由四部分组成：

1. “当前配置”摘要：版本、未发布草稿、最近刷新时间；
2. “可用性”摘要：UP、UNKNOWN、HALF_OPEN、DOWN 和无可用 route 数量；
3. 模型主表：模型、协议、priority/weight、参与 tier、当前状态、最近错误、连续失败、延迟；
4. 操作区：单模型 reset、全部 reset、刷新。

全部 reset 必须二次确认，确认文案明确“只清理短期健康状态，不改配置和历史指标”。健康数据自动定时刷新，同时保留手动刷新和最近刷新时间。

### 5.2 模型与 Tier

模型页拆为两个并列视图：

- 模型注册：身份/协议、endpoint/密钥状态、能力/场景、调度参数、tier 使用情况和运行状态；
- Tier 管理：tier 名称/描述、策略、能力要求、成员顺序、成员实际 priority/weight/latency、候选健康情况。

模型调度参数旁边直接显示生效范围：

- “priority：仅 FAIL_OVER 生效，数值越小越优先”；
- “weight：仅 WEIGHTED_RANDOM 生效，数值为 0 表示不参与该策略”；
- “平均延迟：仅 LEAST_LATENCY 生效，由运行态采样计算”。

编辑已有模型时锁定内部 ID。新增模型必须先保存模型身份，再配置 tier 成员。取消编辑不会提交或残留 tier 变更。

### 5.3 场景路由

路由页以“场景”为第一索引，而不是以字段表单为第一索引。列表展示：

- scene、risk、route priority；
- 主 tier 和 fallback tier 链；
- 每个 tier 的 strategy；
- 主链和 fallback 的整体可用性；
- 当前是否存在未发布修改。

详情区按以下顺序展示实际决策链：

```text
请求场景 / 风险
    -> 命中 route
        -> 主 tier + strategy
            -> 成员排序与排除原因
        -> fallback tier 1 + strategy
        -> fallback tier 2 + strategy
```

route 编辑器只编辑 route 自身。修改 tier strategy 和 tier members 必须跳转到 Tier 管理，不再让 route 编辑器偷偷修改主 tier 的全局策略。

### 5.4 调用活动

调用活动保留现有低敏边界，但详情增加结构化 route decision、候选排序依据、错误分类和 upstream HTTP status（若已被归一化采集）。模型和场景筛选使用新的模型索引。

## 6. 数据库迁移

短期运行态仍只在 Redis，不新增运行态表。模型 reset 审计复用已有 `security_audit_event`，因此不需要为 reset 新建表。

为了支持模型主视图和模型详情中的调用轨迹查询，新增手工 migration：

文件：`src/main/resources/db/migration/V20260831__add_model_management_read_indexes.sql`

```sql
ALTER TABLE `model_call_trace`
    ADD KEY `idx_model_call_trace_model_created` (`model_id`, `created_at`),
    ADD KEY `idx_model_call_trace_scene_model_created` (`scene`, `model_id`, `created_at`);
```

同时把相同两个索引加入 `src/main/resources/db/init.sql`，保证新环境和存量环境一致。项目当前没有自动 Flyway 依赖，部署时按现有 migration 流程执行该脚本；不在应用启动时自动修改数据库结构。

## 7. 错误处理与安全边界

- 配置保存、草稿预览和 reset 均要求管理员权限。
- reset 不清理调用轨迹、Prometheus/Micrometer 指标、配置版本或 Redis runtime config。
- reset 失败必须区分 Redis 写失败、Pub/Sub 延迟和参数错误；不再返回或展示 `unknown` 作为唯一原因。
- 任何日志和 DTO 都不得包含 API key、prompt、回答、思考内容、图片 URL、工具参数或完整上游响应体。
- 上游错误只保留错误类别、HTTP status、provider 错误码和截断后的低敏摘要。

## 8. 验证边界

不新增测试文件，不引入新的测试套件。实现后执行：

- 现有相关 Maven 测试和后端编译；
- 现有前端 TypeScript、lint、production build；
- `git diff --check`；
- migration SQL 静态检查，并在可用数据库环境执行一次索引存在性检查；
- 手动验证模型 priority/weight 在四种策略下的排序展示、草稿预览、单模型 reset、全部 reset 和多 Docker 实例收敛。

如果现有认证映射合同因新增两个管理员接口而变化，只同步修改已有合同基线，不新增测试。

## 9. 验收标准

- 管理员在总览一屏内可以知道哪个模型不可用、最近错误是什么、影响哪些 tier/scene，以及可以直接 reset。
- 模型列表直接展示 priority 和 weight，并明确哪个策略使用哪个字段。
- 每个场景都能看到命中的 route、主 tier、fallback tier、tier strategy 和实际候选顺序。
- 路由预览反映当前草稿，而不是旧的已发布配置。
- `riskLevel` 参与真实路由匹配，route priority 与 model priority 的展示和实际算法一致。
- 单模型和全部模型 reset 后无需清 Redis 或重启服务；所有 Docker 实例最终状态一致。
- reset 不改变治理配置和历史调用指标，且有低敏管理员审计记录。
- migration 脚本可在存量库执行，`init.sql` 可初始化同等索引。

