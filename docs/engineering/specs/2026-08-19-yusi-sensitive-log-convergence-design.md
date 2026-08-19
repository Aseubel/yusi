# Yusi 敏感明文日志收敛设计

> **Status:** STRICT scope approved for implementation
> **Date:** 2026-08-19
> **Scope:** Phase 5 第一刀；本轮只勘察和设计，不修改生产代码、roadmap、CI、migration 或评测文件。

## 1. 结论

当前明确存在五个完整打印查询正文的日志调用，分布在 roadmap 指定的四个生产组件中：MCP 日记搜索、MCP 记忆搜索、`DiarySearchTool`、`LifeGraphTool` 和 `MidTermMemorySearchService`。它们的级别都是 `INFO`，并且直接把 `query` 或 `keyword` 作为参数传给 SLF4J。

推荐采用“固定低敏摘要 + 固定失败分类”的方式保留排障信号：

- 查询正文、前后缀、脱敏后的片段和无密钥的普通 hash 永不进入日志；
- 只记录已经批准的低敏字段：用户 ID、输入长度桶、日期过滤是否存在、`topK`/`maxResults`、结果数量、耗时、固定操作结果和异常类型；
- 目标流程的异常日志不再把 `Throwable` 作为最后参数传给 logger，避免异常 message 或堆栈间接回显 query、记忆正文或工具参数；
- `DiarySearchTool` 的 `expr` 调试日志也必须去掉，因为它包含用户 ID 和日期过滤值，属于完整工具过滤参数；改为固定的过滤开关摘要。

全量扫描同时发现了多处不属于四个 query 搜索组件、但同样打印用户输入或模型输出的日志。因而“这四处是生产侧最后的明文缺口”只能准确理解为“最后一组完整 query 搜索日志”，不能作为全量日志无明文的事实。若 Phase 5 日志安全条目的验收仍采用“日志中不再出现任何用户 query、记忆正文、Prompt 或工具结果”，这些相邻命中必须在同一安全门槛前被处理，或由评审明确拆为紧随其后的独立安全切片；本设计推荐前者。roadmap 保持不改，直到评审确认范围并完成证据。

## 2. 目标和边界

### 2.1 本刀目标

1. 四个指定搜索组件不再打印用户 query/keyword 明文。
2. 四个组件的错误路径不再输出异常正文或堆栈，且保留 `ERROR` 级别、操作名、异常类型和可关联的低敏字段。
3. `DiarySearchTool` 不再输出含用户 ID 和日期值的完整 Milvus `expr`。
4. 完成功能回归测试、运行时日志捕获测试和 `src/main` 全量日志模式审计。
5. 证明输入仍原样传给检索/模型边界，只有日志投影被改变；不改变返回结果、权限过滤、检索参数或异常响应契约。

### 2.2 不在本轮执行

- 不改 `src/main`、migration、CI、roadmap、既有评测 fixture、loader、报告或 `QualityGatePolicy`；本轮只新增本设计和实施计划。
- 不启动应用、H2/Milvus/Redis/MySQL 或任何外部服务，不运行 Maven 测试。
- 不引入 actuator、Micrometer、告警通道、备份恢复演练、灰度/回滚或限流复核。
- 不把查询摘要写入数据库、Trace、报告、fixture 或用户可见响应。
- 不使用 `SensitiveDataMaskService` 作为日志脱敏器。该服务的目标是模型边界，且失败回退可能把原文带回调用方，不适合作为日志安全的最后防线。

## 3. 现状勘察

行号是 2026-08-19 工作树中的基线行号，后续实现会以方法和日志消息同时定位。

### 3.1 四个指定组件

