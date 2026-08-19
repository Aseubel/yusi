# Yusi 异常日志政策收敛设计

**日期：** 2026-08-19
**状态：** 待评审
**前置提交：** `f7b6cd4 security: converge sensitive production logs`

## 1. 目标与边界

本刀关闭上一刀 `SensitiveLogSourceAuditTest.DEFERRED_EXCEPTIONS` 中的 7 个延期异常日志政策项。目标是让这些位置及其所属 5 个类的相邻异常日志只输出固定操作分类、低敏标识、异常类型和必要的有界元数据，不把 Throwable、`getMessage()`、SQL、缓存 key 或模型错误正文交给 logger。

本刀只收敛日志投影，不改变异常分类、重试/降级、事务清理、锁释放、HTTP 响应状态或已有业务返回值。允许修改的生产文件限定为：

- `src/main/java/com/aseubel/yusi/common/exception/GlobalExceptionHandler.java`
- `src/main/java/com/aseubel/yusi/service/ai/model/ModelProxyFactory.java`
- `src/main/java/com/aseubel/yusi/service/ai/prompt/PromptManager.java`
- `src/main/java/com/aseubel/yusi/service/user/impl/AdminServiceImpl.java`
- `src/main/java/com/aseubel/yusi/redis/aspect/CacheAspect.java`

安全测试复用上一刀的 `LowSensitivityLogSummary`，新增运行时异常 sentinel 测试并演进 `SensitiveLogSourceAuditTest`。不修改 roadmap、CI、migration、Phase 4 评测 fixture/loader/report、`QualityGatePolicy`、指标、Trace 注入或备份恢复配置。

## 2. 逐条勘察

### 2.1 延期组原始调用

| 位置 | 级别与上下文 | 异常来源 | 明文风险 | 现有排障消费 | 本刀政策 |
| --- | --- | --- | --- | --- | --- |
| `GlobalExceptionHandler.java:86` | `DEBUG`；SSE 已提交或响应已 committed 后的通用异常 handler | 流式控制器、序列化、下游模型或连接异常 | `e.getMessage()` 可能包含请求参数、下游响应片段、数据库/网络错误细节 | 仅进入 root console；logback 会尝试打印现有 MDC `traceId` | 保留 `DEBUG`、固定 `operation=sse_after_commit` 和 `exceptionType`，移除 message |
| `GlobalExceptionHandler.java:89` | `ERROR`；所有未被专用 handler 处理且非 streaming 的 `Exception` 兜底 | 任意 HTTP 请求路径的未预期异常 | Throwable 会输出完整堆栈及 cause message，是全系统最大的暴露面；message 还可能含请求、SQL、模型响应或路径信息 | 仅进入 root console；格式含 `%X{traceId}`，但生产代码未发现统一 `MDC.put("traceId", ...)` | 保留 `ERROR`、固定 `operation=global_unhandled_exception`、`status=500`、`exceptionType`；不传 Throwable，不改变响应状态和响应体契约 |
| `ModelProxyFactory.java:279` | `WARN`；同步模型调用失败，已完成 normalize、预算 reconcile、失败事件发布 | LangChain4j provider、HTTP client、结构化输出或取消异常 | `normalized.getMessage()` 来自 provider/root cause；`ModelInvocationException` 会把 cause message 拼入 detail，可能带 provider 错误正文、请求/响应片段 | 同一方法已持久化 `ModelCallAttemptEvent`，包含 request/run/model/provider/status/errorCode 等低敏字段 | 保留 attempt、requestId/runId、provider/modelId、`failureKind`、fallback 状态和 root exception type；移除 message |
| `PromptManager.java:64` | `WARN`；数据库 Prompt 查询失败后继续 classpath/hardcoded fallback | `PromptService.getPromptTemplate` 的 JPA/JDBC/配置异常 | 数据库错误 message 可能带 SQL、约束、连接信息或 Prompt key 上下文 | 无独立异常事件；后续 fallback 和挂载日志只能说明最终来源 | 保留 `operation=prompt_load_db`、受限 `promptKey`、`exceptionType`；继续 fallback |
| `PromptManager.java:123` | `DEBUG`；fallback Prompt 自动写回数据库失败或已存在 | `PromptService.savePrompt` 的数据库写入/唯一约束异常 | message 可能带 SQL、字段值、约束或 Prompt 配置细节 | 无独立异常事件；当前异常被安全忽略 | 保留 `operation=prompt_auto_init`、受限 `promptKey`、`exceptionType`；继续忽略并返回 |
| `AdminServiceImpl.java:302` | `ERROR`；管理员注销用户时逐条执行固定 native delete SQL | `JdbcTemplate.update(query, userId...)` 的数据库/约束/事务异常 | `query` 是代码内固定 SQL，不是用户输入，但会泄露表结构；`e.getMessage()` 可能包含 SQL/参数/数据库细节 | 管理员安全审计记录成功/拒绝结果，但没有逐语句失败事件 | 保留 `operation=admin_deregister_cleanup`、`userId`、固定 `statementIndex`、`exceptionType`；移除 SQL 和 message，继续下一条清理 |
| `CacheAspect.java:267` | `ERROR`；缓存未命中后执行源方法、序列化/写缓存失败的 catch | 被代理业务方法、Jackson、压缩、Redis 或锁脚本异常 | `key` 由 SpEL 参数生成，可能包含 userId、业务标识或组合输入；Throwable 可能包含业务正文和堆栈 | 仅 root console；没有缓存失败事件或 key 关联记录 | 保留 `operation=cache_query_data`、key 长度桶、`exceptionType`；移除 raw key 和 Throwable，保留锁释放及原异常重抛 |

