# Yusi 最小告警通道实施计划

> **For agentic workers:** 按任务顺序在当前 worktree 内执行；仓库指令禁止子 agent 和 auto-review。每一步使用 checkbox 记录，先红后绿；本计划不授权修改 roadmap、CI、migration、既有评测套件或 QualityGatePolicy。

**目标：** 在不阻塞业务和 readiness 的前提下，建立四类低敏告警规则、飞书 webhook 调用契约、跨实例去重和真实部署验收边界。

**架构：** 当前实现采用应用内 `AlertEvaluator` + `FeishuAlertNotifier`。评估器读取 readiness、Micrometer counter 和固定任务状态，纯规则生成 `AlertSignal`；状态存储负责 fingerprint 去重，通知器只发送白名单字段。Prometheus/Alertmanager + 飞书 receiver 作为后续平台具备后的迁移目标，不在本计划中伪造已部署。

**技术栈：** Java 21、Spring Boot 3.4.5、Actuator、Micrometer、Redisson、JUnit 5、Mockito、Spring Boot test、Maven Wrapper；不新增告警平台依赖，不引入真实外部服务测试。

## 全局约束

- 飞书 webhook URL 和签名密钥只从 `YUSI_ALERT_FEISHU_WEBHOOK_URL`、`YUSI_ALERT_FEISHU_SIGNING_SECRET` 等运行时环境变量/部署 Secret 读取；禁止写入代码常量、配置明文、测试、fixture、报告、日志和异常。
- 只允许四类告警：`service_unavailable`、`model_failure_rate`、`task_backlog`、`budget_denied`；消息字段仅限设计文档 §6.1 白名单。
- 指标标签只允许 `tool`、`operation`、`result`、`failure_category`；未知值归一化为固定 `unknown`，禁止 user ID、query、正文、prompt、token、request 参数、动态 model/provider/taskId/sourceId。
- readiness、业务请求、模型调用和后台任务不依赖通知器结果；webhook 失败只进入有界异步重试和低敏分类日志。
- test profile 不连接真实 MySQL、Redis、Milvus、模型网关或飞书；真实抓取、真实送达、Secret 注入、双副本租约和轮值确认必须标记 deployment-only。
- 不修改 `docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md`、CI、migration、Phase 4 评测/fixture/报告、QualityGatePolicy、既有备份/隐私切片文件。
- 所有阈值都是“初始值，待生产调优”：readiness 2 分钟、模型失败率 5 分钟/20%/最小 20 次、任务 15 分钟/critical 60 分钟、预算拒绝 5 分钟/至少 10 次，默认重复抑制 30 分钟。

## 文件地图

以下是评审通过后实现本刀允许触及的未来文件；当前勘察/设计阶段只新增本设计文档和本计划文件。

### 新增生产文件

- `src/main/java/com/aseubel/yusi/observability/alert/AlertSignal.java`：四类告警的不可变、已归一化内部信号，包含 category、service、operation、level、window、count/value、classification、observedAt、state。
- `src/main/java/com/aseubel/yusi/observability/alert/AlertMessage.java`：只允许飞书字段白名单的消息对象；不接收请求对象、Throwable、模型响应或任意 Map。
- `src/main/java/com/aseubel/yusi/observability/alert/AlertPolicy.java`：固定初始阈值、窗口、最小样本、抑制和 recovery 策略；只包含非敏感数值和枚举。
- `src/main/java/com/aseubel/yusi/observability/alert/AlertEvaluator.java`：读取健康、指标、任务快照，使用注入 Clock 做纯评估，产出 firing/recovered 信号。
- `src/main/java/com/aseubel/yusi/observability/alert/AlertStateStore.java`：fingerprint 的 claim、active、last-notified、recovered 状态接口。
- `src/main/java/com/aseubel/yusi/observability/alert/InMemoryAlertStateStore.java`：test profile/单实例降级使用的有界本地状态实现。
- `src/main/java/com/aseubel/yusi/observability/alert/RedisAlertStateStore.java`：生产跨实例去重实现；key 仅由固定 category/service/operation/level 组成，不含用户或请求字段。
- `src/main/java/com/aseubel/yusi/observability/alert/FeishuAlertProperties.java`：读取非敏感开关和运行时凭据引用；禁止输出凭据的 `toString`、日志和异常。
- `src/main/java/com/aseubel/yusi/observability/alert/FeishuAlertNotifier.java`：低敏 JSON 构造、签名、异步发送、有限重试和固定失败分类。
- `src/main/java/com/aseubel/yusi/observability/alert/AlertScheduler.java`：固定 30 秒评估调度和有界通知队列；不参与 readiness。
- `src/main/java/com/aseubel/yusi/observability/task/TaskScheduleCatalog.java`：固定任务名到预期周期的映射，用于 due gap/lag；不接受动态任务名。

