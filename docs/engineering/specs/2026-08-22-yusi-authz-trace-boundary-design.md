# Yusi API 鉴权与低敏 Trace 边界设计

## 1. 范围与证据口径

本切片覆盖 roadmap L638-L643 挂账的两个本地子项：API 鉴权与越权回归、低敏 Trace 边界全量复查。本文只记录仓库现状、缺陷登记和下一轮测试设计，不修改生产代码、测试、CI、roadmap 或部署配置。

所有路由统计复用 `src/test/java/com/aseubel/yusi/common/ratelimit/RateLimitCoverageContractTest.java:240-266` 的扫描口径：扫描 `src/main/java/com/aseubel/yusi/controller` 下 `*Controller.java`，跳过以 `//` 开头的映射行，读取类级 `@RequestMapping` 与 `@GetMapping`/`@PostMapping`/`@PutMapping`/`@DeleteMapping`/`@PatchMapping`。该契约明确排除注释中的 `POST /api/match/run`，并在 `:156-164`、`:30-120` 固化 90 个真实写映射与 22 个既有限流点。

按相同扫描器复核得到 22 个 HTTP Controller、158 个真实映射：90 个写映射、68 个读映射。158 是源码映射统计，不是 MockMvc 实际执行覆盖数；上一轮出现的 159/69 不属于当前扫描器口径，不作为本设计的事实。

## 2. 鉴权链路现状

### 2.1 认证实现

| 环节 | 当前事实 | 证据 |
| --- | --- | --- |
| 注解契约 | `@Auth(required=true)` 是默认值；方法级配置可覆盖类级配置 | `src/main/java/com/aseubel/yusi/common/auth/Auth.java:9-15` |
| HTTP 拦截 | 自定义 `AuthAspect` 通过 `@within || @annotation` 拦截；当前未发现 Spring Security filter/config 作为 HTTP 主链路 | `src/main/java/com/aseubel/yusi/common/auth/AuthAspect.java:31-47` |
| Bearer 解析 | 只读取 `Authorization: Bearer ...`，校验黑名单、JWT access 类型、subject 和设备 token | `AuthAspect.java:60-105` |
| 上下文注入 | JWT subject 写入 `UserContext`，业务调用结束后在 `finally` 清理 | `AuthAspect.java:105-122`；`src/main/java/com/aseubel/yusi/common/auth/UserContext.java:3-16` |
| 非 HTTP 调用边界 | 没有 Servlet request attributes 时直接 `joinPoint.proceed()`；这是服务层直接调用不经过认证的事实，不等同于 HTTP 匿名可达 | `AuthAspect.java:42-47` |
| 管理权限 | Admin 使用 `checkAdminPermission`；模型与 prompt 使用各自的 admin helper；super-admin 另按 permission level >= 99 校验 | `src/main/java/com/aseubel/yusi/controller/AdminController.java:71-91`；`src/main/java/com/aseubel/yusi/controller/ModelManagementController.java:78-82`；`src/main/java/com/aseubel/yusi/controller/PromptController.java:38-43` |

### 2.2 Controller 矩阵

表中“认证”使用 `required` 语义：`必需` 表示 HTTP 入口必须有有效 access token，`可选` 表示匿名也能进入，`无注解` 表示该 Controller 没有 `@Auth` 契约。除已写出完整路径的行外，“全部读映射”列使用相对于该 Controller 类级 `@RequestMapping` 的路径。归属校验记录实际把资源限定到当前用户、参与者、管理员或公开资源的方式。