### 2.2 同类伴随命中

逐条查看 5 个归属类后，发现只删除 7 个 allowlist 位置会留下等价政策漏洞。它们一并纳入本刀，但不新增延期 allowlist：

| 位置 | 当前问题 | 统一处理 |
| --- | --- | --- |
| `ModelProxyFactory.java:437` | 模型 attempt 事件发布失败时输出 `publishFailure.getMessage()` | 固定 `operation=publish_model_attempt`、attemptId、异常类型 |
| `PromptManager.java:94` | classpath Prompt 读取失败时把 Throwable 作为最后参数传给 logger | 固定 `operation=prompt_load_classpath`、promptKey、异常类型 |
| `AdminServiceImpl.java:213` | token 清理输出 `e.getMessage()` | 固定 `operation=admin_cleanup_tokens`、userId、异常类型 |
| `AdminServiceImpl.java:220` | LangChain Redis key 清理输出 `e.getMessage()` | 固定 `operation=admin_cleanup_langchain_cache`、userId、异常类型 |
| `AdminServiceImpl.java:230` | Milvus 清理输出 `e.getMessage()` | 固定 `operation=admin_cleanup_embeddings`、userId、异常类型 |
| `AdminServiceImpl.java:257` | 情景房间清理输出 `e.getMessage()` | 固定 `operation=admin_cleanup_situation_rooms`、userId、异常类型 |
| `CacheAspect.java:198` | 锁等待超时输出完整 cache key | 固定 `operation=cache_lock_timeout`、key 长度桶 |
| `CacheAspect.java:224` | 异步刷新输出完整 cache key 和 Throwable | 固定 `operation=cache_async_refresh`、key 长度桶、异常类型 |
| `CacheAspect.java:273` | 释放锁失败输出完整 cache key 和 Throwable | 固定 `operation=cache_release_lock`、key 长度桶、异常类型 |

这些伴随命中解释了本刀把审计的异常策略文件集扩大到 5 个类：否则 allowlist 虽然可以清空，后续同类日志仍会被误认为已经完成收敛。

## 3. 统一异常日志政策

### 3.1 允许字段