### 修改生产文件

- `src/main/java/com/aseubel/yusi/observability/metrics/YusiMetrics.java`：补充 `dependency_health`、`task_due_gap`、`task_lag`、`budget_denied_total` 的固定名称/标签契约，保留现有四键标签白名单。
- `src/main/java/com/aseubel/yusi/observability/task/TaskHealthRegistry.java`：在现有固定状态基础上提供只读、低敏的 schedule-aware snapshot；不保存任务输入、用户 ID 或错误正文。
- `src/main/java/com/aseubel/yusi/service/ai/model/ModelProxyFactory.java`：在两个 `permit.granted()==false` 分支记录预算拒绝分类；保留现有构造器兼容性和 fallback 顺序。
- `src/main/java/com/aseubel/yusi/config/ObservabilityConfig.java`：注册 evaluator、状态存储和 notifier 的条件 bean；test profile 不触发外部连接。
- `src/main/resources/application.yml`、`src/main/resources/application-prod.yml`：只增加非敏感告警开关、阈值和调度配置；不出现 URL 或密钥值。管理端点既有 `health,prometheus` 最小暴露不扩展。

### 新增/修改测试文件

- `src/test/java/com/aseubel/yusi/observability/alert/AlertPolicyTest.java`：纯阈值、窗口、最小样本、升级和恢复规则。
- `src/test/java/com/aseubel/yusi/observability/alert/AlertEvaluatorTest.java`：readiness、model、task、budget 四类信号、抑制和缺样本 UNKNOWN。
- `src/test/java/com/aseubel/yusi/observability/alert/AlertStateStoreTest.java`：fingerprint、30 分钟抑制、recovery 和多次 claim 语义。
- `src/test/java/com/aseubel/yusi/observability/alert/FeishuAlertNotifierContractTest.java`：mock HTTP client 的调用契约、有限重试和低敏字段。
- `src/test/java/com/aseubel/yusi/security/AlertSensitiveDataTest.java`：源代码/配置/序列化结果的 URL、secret、payload、query、正文和高基数维度 sentinel 审计。
- `src/test/java/com/aseubel/yusi/observability/metrics/YusiMetricsTest.java`、`src/test/java/com/aseubel/yusi/observability/task/TaskHealthRegistryTest.java`：扩展缺失指标和任务快照契约，不删除既有断言。
- `src/test/java/com/aseubel/yusi/service/ai/model/ModelProxyFactoryTest.java`：预算拒绝计数回归，保留当前模型 fallback 和事件发布语义。

## Task 1：先红，锁定四类规则和秘密边界

**Files:** 只新增本计划列出的测试文件；本任务不改生产代码。

**Interfaces:** 测试先定义 `AlertPolicy.evaluate(...)`、`AlertEvaluator.evaluate(AlertSnapshot, Instant)`、`AlertMessage` 字段白名单、`FeishuAlertNotifier` 的 mock client 调用接口和四个新指标名称。

- [ ] **Step 1：写失败测试。**

  固定 Clock、固定 health snapshot 和 `SimpleMeterRegistry`，覆盖：readiness DOWN 只在连续 2 分钟后 firing；模型失败率要求 5 分钟窗口和至少 20 次；task due gap/lag 15/60 分钟分级；budget 5 分钟至少 10 次；重复 firing 被抑制；健康窗口产生 recovery；未知任务/未知分类归一化为 unknown。为消息构造加入 `fixture-user-alert`、`fixture-query-alert`、`fixture-content-alert`、`fixture-token-alert`、`fixture-object-key-alert` sentinel，断言均不出现在 JSON。

- [ ] **Step 2：运行聚焦测试确认红。**

  ```powershell
  .\mvnw.cmd -q "-Dtest=AlertPolicyTest,AlertEvaluatorTest,AlertStateStoreTest,FeishuAlertNotifierContractTest,AlertSensitiveDataTest" test
  ```

  预期：因生产契约尚不存在而非零，且失败包含缺失四类信号/消息字段/通知器契约；不得删除 sentinel 或把断言改为宽松 contains。

## Task 2：补齐缺失指标和任务时间事实

**Files:** `YusiMetrics.java`、`TaskHealthRegistry.java`、`TaskScheduleCatalog.java`、`ModelProxyFactory.java`、对应 metrics/task/model 测试。