| Controller | 映射统计（总/写/读） | 全部读映射 | 认证方式 | 资源归属或权限校验 | 证据 |
| --- | ---: | --- | --- | --- | --- |
| AdminController | 18/7/11 | `/stats`, `/users`, `/me`, `/audit`, `/scenarios/pending`, `/scenarios`, `/suggestions`, `/suggestions/{suggestionId}`, `/suggestions/pending-count`, `/announcements`, `/memory/config` | 类级 `@Auth`，管理员方法再 checkAdmin；full-sync/deregister 为 super-admin | 管理员角色、目标用户权限等级、目标场景/建议由 AdminService 处理 | `AdminController.java:54-91,94-275` |
| AiController | 12/6/6 | `/chat/history`, `/persona-config`, `/soul-report/latest`, `/soul-report/history`, `/agent-growth`, `/cognitive-conflicts` | 各入口 `@Auth` | 使用 `UserContext` 查询和写入；冲突按当前用户比对，注入 greeting 按 notification.userId 比对 | `AiController.java:159-163,677-762` |
| DeveloperConfigController | 4/3/1 | `/` | 类级 `@Auth` | API key 配置全部取 `UserContext.getUserId()` | `DeveloperConfigController.java:21-58` |
| DiaryController | 6/3/3 | `/list`, `/{diaryId}`, `/footprints` | 类级 `@Auth` | list/footprints 显式比对 userId；创建/编辑强制写当前用户；详情将当前用户传入 service | `DiaryController.java:28-95` |
| GeoController | 2/0/2 | `/search`, `/reverse` | 无 `@Auth`，公开地理代理 | 无用户资源，属于 IP 限流后的公开外部查询 | `GeoController.java:18-55` |
| ImageController | 10/7/3 | `/check`, `/chunk/progress`, `/url` | 类级 `@Auth` | 所有 OSS URL、删除、分片操作传当前 userId；对象 key 以前缀校验归属 | `ImageController.java:19-179`；`OssService.java:190-225,691-710` |
| KeyManagementController | 6/4/2 | `/settings`, `/diaries-for-reencrypt` | 类级 `@Auth` | 密钥设置、重加密、恢复均使用当前用户 | `KeyManagementController.java:26-96` |
| LifeGraphController | 16/8/8 | `/search`, `/timeline`, `/communities`, `/emotions`, `/emotions/triggers`, `/merge-suggestions`, `/graph`, `/graph/bfs` | 类级 `@Auth` | 查询使用当前用户；实体/关系的 ID 与 source/target 由数据服务按 userId 校验 | `LifeGraphController.java:31-229`；`LifeGraphDataService.java:157-180,265-364` |
| MatchController | 8/6/2 | `/recommendations`, `/status` | 类级 `@Auth` | matchId 进入 service 后由 `requireMatch` 校验 userA/userB 参与者 | `MatchController.java:29-99`；`MatchServiceImpl.java:771-897` |
| MemoryCenterController | 9/6/3 | `/center`, `/persona`, `/life-graph` | 类级 `@Auth` | 所有记忆中心、画像、life-graph 操作传当前用户 | `MemoryCenterController.java:30-100` |
| ModelManagementController | 6/2/4 | `/states`, `/console`, `/attempts`, `/metrics` | 类级 `@Auth`，每个入口 checkAdmin | 非管理员被拒绝；模型治理与 trace 查询没有以普通用户身份执行 | `ModelManagementController.java:29-82` |
| NotificationController | 6/3/3 | `/`, `/unread`, `/unread/count` | 类级 `@Auth` | 列表按 userId 查询；读/删 repository 条件同时带 notificationId 和 userId | `NotificationController.java:16-81`；`NotificationService.java:246-298` |
| PingController | 1/0/1 | `/health` | 无 `@Auth` | `/api/health` 为公开健康入口 | `PingController.java:17-20` |
| PromptController | 6/4/2 | `/{name}`, `/search` | 类级 `@Auth` | search 与 4 个写入口 checkAdmin；`GET /api/prompt/{name}` 当前只有登录校验，未 checkAdmin | `PromptController.java:27-50,52-106` |
| RoomChatController | 3/1/2 | `/history`, `/poll` | 类级 `@Auth` | send/history/poll 查询 room 后均要求 members 包含当前用户 | `RoomChatController.java:36-150` |
| SituationRoomController | 16/10/6 | `/scenarios/my`, `/scenarios`, `/scenarios/status`, `/history`, `/report/{code}`, `/{code}` | 类级 `@Auth` | 房主、房间成员、scenario submitter 分别由 service 校验；`/scenarios` 是 approved scenario catalogue，不按用户归属过滤；status 查询要求 admin | `SituationRoomController.java:18-131`；`SituationRoomServiceImpl.java:87-167,257-322,375-432` |
| SoulChatController | 4/2/2 | `/history`, `/unread/count` | 类级 `@Auth` | send/history 以 match userA/userB 校验；read 以当前用户标记接收消息 | `SoulChatController.java:37-151` |
| SoulPlazaController | 10/6/4 | `/feed`, `/my`, `/signals/received`, `/signals/unread-count` | 9 个必需，feed 为 `@Auth(required=false)` | card 更新/删除由 owner 校验，resonate 禁止自己的 card；signal 的 toUserId/cardId 关系未在 service 显式校验 | `SoulPlazaController.java:29-134`；`SoulPlazaServiceImpl.java:257-338`；`ResonanceSignalService.java:38-63` |
| StatsController | 1/0/1 | `/platform` | `@Auth(required=false)` | 平台聚合统计，无用户资源 | `StatsController.java:19-31` |
| SuggestionController | 2/1/1 | `/{suggestionId}` | 创建无 `@Auth`；详情方法 `@Auth` 后 checkAdmin | 创建是公开意见提交；详情仅管理员 | `SuggestionController.java:18-50` |
| UserController | 8/8/0 | 无 | 6 个注册/登录/刷新/找回入口 `required=false`，update/logout 使用类级必需认证 | update/logout 使用当前用户；公开认证流程不读取其他用户资源 | `UserController.java:30-107` |
| UserLocationController | 4/3/1 | `/list` | 类级 `@Auth` | list/delete 显式 `requireCurrentUser`；写入强制覆盖 request.userId | `UserLocationController.java:27-92` |

