# Yusi 限流与成本准入复核实施计划

**目标：** 在不改变权限语义和业务响应边界的前提下，补齐高风险 HTTP 写入口的请求频率保护，统一低敏 429 与 `rate_limited_total` 指标契约，锁定 90 个真实写映射的覆盖清单，并证明 model gateway admission 的拒绝分类、告警分类和指标分类保持一致。

**架构：** 应用层使用方法级 `@RateLimiter` 处理用户/IP 操作粒度；Ingress/API gateway 负责请求体大小、批量大小、并发连接和跨实例粗保护；ModelBudgetAdmission 继续负责模型 request/token 的 user/model/provider 预算准入。限流拒绝、预算拒绝、权限拒绝保持独立语义。管理端口只做网络 allowlist，不接入业务限流。

**技术栈：** Spring MVC/AOP、Redisson `RRateLimiter`、Guava local fallback、Micrometer `MeterRegistry`、Mockito/MockMvc、H2 业务回归和 PowerShell 静态扫描。真实 Redis、模型、OSS、Milvus、Ingress 和并发压力属于 deployment-only。

**全局约束：**

- 先红后绿；先扩展契约测试并运行聚焦命令确认失败，再写生产实现。
- 不弱化 sentinel 断言，静态/JSON/日志扫描必须精确拒绝 `fixture-user-rate`、`fixture-query-rate`、`fixture-content-rate`、`fixture-token-rate`、`fixture-object-key-rate`。
- 90 个真实 `POST/PUT/DELETE/PATCH` mapping 必须逐接口登记；`MatchController:95` 注释映射不得被误计为真实接口。22 个既有限流点必须逐个解析。
- 标签只允许 `tool`、`operation`、`result`、`failure_category`；禁止 user ID、IP、query、正文、token、provider、model、path parameter、完整 Redis key。
- 限流 429 只返回固定 `RATE_LIMIT_EXCEEDED` 错误码和固定文案；不得把本地/Redis 后端、key、阈值或 exception message 带给客户端。
- USER/IP 限流 key 的 subject 不能保留明文，使用部署密钥 HMAC 的固定长度摘要；摘要不得进入日志、异常、响应或指标。
- ModelBudgetAdmission 的 `ADMISSION_STORE_UNAVAILABLE`、`RESERVATION_CONFLICT`、`LIMIT_EXCEEDED:<dimension>`、unknown 必须归一化为四个固定分类；dimension 原文不得进入标签或消息。
- 不修改 roadmap、CI、migration、既有评测/QualityGatePolicy、备份/隐私/告警切片文件；本计划只在 File Map 范围内新增/修改限流切片相关文件。
- 不把 Mockito、H2 或本地 fallback 结果写成真实 Redis/网关/模型/OSS 已验收；deployment-only 清单必须在报告中原样保留。

## File Map

**测试与契约：**

- `src/test/java/com/aseubel/yusi/common/ratelimit/RateLimitCoverageContractTest.java`：90 个真实写映射、22 个既有注解和高风险覆盖 manifest 的静态契约。
- `src/test/java/com/aseubel/yusi/common/ratelimit/RateLimiterAspectTest.java`：Redis 允许/拒绝、窗口、维度、HMAC subject、local fallback 和 fail-closed 契约。
- `src/test/java/com/aseubel/yusi/common/ratelimit/RateLimitResponseContractTest.java`：429/固定响应、SSE 前置拒绝和无 backend/key/message 泄露。
- `src/test/java/com/aseubel/yusi/observability/metrics/RateLimitedMetricContractTest.java`：`rate_limited_total` 四标签白名单与分类归一化；报告标 `mock-contract-only`。
- `src/test/java/com/aseubel/yusi/service/ai/model/ModelBudgetAdmissionClassificationTest.java`：admission 维度、拒绝路径、`budget_denied_total` 与 AlertPolicy 分类一致性。

**实现：**

- `src/main/java/com/aseubel/yusi/common/ratelimit/RateLimiterSubjectEncoder.java`：部署密钥 HMAC 的固定长度 subject 编码，不输出明文。
- `src/main/java/com/aseubel/yusi/common/ratelimit/RateLimiterAspect.java`：接入 subject 编码、统一拒绝分类、Redis 故障策略和计数 facade；保留既有接口语义。
- `src/main/java/com/aseubel/yusi/common/exception/RateLimitException.java`、`src/main/java/com/aseubel/yusi/common/exception/GlobalExceptionHandler.java`：固定 429 边界；不得把后端或 exception message 返回客户端。
- `src/main/java/com/aseubel/yusi/observability/metrics/YusiMetrics.java`：新增 `rate_limited_total`，扩充的操作/分类仍限白名单。
- `src/main/java/com/aseubel/yusi/service/ai/model/ModelBudgetAdmission.java`：修正三维 Lua
  超限返回码，并将拒绝 reason 固定为低敏分类，避免内部 dimension 进入消息或事件。
