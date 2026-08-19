# Yusi 统一健康检查与指标暴露设计

## 1. 目标与边界

本刀为 Phase 5 第三项“统一健康检查与指标暴露”做实现准备，目标是在不增加用户可见功能的前提下，让运维能够回答三件事：HTTP 服务是否存活、关键依赖是否可用、后台处理是否持续推进。实现范围覆盖 HTTP、MySQL、Redis、Milvus、模型网关和关键后台任务，并补齐现有日志格式已经预留但尚未写入的 traceId。

本刀只设计 actuator、Micrometer、健康组、低敏指标和 trace 传播。最小告警通道作为紧随的独立小刀，只在本设计中预留信号和接口；备份恢复、上线运维、roadmap 勾选、CI、migration、评测套件和 QualityGatePolicy 均不属于本刀。

## 2. 现状勘察

### 2.1 依赖与配置基线

- pom.xml 使用 Spring Boot 3.4.5，已有 web、websocket、validation、data-jpa、aop 和 test starter，但没有 spring-boot-starter-actuator，也没有显式 Micrometer registry。
- 生产使用 Hikari/MySQL datasource；测试 profile 使用 H2，且把 ddl-auto 设为 create-drop。
- Redis 不是 Boot 默认连接入口：RedisClientConfig 在非 test profile 下直接创建 RedissonClient，配置前缀为 redis.sdk.config；测试排除了 Redisson/Redis 自动配置并用测试基础设施替身。
- Milvus 由 MilvusConfig 在非 test profile 下直接创建 MilvusClientV2，启动时还会检查/创建三个集合；测试 profile 不应因健康检查触发这段连接初始化。
- application.yml 和 application-prod.yml 没有 management.* 配置，也没有管理端口。docs/devops/gitops_proposal.md 已经提出 /actuator/health/liveness、/actuator/health/readiness 和 Prometheus/Grafana，但这些只是部署建议，尚未成为应用契约。
- 当前 /api/health (PingController) 只返回固定字符串，不检查依赖，不能替代 actuator readiness/liveness。

### 2.2 已有可观测素材

- logback-spring.xml 已使用 %X{traceId}，但生产代码没有统一的 HTTP 请求入口 MDC.put("traceId", ...)。因此当前日志格式有字段、没有可靠写入点。
- ThreadPoolConfig 的 TaskDecorator 已复制提交线程的 MDC 和 UserContext，任务结束时执行 MDC.clear() 和 UserContext.clear()。它不会生成 traceId，也不覆盖 servlet、gRPC、WebSocket、SSE 和 scheduler 入口。
- ModelRouteContext 已包含 requestId、runId、scene、prompt identity 和 risk level；模型调用事件/ModelCallTraceService 已记录 route、provider/model、状态、失败分类、latency、fallback 和 usage 等低敏素材。
- ModelStateCenter 已维护每个模型实例的成功/失败次数、平均延迟、error rate、health score、连续失败和 UP/HALF_OPEN/DOWN 阶段，可作为模型网关健康判断的主要事实来源。
- AgentRunTrace/AgentToolTrace 是低敏生命周期记录，包含运行状态、当前阶段、工具数量、失败分类、开始/完成时间、耗时和工具名；不应把工具参数、结果或用户正文加入指标或健康响应。
- TaskExecution 已提供 PENDING/RUNNING/RETRY_WAIT/SUCCEEDED/FAILED/CANCELLED、retry、claim、completedAt、updatedAt 等状态。EmbeddingTaskRepository 已有 pending/failed count；LifeGraph task repository 有 pending/recovery/cleanup 查询，但没有统一跨任务汇总接口。
- YusiScheduledTasks 集中注册 memory scan、embedding、life graph、model state sync、task recovery、weekly matching/report 等周期任务；DistributedJobRunner 负责 Redis leader lock，但目前没有记录任务最后一次开始/成功/失败的统一状态。

## 3. 方案比较与选型

### 方案 A：只引入 actuator 默认探针

依赖 actuator 后直接使用 Boot 的 datasource、Redis 和 JVM 指标，并暴露默认 health。优点是改动少；缺点是项目 Redis/Milvus 是自定义客户端，默认 Redis health 不代表真实 Redisson 连接，Milvus 和模型网关没有默认 contributor，后台任务也没有覆盖，且管理端点容易被误暴露。不可满足目标。