| 位置 | 级别 | 当前字段 | 敏感性判断 | 设计动作 |
| --- | --- | --- | --- | --- |
| `McpGrpcServiceImpl.java:70-71` | `INFO` | `userId`、`keyword` 明文、`startTimeStr`、`endTimeStr` | `keyword` 是 MCP 工具输入；时间值是工具参数 | 保留用户 ID、`keywordLengthBucket`、两个时间过滤布尔值；移除正文和日期值 |
| `McpGrpcServiceImpl.java:127` | `ERROR` | 固定错误文案 + `Throwable` | 异常 message/stack 可能回显请求 | 保留操作和异常类型，不传 throwable |
| `McpGrpcServiceImpl.java:148` | `INFO` | `userId`、`query` 明文、`maxResults` | `query` 是 MCP 工具输入 | 保留用户 ID、`queryLengthBucket`、`maxResults`；移除正文 |
| `McpGrpcServiceImpl.java:197` | `ERROR` | 固定错误文案 + `Throwable` | 异常 message/stack 可能回显请求 | 保留操作和异常类型，不传 throwable |
| `DiarySearchTool.java:126-127` | `INFO` | 用户 ID、`query` 明文、`startDate`、`endDate` | query 和日期都是 Agent 工具参数 | 保留用户 ID、query 长度桶、日期过滤开关；移除正文和值 |
| `DiarySearchTool.java:181` | `ERROR` | 固定错误文案 + `Throwable` | 异常可能包含 Milvus 请求或 query | 保留固定失败分类和异常类型，不传 throwable |
| `DiarySearchTool.java:202` | `DEBUG` | 完整 `expr`，含 user ID 和日期值 | 完整工具过滤参数；不是 query 正文，但违反低敏参数边界 | 改为过滤开关摘要；不记录 `expr` |
| `LifeGraphTool.java:47` | `INFO` | 用户 ID、`query` 明文 | 图谱工具输入可能是人名、地点或事件名 | 保留用户 ID、query 长度桶；移除正文 |
| `LifeGraphTool.java:58` | `ERROR` | 固定错误文案 + `Throwable` | 异常可能包含查询/数据库内容 | 保留固定失败分类和异常类型，不传 throwable |
| `MidTermMemorySearchService.java:46` | `INFO` | 用户 ID、`query` 明文 | 中期记忆搜索输入 | 保留用户 ID、query 长度桶、`topK`；移除正文 |
| `MidTermMemorySearchService.java:102` | `ERROR` | 用户 ID + `Throwable` | 异常可能包含检索请求或记忆正文 | 保留用户 ID、固定失败分类和异常类型，不传 throwable |
| `MidTermMemorySearchService.java:146` | `INFO` | 用户 ID、`limit` | 没有 query 或记忆正文，属于低敏操作元数据 | 可保留；统一为同一事件格式 |
| `MidTermMemorySearchService.java:161` | `ERROR` | 用户 ID + `Throwable` | 异常 message/stack 可能带出记忆摘要 | 保留用户 ID、固定失败分类和异常类型，不传 throwable |

四个组件都把检索输入继续传给原有 `EmbeddingModel`、Milvus、`LifeGraphQueryService` 或 `MemorySearchTool`。本刀只改变 logger 参数，不改变这些生产入口的输入或返回值。

### 3.2 日志基础设施和排障关联

- `src/main/resources/logback-spring.xml` 已把 `%X{traceId}` 放入控制台和文件日志格式。
- `ThreadPoolConfig` 只负责复制已有 MDC；当前 `src/main` 未搜索到明确的 `MDC.put("traceId", ...)` 生产写入点，因此不能假设每个请求都有 trace ID。
- `ModelRouteContext`、`ModelRouteContextHolder`、`AgentRunTraceService` 和 `AgentToolTraceService` 已能记录 `requestId`/`runId`、工具名称、状态、耗时和固定失败类别，但它们不是统一的日志 MDC 注入器。
- 因此本刀不新增 MDC 传播或 Trace 表字段。日志只使用现有 MDC 中已经存在的 trace ID；统一 request/trace 传播属于 Phase 5 可观测性切片。

## 4. 全量日志扫描结果

### 4.1 扫描方法

本次只读扫描覆盖 `src/main/java/**/*.java`：

1. `rg` 收集所有 `log.trace/debug/info/warn/error` 调用，并把跨行 logger 调用拼成调用块后检查参数。
2. 单独统计 `INFO/DEBUG`，检查 `query`、`keyword`、`content`、`text`、`message`、`profile`、`reason`、`result`、`response`、`prompt`、`name`、`location`、`device`、`image` 等输入/输出变量。
3. 对异常路径额外检查 `Throwable`、`getMessage()` 和截取输出的写法，因为它们不会全部被“logger 同行包含 query 变量”的 grep 命中。

