# Yusi 最小告警通道设计

> 状态：待评审设计
>
> 范围：Phase 5 最小告警通道，接收端固定采用飞书告警机器人 webhook。本文只记录现状、设计和验证边界，不修改生产代码、配置、测试、CI、migration 或 roadmap。

## 1. 目标与不变约束

本刀为 roadmap `docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md:627-631` 的实施准备，覆盖四类告警：服务不可用、模型调用失败率、后台任务积压、预算准入拒绝。告警消息只提供低敏运维事实，不承担业务诊断、用户定位或正文传输职责。

飞书 webhook URL 和签名密钥是运行时凭据，只允许通过环境变量或部署平台 Secret 注入。代码、`application*.yml` 明文值、测试 fixture、测试断言输出、日志、指标标签和告警消息均不得出现 URL、密钥或其片段。设计文档只使用“运行时注入的 webhook URL/签名密钥”这样的占位描述，不记录真实地址。

告警组件必须满足以下故障隔离边界：

- webhook 调用在告警异步通道中执行，失败只产生低敏重试日志，不阻塞 HTTP、模型调用或后台任务。
- 告警组件、去重存储或飞书网络故障不能把 readiness 置为 DOWN，也不能改变既有 health contributor 的结果。
- 告警字段、去重 fingerprint、Redis key 和指标标签只能来自固定枚举、计数、时间窗口和服务分类；禁止 `userId`、`query`、正文、prompt、token、模型响应、完整 object key、请求参数和异常 message/stack。
- 本地 mock 能证明“评估和调用契约”，不能证明飞书真实送达、Prometheus 抓取、Alertmanager 规则生效或轮值人员收到通知。

## 2. 现状勘察与事实核验

### 2.1 Actuator、健康组和管理面

当前工程已经具备告警可复用的健康入口，而不是缺少 Actuator：`pom.xml:69-75` 声明 `spring-boot-starter-actuator` 和 `micrometer-registry-prometheus`。基础配置 `src/main/resources/application.yml:28-42` 的事实如下：

| 配置键 | 当前值/语义 | 告警用途 |
| --- | --- | --- |
| `management.endpoints.web.exposure.include` | `health,prometheus` | 可供内部探针和后续指标采集使用 |
| `management.endpoint.health.show-details` | `never` | 不能从管理接口取得敏感详情；告警只能消费状态和固定分类 |
| `management.endpoint.health.probes.enabled` | `true` | 启用 liveness/readiness 探针语义 |
| `management.endpoint.health.group.liveness.include` | `livenessState` | 服务进程存活信号，不包含外部依赖 |
| `management.endpoint.health.group.readiness.include` | `readinessState,db,redis,milvus,modelGateway,tasks` | 服务是否可以接收流量的聚合信号 |

生产管理端口和绑定地址在 `src/main/resources/application-prod.yml:15-18` 通过 `MANAGEMENT_SERVER_PORT` 与 `MANAGEMENT_SERVER_ADDRESS` 注入，当前默认端口为 `20611`、默认地址为 `0.0.0.0`。现有 `HealthEndpointExposureTest` 已在 `src/test/java/com/aseubel/yusi/observability/health/HealthEndpointExposureTest.java:39-51` 锁定暴露面和 `show-details`，并在 `:55-69` 锁定 liveness/readiness 成员以及 liveness 的独立性。

现有 contributor 可直接作为第一版服务不可用信号的事实来源：

- Redis contributor `src/main/java/com/aseubel/yusi/observability/health/RedisHealthIndicator.java:10-36` 使用固定的非用户探针 key `yusi:health:probe`，只返回 `dependency=redis` 和固定分类。
- Milvus contributor `src/main/java/com/aseubel/yusi/observability/health/MilvusHealthIndicator.java:10-38` 只对固定 collection `yusi_embedding_collection` 做 existence 检查，并返回固定分类。
- 模型 contributor `src/main/java/com/aseubel/yusi/observability/health/ModelGatewayHealthIndicator.java:19-25,39-81` 根据本地路由和运行状态判断是否有可用 tier；`src/main/java/com/aseubel/yusi/observability/health/ModelGatewayHealthIndicator.java:19-22` 明确其不调用真实 chat 或 embedding 模型。
- 任务 contributor `src/main/java/com/aseubel/yusi/observability/health/TaskHealthIndicator.java:10-40` 读取 `TaskHealthRegistry`，当前以 FAILED 和超过两小时未成功作为 readiness 健康判断；阈值事实为 `:14` 的 `STALE_AFTER=2 hours`，不是 roadmap 所称的 due gap/lag 指标。