### 方案 B：actuator + Micrometer + 低敏自定义 contributor（推荐）

引入 actuator 与 Prometheus registry，保留 Boot 的 HTTP/JDBC 基础能力，在项目边界增加 Redis、Milvus、model gateway、task health contributor；用 MeterRegistry 记录工具检索和模型/任务低敏指标；统一请求与异步入口注入 traceId。管理端点用独立管理端口或内部网络入口暴露，业务端口不公开 actuator。推荐此方案，因为它复用现有 Hikari/模型状态/任务台账，能覆盖真实依赖而不发起昂贵或有副作用的模型调用。

### 方案 C：外部 sidecar/probe + 应用只保留固定 ping

把依赖检查交给 Kubernetes sidecar、黑盒探针和日志平台。优点是应用侵入小；缺点是不能可靠读取应用内的 ModelStateCenter、任务台账和自定义 Redisson/Milvus 客户端，也无法统一 Micrometer 指标与 trace 关联。可作为部署层补充，不作为本刀主方案。

选型：采用方案 B；Kubernetes liveness/readiness 和 Prometheus scrape 仍是消费方，但健康事实由应用内 contributor 统一提供。

## 4. 健康检查设计

### 4.1 健康组与状态语义

配置两个健康组：

- liveness：只回答进程是否能运行，不依赖 MySQL、Redis、Milvus、模型或任务。包含 Spring Boot liveness state，避免依赖故障导致所有实例同时被重启。
- readiness：表示实例是否可以接收流量。包含 HTTP 应用上下文、MySQL、Redis、Milvus、模型网关和关键后台任务 contributor。任一必需依赖不可用时为 DOWN 或 OUT_OF_SERVICE，但返回体只给固定组件名、状态和低敏分类，不带 URI、连接串、用户名、cache key、SQL、模型响应、异常 message 或堆栈。

健康组只暴露 /actuator/health/liveness 和 /actuator/health/readiness；必要时内部 Prometheus 端点 /actuator/prometheus。不暴露 /actuator/env、/beans、/configprops、/mappings、/loggers、/heapdump、/threaddump、/scheduledtasks、/conditions 或任意 wildcard endpoint。/api/health 可继续作为兼容 ping，但不作为部署探针，也不拼接依赖详情。

### 4.2 各探针

| 面 | 探针事实 | 超时/副作用边界 | readiness 语义 |
| --- | --- | --- | --- |
| HTTP | actuator 应用上下文与管理端口本身 | 不向业务 API 发请求，不回显请求参数 | 管理应用上下文可响应即通过 |
| MySQL | 注入 DataSource/HikariDataSource，借用连接池的低成本连接验证或 Spring JDBC SELECT 1 | 固定超时、不可打印 JDBC URL/SQL 结果；连接失败只归类 connection_failure/timeout | 必需数据库不可用则失败 |
| Redis | 注入项目实际 RedissonClient，执行固定 ping 或等价无写入检查 | 不使用用户 key，不写入，不把 endpoint 或异常正文带入响应；test profile 用替身/禁用 contributor | 真实 Redisson 无法 ping 则失败 |
| Milvus | 注入 MilvusClientV2，执行只读、固定集合的轻量 existence/health 请求；集合名从固定常量选择 | 不创建集合、不 insert/delete、不携带用户数据；超时和 SDK 错误只归类 | 必需向量存储不可用则失败 |
| 模型网关 | 读取 ModelConfigCenter 的有效路由和 ModelStateCenter.listStates()，确认每个必需 tier 至少有可用 candidate | 默认不发起真实模型/Embedding 请求，不产生费用、延迟、模型副作用；无运行态记录时按配置候选做 UNKNOWN/UP 的明确冷启动策略 | 必需路由无可用候选则失败；单个非必需模型 down 不拖垮全组 |
| 关键后台任务 | 汇总 TaskExecution 与 domain task pending/running/retry/failed 数量，并读取统一任务状态 registry 的最后 success/失败时间 | 不读取正文/错误 message；查询限量、只读；scheduler 自身不因 probe 被触发 | 关键队列持续失败、积压超过配置阈值或超过最大 staleness 才失败；没有可观测样本时返回 UNKNOWN 并按部署策略决定是否影响 readiness |