- `src/main/java/com/aseubel/yusi/controller/*.java`：只在本计划高风险和已登记中风险入口补 `@RateLimiter`；每个改动必须对应 coverage manifest，不能顺手重排 Controller。
- `src/main/resources/application.yml`、`src/main/resources/application-prod.yml`、`src/main/resources/application-dev.yml`：仅增加非敏感 rate-limit 开关/阈值和 HMAC secret 的环境变量引用，不写 secret 值；不改变 actuator exposure、health group 或 show-details。

**文档/交接：**

- `docs/engineering/runbooks/yusi-rate-limit-admission-runbook.md`：deployment-only 的 Redis 多副本、网关、并发、OSS/model、管理端口和阈值校准步骤；只记录分类/计数，不记录主体、query 或正文。

## Task 1：建立完整覆盖清单并先红

**目标：** 把“90 个真实写入口、22 个已有注解、高风险优先项、注释映射排除”固化成不能漂移的测试契约。

**步骤：**

1. 新建 `RateLimitCoverageContractTest`，从源码读取 Controller mapping 和注解元数据，展开类级 path；精确排除注释行 `MatchController:95`。
2. 写入固定 endpoint manifest，逐条包含 HTTP method、完整 path、source file:line、当前状态、维度、count/time、风险等级；已有状态必须与源码注解精确匹配，缺失状态必须在清单中明确列出。
3. 用 sentinel fixture 值验证未来报告/JSON/日志不包含用户、query、正文、token、object key 形态；不使用模糊 `contains` 代替完整字段拒绝。
4. 运行聚焦测试命令，预期非零；失败证据至少包含缺失的高风险接口、注释映射计数差异或当前无 `rate_limited_total` 的契约失败。

**验证：**

```powershell
.\mvnw.cmd -q "-Dtest=RateLimitCoverageContractTest,RateLimiterAspectTest,RateLimitResponseContractTest,RateLimitedMetricContractTest,ModelBudgetAdmissionClassificationTest" test
```

预期 Task 1 红，不得因为实现尚未完成而放宽断言。

## Task 2：统一低敏限流 subject、拒绝响应和计数

1. 先让 `RateLimiterAspectTest` 锁定 Redis `RRateLimiter` 的维度/窗口、固定 operation key、Redis 异常降级和 local fallback 不能跨实例等行为。
2. 实现 `RateLimiterSubjectEncoder`：secret 只从环境属性注入，HMAC 输出固定长度摘要；测试证明任意 fixture user/IP 不出现在 key、异常、日志和响应。
3. 统一 Redis 与 local fallback 的拒绝分类：`limit_exceeded`、`dependency`、`unknown`；高风险 endpoint 的 Redis 不可用策略采用 fail-closed 或小于分布式上限的 bounded local cap，不能默认无限放行。
4. 统一 `RateLimitException`/`GlobalExceptionHandler`：429、固定错误码和文案；SSE 方法在 response commit 前拒绝为普通 429，已提交流只结束，不发送内部原因。
5. 接入 `YusiMetrics.rate_limited_total`，标签集合精确等于 `tool/operation/result/failure_category`，固定操作枚举不包含 path parameter/user/IP/query。
6. 运行 `RateLimiterAspectTest,RateLimitResponseContractTest,RateLimitedMetricContractTest`，确认先前红点转绿；若仍失败，修实现，不降低 sentinel 或白名单断言。

## Task 3：补齐高风险写入口

1. 先扩展覆盖测试，明确以下入口在当前源码中应为缺失并红：
   - `AiController` 的 memory fusion；
   - `SituationRoomController` 的最终 narrative submit；
   - `AdminController` 的 embeddings full-sync；
   - `ImageController` 的 URL 批量签名、单删和批删；
   - `KeyManagementController` 的 reencrypt-diaries。
2. 为模型触发入口使用 USER 限流并保持 model admission：memory fusion 初始 2/600s，room submit 初始 3/600s；所有值标“初始值，待生产调优”。
3. 为 full-sync 使用超级管理员权限 + USER 1/3600s，并在 runbook 写明 deployment allowlist；不要把应用测试当作全量 Milvus 重建验证。
4. 为 URL 签名和 OSS 删除增加 USER 维度及单次列表/总字节边界；批量 OSS 入口同时需要 gateway 规则。
5. 为 reencrypt 增加低频 USER 限制，并确保不把密钥、object key 或 request body 写入日志/指标。
6. 每完成一组运行对应聚焦测试和 Controller 回归；检查原有 auth/ownership/transaction 行为不变。

## Task 4：补齐业务写入口并锁定风险分级