因此，第一类告警应优先读取 `HealthEndpoint` 的 readiness 结果或同一组 contributor 的固定分类，而不是从公开接口抓取详情。liveness DOWN 可以作为服务进程故障的补充信号，但 liveness 不应因为依赖故障而告警风暴。

### 2.2 已有指标和缺失指标

`YusiMetrics` 已经执行低基数归一化。`src/main/java/com/aseubel/yusi/observability/metrics/YusiMetrics.java:21-36` 给出了工具、操作、结果和失败分类的固定白名单；`src/main/java/com/aseubel/yusi/observability/metrics/YusiMetrics.java:138-144` 实际写入的标签键恰为 `tool`、`operation`、`result`、`failure_category`。已有指标如下：

| 指标 | 代码事实 | 可用于 |
| --- | --- | --- |
| `tool_search_total`、`tool_search_failure_total` | `YusiMetrics.java:48-70` | 既有检索失败质量观测，不是本刀四类告警的主信号 |
| `tool_search_latency`、`tool_search_results` | `YusiMetrics.java:72-81` | 检索耗时和结果量，保留为诊断旁路 |
| `model_call_total`、`model_call_failure_total`、`model_call_latency` | `YusiMetrics.java:87-102` | 模型失败率窗口计算 |
| 任务记录 | `YusiMetrics.java:108-115` 将 `recordTask` 转发为 `tool_search_*`，不是独立 backlog gauge | 只能作为现有成功/失败计数事实，不能冒充 due gap/lag |

模型计数的来源是 `src/main/java/com/aseubel/yusi/service/ai/runtime/ModelCallTraceService.java:32-45`：事件先调用 `metrics.recordModelCall(...)`，再持久化调用轨迹；失败持久化日志也只在本次设计中作为上下文事实，不是告警来源。已有 trace 关联实现位于 `src/main/java/com/aseubel/yusi/observability/trace/TraceIdSupport.java:11-13,32-70`，日志格式读取 MDC 的 `traceId` 位于 `src/main/resources/logback-spring.xml:5-6`；本刀只复用它做受控排障关联，不把 traceId 加入告警字段或指标标签。

路线图的前置描述需要明确纠正为“部分已经存在、部分待补齐”。仓库生产实现中没有 `dependency_health`、`task_due_gap`、`task_lag` 或 `budget_denied_total` 的声明/使用；全局代码和配置检索只能找到 readiness 组以及 `model_call_failure_total`。路线图 `docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md:627-631` 将四者并列描述为已暴露信号，但当前实现与该描述不一致。本刀不得据此伪造已存在的指标 PASS，计划必须先建立缺失信号契约。

预算拒绝的业务来源已存在但没有独立计数：`src/main/java/com/aseubel/yusi/service/ai/model/ModelBudgetAdmission.java:104-146` 可能返回 `ADMISSION_STORE_UNAVAILABLE`、`RESERVATION_CONFLICT` 和 `LIMIT_EXCEEDED:<dimension>`；`src/main/java/com/aseubel/yusi/service/ai/model/ModelProxyFactory.java:248-255` 与 `:315-326` 在 permit 未授予时分别发布 REJECTED 尝试。`LIMIT_EXCEEDED:<dimension>` 中的 dimension 不能直接作为标签，必须归一化为固定的 `limit_exceeded` 类别。`ModelCallTraceService.java:53-75` 将 REJECTED 识别为调用结果，但当前逻辑不能把所有 REJECTED 误算成预算拒绝，因此需要单独 `budget_denied_total`。