HTTP/MySQL 属于 Spring 基础面；Redis/Milvus/model/task 必须是项目自定义 HealthIndicator 或 ReactiveHealthIndicator（本项目为同步路径）。每个自定义 contributor 应有固定 id、固定状态映射和异常分类函数，不允许把原始 throwable 交给 actuator JSON 序列化。

### 4.3 管理面安全

管理端点是运维入口，不应和业务 HTTP 端口共享公开路由。生产优先设置独立 management.server.port（例如部署分配的内部端口）并绑定内部接口/网络策略；若部署环境无法提供独立端口，则使用业务端口下的严格内网 ingress allowlist，不能把端点交给浏览器 CORS 或公开鉴权切面。

管理端点只允许 Kubernetes probe 和 Prometheus scraper 的网络身份访问。由于当前项目没有 Spring Security SecurityFilterChain，本刀计划先采用端口/网络隔离和网关 allowlist；若上线环境要求应用层认证，新增的管理安全配置必须只允许内部 service account/mTLS 或已批准的 admin principal，不可复用面向用户的 JWT/CORS 规则。任何 actuator response 默认关闭 show-details，不暴露 component details；Prometheus 指标也只走内部端口。

测试必须证明：业务端口访问 actuator 返回 404/不可达；管理端口只可访问两类 health 和 prometheus；敏感 endpoint 返回 404；health body 不含 URI、密码、SQL、key、message、stack、query 或正文。

## 5. 指标设计

### 5.1 指标命名与类型

复用上一刀敏感日志设计 §5.3 的预留名称，不改成用户维度：

- tool_search_total：Counter，成功/空结果/失败都计数，至少固定标签 tool、operation、result。
- tool_search_failure_total：Counter，失败按固定 failure_category 分类；不把 exception message 作为标签。
- tool_search_latency：Timer/Prometheus histogram，记录工具检索耗时，标签只有 tool、operation、result。
- tool_search_results：DistributionSummary/Prometheus histogram，记录结果数量，标签只有 tool、operation、result。

以上是对外 Prometheus 语义名称，必须与上一刀 §5.3 的预留名称一致。Micrometer builder 的内部 name 可以按 exporter 规则使用不重复追加 _total 的形式，但测试必须锁定最终 exposition 名称，避免同一指标同时注册 snake/camel 两套名字。

补充运维必需指标：

- model_call_total、model_call_failure_total、model_call_latency：标签只使用固定 operation=model_call、result、failure_category；不把 model/provider/scene 作为 meter name 或动态 tag，不带 userId、requestId、runId、prompt、query 或 response。
- task_pending、task_running、task_retry_wait、task_failed、task_last_success_timestamp：标签只使用 operation、result、failure_category；任务类别进入固定 operation 白名单，不带 taskId、userId、sourceId 或错误正文。
- dependency_health：Gauge，标签只使用 operation、result、failure_category，值为 0/1；依赖名称进入固定 operation 白名单，不用异常 message、URL 或任意请求参数。

### 5.2 标签白名单与基数政策

本刀所有自定义业务指标的标签白名单只有：工具名、操作名、结果分类、失败分类。模型、依赖和后台任务的名称必须进入固定 operation 白名单，不能另开 provider/model/scene/taskName 等 tag。明确禁止 userId、requestId、runId、traceId、query、keyword、正文、prompt、工具参数、结果文本、cache key、sourceId、IP、URI 和任意动态 request 参数。

指标注册应集中在一个 meter binder/registry façade 中，标签值先通过枚举或白名单归一化，未知值落到 unknown，不可直接把调用方字符串传给 Micrometer。测试需检查每个 meter 的 tag key 集合和一组 sentinel 值均不含用户/正文；还要检查同一操作重复调用只增长计数，不创建无界 meter 数。

### 5.3 采集边界

工具检索计时放在实际搜索调用边界，成功、空结果和失败必须在 finally/明确分支中完整计数；响应功能不依赖指标写入成功。模型指标优先从 ModelCallAttemptEvent/ModelCallTraceService 的低敏事件计数，不能从模型响应正文推导标签。后台指标从任务 repository 状态计数和统一任务执行记录更新，不能将任务日志文本作为数据源。

## 6. traceId 注入与传播

### 6.1 入口

新增无状态 HTTP trace filter/interceptor：