扫描基线有两种正则口径：本地扫描为 81 个 Java 文件、302 个 logger 调用、159 个 `INFO/DEBUG` 调用；评审方独立复核为 82、304、160。差异仅来自跨行调用和 logger 别名的正则边界，验收记录采用评审方复核的 82/304/160，同时保留本地 81/302/159 作为复现口径。下面列出确认涉及用户输入、用户派生记忆或模型输出的命中；纯数量、枚举、模型名、任务 ID 和已存在的低敏 ID 日志不列为正文命中。

### 4.2 确认的相邻明文命中

| 位置 | 级别 | 明文字段 | 判断 | 推荐处理 |
| --- | --- | --- | --- | --- |
| `UserPersonaTool.java:58-59` | `INFO` | `preferredName`、`location`、`interests`、`tone`、`customInstructions` | 直接打印用户画像写入参数，敏感度高于 query | 同一安全门槛内改为 `updatedFieldCount`、固定操作结果和用户 ID |
| `SituationReportService.java:59` | `INFO` | 完整 `jsonReport` | 可能含情景参与者答案和模型分析正文 | 删除正文；只记录 `scenarioId`、输出状态和长度桶 |
| `SituationReportService.java:73` | `ERROR` | `Throwable` | 外层分析异常可能携带模型输出或用户答案 | 保留固定失败分类和异常类型，不传 throwable |
| `SituationReportService.java:104-105` | `WARN` | 无效 JSON 前 100 字符 | 截取仍可能是用户/模型正文 | 只记录固定格式分类和异常类型 |
| `SituationReportService.java:110-111` | `ERROR` | 无效输出前 500 字符 | 同上 | 只记录固定格式分类和异常类型 |
| `CognitiveConflictDetector.java:111` | `INFO` | LLM 生成的 `description` | 由用户画像和新洞察派生，属于记忆正文 | 只记录 `conflictDetected=true` 和用户 ID |
| `MidMemoryFusionService.java:165-166` | `INFO` | LLM 返回的 `reason` | 可能是记忆内容或解释正文 | 只记录固定 `conflictAction`/`merge` 分类和 ID |
| `MatchServiceImpl.java:229` | `INFO` | `userA.getUserName()`、`userB.getUserName()` | 直接打印用户昵称 | 改为两侧用户 ID 或 pair 事件计数，不记录名称 |
| `UserLocationServiceImpl.java:48` | `INFO` | `request.getName()` | 用户地点名称 | 改为用户 ID、地点类型和字段计数 |
| `TokenServiceImpl.java:97` | `DEBUG` | `deviceInfo` | 设备指纹/客户端信息，可能含个人或网络数据 | 改为 `deviceInfoPresent`，绝不记录 token 或设备字符串 |
| `DiaryServiceImpl.java:358` | `WARN` | `imagesJson` | 用户图片对象 key/URL | 只记录 payload 格式分类和长度桶 |
| `PersistentChatMemoryStore.java:310` | `WARN` | `imagesJson` | 聊天图片 URL 列表 | 只记录固定解析失败分类，不记录 JSON |
| `SpelResolverHelper.java:47-53` | `DEBUG/ERROR` | SpEL 表达式和 resolved value | 数据流未证明永远不含用户输入 | 删除表达式和值；记录固定解析结果/异常类型 |

审计回放又发现同类遗漏：`SpelResolverAspect.java:55-61` 也把 SpEL 表达式、解析值和异常堆栈直接写入 logger。它不改变业务结果，按同一低敏投影规则纳入 STRICT 实现范围；因此本刀实际修改 11 个确认相邻文件外，再加这一处审计发现文件。

`EmotionAnalyzerImpl.java:65` 目前打印模型原始结果到 `DEBUG`。正常契约应是固定 `EmotionType` 枚举，但如果模型返回异常文本，仍存在输出回显风险；建议改为只记录归一化枚举或 `UNKNOWN` 固定类别。

### 4.2.1 延迟异常政策组（下一刀阻塞项）

以下调用不属于本刀生产修改范围。它们必须在审计测试中以逐条 allowlist 锁定，不能用目录、通配符或“其他异常”这种模糊规则放行；交接报告必须原样列出这张表。allowlist 只解释延期，不把这些日志计入本刀的严格完成证据。