### 2.3 关键后台任务事实

`TaskHealthRegistry` 的类注释 `src/main/java/com/aseubel/yusi/observability/task/TaskHealthRegistry.java:11-14` 说明其只保存固定名称、有限状态、时间戳和失败分类。任务名白名单在 `:17-32`，状态写入在 `:49-89`，快照在 `:91-122`；当前快照没有 next due time、due gap 或 lag 字段。

调度入口在 `src/main/java/com/aseubel/yusi/common/task/scheduler/YusiScheduledTasks.java:55-140`，覆盖 usage-sync、memory-scan、room-cleanup、memory-fusion、embedding-worker、lifegraph-worker、task-execution-recovery、weekly-report、weekly-match 等固定作业。`src/main/java/com/aseubel/yusi/common/task/DistributedJobRunner.java:40-70` 对 leader job 记录 start/success/failure 和已有 task metric；`YusiScheduledTasks.java:143-153` 对 worker 也记录这些状态。该事实足以复用任务生命周期，但不等于已有可计算的调度积压指标。

### 2.4 Prometheus/Alertmanager 部署事实

当前仓库没有可以独立复核为“已部署”的 Prometheus/Alertmanager：

- `docs/devops/gitops_proposal.md:588-595` 只把 Prometheus + Grafana 抽取 Actuator 指标写成生产运维建议。
- `docs/devops/gitops_proposal.md:223-235` 写有 Kubernetes liveness/readiness probe 路径，但没有在该仓库声明 Prometheus scrape、Alertmanager deployment 或规则文件。
- `.github/workflows/deploy_k8s.yml:29-37` 运行测试并上传评测 artifact，`:124-150` 只检出外部 `yusi-infra`、更新镜像 tag 并推送 Kustomize 变更；没有 Prometheus/Alertmanager 规则发布步骤。
- `.github/workflows/build_deploy.yml:1-27` 标记为暂时弃用，并且只是 SSH 执行部署命令，不能作为 Prometheus/Alertmanager 已存在的证据。
- GitOps 建议的后端副本数为 2，见 `docs/devops/gitops_proposal.md:159-164`。应用内评估器若直接在每个实例上运行，必须有跨实例去重，否则会重复通知。

凭据注入的既有部署模式是 Secret 到环境变量：`docs/devops/gitops_proposal.md:128-147` 禁止提交真实密钥，`:187-207` 展示了 SecretKeyRef 注入环境变量的模式。飞书 URL/签名密钥应沿用该模式，不能写入仓库的 ConfigMap、YAML 默认值、测试或日志。

## 3. 四类告警与指标映射

### 3.1 映射总表

| roadmap 告警类别 | 当前可消费的事实 | 缺失/补齐项 | 第一版判定输入 |
| --- | --- | --- | --- |
| 服务不可用 | readiness 组：`readinessState,db,redis,milvus,modelGateway,tasks`，见 `application.yml:38-42`；各 contributor 的状态和固定分类 | `dependency_health` Gauge 尚不存在 | `HealthEndpoint.healthForPath("readiness")` 的 DOWN/UP、固定 component 分类；后续同时发布 `dependency_health` |
| 模型调用失败率 | `model_call_total`、`model_call_failure_total`、`model_call_latency`，见 `YusiMetrics.java:87-102`；事件来源见 `ModelCallTraceService.java:32-39` | 无；需定义按所有固定标签聚合的窗口读取器 | 5 分钟 counter 增量比值 + 最小样本数 |
| 后台任务积压 | TaskHealthRegistry 固定任务状态和时间戳，见 `TaskHealthRegistry.java:17-122`；调度名和生命周期边界见 `YusiScheduledTasks.java:55-153` | `task_due_gap`、`task_lag` 及固定 schedule catalog 尚不存在 | 固定任务的 due gap/运行 lag，未知样本不伪造为零 |
| 预算准入拒绝 | `ModelBudgetAdmission.reserve` 的拒绝原因和 `ModelProxyFactory` 两个 permit 拒绝分支，见上述代码事实 | `budget_denied_total` 尚不存在；拒绝原因需归一化 | 5 分钟 counter 增量，按固定拒绝分类计数 |