所有本刀异常 logger 必须使用固定事件名和命名字段；字段值只允许：异常类 simple name、固定枚举/分类、低敏 userId/requestId/runId/attemptId、模型 provider/modelId、statementIndex、key 长度桶、布尔值和数量。禁止把 Throwable 作为 SLF4J 最后参数，禁止 `getMessage()`、`getCause().getMessage()`、堆栈字符串、SQL、cache key、Prompt 正文、模型响应或请求正文作为 logger 参数。

`LowSensitivityLogSummary.exceptionType(Throwable)` 只输出异常类 simple name；`lengthBucket(String)` 只输出既有 `empty/short/medium/long` 固定集合。本刀不新增 hash，也不记录 key 前缀/后缀。

### 3.2 各类具体投影

| 类 | 保留的排障字段 | 删除的字段 | 业务行为保持 |
| --- | --- | --- | --- |
| `GlobalExceptionHandler` | `operation`、`status`、`exceptionType`、现有 logback trace slot | message、Throwable | streaming 分支仍返回 null；非 streaming 仍返回 500 和现有 Response 文本 |
| `ModelProxyFactory` | attempt、requestId、runId、provider、modelId、failureKind、fallbackEligible、root exception type | normalized message、Throwable | model state、budget reconcile、attempt event、fallback 和 rethrow 不变 |
| `PromptManager` | operation、Prompt key、exception type | DB/classpath/写入异常 message、Throwable | DB -> classpath -> hardcoded fallback、cache 和 auto-init 忽略逻辑不变 |
| `AdminServiceImpl` | operation、userId、statementIndex、exception type | SQL、异常 message | 每项 cleanup 继续捕获并执行后续项，最终安全审计记录不变 |
| `CacheAspect` | operation、key 长度桶、exception type | raw key、Throwable | 原方法结果、Redis 锁释放、异步刷新和原异常重抛不变 |

Global handler 的非 streaming 响应当前仍拼接 `e.getMessage()`（`GlobalExceptionHandler.java:91`）。这是客户端错误响应暴露面，不是日志参数；本刀保持现有响应契约，不把日志安全任务扩展成 API 错误文案变更。该边界必须在实施报告中明列，避免把“日志不打印 message”误报成“系统任何通道都不含 message”。

## 4. GlobalExceptionHandler:89 的取舍

这是全系统最大的异常暴露面。保留 Throwable 的好处是一次日志即可看到栈帧和 cause 链，能够定位任意未预期故障；代价是 generic handler 对请求、数据库、模型、文件和下游 HTTP 的内容没有类型边界，任何一条异常链都可能把敏感正文写入 root console。它不能因为排障方便而成为本刀的例外。

本刀选择固定 `ERROR` 事件 + 异常类型，利用现有 `%X{traceId}` 输出位和应用已有的业务/模型低敏记录关联故障。由于当前生产代码没有统一生成或写入 `traceId` 的实现，本刀不伪造 traceId，也不把可观测性注入混入本刀。`exceptionType` 对聚类足够，但对首次定位任意 NPE/数据库错误不等价于堆栈。

结论分为两个层面：

- 对本刀安全门槛：受控采样不是必需条件；必须先做到默认日志零 message/stack，才能清零 allowlist。
- 对生产排障能力：受控诊断通道是有必要的后续能力，尤其针对 GlobalExceptionHandler 兜底故障；它不应继续复用 root logger 的 Throwable 参数。

后续独立设计应满足：默认关闭、显式 incident 开关、按 route/exception type 有界采样、访问控制和短保留期、以 trace/request ID 关联、只保存异常类和脱离 message 的 stack frame，不序列化 Throwable 的 `toString`、cause message 或请求参数。本刀不添加该配置、appender、指标或数据表。

## 5. 方案比较与选择

### 方案 A：只改 7 条 allowlist

改动最小，但会留下 Prompt classpath、Cache async/release、Admin 其他清理和 Model attempt 发布失败的同类日志。审计名单清零会产生虚假完成信号，不采用。

### 方案 B：7 条阻塞项加 5 个归属类的相邻异常投影（推荐）