| 位置 | 当前日志字段 | 延迟归类理由 |
| --- | --- | --- |
| `GlobalExceptionHandler.java:86` | `e.getMessage()` | SSE 已开始后的中心异常处理诊断；属于全局异常响应/日志政策，留给异常治理刀处理 |
| `GlobalExceptionHandler.java:89` | `Throwable e` | 中心 HTTP 异常处理器保留堆栈；需要统一异常采样和脱敏策略，超出本刀局部日志投影范围 |
| `ModelProxyFactory.java:279` | `normalized.getMessage()` | 模型重试失败日志包含模型错误正文；属于模型调用错误分类与采样政策，留给可观测性/异常治理刀处理 |
| `PromptManager.java:64` | `e.getMessage()` | 数据库 Prompt 加载失败；属于 Prompt 配置加载异常政策，留给配置可观测性刀处理 |
| `PromptManager.java:123` | `e.getMessage()` | Prompt 自动初始化失败；属于 Prompt 配置写入异常政策，留给配置可观测性刀处理 |
| `AdminServiceImpl.java:302` | 内部固定 SQL `query` + `e.getMessage()` | `query` 是代码内固定删除 SQL，不是用户输入；但 SQL/异常正文仍不应进入长期日志，留给管理员数据清理审计刀处理 |
| `CacheAspect.java:267` | cache `key` + `Throwable e` | 缓存 key 可能携带业务标识且异常带堆栈；属于缓存错误日志和 key 脱敏政策，留给缓存可观测性刀处理 |

STRICT 还要求本刀修改的 11 个文件中所有相邻 `Throwable`/`getMessage()`/stack logger 一并收敛，即使它们不是直接 payload 命中；这些位置只允许固定 operation、结果分类和异常类型。`DiaryServiceImpl.java:358` 曾在勘察阶段属于异常政策候选，但 STRICT 范围已在本刀改为固定格式分类和异常类型，因此不得出现在延期 allowlist。其余第 4.2 节确认的 payload 命中同样必须在本刀修改；“多个模型/解析服务”不作为模糊豁免，后续发现的每一处都必须新增明确的 `file:line` 记录后才能延期。

### 4.3 范围决策

STRICT 范围已批准：四个 query 搜索组件 + 上表中已确认直接打印用户/模型正文的相邻命中一起进入本刀，最后以全量源码审计作为安全门槛。延期异常政策组只允许按 4.2.1 的逐条 allowlist 记录，不得成为本刀“全局无明文”的替代证据。

## 5. 收敛方案

### 5.1 低敏摘要策略

新增一个很小的 testable 生产工具类，例如 `com.aseubel.yusi.common.utils.LowSensitivityLogSummary`，只提供纯函数，不保存输入：

```java
public final class LowSensitivityLogSummary {
    public static String lengthBucket(String value) {
        if (value == null || value.isBlank()) return "empty";
        int codePoints = value.codePointCount(0, value.length());
        if (codePoints <= 32) return "short";
        if (codePoints <= 256) return "medium";
        return "long";
    }

    public static String exceptionType(Throwable error) {
        return error == null || error.getClass().getSimpleName().isBlank()
                ? "unknown" : error.getClass().getSimpleName();
    }
}
```

实现时保留 `null`/空白语义和 Unicode code point 计数；只输出固定桶名，不输出精确长度、输入片段或 hash。普通 SHA-256 不能作为安全方案：短 query 的字典空间小，离线枚举后仍可恢复常见主题。若将来确有跨请求内容关联需求，应另行设计密钥化摘要和密钥轮换，不能在本刀加入一个无密钥 hash。

### 5.2 四处日志的目标形态

目标消息应是固定事件名和低敏字段，例如：

```java
log.info("MCP memory search started: userId={}, queryLengthBucket={}, maxResults={}",
        userId, LowSensitivityLogSummary.lengthBucket(query), maxResults);
log.error("MCP memory search failed: operation=memory_search, exceptionType={}",
        LowSensitivityLogSummary.exceptionType(error));
```

具体取舍如下：