### 3.2 缺失信号的规范

为了让当前应用内通道和未来 Alertmanager 共享同一语义，实施时新增以下低敏信号。它们是本刀的实现目标，不是当前现状：

- `dependency_health`：Gauge，值为 `1` 或 `0`；`operation` 只允许 `readiness`、`db`、`redis`、`milvus`、`model_gateway`、`tasks` 等固定成员，`result` 只允许 `up`、`down`、`unknown`，失败分类仍使用既有白名单。不要把异常 message、URL、连接串或组件详情写入标签。
- `task_due_gap`：Gauge，单位为分钟，表示固定任务超过其 expected next due instant 的非负分钟数；任务名进入既有 `operation` 白名单。无 schedule 或尚未形成样本时结果为 `unknown`，不写入一个看似正常的零值。
- `task_lag`：Gauge，单位为分钟，表示固定任务当前 RUNNING 的持续分钟数；非运行状态为零或明确 `not_running`，不把用户、taskId、sourceId 作为维度。若任务没有可靠的 start/success 样本，评估器输出 UNKNOWN 而不是告警。
- `budget_denied_total`：Counter，`operation=model_admission`、`result=denied`，拒绝分类只允许 `admission_store_unavailable`、`reservation_conflict`、`limit_exceeded`、`unknown`。`LIMIT_EXCEEDED:<dimension>` 的 dimension 只用于内部分类归一化，不能进入指标或消息。

上述指标沿用 `YusiMetrics.java:21-36,138-144` 的四键标签边界：`tool`、`operation`、`result`、`failure_category`。禁止新增 `userId`、`query`、`prompt`、`requestId`、`runId`、`traceId`、`model`、`provider`、`taskId`、`sourceId`、IP、URI 或任意请求参数标签。

## 4. 架构选型

### 4.1 方案 A：Prometheus Alertmanager 规则 + 飞书 receiver

Prometheus 采集 Actuator `/actuator/prometheus`，Alertmanager 根据 PromQL 规则执行窗口、for、grouping、dedup、inhibit 和恢复通知，再由 webhook receiver 适配飞书机器人。

优点：

- 规则、去重、静默和恢复语义由成熟平台负责，天然适合当前两副本部署。
- 模型失败率和预算 counter 的 `increase`/比值表达清楚，任务 gauge 的 `for` 语义也比应用内定时器容易复核。
- 业务进程不需要维护 webhook 重试队列，告警网络故障与业务线程隔离更清晰。

限制：

- 当前仓库只有 Actuator/Prometheus registry 依赖和 GitOps 规划，没有 Prometheus、Alertmanager 或规则 provisioning 的事实证据，见第 2.4 节。
- 本地 Maven 测试无法证明真实抓取、规则加载、Alertmanager 路由和飞书送达；若把 mock receiver PASS 当端到端 PASS，会掩盖部署缺口。
- 任务积压和预算拒绝的缺失指标仍需先补齐，不能直接引用路线图文字。

结论：作为平台成熟后的长期目标保留；不适合作为当前“最小可交付通道”的唯一实现。

### 4.2 方案 B：应用内轻量 AlertEvaluator + FeishuAlertNotifier

应用内定时器每 30 秒读取 `HealthEndpoint`/固定 health contributor、`MeterRegistry` counter 快照和 `TaskHealthRegistry` 快照，经过纯规则评估后生成 `AlertSignal`。`AlertStateStore` 负责跨实例 fingerprint 去重和恢复状态，`FeishuAlertNotifier` 只接受已脱敏的 `AlertMessage`，异步调用运行时注入的 webhook。

优点：

- 可以直接复用当前 readiness、模型计数和任务状态，不依赖尚未部署的 Prometheus/Alertmanager。
- 阈值、窗口、最小样本数、抑制和消息脱敏都能用 JUnit/Mockito 在本地确定性验证。
- 适合本刀先建立“规则评估 + receiver 调用契约”，真实送达仍独立列为部署验收。

代价与控制措施：