1. 先将日记 create/edit、match settings/action/feedback/end/report/block、lifegraph 八个入口、memory center 六个入口、room 状态/情景入口、soul-chat read、通知三入口、plaza 六入口、user update/logout、location 三入口加入 coverage manifest；缺任何一个都失败。
2. 使用固定 key 和 USER 维度；普通 DB write 初始 20-30/60s，举报/验证码/批量状态使用更低阈值，全部写明“初始值，待生产调优”。
3. 对已废弃的 `/api/diary/chat` 保持低敏固定失败；不重新接回模型，不以限流掩盖废弃路由。
4. 对 admin/prompt/model/developer config 保留既有认证/管理员检查，增加低频限流但不把限流当权限。
5. 运行覆盖契约、MockMvc/Controller 回归和敏感字段扫描；验证 HTTP 429 不含 route parameter、body、user/IP。

## Task 5：Admission 与既有观测机制复核

1. 先红扩展 `ModelBudgetAdmissionClassificationTest`：覆盖无配置 noop、user/model/provider request/token charge、Redis 缺失/异常、reservation conflict、每种 `LIMIT_EXCEEDED:<dimension>`。
2. 断言 `YusiMetrics.normalizeBudgetReason` 与 `AlertPolicy.normalizeBudgetReason` 的输出集合精确为 `admission_store_unavailable`、`reservation_conflict`、`limit_exceeded`、`unknown`，且 dimension 原文不进入标签/消息。
3. 断言同步和 streaming permit 拒绝都会记录 `budget_denied_total`，但不把它计入 `rate_limited_total`；测试结果只标本地契约，不声称真实供应商配额。
4. 盘点 `InterfaceUsageMonitor` 仍是报表统计，violation 仍是内容安全计数；禁止新增联动读取或把 user/IP 放入新指标。
5. 对管理端口只增加 runbook/配置审计：health/prometheus 保持既有最小暴露，20611 的真实网络隔离列 deployment-only。

## Task 6：本地边界验证与 deployment-only 交接

1. H2 只验证有 DB 写入口的事务/回归，不将 H2 当 Redis、Ingress、Milvus、OSS 或模型证据。
2. Mockito 只验证 aspect、RRateLimiter、fallback、fixed response、MeterRegistry 和 admission 分类；报告统一使用 `mock-contract-only`。
3. 编写 runbook 的 deployment-only 步骤：真实并发/SSE/Multipart 压力、网关 byte/concurrency、Redis 多副本/故障、真实 model/provider RPM/TPM、Milvus full-sync、OSS 删除/签名、20611 allowlist、WebSocket/gRPC 入口。
4. 记录初始阈值、调优输入和回滚策略；没有真实证据的字段保持 `NOT_RUN`，不得生成 PASS。

## Task 7：审计、回归和交付

1. 用 PowerShell `Select-String` 扫描变更范围和新增报告/日志，拒绝 `fixture-user-rate`、`fixture-query-rate`、`fixture-content-rate`、`fixture-token-rate`、`fixture-object-key-rate`，并扫描 `userId|query|content|token|objectKey|provider|model` 是否进入新 logger/metric payload；逐条归类必要的源码字段引用。
2. 检查 metric tags 只有四个白名单键；检查限流 key 不含明文 user/IP；检查 429 响应只含固定错误码/文案。
3. 执行聚焦测试：

```powershell
.\mvnw.cmd -q "-Dtest=RateLimitCoverageContractTest,RateLimiterAspectTest,RateLimitResponseContractTest,RateLimitedMetricContractTest,ModelBudgetAdmissionClassificationTest" test
```

4. 执行全量回归：

```powershell
.\mvnw.cmd -q test
```

要求退出码 0，既有 Sensitive*/Observability/QualityGate 套件不回退；任何 deployment-only 项不计入本地 PASS。

5. 执行范围检查：`git diff --check`；确认没有 roadmap、CI、migration、既有评测或其它 Phase 5 切片文件变更；确认新增文档/实现/测试只来自 File Map。
6. 按评审批准后清单独立提交：

```text
security: review rate limit and admission gate
```

提交前检查 roadmap 对应 L636 是否需要评审方勾选；本刀不自行勾选，提交后停下等待验收。

## 完成标准

- 聚焦测试按先红后绿完成；覆盖契约明确 90 个真实写入口和 22 个既有限流点，注释映射不计数。
- 高风险入口已由应用 USER 限流和/或网关契约覆盖，初始阈值全部标注“初始值，待生产调优”。
- 429 固定低敏；`rate_limited_total` 标签精确四键；无 user/IP/query/content/token/object key 泄露。
- admission 四分类和告警分类精确一致，`budget_denied_total` 与 `rate_limited_total` 语义分离。
- H2/Mockito 证据分别标 application-invariant-only 或 mock-contract-only；真实并发、Redis 多副本、网关、model/OSS、management port 和阈值校准保留 deployment-only。
- 聚焦与全量 Maven 退出码为 0，`git diff --check` 干净，工作树范围符合 File Map；独立提交后停下报告测试数字、扫描归类、deployment-only 清单和 hash。