只触碰已经被事实勘察证明相关的 5 个类，统一字段和失败行为，静态审计可以把这些类设为异常政策范围并要求零 message/stack。改动面可控，完成标准与 allowlist 清零相互一致，采用此方案。

### 方案 C：本刀同时实现受控堆栈采样

能够补回部分 GlobalExceptionHandler 的定位能力，但会引入配置、采样、脱敏 sink、访问控制、保留策略和新的测试/运维边界，且容易把“受控”误实现为另一个明文日志通道。本刀不采用，单独作为可观测/事故诊断设计。

## 6. 完成标准与审计演进

完成标准固定为：**全局直接 payload 规则保持通过 + 异常政策范围零 message/stack + `DEFERRED_EXCEPTIONS` 为空**。

`SensitiveLogSourceAuditTest` 的演进方式：

1. 保留 `DeferredException` 结构和逐位置匹配机制，但把 `DEFERRED_EXCEPTIONS` 改为 `List.of()`，并显式断言列表为空；未来有人偷偷添加延期项时测试立即失败。
2. 新增 `EXCEPTION_POLICY_LOG_FILES`，包含上述 5 个生产类；对这些文件的所有 multiline logger block 硬拒 `Throwable`、`getMessage()`、stack 和截断正文，不再依赖逐条 allowlist 放行。
3. 直接 payload 判断不允许被 deferred 分支绕过；allowlist 只能描述历史延期状态，不能放行 query/payload。
4. 继续扫描全部 `src/main/java/**/*.java` 的 logger block，保留上一刀的全局 direct payload 检查。其他模块的既有技术异常日志不在本刀的 5 类异常政策范围内，不得借本刀新增模糊 allowlist。

最终审计应证明：direct payload findings 为空、5 类异常 message/stack findings 为空、observed deferred 为空、`DEFERRED_EXCEPTIONS` 为空。测试失败消息只使用固定 code，不回显源码块、异常正文或 sentinel。

## 7. 验证与回归

新增 `SensitiveExceptionLogSafetyTest`，使用 Logback `ListAppender` 和脱敏 sentinel 验证：

- Global handler 的 SSE committed 分支与 generic 500 分支不输出 `fixture-exception-message-7f3c`，且日志事件没有 Throwable proxy；Response 状态/返回值仍符合现有契约。
- ModelProxyFactory 的 fallback attempt 不输出 `fixture-model-error-7f3c`，仍发布失败 attempt 并继续 fallback；发布 attempt 失败路径同样只出现异常类型。
- PromptManager 的数据库读取、classpath 读取和 auto-init 写入异常不输出 `fixture-prompt-error-7f3c`，fallback/cache 行为不变。
- AdminServiceImpl 的 token/cache/Milvus/room/SQL cleanup 异常不输出 `fixture-admin-error-7f3c` 或 SQL sentinel，仍继续 cleanup 和审计。
- CacheAspect 的 timeout、async refresh、queryData failure、release-lock failure 不输出 `fixture-cache-key-7f3c` 或 `fixture-cache-error-7f3c`，仍保留锁释放和原异常重抛。

测试只使用 fixture ID、异常类和固定 token，不写自然语言正文，不生成评测 fixture/report。实现阶段先运行安全测试使其对旧日志失败，再改生产日志变绿；不通过弱化 sentinel 断言。

## 8. Phase 5 边界自查

- **可观测性：** 只消费已有 trace slot、requestId/runId 和模型 attempt 记录，不注入 MDC、指标、告警或新采样 sink。
- **备份恢复：** 不接触 MySQL、Milvus、Redis、OSS 备份和恢复流程。
- **上线运维：** 不增加灰度、回滚、incident 开关或保留策略；受控堆栈通道作为独立后续设计。
- **质量门槛：** 不修改任何评测套件、报告、fixture 或 `QualityGatePolicy`。
- **roadmap：** 不修改或勾选 roadmap；allowlist 清零后由评审方统一处理。