- 两个副本会重复评估，因此必须用 Redis 固定 key 的短租约/状态记录做分布式去重；Redis 不可用时退回有界的本实例抑制并记录低敏 `dedup_store_unavailable`，不能影响 readiness。
- 应用需要异步队列、重试和 backoff。队列必须有界，满载时丢弃重复通知并记录固定分类，不能阻塞业务。
- 评估器只读取现有状态，不主动调用模型、不执行写数据库、不创建 Milvus collection、不读取健康详情正文。

推荐方案：**当前实施采用方案 B**。原因是仓库没有 Prometheus/Alertmanager 已部署证据，而方案 B 可以在不伪造外部送达的前提下先把四类阈值、低敏消息和调用契约落地。方案 A 作为后续平台小刀：当 Prometheus scrape、Alertmanager rule provisioning、receiver 网络和 Secret 注入在 `yusi-infra` 有独立证据后，再迁移规则，应用内 evaluator 可以保留为本地规则契约或移除，但不重复发送。

## 5. 阈值、窗口、去重和恢复

以下全部是**初始值，待生产调优**，不能在上线报告中写成经过生产流量验证的最终阈值。评估周期建议 30 秒；计数窗口由 `Clock` 驱动的滑动快照实现，Alertmanager 迁移时转换为等价 PromQL `increase`/`rate`。

| 类别 | 初始触发条件（初始值，待生产调优） | 最小样本/持续时间 | 重复抑制 | 恢复通知 |
| --- | --- | --- | --- | --- |
| 服务不可用 | readiness 连续 DOWN 至少 2 分钟；可按固定 component 分类 | 4 次 30 秒评估；liveness DOWN 可直接进入 critical，但不替代 readiness | 同一 `service_unavailable + component + level` 30 分钟内只发一次；readiness 根告警抑制其下游 model/task 子告警 | readiness 连续 2 次 UP 后发一条 recovery；恢复也受 fingerprint 去重 |
| 模型调用失败率 | `model_call_failure_total` 窗口增量 / `model_call_total` 窗口增量 `>=20%` | 5 分钟窗口，至少 20 次调用；低于最小样本不告警 | 30 分钟；同一窗口内不因标签分片重复发送，先按所有固定标签聚合 | 失败率低于 20% 连续 2 个窗口后发送 recovery |
| 后台任务积压 | 任一固定任务 `task_due_gap >=15` 分钟或 `task_lag >=15` 分钟；`>=60` 分钟升级 critical | 条件持续 5 分钟；无可靠样本为 UNKNOWN，不报警 | 同一 task operation + level 30 分钟；服务不可用根告警存在时抑制相关子告警 | 连续一个完整健康窗口低于阈值发送 recovery；UNKNOWN 不发送 recovery |
| 预算准入拒绝 | `budget_denied_total` 5 分钟增量 `>=10` | 至少 10 次拒绝；按固定拒绝分类可在消息中只显示分类计数 | 30 分钟；同类别只发一条聚合告警 | 一个完整窗口增量低于 10 后发送 recovery |

补充策略：

- 所有阈值配置只能是非敏感数值、Duration、固定分类开关和接收级别；不得把 URL、Secret、用户或请求字段放进配置对象。
- fingerprint 只由 `category`、固定 `service/operation` 和 `level` 组成；窗口时间和计数不进入 fingerprint，避免同一事故每个窗口产生新告警。
- readiness DOWN 是根因抑制源：在根告警 active 期间，来自同一服务实例的模型/任务告警只更新内部状态，不重复推送；预算拒绝不自动被 readiness 抑制，除非固定分类为 `admission_store_unavailable` 且同一依赖根告警已 active。
- 多副本使用 Redis 状态 key 只保存 fingerprint、状态、最近发送时间和恢复状态；key 由固定枚举拼接，不含用户或请求字段。Redis 状态不可用不能反向改变应用 readiness。

## 6. 飞书低敏消息契约

### 6.1 字段白名单