| 组件 | 保留的排障信号 | 删除的字段 | 原因 |
| --- | --- | --- | --- |
| `McpGrpcServiceImpl` | 用户 ID、输入长度桶、日期过滤存在性、`maxResults`、操作分类 | keyword/query、具体时间字符串、异常 message/stack | 可以区分空输入、时间过滤和分页问题，同时不暴露 MCP 参数 |
| `DiarySearchTool` | 用户 ID、输入长度桶、起止日期过滤存在性、结果数量、操作分类 | query、具体日期、完整 `expr`、异常 message/stack | 结果数量已经是现有有效信号；`expr` 是内部实现细节且含用户参数 |
| `LifeGraphTool` | 用户 ID、输入长度桶、操作分类 | query、异常 message/stack | 能定位调用是否到达图谱检索和失败分类 |
| `MidTermMemorySearchService` | 用户 ID、输入长度桶、`topK`/`limit`、结果数量、操作分类 | query、记忆文本、异常 message/stack | 保留检索容量和空结果诊断，不碰记忆正文 |

查询开始日志保留 `INFO`，结果数量日志保留现有 `INFO`，过滤表达式日志降为固定摘要 `DEBUG` 或直接删除，失败日志保留 `ERROR`。实现中不把 `userId`、`runId`、`traceId` 当作 query 替代物以外泄露；这些 ID 仍遵循既有低敏访问和保留策略。

### 5.3 排障能力替代

- 日志关联：继续使用 logback 已配置的 `traceId`；在 trace ID 存在时用它串起一次请求，不在日志中复制 query。
- Agent 调用关联：聊天工具路径优先沿现有 `requestId`/`runId`、`AgentRunTrace` 和 `AgentToolTrace` 查询生命周期、工具名、耗时和固定失败分类。
- 可观测指标：由 Phase 5 可观测性切片新增 `tool_search_total`、`tool_search_failure_total`、检索延迟 histogram 和结果数量 histogram；标签只允许工具名、操作名、结果/失败分类，不允许 user ID、query、正文或高基数 request 参数。本刀不引入 Micrometer。
- 复现路径：生产日志提供时间、trace ID、操作和低敏参数桶；排障人员在有权限的受控环境中用用户授权数据复现，不能通过日志回填原 query。若需要保留栈信息，应建立已脱敏的内部错误采样通道，由单独安全设计定义，不恢复裸 throwable。

代价是线上日志不再直接给出异常堆栈和 query，复杂检索问题的首次定位会变慢；这是可接受的隐私取舍。异常类型、固定分类、结果数量和 trace/run 关联足以判断失败阶段，详细内容由受控复现和后续可观测数据补齐。

## 6. 验证方案

### 6.1 低敏摘要单测

对 `LowSensitivityLogSummary` 覆盖 `null`、空白、ASCII、中文/emoji、边界长度和异常类型。断言任何结果只属于固定集合 `empty/short/medium/long` 或异常类简单名，且不包含输入 sentinel。该测试不使用 JSON fixture，不写报告。

### 6.2 运行时日志捕获测试

使用已有 `spring-boot-starter-test` 的 Mockito 和 Logback `ListAppender<ILoggingEvent>`，直接实例化四个组件或使用窄 Spring 测试，不启动任何外部依赖：

1. 为每个组件注入唯一的内存 sentinel query，例如 `fixture-log-sensitive-query-7f3c`，走成功路径；捕获目标 logger 的 `formattedMessage`，同时检查 `ThrowableProxy` 的 message/stack 字符串。
2. 让 Milvus、图谱查询服务或 Embedding 失败，并把同一 sentinel 放进异常 message；断言日志仍不含 sentinel，且包含预期固定级别、操作分类和异常类型。
3. 对 Diary 搜索捕获日志并断言不存在 `metadata["userId"]`、具体日期和 sentinel query；返回的检索结果仍与原测试相同。
4. 对 MCP 搜索同时验证 gRPC 响应仍完成、结果数量仍正确；只检查日志不泄露，不能因为响应错误字段仍按既有契约携带 exception message 就把功能测试与日志测试混为一谈。
5. 对 `LifeGraphTool` 和 `MidTermMemorySearchService` 断言返回结果、空结果和异常回退行为不变。

### 6.3 相邻命中回归

若按推荐范围实施，新增同样的 appender 测试覆盖 `UserPersonaTool`、`SituationReportService`、`CognitiveConflictDetector`、`MidMemoryFusionService`、`MatchServiceImpl`、`UserLocationServiceImpl`、`TokenServiceImpl`、图片解析日志和 `SpelResolverHelper`。测试只使用内存 sentinel，不把它放入 evaluation fixture/report；每个日志只允许固定分类、字段计数、枚举、低敏 ID 和异常类型。