1. 读取受控 inbound header（建议 X-Trace-Id），只接受固定长度、ASCII 字母数字和 -/_ 的值；不接受空值、超长值、控制字符或把任意 header 原样放入 MDC。
2. 没有合法 header 时生成随机 UUID；MDC key 固定为 traceId。
3. 在 response header 回写最终 traceId，供客户端/运维关联；不把用户 ID、query 或 token 写入 MDC。
4. 用 try/finally 清理 MDC，确保 servlet 线程复用不串数据。

HTTP 请求、SSE handler 的提交入口和 gRPC server interceptor 分别是独立入口；WebSocket/STOMP connect/message 也要在 channel interceptor 或 handler 线程建立短生命周期 MDC。scheduler 每次 job invocation 生成独立 traceId，并在 DistributedJobRunner/集中调度 wrapper 中清理。具体跨线程传递统一复用 ThreadPoolConfig 的 MDC snapshot；该 decorator 只传播合法已有值，不负责接受外部 header 或生成根 trace。

### 6.2 与已有 requestId/runId 的关系

traceId 是日志关联 ID，requestId/runId 继续作为业务/模型生命周期 ID；三者不能互相替代或作为指标标签。HTTP filter 生成的 traceId 可在同一线程被 ModelRouteContext 和 ModelCallAttemptEvent 通过低敏关联查询使用，但不应把 traceId 写进持久化的用户内容或公开 health/metrics 标签。

### 6.3 验证

- 单元测试：合法 inbound trace 被保留，非法/超长值被替换；无 header 会生成合法 ID；filter 完成或抛异常后 MDC.get("traceId") == null。
- 异步测试：ThreadPoolConfig decorator 将父线程 traceId 传给任务，任务完成后工作线程 MDC 为空；连续两个任务不能互相泄漏。
- Web/SSE/gRPC/scheduler 窄测试：入口日志能读到 traceId，异常路径仍清理；不启动服务或外部依赖。
- 集成回归：现有 requestId/runId、SSE cancellation、gRPC 响应、WebSocket 鉴权和 scheduled job 功能行为不变。

## 7. 错误与降级政策

- 健康检查失败只返回固定 DOWN/OUT_OF_SERVICE 和分类；原始异常进入日志时遵循已验收的异常日志政策，不传 Throwable、getMessage()、SQL、URL 或模型响应。
- 指标记录失败不能使业务请求、模型调用或后台任务失败；meter 注册冲突在启动时 fail fast，单次 MeterRegistry 操作不向业务抛出敏感错误。
- readiness 应区分“依赖 down”和“没有样本/冷启动 unknown”。liveness 不跟随依赖 down。模型探针无真实调用，因此只能证明路由候选状态和最近调用健康，不能声称供应商端到端可用。
- Redis/Milvus 不可用时相应 contributor 失败，但不能为了健康检查追加写入、集合创建、模型调用或重试风暴。

## 8. 最小告警通道预留（不实现）

本刀只定义后续告警消费的稳定信号：readiness down、模型调用失败率/持续无可用 candidate、关键任务 pending/retry/failed 超阈值、预算准入拒绝计数。后续小刀负责阈值、去重、冷却、接收人和通知适配；本刀不创建通知客户端、规则调度、邮箱/Webhook 或 roadmap 勾选。

## 9. 验收标准

实现完成后必须同时满足：

1. actuator/Micrometer 依赖和配置已就位，管理端点最小暴露并完成端口/网络隔离测试。
2. liveness 不受依赖影响；readiness 覆盖 HTTP、MySQL、实际 Redis、Milvus、模型网关和关键后台任务，响应无敏感详情。
3. 工具检索四类指标和模型/任务/依赖指标均可采集；标签集合严格通过白名单，零用户/正文/请求高基数维度。
4. HTTP、SSE、gRPC、WebSocket、scheduler 的 traceId 注入、异步传播和 finally 清理均有测试证据；既有 %X{traceId} 日志格式开始得到真实值。
5. focused tests、全量 Maven tests、git diff --check 均通过；不修改评测套件、QualityGatePolicy、migration、CI 或 roadmap。
6. 交接报告必须单列尚未实现的最小告警通道，以及本刀没有覆盖的备份恢复和受控堆栈采样通道，防止后续切片误认为已完成。