飞书请求使用固定的协议 envelope（例如固定的文本消息类型和固定 `content.text` 容器），协议要求的 `msg_type`、时间戳和签名字段只作为传输层常量/派生值处理，不进入日志、诊断对象或语义告警字段。`content.text` 内只由固定模板生成，语义白名单字段为：

- `alert_category`：`service_unavailable`、`model_failure_rate`、`task_backlog`、`budget_denied`。
- `service`：固定服务名，例如 `yusi-backend`；不能来自请求参数。
- `operation`：固定 health/模型/任务操作名；未知值归一化为 `unknown`。
- `level`：`warning` 或 `critical`。
- `window`：固定格式的时间窗口，例如 `5m`，不是原始请求时间线。
- `count`：聚合调用/拒绝/任务数量，非负整数。
- `value`：失败率或 lag/due gap 的数值，固定小数位，不能携带正文。
- `classification`：固定依赖或拒绝分类。
- `observed_at`：UTC/Asia/Shanghai 的标准时间字符串，不含请求 ID。
- `state`：`firing` 或 `recovered`。

明确禁止进入语义消息和日志的字段及嵌套内容：`userId`、`query`、`keyword`、正文、prompt、response、token、requestId、runId、traceId、URL、webhook、签名密钥、model/provider 动态名、SQL、cache key、完整 object key、异常 message、stacktrace 和任意原始 HTTP 参数。协议必须传输的签名/时间戳只允许由运行时密钥派生后作为 transport auth 字段发送，不能被告警模板或测试断言记录。

### 6.2 固定文本示例

以下是脱敏模板示例，不是实际 webhook 地址或密钥：

```text
[YUSI ALERT]
alert_category=model_failure_rate
service=yusi-backend
operation=model_call
level=warning
window=5m
count=42
value=0.238
classification=timeout
state=firing
observed_at=2026-08-20T12:00:00Z
```

恢复消息只把 `state` 改为 `recovered`，并使用同一 category/service/operation fingerprint。消息构造器必须接收已归一化的 `AlertMessage`，不能接收 `Throwable`、请求对象、模型响应或任意 Map 直接序列化。

### 6.3 凭据与请求处理

运行时配置只读取：

- `YUSI_ALERT_FEISHU_ENABLED`：非敏感开关，生产由部署平台控制。
- `YUSI_ALERT_FEISHU_WEBHOOK_URL`：通过环境变量/Secret 注入的 webhook URL；不写入仓库默认值，不在日志和异常中输出。
- `YUSI_ALERT_FEISHU_SIGNING_SECRET`：通过环境变量/Secret 注入的签名密钥；只在内存中用于请求签名，不放入消息或诊断对象。

变量名本身不是凭据；其实际值不得出现在代码、配置文件、fixture、报告或日志。签名失败、HTTP 非成功响应和网络异常只返回固定 `delivery_failed`/`configuration_missing` 分类给重试状态机。

## 7. 失败语义和排障能力

告警失败日志只允许记录 `alert_category`、固定 `service/operation`、`attempt`、`backoff_class`、`exceptionType` 和 `delivery_failed` 等字段；不记录 URL、签名、消息 body、响应 body、异常 message/stack 或任何请求字段。日志应沿用既有 trace 关联能力，但 traceId 不能进入告警 payload 或指标标签。

通知流程为：规则评估 -> fingerprint 去重/租约 -> 有界异步队列 -> 固定消息构造 -> webhook 签名/发送 -> 有限重试。建议最多 3 次指数 backoff，随后进入冷却并保留低敏失败计数；评估器下一周期可再次尝试，但同一 fingerprint 仍受 30 分钟抑制。

去掉业务阻塞后，排障能力不再依赖原始响应或堆栈。替代手段是：

- 用 readiness 的固定 component/classification 判断依赖类别；实际管理端口访问和网络隔离由部署验收证明。
- 用 `model_call_total`/`model_call_failure_total` 窗口、固定 failure category 和健康状态组合定位模型故障面。
- 用固定 task operation、due gap/lag、last-success 状态定位后台任务；需要任务具体输入时通过受控后台查询，不把输入复制进告警。
- 用 `requestId`/`runId`/`traceId` 在受控日志系统中关联排障，但这些 ID 不进入指标标签和飞书消息。
- 用 `delivery_failed` 分类、重试次数和部署日志检查告警通道本身，不把 webhook 凭据暴露出来。