### 6.4 源码扫描门槛

运行时测试之外，保留全量 grep 自检：

```powershell
rg -n -i "\blog\.(trace|debug|info|warn|error)\s*\(" src/main/java --glob "*.java"
rg -n -i "\b(query|keyword|plainContent|profileText|reason|description|jsonReport|deviceInfo|imagesJson|preferredName|customInstructions)\b" src/main/java --glob "*.java"
```

第二个命令的结果必须逐条人工归类，不能简单要求关键字总数为零，因为生产业务方法本身合法地需要这些变量。审计测试必须全局硬拒 logger 参数中的直接用户/模型 payload（包括 `query`/`keyword`、`payload` 及第 4.2 节列出的正文变量）；只对本刀修改文件硬拒 `Throwable`、`getMessage()`、stack 或截断正文。4.2.1 的每个延期异常调用必须按精确 `file:line` 显式 allowlist，且不能放行直接 payload。验收记录必须列出每一条剩余命中、为何是固定枚举/内部 ID/异常类型，或对应的后续安全切片；不得只 grep 四个文件。

### 6.5 功能和范围门槛

- 先运行聚焦日志安全和既有受影响单测，再运行 `\.mvnw.cmd -q test` 全量测试；本轮设计阶段两条命令都不执行。
- `git diff --check` 必须干净；实现切片的变更只能包含批准的生产日志文件、摘要 helper 和对应测试。
- 不修改已有 Phase 4 评测 fixture、loader、report、roadmap 或 post-release backlog。
- STRICT 完成标准是“全局零直接明文 + 修改范围内零 message/stack + 延迟组明列”：主路径测试、失败路径测试、全量源码审计和全量 Maven 均通过，且 4.2.1 allowlist 逐条存在并与延期理由一致，才可以把 Phase 5 日志安全条目标记为候选完成；本刀不勾选 roadmap。

## 7. 风险和边界

| 风险 | 影响 | 控制 |
| --- | --- | --- |
| 去掉 throwable 后栈信息减少 | 线上首次定位变慢 | 保留异常类型、固定分类、trace/run 关联；详细栈由单独受控通道设计 |
| 只修四个组件 | 全量日志仍可能有画像、情景和地点明文 | 将相邻命中列为同一安全门槛阻塞项，或由评审明确后续切片，不提前宣称完成 |
| traceId 实际为空 | 跨日志关联能力不足 | 本刀不伪造 traceId；Phase 5 可观测性切片负责注入和传播验证 |
| 长度桶仍有极小侧信道 | 可区分空/短/长输入 | 不记录精确长度和 hash；仅保留四档固定桶，且不进入用户标签或高基数指标 |
| 误把用户 ID 当正文 | 日志仍需访问控制和保留策略 | 沿用项目既有低敏 ID 边界，不记录昵称、地点名、邮箱、token 或正文；后续隐私自检复核 ID 范围 |

## 8. Phase 5 边界自查

- **可观测/告警：** 本刀只定义日志字段和测试证据，不添加指标、健康检查、告警或 Trace 传播；指标标签和 traceId 注入留给下一刀。
- **备份恢复：** 不接触 MySQL、Milvus、Redis、OSS 备份周期、RTO 或恢复演练。
- **上线运维：** 不写灰度、回滚、降级、上线清单或应急流程。
- **质量门槛：** 不改四刀评测套件、报告或 `QualityGatePolicy`；日志安全的 sentinel 测试是生产代码单测，不新增低敏评测报告。
- **roadmap：** 保持现状，不修改任何 checkbox；评审通过并完成实际实现/验证后再由评审方统一收尾。

## 9. 评审决策

评审已条件批准 STRICT 范围：四个指定组件、11 个相邻生产文件、审计发现的 `SpelResolverAspect` 同类遗漏、摘要 helper 和 3 个安全测试按实施计划执行。批准条件已写入本设计与实施计划：全局直接 payload 硬拒；本刀修改文件内 `Throwable`/`getMessage()`/stack 硬拒；4.2.1 延迟异常组逐条 allowlist 并列入交接报告；不修改 roadmap、CI、migration 或既有评测文件。