认证统计为：必需认证 146/158（92.4%）；具有显式 `@Auth` 契约（必需或可选）154/158（97.5%）；其余 4 个无注解公开映射是 Geo 2、Ping 1、Suggestion create 1。资源/角色校验按“当前用户上下文、参与者、owner、admin/super-admin”分类后，146 个必需认证映射中 145 个有静态可追溯的归属或角色门，1 个确认缺口见第 4 节；该百分比不是运行时安全通过率。

WebSocket 不计入上述 158 个 HTTP 映射：STOMP CONNECT 校验 Bearer token，后续无 principal 拒绝，soul-match topic 要求参与者，room topic 要求成员；证据为 `src/main/java/com/aseubel/yusi/config/WebSocketTokenAuthenticator.java:111-127`、`WebSocketAuthChannelInterceptor.java:25-85`、`WebSocketConfig.java:158-184`。日记语音 WebSocket 在 start 消息中认证，handler 注册见 `DiaryVoiceWebSocketHandler.java:41,178-207`、`DiaryVoiceWebSocketConfig.java:15-26`，需要单列测试。

## 3. 越权攻击面分级

以下数量是下一轮回归设计的攻击面条目数，不是已经执行的测试结果。

### 3.1 水平越权：9 个回归条目

| 编号 | 真实入口 | 当前保护证据 | 回归断言 |
| --- | --- | --- | --- |
| H-01 | `GET /api/diary/{diaryId}`、list、footprints | `DiaryController.java:36-74,88-95` | fixture-user-authz 不能读取 fixture-user-other-authz 的 diary |
| H-02 | `POST/GET /api/match/{matchId}/*`、history/status | `MatchController.java:56-99`；`MatchServiceImpl.java:891-897` | 非 userA/userB 的 matchId 只能得到固定拒绝/不存在语义 |
| H-03 | `POST/GET /api/room-chat/*?roomCode=...` | `RoomChatController.java:52-150` | 非成员不能发送、拉取 history 或 poll |
| H-04 | `GET /api/room/{code}`、report、submit/cancel | `SituationRoomServiceImpl.java:193-322` | 非成员、非房主不能读取或执行对应操作 |
| H-05 | `GET /api/image/url`、`POST /api/image/urls`、DELETE 与批删 | `ImageController.java:155-179`；`OssService.java:190-225,691-710` | fixture-object-key-authz 不得穿过另一用户的 object-key 前缀校验 |
| H-06 | lifegraph entity/relation ID 操作 | `LifeGraphController.java:153-229`；`LifeGraphDataService.java:157-180,265-364` | 另一用户的实体、关系和跨用户 source/target 均拒绝 |
| H-07 | notification read/delete | `NotificationController.java:57-81`；`NotificationService.java:275-298` | 另一用户 notificationId 不改变状态、不删除 |
| H-08 | plaza card update/delete/resonate | `SoulPlazaController.java:75-99`；`SoulPlazaServiceImpl.java:257-338` | 非 owner 不能改删；不能与自己的 card resonate |
| H-09 | `POST /api/plaza/signal` 的 toUserId/cardId | `SoulPlazaController.java:105-112`；`ResonanceSignalService.java:38-63` | 先固定复现当前未校验目标关系的行为；产品确认“card owner 必须等于 toUserId”后，将其升级为强制拒绝测试 |