**Interfaces:**

- `YusiMetrics.recordDependencyHealth(String dependency, String result, String failureCategory, boolean available)` 注册 `dependency_health`。
- `YusiMetrics.recordTaskBacklog(String taskName, double dueGapMinutes, double lagMinutes, String result, String failureCategory)` 更新 `task_due_gap` 和 `task_lag`；没有样本必须传递 `unknown`，不能用动态 task name。
- `YusiMetrics.recordBudgetDenied(String reason)` 注册 `budget_denied_total`，把 `ADMISSION_STORE_UNAVAILABLE`、`RESERVATION_CONFLICT`、`LIMIT_EXCEEDED:<dimension>` 归一化为固定分类。
- `TaskScheduleCatalog.expectedInterval(String taskName)` 只接受 `TaskHealthRegistry.allowedTaskNames()` 的固定成员。

- [ ] **Step 1：先扩展指标和任务契约测试。** 断言四个新指标的最终名称、类型、固定 tag keys、未知归一化和 no-sample UNKNOWN；断言预算 dimension 不进入 meter/tag/message；断言 task snapshot 不含 taskId、userId、query、正文或异常 message。
- [ ] **Step 2：实现最小指标 facade。** 保持 `tool`、`operation`、`result`、`failure_category` 四键白名单；将 dependency/alert operation 加入固定白名单，未知值统一为 unknown；Gauge 更新使用有界固定状态，不以每次请求创建 meter。
- [ ] **Step 3：补充 schedule-aware task snapshot。** 使用固定任务 schedule catalog 和现有 `startedAt`/`lastSuccessAt` 计算 due gap/运行 lag；重启后无样本为 UNKNOWN；不改变现有 `TaskHealthIndicator` 的 readiness 判定。
- [ ] **Step 4：接入预算拒绝。** 在 `ModelProxyFactory` 的同步与 streaming 两个 permit 拒绝分支调用 `recordBudgetDenied`；不得把 provider、modelId、reservation key、dimension 原文传入指标。
- [ ] **Step 5：运行信号聚焦回归。**

  ```powershell
  .\mvnw.cmd -q "-Dtest=YusiMetricsTest,TaskHealthRegistryTest,ModelBudgetAdmissionTest,ModelProxyFactoryTest,ObservabilitySensitiveDataTest" test
  ```

  预期：PASS；现有模型调用、fallback、任务状态和敏感数据断言不回归。

## Task 3：实现纯规则评估器和四类告警策略

**Files:** `AlertSignal.java`、`AlertPolicy.java`、`AlertEvaluator.java`、`AlertPolicyTest.java`、`AlertEvaluatorTest.java`。

**Interfaces:** `AlertEvaluator` 只接收低敏 `AlertSnapshot`、`Clock`、`AlertPolicy` 和已归一化 meter/health/task 读取器，返回 `List<AlertSignal>`；不接收 HTTP request、模型响应、Throwable 或 arbitrary Map。

- [ ] **Step 1：定义不可变内部类型。** `AlertSignal` 只允许四类 category、固定 level/state、非负 count/value、固定 window、固定 classification 和时间；构造时拒绝敏感字段。
- [ ] **Step 2：实现 readiness 规则。** 聚合 readiness DOWN 连续时长；2 分钟后 firing，30 分钟抑制重复；连续两次 UP 后发 recovery。根 readiness 告警标记为抑制 model/task 子告警。
- [ ] **Step 3：实现模型失败率规则。** 对所有固定标签组合聚合 counter 增量，不按 model/provider/用户分片；窗口 5 分钟、最小 20 次、失败率至少 20%；两窗口恢复。
- [ ] **Step 4：实现任务规则。** 对固定 task operation 评估 due gap/lag；15 分钟 warning、60 分钟 critical，持续 5 分钟；UNKNOWN 不转成零，不产生伪恢复。
- [ ] **Step 5：实现预算拒绝规则。** 对 `budget_denied_total` 五分钟增量聚合，至少 10 次 firing；只在信号本身清窗后 recovery。
- [ ] **Step 6：运行规则聚焦测试。**

  ```powershell
  .\mvnw.cmd -q "-Dtest=AlertPolicyTest,AlertEvaluatorTest" test
  ```

  预期：PASS，并保留每一条持续时间、最小样本、抑制和 recovery sentinel。

## Task 4：实现跨实例去重和抑制状态

**Files:** `AlertStateStore.java`、`InMemoryAlertStateStore.java`、`RedisAlertStateStore.java`、`AlertStateStoreTest.java`、必要的 `ObservabilityConfig.java`。