## 8. 本地测试与 deployment-only 边界

### 8.1 本地可证明的内容

使用 JUnit、Mockito、`SimpleMeterRegistry` 和 test profile 的无外部依赖上下文可验证：

1. **阈值评估**：固定 Clock 下验证 readiness 连续时长、模型最小样本和失败率、task due gap/lag、budget counter 增量、warning/critical 分级、根告警抑制、重复窗口和 recovery 条件。
2. **指标契约**：验证四个缺失信号的名称、类型、单位/数值语义、固定标签键和值白名单，拒绝 user/query/prompt/token/动态 model/provider/taskId 等输入。
3. **健康复用**：用固定 mock contributor/`HealthEndpoint` 返回 UP/DOWN/分类，验证评估器不调用模型、不执行写操作，liveness DOWN/依赖 DOWN 的边界按策略处理。
4. **任务契约**：用 `TaskHealthRegistry` 固定状态和 `TaskScheduleCatalog` 生成 due gap/lag；无样本必须保留 UNKNOWN，不把零值当健康证据。
5. **飞书消息低敏契约**：用 `AlertMessage` 构造 JSON，断言字段恰在白名单，禁止字段和敏感 sentinel 不出现；对 mock HTTP client 验证方法、请求头签名存在性、超时和重试次数。
6. **失败隔离**：mock webhook 抛异常时，验证业务调用返回不变、队列不阻塞、只记录固定分类，readiness contributor 不依赖 notifier。
7. **凭据静态门槛**：测试代码和配置扫描实际 URL、常量 Secret、消息序列化结果和日志参数，确保只存在环境变量名，不存在真实凭据。

上述 Feishu 测试的结论必须标注 `mock-contract-only`；mock client 被调用不等于飞书送达。

### 8.2 只能 deployment-only 的内容

以下不能在本地或 Mockito 报告中标 PASS：

- Secret 注入到生产 Pod 后的真实 webhook URL 解析、签名验证、飞书机器人真实送达和接收人确认。
- Prometheus 对 `/actuator/prometheus` 的真实抓取、Target 状态、rule reload、Alertmanager grouping/inhibit/silence/recovery 和 receiver 路由。
- 两副本/滚动发布下 Redis 去重租约的实际行为、网络分区和飞书限流。
- MySQL/Redis/Milvus/模型网关真实故障、readiness 变化、模型供应商失败率和真实任务积压调优。
- 值班接收人、升级路径、消息到达时延、飞书 API 配额与生产阈值调优。

部署验收记录必须分别写出“规则评估 PASS”“webhook 调用契约 PASS”“真实送达 PASS/未执行”，不能把前两项合并成通道可用。

## 9. 风险、边界与验收标准

### 9.1 诚实缺口

- 路线图列出的四个信号中，目前只有 readiness 和模型三项指标实际存在；`dependency_health`、`task_due_gap/lag`、`budget_denied_total` 必须先补契约。
- 当前任务健康 registry 是进程内存，重启会丢失历史；没有样本时不能可靠证明任务健康，初始策略必须 UNKNOWN/不告警，生产需要观察窗口或持久化/部署验收补充。
- Prometheus/Alertmanager 未在本仓库部署，方案 B 是过渡通道；未来迁移必须避免应用内 evaluator 与 Alertmanager 双发。
- 当前管理地址默认绑定 `0.0.0.0`，端口/网络 allowlist 与 Secret 注入仍是部署责任，不能由本地测试冒充完成。

### 9.2 本刀设计完成标准

评审通过后的实施必须满足：四类阈值和固定初始值有纯规则测试；缺失指标有明确实现或继续列为阻塞；飞书消息字段和凭据静态扫描通过；通知失败不影响业务/readiness；所有真实送达、真实抓取和轮值验证单列 deployment-only；roadmap `L627` 在评审验收前保持未勾选。