### 3.2 垂直越权：3 个命名空间

1. `/api/admin/**`：Controller 有类级认证和 checkAdmin，full-sync/deregister 还要求 super-admin；例证 `AdminController.java:71-91,249-266`。
2. `/api/model/**`：6 个入口均调用 `checkAdmin`；例证 `ModelManagementController.java:39-82`。
3. `/api/prompt/**`：search 和写入口调用 `checkAdmin`，但 `GET /api/prompt/{name}` 在 `PromptController.java:45-50` 直接返回 prompt 内容。

### 3.3 未认证访问：4 个边界条目

1. 受保护 diary：`DiaryController.java:28-30`，匿名请求必须被 `TOKEN_MISSING` 拒绝。
2. 受保护 AI：`AiController.java:159-160,243-247`，匿名不能进入 chat history 或 stream。
3. 管理模型/prompt：`ModelManagementController.java:29-31`、`PromptController.java:27-29`，匿名不能绕过认证层到达 admin 判断。
4. 公开边界保留：User 注册/登录等 `UserController.java:39-89`、建议创建 `SuggestionController.java:26-41`、Geo、health、平台统计和 plaza feed 的公开语义不得被回归修复误伤；这些是 allowlist，不应被误判为未认证漏洞。

## 4. 缺陷登记

### AUTHZ-001：prompt 读取缺少管理员权限

- 级别：确认的垂直越权，必须修复。
- 位置：`src/main/java/com/aseubel/yusi/controller/PromptController.java:45-50`。
- 复现路径：已认证但 `userService.checkAdmin(UserContext.getUserId()) == false` 的用户访问 `GET /api/prompt/{name}`，请求只经过类级 `@Auth`，随后调用 `promptService.getPrompt(name, locale)` 并返回模板内容。
- 影响：普通登录用户可读取 `/api/prompt/**` 中受管理的 prompt 内容，违反本切片规定的 prompt 垂直授权边界。
- 修复建议：在 `getPrompt` 入口调用既有 `checkAdmin()`，复用既有固定拒绝响应；新增 MockMvc application-invariant-only 非管理员 403/固定错误码回归，管理员成功路径单独保留。
- 本轮状态：只登记，不修改源码；测试先红，修复在下一轮实施。

### AUTHZ-CANDIDATE-001：共鸣信号目标关系未显式验证

- 级别：待业务语义确认的水平越权候选，不计入确认漏洞。
- 位置：`SoulPlazaController.java:105-112`、`ResonanceSignalService.java:38-63`。
- 复现路径：已认证用户可提交任意 `toUserId` 与 `cardId`；service 只拒绝 fromUserId 等于 toUserId、重复发送和过长 message，没有验证 card owner 是否为 toUserId，也没有验证目标用户是否允许接收该 card 的信号。
- 风险：若产品语义是“对某张 card 的 owner 发送信号”，可能写入不匹配的跨用户关联；若产品允许任意目标用户接收匿名信号，则该行为是设计语义而非漏洞。
- 下一步：安全/产品评审先确认目标关系契约；确认必须匹配后，增加 card owner 与 toUserId 的固定拒绝测试和 service 校验。未确认前不擅自修复。

未发现其他可在静态证据下直接确认的 HTTP 水平/垂直越权。真实部署渗透、代理层路径覆盖和 WebSocket native handshake 不在本地静态结论内，列入 deployment-only。