**Interfaces:** `claim(fingerprint, now, suppressionWindow)`、`markFiring(...)`、`markRecovered(...)` 和 `isRootSuppressionActive(...)` 只处理固定 fingerprint 状态；持久化字段只含 fingerprint、状态和时间戳。

- [ ] **Step 1：先红扩展状态测试。** 同一 fingerprint 在 30 分钟内只能 claim 一次；不同 level/category 独立；recovery 只发一次；根 readiness active 时相关子类被抑制。
- [ ] **Step 2：实现有界内存状态。** 用固定容量/过期清理保存 test profile 状态，超限丢弃最旧重复状态并返回低敏分类，不保存原始输入。
- [ ] **Step 3：实现 Redis 状态。** 使用固定 prefix 和 TTL/lease，key 组成只允许 category/service/operation/level；Redis 异常返回 `dedup_store_unavailable`，不向 readiness 抛出，不记录 key 原文。
- [ ] **Step 4：运行状态测试。**

  ```powershell
  .\mvnw.cmd -q "-Dtest=AlertStateStoreTest" test
  ```

  预期：PASS；测试只使用 Mockito Redis，不声称真实双副本租约已经验证。

## Task 5：实现飞书消息和异步通知器

**Files:** `AlertMessage.java`、`FeishuAlertProperties.java`、`FeishuAlertNotifier.java`、`FeishuAlertNotifierContractTest.java`、`AlertSensitiveDataTest.java`。

**Interfaces:** `FeishuAlertNotifier.notify(AlertMessage)` 立即入有界异步队列并返回；HTTP 客户端接口在测试中可替换。消息构造器只接收 `AlertMessage`，不接收凭据以外的原始配置 Map、Throwable 或请求上下文。

- [ ] **Step 1：先红锁定消息 JSON。** 断言 JSON 只有固定的飞书文本 envelope，以及 `content.text` 中的 `alert_category`、`service`、`operation`、`level`、`window`、`count`、`value`、`classification`、`observed_at`、`state`；URL、secret、签名密钥、响应 body 和 sentinel 不得进入日志、诊断对象或语义模板，协议要求的签名只验证存在性和格式，不断言真实值。
- [ ] **Step 2：实现运行时凭据读取。** 只从环境/Secret 绑定 URL 和签名密钥；属性对象的 `toString`、日志和异常必须是 `[configured]`/固定分类，不能返回值本身。未启用或缺凭据时不创建可发送任务，不影响 readiness。
- [ ] **Step 3：实现签名和有限重试。** 运行时生成签名 header；失败只分类为 `configuration_missing`、`timeout`、`connection_failure`、`http_failure` 或 `unknown`，最多 3 次指数 backoff，日志只记分类/attempt/backoff class/exceptionType。
- [ ] **Step 4：实现有界异步隔离。** 队列满时丢弃重复通知并记录固定 `queue_full`；业务调用线程不等待网络，notifier bean 不被 health group 引用。
- [ ] **Step 5：运行 mock-contract-only 测试。**

  ```powershell
  .\mvnw.cmd -q "-Dtest=FeishuAlertNotifierContractTest,AlertSensitiveDataTest" test
  ```

  预期：PASS；报告标签必须为 `mock-contract-only`，不能使用“送达成功”措辞。

## Task 6：组装调度、配置和本地集成契约

**Files:** `AlertScheduler.java`、`ObservabilityConfig.java`、`application.yml`、`application-prod.yml`、必要的 alert integration tests。

- [ ] **Step 1：按条件注册组件。** `YUSI_ALERT_FEISHU_ENABLED` 默认关闭；test profile 使用 in-memory state 和 mock client，不连接外部依赖。生产启用时缺凭据只让 notifier 处于 disabled/error 分类，不让 Spring context 或 readiness 失败。
- [ ] **Step 2：接入 30 秒评估调度。** 调度器读取 snapshot、运行 evaluator、执行 state claim、提交 notifier；任何 runtime exception 被固定分类捕获，不能传入日志 message/stack，也不能冒泡到业务线程。
- [ ] **Step 3：验证现有 health/metrics 组不扩展。** 保持 `health,prometheus`、`show-details: never`、liveness/readiness 成员不变；不开放 wildcard actuator endpoint，不把 notifier 状态塞入 readiness。
- [ ] **Step 4：运行本地集成契约。**

  ```powershell
  .\mvnw.cmd -q "-Dtest=AlertEvaluatorTest,FeishuAlertNotifierContractTest,HealthEndpointExposureTest,ObservabilitySensitiveDataTest" test
  ```

  预期：PASS；只证明本地规则、健康复用、消息和失败隔离，不证明真实飞书送达。