## 5. Trace 与低敏日志边界

### 5.1 Trace 表与写入点

| 组件 | 允许字段/当前写入 | 明文内容结论 | 尚未锁定的边界 |
| --- | --- | --- | --- |
| AgentToolTrace | user/run/tool call 标识、toolName/source、版本、重试、幂等、状态、失败分类、时间、耗时 | entity 明确禁止 arguments/results、queries、model content；service start/complete/close 只写上述元数据 | 需要静态断言没有 payload、tool input/output 字段，且运行时保存对象不包含 sentinel |
| AgentRunTrace | user/run、scene、status、stage、toolCount、responseCharCount、failure/cancel category、时间、耗时 | entity 明确只保存生命周期摘要；`responseCharCount` 是计数，不是响应正文 | 需要断言 response 内容、prompt、thinking、tool input/output 不进入 entity 或 repository save |
| ModelCallTrace | request/attempt/run/user、scene、prompt identity、policy/route、tier、model/provider、token/cost、status/error/finish metadata | entity 无 prompt/model response 正文字段；`ModelProxyFactory` 发布的是 `ModelCallAttemptEvent` 元数据 | promptKey/routeReason/errorCode 的格式需要 allowlist；需要断言事件和 entity 的字段集合不扩展为 content/payload |

证据：`src/main/java/com/aseubel/yusi/pojo/entity/AgentToolTrace.java:25-28,47-116`、`AgentToolTraceService.java:31-73,93-127`；`AgentRunTrace.java:23-27,47-90`、`AgentRunTraceService.java:41-156`；`ModelCallTrace.java:44-132`、`src/main/java/com/aseubel/yusi/service/ai/runtime/ModelCallAttemptEvent.java:5-29`、`ModelCallTraceService.java:32-109`、`src/main/java/com/aseubel/yusi/service/ai/model/ModelProxyFactory.java:431-460`。

### 5.2 关联运行时与日志写入点

| 位置 | 写入内容 | 当前边界判定 |
| --- | --- | --- |
| `AiController.java:249,329,343-350` | 读取用户 message、构造模型请求、接收 responseText；response 只通过 SSE 输出并累计字符数 | 不是 Trace 表写入，但需测试保证 sentinel 不进入 trace service/repository；不把 client response 误称为低敏日志 |
| `AiController.java:396-403,420-434` | tool name/source、执行成功和耗时事件；未见 arguments/results 写入 Trace | 允许的工具元数据；需新静态/写入点断言锁住“完整拒绝 input/output” |
| `AiController.java:449` | `log.error(..., e)` | 未被 Trace 专用测试锁定；异常对象可能带 message，下一轮必须改为低敏 exceptionType/固定分类或纳入明确政策 |
| `AiController.java:642-653` | AgentRunTrace/AgentToolTrace 失败时把 throwable 传 logger | 未被 Trace 专用测试锁定；不可用 `contains` 代替完整 throwable/message 拒绝 |
| `AgentRunTraceService.java:137` | trace terminal convergence 失败时 logger 接收 exception | 未被 Trace 专用测试锁定；日志只应保留 operation、exceptionType 等低敏摘要 |
| `AgentToolExecutionAttemptRegistry.java:15-18,31-47,60-72` | 内存只保存 retry 关联元数据；异常日志带 runId 和 throwable | javadoc/结构禁止复制 request arguments/results，但 logger throwable 尚无 Trace 边界 sentinel 锁定 |
| `AgentToolIdempotencyMaintenance.java:24-36` | 维护任务成功计数；失败 logger 接收 throwable | 非用户 payload，但属于关联 Trace/后台日志，需纳入统一异常字段扫描 |
| `ModelCallTraceService.java:40-45` | repository 保存失败时 `exception.getMessage()` 进入 logger | 明确的 message 写入点；当前五类异常政策测试不覆盖该类，需新测试或下一刀异常政策收敛 |
| `DiaryVoiceWebSocketHandler.java:267-272` | connectionId、code、userId 与 throwable | 与语音 Trace 关联的日志；userId 与异常对象不应作为原文日志，当前 Trace 专用测试未锁定 |
| `PlazaLifeGraphListener.java:89-94` | sourceId 与 throwable | sourceId 属于关联资源标识，异常对象未低敏化；需在 Trace/异步任务日志审计中单独断言 |
| `LifeGraphTaskBatchService.java:176-178,219-221` | `exception.getMessage()` 写入 task retry error 字段 | 不是 logger，但属于持久化错误字段边界；不能由 Trace 表无正文推断全链路安全，需纳入任务错误字段检查 |
| `GlobalExceptionHandler.java:88-98` | logger 只输出 exceptionType；HTTP 500 response 仍拼接 `e.getMessage()` | logger 已是低敏摘要，但 response 是另一边界；本切片只登记，不把 HTTP 响应内容误算为 Trace PASS |