## Task 7：安全审计、全量回归和交接

**Files:** 仅本计划 File Map 中的 alert/metrics/task/model/config/test 文件；禁止 roadmap、CI、migration、评测和既有质量门槛文件。

- [ ] **Step 1：执行凭据和敏感 payload 静态扫描。** 使用仓库现有 `SensitiveConfigAuditTest`/新增 `AlertSensitiveDataTest`，仅扫描本刀新增或修改的 production source、配置、测试和日志模板，检查 webhook URL 形式、签名常量、`userId/query/content/token/objectKey` 字段；只允许环境变量名和固定分类文本命中，不把既有未改动业务文件的合法数据字段误归为本刀命中。
- [ ] **Step 2：逐项审计指标标签。** 输出四个新指标名称、类型、固定 tag keys、所有 operation/result/failure category 白名单；确认没有动态 model/provider/user/request 维度。
- [ ] **Step 3：执行聚焦测试并保留边界证据。**

  ```powershell
  .\mvnw.cmd -q "-Dtest=AlertPolicyTest,AlertEvaluatorTest,AlertStateStoreTest,FeishuAlertNotifierContractTest,AlertSensitiveDataTest,YusiMetricsTest,TaskHealthRegistryTest,ModelProxyFactoryTest,HealthEndpointExposureTest,ObservabilitySensitiveDataTest" test
  ```

  预期：退出码 0；mock receiver 结果标 `mock-contract-only`，任务无样本/健康复用结果按各自契约记录，不合并为 deployment PASS。

- [ ] **Step 4：执行全量测试。**

  ```powershell
  .\mvnw.cmd -q test
  ```

  预期：退出码 0；既有质量门槛、Sensitive*/Observability 套件和评测报告语义不变。

- [ ] **Step 5：检查 roadmap 和工作树边界。**

  ```powershell
  git diff --check
  git diff -- docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md
  git status --short
  ```

  预期：roadmap 无 diff，L627 仍为未勾；只包含本刀 File Map 文件和必要测试/实现文件。提交前必须再次确认，不因测试通过自动勾选 roadmap。

- [ ] **Step 6：独立提交并停下。**

  ```powershell
  git add src/main/java/com/aseubel/yusi/observability/alert src/main/java/com/aseubel/yusi/observability/metrics/YusiMetrics.java src/main/java/com/aseubel/yusi/observability/task/TaskHealthRegistry.java src/main/java/com/aseubel/yusi/observability/task/TaskScheduleCatalog.java src/main/java/com/aseubel/yusi/service/ai/model/ModelProxyFactory.java src/main/java/com/aseubel/yusi/config/ObservabilityConfig.java src/main/resources/application.yml src/main/resources/application-prod.yml src/test/java/com/aseubel/yusi/observability/alert src/test/java/com/aseubel/yusi/security/AlertSensitiveDataTest.java src/test/java/com/aseubel/yusi/observability/metrics/YusiMetricsTest.java src/test/java/com/aseubel/yusi/observability/task/TaskHealthRegistryTest.java src/test/java/com/aseubel/yusi/service/ai/model/ModelProxyFactoryTest.java
  git commit -m "ops: add minimal alert channel"
  ```

  交接报告必须包含：四类指标映射、初始阈值且标注待生产调优、focused/full 退出码、低敏扫描命中与归类、`mock-contract-only` 证据、deployment-only 清单、真实 webhook 未在本地验证的事实、剩余 Prometheus/Alertmanager 迁移风险和提交 hash。roadmap L627 由评审方在真实接收证据齐全后决定。

## Deployment-only 清单

本计划实施者不得以本地测试代替以下验收：

1. 生产 Secret 注入、飞书签名校验、真实机器人送达、接收人确认、API 限流和消息时延。
2. Prometheus 对 Actuator 的真实 scrape、`dependency_health`/task/budget 规则在 Alertmanager 的加载、grouping/inhibit/silence/recovery 和 receiver 路由。
3. 两个生产副本的 Redis 去重租约、滚动发布、网络分区、Redis 不可用时的降级和重复抑制。
4. HTTP/MySQL/Redis/Milvus/模型网关真实故障、任务真实积压、失败率与预算拒绝阈值的生产调优。
5. 管理端口只对 probe/scraper 开放的网络隔离，以及实际轮值升级流程。