### 5.3 与既有低敏契约的关系

- `SensitiveLogSourceAuditTest.java:23-39` 扫描既有日志修改文件，`:50-92` 对直接 payload 做全局检查、对修改范围内 message/stack 做硬拒绝；`:48` 的延期列表当前为空。它没有按 Trace entity/repository 字段做完整白名单校验。
- `SensitiveExceptionLogSafetyTest.java:154-285` 覆盖 GlobalExceptionHandler、ModelProxyFactory、PromptManager、AdminServiceImpl、CacheAspect 五类异常日志；它不能替代 AgentRun/AgentTool/ModelCall 关联日志测试。
- `ObservabilitySensitiveDataTest.java` 是指标/任务状态 sentinel 契约，必须复用其“固定 sentinel + 完整 `doesNotContain`”风格，但不应把指标标签测试当作 Trace 字段测试。
- 四标签白名单为 `tool`、`operation`、`result`、`failure_category`，见 `src/main/java/com/aseubel/yusi/observability/metrics/YusiMetrics.java:25-26`。Trace 不得通过标签承载 user ID、query、正文、token、object key 或工具 input/output；敏感词计数日志现有输入边界见 `src/main/java/com/aseubel/yusi/common/utils/SensitiveWordUtils.java:48-63`。

## 6. 测试设计

### 6.1 越权回归：`AuthzBoundaryMockMvcTest`

测试类型标记为 `application-invariant-only`：使用 H2、fixture-user-authz / fixture-user-other-authz 等合成用户和 repository/service mock，不连接真实 MySQL、Redis、Milvus、OSS、模型或 WebSocket 服务。

必须先写红点测试，再修复确认缺陷。测试断言使用固定状态码、固定错误码和服务调用次数，不以“响应包含某字符串”作为授权证明。

最小红点集合：

1. 非管理员访问 `GET /api/prompt/{name}`，当前行为应先暴露 AUTHZ-001；测试先记录实际 200/服务调用，再将契约改为固定拒绝并修复 Controller。
2. 普通用户访问 `/api/admin/me`、`/api/model/states`，断言 admin service 未执行；同时覆盖 `/api/prompt/search`，确保 prompt 其他入口的 admin 保护不回退。
3. 匿名访问 diary detail、AI chat history、model states，断言认证层在 service 之前返回 `TOKEN_MISSING` 语义。
4. 两用户 fixture 对 diary、match、room、image、lifegraph、notification、plaza card 各执行一条他人资源访问；断言 repository/service 不发生跨用户成功写入或返回。
5. `plaza/signal` 先固定当前行为和保存参数，不能把候选语义直接伪装成已修复；产品确认 card owner 关系后再开启严格拒绝断言。

测试必须把 HTTP 90 写映射与 68 读映射的静态统计作为独立 contract assertion 或复用 `RateLimitCoverageContractTest` 的 scanner；不把“本测试选取了 12 个入口”报告成全路由运行时覆盖。

### 6.2 Trace 边界：`TraceBoundarySensitiveDataTest`

测试类型标记为 `mock-contract-only`，不声称真实日志收集器或真实外部依赖安全。测试 fixture 只使用以下脱敏 sentinel：`fixture-user-authz`、`fixture-query-authz`、`fixture-content-authz`、`fixture-token-authz`、`fixture-object-key-authz`、`fixture-prompt-authz`、`fixture-response-authz`、`fixture-input-authz`、`fixture-output-authz`、`fixture-exception-authz`、`fixture-message-authz`。

静态断言：

- `AgentToolTrace`、`AgentRunTrace`、`ModelCallTrace`、`ModelCallAttemptEvent` 的字段名集合精确等于批准的 metadata 集合；完整拒绝 `payload`、`query`、`content`、`prompt`、`response`、`input`、`output`、`arguments`、`results`、`token` 等字段，禁止用宽松 `contains` 代替字段集合断言。
- `AgentToolTraceService`、`AgentRunTraceService`、`ModelCallTraceService` 的 repository save 投影不得含任何 sentinel；只允许 user/run/tool 标识、状态、分类、计数、时延和版本元数据。
- 关联日志调用完整检查 formatted message、throwable proxy、exception message 和 stack text；`AiController`、`AgentRunTraceService`、`AgentToolExecutionAttemptRegistry`、`ModelCallTraceService`、`DiaryVoiceWebSocketHandler`、`PlazaLifeGraphListener` 等未锁定点逐一列出，不能以“没有 payload 字段”替代。
- `LifeGraphTaskBatchService` 的 retry error 字段必须只接受固定 failure category 或低敏 exceptionType，不得把 fixture-message-authz 保存进去；这项若当前失败，登记为下一轮生产修复项。

可直接扩展的测试：

- 在 `AgentToolTraceServiceTest`、`AgentRunTraceServiceTest`、`ModelCallTraceServiceTest` 中扩展 repository capture，验证 metadata 映射和 sentinel 不进入保存对象。
- 在 `AgentToolTraceCorrelationTest`、`AgentToolInvocationContextPropagationTest`、`TraceIdWebFilterTest`、`TraceIdSupportTest`、`AsyncTracePropagationTest` 中保留已有 correlation/传播断言，追加“只传播 trace/run 标识，不传播 query/content”断言。
- 保持 `SensitiveLogSourceAuditTest` 和 `SensitiveExceptionLogSafetyTest` 的既有 hard-fail 语义，不删除延期列表空断言。

必须新建的测试：

- `AuthzBoundaryMockMvcTest`：跨 Controller 的应用不变量和三类攻击面。
- `TraceBoundarySensitiveDataTest`：跨三张 Trace 表、关联日志和任务错误字段的精确字段/值拒绝。

## 7. 完成标准与不变量

本地切片完成标准：

1. `AuthzBoundaryMockMvcTest` 聚焦和全量通过；确认 AUTHZ-001 已修复，候选 AUTHZ-CANDIDATE-001 有明确业务结论。
2. Trace 静态字段集合、repository save 投影、日志 formatted message/throwable、任务错误字段均通过低敏契约；所有 forbidden sentinel 逐项为零。
3. 统计保持 22 Controller、158 映射、90 写、68 读；任何新增/删除映射必须更新独立 contract，不得静默改变口径。
4. H2 结果只标 `application-invariant-only`，MockMvc/日志替身结果只标 `mock-contract-only`；不能合并成真实部署安全 PASS。
5. 不改变公开 endpoint allowlist、GlobalExceptionHandler 响应语义、WebSocket 鉴权链或已有 Sensitive*/Observability 套件的强断言。

## 8. Deployment-only 边界

- 真实双用户 HTTP 渗透：实际 JWT、反向代理、跨实例/异步线程和真实 DB 数据。
- K8s/Compose 管理端口与网关层路径保护，包括 20611 allowlist、代理 header 清理和真实 CORS/CSRF 配置。
- WebSocket STOMP 与日记语音 native handshake、topic 订阅和并发断连授权。
- 真实日志采集、集中检索、保留期和脱敏规则验证；本地 ListAppender/mock 只能证明 mock contract。
- 真实 Milvus/Redis/OSS、第三方模型网关和任务 worker 在跨用户删除/Trace 关联下的残留验证。

这些项目必须由部署环境责任人提供证据；本地静态扫描、H2 或 Mockito 不得替代 deployment-only 结论。
