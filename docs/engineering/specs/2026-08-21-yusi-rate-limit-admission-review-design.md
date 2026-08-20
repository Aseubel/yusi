# Yusi 限流与成本准入复核设计

**日期：** 2026-08-21
**状态：** 待评审，勘察与设计阶段
**范围：** HTTP 写入口的请求频率保护、现有限流点复核、模型网关预算准入复核，以及与 usage/violation/管理端口的边界确认。

## 1. 结论摘要

当前限流实现是方法级 Spring AOP：Redis 优先，Redis 不可用时降级到进程内 Guava 限流。源码中有 22 个真实 `@RateLimiter` 使用点，全部位于 Controller；没有发现 Service 或管理端点上的额外注解使用。对 Controller 中 `POST/PUT/DELETE/PATCH` 的真实映射逐文件扫描得到 90 个入口；`MatchController:95` 的 `// @PostMapping("/run")` 是注释示例，不是可路由接口，因此不计入 90 个真实入口。

现有覆盖集中在认证、图片上传/分片、聊天、地理查询、平台统计、广场 feed 和建议提交。主要缺口是：高成本的内存融合、情境房间完成后触发的模型分析、管理员 embedding full-sync，以及未受保护的图片 URL 批量签名和 OSS 删除；日记、匹配反馈、生活图谱、情境房间、通知、记忆中心、用户位置等大量业务写入口也没有应用层限流。

建议采用“双层保护”：应用层为每个需要业务身份的入口使用固定枚举 key 的 `@RateLimiter`；网关/Ingress 负责请求体字节数、并发连接和跨实例的粗粒度保护，尤其覆盖 Multipart、SSE 和批量操作。应用指标新增 `rate_limited_total`，但只使用既有四键标签白名单 `tool/operation/result/failure_category`，绝不携带 user ID、IP、query、正文、token 或路由参数。

模型预算准入本身已经按 `user/model/provider` 三个维度进行 Redis 原子预留，拒绝分类与告警、指标实现一致。当前配置值是部署默认值，不是生产调优或真实配额验收证据；上线前必须用真实调用量、token 分布和供应商配额校准。

## 2. 勘察口径与证据

### 2.1 扫描口径

- 注解扫描：`src/main/java` 中以 `@RateLimiter` 开头的真实注解行，共 22 处；注释中的注解不计入。
- 写入口扫描：`src/main/java/com/aseubel/yusi/controller` 中行首 `@PostMapping`、`@PutMapping`、`@DeleteMapping`、`@PatchMapping`，再按 Controller 类级 `@RequestMapping` 展开路径。文本命中为 91，其中 `src/main/java/com/aseubel/yusi/controller/MatchController.java:95` 是注释，真实映射 90 个。
- 风险等级不是“是否已经限流”的判断，而是入口被滥用后的资源/数据影响：高为模型调用或 OSS 写/删、批量外部资源操作；中为业务/用户 DB、Redis 或连接状态写入；低为有管理员/用户权限且无模型或 OSS 副作用的控制/配置操作。标注为中或低不代表无需保护。

### 2.2 注解与切面事实

`RateLimiter` 只允许方法级使用，参数为 key、秒级 time、次数 count 和 `LimitType`；默认值分别是 `rate_limit:`、60 秒、100 次和 `DEFAULT`（`src/main/java/com/aseubel/yusi/common/ratelimit/RateLimiter.java:8-30`）。`LimitType.DEFAULT` 是全局、`IP` 使用请求 IP、`USER` 使用 `UserContext` 用户 ID（`src/main/java/com/aseubel/yusi/common/ratelimit/LimitType.java:6-20`）。

切面以 `@Order(2)` 运行（`src/main/java/com/aseubel/yusi/common/ratelimit/RateLimiterAspect.java:27-31`）。Redis 可用时使用 Redisson `RRateLimiter` 的 `RateType.OVERALL`，调用 `trySetRate(count,time)`、设置窗口加 10 秒的 TTL，再 `tryAcquire()`；失败抛出 `RateLimitException`（`RateLimiterAspect.java:51-89`）。Redis 异常会切换到进程内 Guava `RateLimiter`，本地初始化速率为 `count/time`；本地限流自身异常时默认拒绝（`RateLimiterAspect.java:91-126`）。Redis 每 30 秒重新探测（`RateLimiterAspect.java:133-149`）。

组合 key 由固定前缀、注解 key、可选 IP/用户值、声明类和方法名组成（`RateLimiterAspect.java:153-171`）。目前 USER key 直接包含 user ID，IP key 直接包含 IP；这不是响应泄露，但属于 Redis 内部敏感值与高基数 key 风险，实施时应改为服务端密钥 HMAC/固定长度摘要，且摘要输入不进入日志和指标。

限流异常当前在 Redis、本地两条路径带有不同 message（`RateLimiterAspect.java:56-66`），全局异常处理器把异常 message 直接写入 429 `info`（`src/main/java/com/aseubel/yusi/common/exception/GlobalExceptionHandler.java:75-82`）。`RATE_LIMIT_EXCEEDED` 已有固定 429 错误码和固定中文文案（`src/main/java/com/aseubel/yusi/common/exception/ErrorCode.java:22-25`），因此后续应让响应只使用该固定错误码/文案，不暴露“本地限流”、Redis 状态、key 或阈值。

## 3. 现有限流覆盖矩阵

以下 22 行是全部真实 `@RateLimiter` 使用点。`count/time` 为窗口内次数/秒数；`USER`、`IP`、`DEFAULT` 分别表示用户、IP、全局维度。

| 类别 | 接口与证据 | 维度/阈值 | 现状判断 |
|---|---|---:|---|
| 读 | `GET /api/geo/search`，`GeoController.java:29` | IP，60/60s | 已覆盖 |
| 读 | `GET /api/geo/reverse`，`GeoController.java:44` | IP，120/60s | 已覆盖 |
| 读 | `GET /api/stats/platform`，`StatsController.java:29` | IP，30/60s | 已覆盖 |
| 读 | `GET /api/plaza/feed`，`SoulPlazaController.java:52-55` | IP，60/60s | 已覆盖；匿名访问也受保护 |
| 读 | `GET /api/image/check`，`ImageController.java:74-76` | USER，60/60s | 已覆盖；检查/签名仍有 OSS 访问成本 |
| 读 | `GET /api/image/chunk/progress`，`ImageController.java:141-144` | USER，120/60s | 已覆盖 |
| SSE/模型 | `POST /api/ai/chat/stream`，`AiController.java:243-246` | USER，20/60s | 已覆盖；真实模型入口 |
| 写/认证 | `POST /api/user/register`，`UserController.java:40-42` | IP，10/60s | 已覆盖 |
| 写/认证 | `POST /api/user/register/send-code`，`UserController.java:52-54` | IP，3/60s | 已覆盖；外发验证码 |
| 写/认证 | `POST /api/user/login`，`UserController.java:60-62` | IP，10/60s | 已覆盖 |
| 写/认证 | `POST /api/user/refresh`，`UserController.java:67-69` | IP，30/60s | 已覆盖 |
| 写/认证 | `POST /api/user/forgot-password/send-code`，`UserController.java:76-78` | IP，3/60s | 已覆盖 |
| 写/认证 | `POST /api/user/forgot-password/reset`，`UserController.java:84-86` | IP，10/60s | 已覆盖 |
| 写/密钥 | `POST /api/key/recovery/send-code`，`KeyManagementController.java:81-83` | IP，3/60s | 已覆盖 |
| 写/密钥 | `POST /api/key/recovery`，`KeyManagementController.java:90-92` | IP，10/60s | 已覆盖 |
| 写 | `POST /api/suggestions`，`SuggestionController.java:26-28` | IP，5/60s | 已覆盖 |
| 写/房间聊天 | `POST /api/room-chat/send`，`RoomChatController.java:52-54` | USER，60/60s | 已覆盖；DB 写入并广播 |
| 写/匹配聊天 | `POST /api/soul-chat/send`，`SoulChatController.java:62-65` | USER，60/60s | 已覆盖；DB、事件和 WebSocket 广播 |
| 写/OSS | `POST /api/image/upload`，`ImageController.java:28-30` | USER，20/60s | 已覆盖 |
| 写/OSS | `POST /api/image/upload/batch`，`ImageController.java:49-51` | USER，5/60s | 已覆盖；批量仍需字节/文件数网关限制 |
| 写/OSS | `POST /api/image/chunk/upload`，`ImageController.java:92-94` | USER，120/60s | 已覆盖；需配合总字节/并发限制 |
| 写/OSS | `POST /api/image/chunk/merge`，`ImageController.java:116-118` | USER，20/60s | 已覆盖 |

未发现 `DEFAULT` 全局维度的实际使用点。现有注解全部位于 Controller，未覆盖下节的其他写入口、定时任务、WebSocket 帧或 gRPC 方法。

## 4. 全部 HTTP 写入口与缺口

以下矩阵逐一列出 90 个真实 `POST/PUT/DELETE/PATCH` 入口。`缺失` 表示对应 mapping 行上下文没有 `@RateLimiter`；“建议”只记录本设计的初始策略，实际参数需在实施和部署验收时调优。

### 4.1 管理、模型和密钥

| 接口（mapping 证据） | 当前限流 | 风险/事实与建议 |
|---|---|---|
| `POST /api/admin/users/{userId}/permission`，`AdminController.java:131-145` | 缺失 | 低：管理员权限变更；保留既有 `checkAdminPermission`，USER 10/60s 初始值，待生产调优 |
| `POST /api/admin/scenarios/{scenarioId}/audit`，`AdminController.java:165-169` | 缺失 | 低：管理员审核状态写入；USER 30/60s 初始值，待生产调优 |
| `POST /api/admin/suggestions/{suggestionId}/reply`，`AdminController.java:187-196` | 缺失 | 低：管理员回复写入；USER 30/60s 初始值，待生产调优 |
| `POST /api/admin/suggestions/{suggestionId}/status`，`AdminController.java:199-218` | 缺失 | 低：管理员状态写入；USER 30/60s 初始值，待生产调优 |
| `POST /api/admin/announcements`，`AdminController.java:235-240` | 缺失 | 低：管理员公告配置写入；USER 10/60s 初始值，待生产调优 |
| `POST /api/admin/embeddings/full-sync`，`AdminController.java:242-250`；`EmbeddingBatchService.java:379-399` | 缺失 | 高：超级管理员触发 Milvus 清空/重建并涉及 embedding 任务；USER 1/3600s + 网关/运维权限保护，初始值，待生产调优 |
| `POST /api/admin/users/{userId}/deregister`，`AdminController.java:254-258` | 缺失 | 中：超级管理员触发用户数据删除；USER 5/60s 初始值，待生产调优，并保留审计 |
| `PUT /api/model/console`，`ModelManagementController.java:49-54` | 缺失 | 低：先 `checkAdmin`，更新模型治理配置并写管理员审计；USER 10/60s 初始值，待生产调优 |
| `POST /api/model/routes/preview`，`ModelManagementController.java:56-60`；`ModelManagementService.java:112-162` | 缺失 | 低：只规划路由候选，不调用模型；USER 30/60s 初始值，待生产调优 |
| `POST /api/developer/config/api-key`，`DeveloperConfigController.java:36-43` | 缺失 | 中：用户开发者 key 轮换；USER 5/60s 初始值，待生产调优 |
| `PUT /api/developer/config/api-key/scopes`，`DeveloperConfigController.java:45-49` | 缺失 | 中：用户 key scope 写入；USER 10/60s 初始值，待生产调优 |
| `DELETE /api/developer/config/api-key`，`DeveloperConfigController.java:51-55` | 缺失 | 中：用户 key 撤销；USER 10/60s 初始值，待生产调优 |
| `POST /api/prompt/save`，`PromptController.java:69-75` | 缺失 | 低：管理员 prompt 配置写入；USER 10/60s 初始值，待生产调优 |
| `PUT /api/prompt/{id}`，`PromptController.java:77-87` | 缺失 | 低：管理员 prompt 配置更新；USER 10/60s 初始值，待生产调优 |
| `POST /api/prompt/{id}/activate`，`PromptController.java:89-95` | 缺失 | 低：管理员 prompt 激活状态切换；USER 10/60s 初始值，待生产调优 |
| `DELETE /api/prompt/{id}`，`PromptController.java:97-102` | 缺失 | 低：管理员 prompt 删除；USER 10/60s 初始值，待生产调优 |
| `POST /api/key/settings`，`KeyManagementController.java:49-54` | 缺失 | 中：用户密钥模式写入；USER 10/60s 初始值，待生产调优 |
| `POST /api/key/reencrypt-diaries`，`KeyManagementController.java:71-75` | 缺失 | 中：批量日记重新加密，CPU/DB 成本高；USER 2/10min 初始值，待生产调优 |
| `POST /api/key/recovery/send-code`，`KeyManagementController.java:81-85` | IP 3/60s | 已覆盖；仍需保持验证码/IP 组合策略 |
| `POST /api/key/recovery`，`KeyManagementController.java:90-95` | IP 10/60s | 已覆盖；上线前补充账号/IP/失败次数联动评估 |

Admin、ModelManagement、Prompt Controller 的类级认证和管理员检查分别见 `AdminController.java:52-70`、`ModelManagementController.java:27-32,74-79`、`PromptController.java:24-41`。限流不能替代权限检查；管理端点还需要网络层 allowlist。

### 4.2 AI、日记、图片和生活图谱

| 接口（mapping 证据） | 当前限流 | 风险/事实与建议 |
|---|---|---|
| `POST /api/ai/chat/stream`，`AiController.java:243-246` | USER 20/60s | 已覆盖；高：模型调用和 SSE |
| `POST /api/ai/chat/cancel`，`AiController.java:559-569` | 缺失 | 低：取消瞬态会话，不调用模型；USER 30/60s 初始值，待生产调优 |
| `PUT /api/ai/persona-config`，`AiController.java:682-687`；`AgentPersonaConfigService.java:31-54` | 缺失 | 中：用户画像配置 DB 写入；USER 20/60s 初始值，待生产调优 |
| `POST /api/ai/cognitive-conflicts/{id}/resolve`，`AiController.java:726-735` | 缺失 | 中：认知冲突 DB 状态写入；USER 30/60s 初始值，待生产调优 |
| `POST /api/ai/memory-fusion/run`，`AiController.java:738-744`；`MidMemoryFusionService.java:86-167` | 缺失 | 高：读取未融合记忆并调用 `ChatModel`；USER 2/10min 初始值，待生产调优，继续由 model admission 兜底 |
| `POST /api/ai/chat/inject-greeting`，`AiController.java:746-785` | 缺失 | 中：聊天记忆、Redis 缓存和通知 DB 写入；USER 10/60s 初始值，待生产调优 |
| `POST /api/diary`，`DiaryController.java:51-57` | 缺失 | 中：日记 DB 写入并可能触发后续任务；USER 30/60s 初始值，待生产调优 |
| `PUT /api/diary`，`DiaryController.java:59-65` | 缺失 | 中：日记 DB 更新并可能触发后续任务；USER 30/60s 初始值，待生产调优 |
| `POST /api/diary/chat`，`DiaryController.java:74-78` | 缺失 | 低：代码明确已废弃且只返回错误，不产生模型/DB 副作用；保留低频保护或移除路由前加固定拒绝 |
| `POST /api/image/upload`，`ImageController.java:28-47` | USER 20/60s | 已覆盖；高：OSS 写入 |
| `POST /api/image/upload/batch`，`ImageController.java:49-72` | USER 5/60s | 已覆盖；高：循环 OSS 写入；另加文件数/总字节上限 |
| `POST /api/image/chunk/upload`，`ImageController.java:92-114` | USER 120/60s | 已覆盖；高：OSS 分片写入；另加用户并发上传数/总字节上限 |
| `POST /api/image/chunk/merge`，`ImageController.java:116-139` | USER 20/60s | 已覆盖；高：OSS 合并写入 |
| `POST /api/image/urls`，`ImageController.java:161-165` | 缺失 | 中：批量生成 OSS 签名 URL，非写入但可被放大滥用；USER 60/60s 初始值，待生产调优，并限制列表大小 |
| `DELETE /api/image`，`ImageController.java:167-171` | 缺失 | 高：OSS 对象删除副作用；USER 30/60s 初始值，待生产调优 |
| `DELETE /api/image/batch`，`ImageController.java:173-177` | 缺失 | 高：批量 OSS 删除；USER 5/60s 初始值，待生产调优，并限制列表大小 |
| `POST /api/lifegraph/merge-suggestions/{judgmentId}/accept`，`LifeGraphController.java:95-99` | 缺失 | 中：图谱合并写入；USER 20/60s 初始值，待生产调优 |
| `POST /api/lifegraph/merge-suggestions/{judgmentId}/reject`，`LifeGraphController.java:101-105` | 缺失 | 中：合并建议状态写入；USER 30/60s 初始值，待生产调优 |
| `POST /api/lifegraph/entities`，`LifeGraphController.java:135-143` | 缺失 | 中：图谱实体 DB 写入；USER 30/60s 初始值，待生产调优 |
| `PUT /api/lifegraph/entities/{id}`，`LifeGraphController.java:148-163` | 缺失 | 中：图谱实体 DB 更新；USER 30/60s 初始值，待生产调优 |
| `DELETE /api/lifegraph/entities/{id}`，`LifeGraphController.java:168-173` | 缺失 | 中：图谱实体删除；USER 30/60s 初始值，待生产调优 |
| `POST /api/lifegraph/relations`，`LifeGraphController.java:178-190` | 缺失 | 中：图谱关系 DB 写入；USER 30/60s 初始值，待生产调优 |
| `PUT /api/lifegraph/relations/{id}`，`LifeGraphController.java:196-210` | 缺失 | 中：图谱关系 DB 更新；USER 30/60s 初始值，待生产调优 |
| `DELETE /api/lifegraph/relations/{id}`，`LifeGraphController.java:216-221` | 缺失 | 中：图谱关系删除；USER 30/60s 初始值，待生产调优 |

日记、图谱和 AI Controller 都是类级 `@Auth`（`DiaryController.java:26-29`、`LifeGraphController.java:29-33`）；`AiController` 对这些方法使用方法级 `@Auth`（`AiController.java:243-246,559-560,675-686,726-751`）。

### 4.3 匹配、记忆、房间、聊天、通知和社区

| 接口（mapping 证据） | 当前限流 | 风险/事实与建议 |
|---|---|---|
| `POST /api/match/settings`，`MatchController.java:40-45` | 缺失 | 中：用户匹配设置 DB 写入；USER 10/60s 初始值，待生产调优 |
| `POST /api/match/{matchId}/action`，`MatchController.java:53-58` | 缺失 | 中：匹配动作/连接状态写入；USER 30/60s 初始值，待生产调优 |
| `POST /api/match/{matchId}/feedback`，`MatchController.java:60-65` | 缺失 | 中：连接反馈写入，用户重点滥用面；USER 30/60s 初始值，待生产调优 |
| `POST /api/match/{matchId}/end`，`MatchController.java:67-72` | 缺失 | 中：连接结束状态写入；USER 20/60s 初始值，待生产调优 |
| `POST /api/match/{matchId}/report`，`MatchController.java:74-79` | 缺失 | 中：连接举报写入；USER 10/60s 初始值，待生产调优，并保留反滥用审计 |
| `POST /api/match/{matchId}/block`，`MatchController.java:81-86` | 缺失 | 中：连接屏蔽状态写入；USER 20/60s 初始值，待生产调优 |
| `PATCH /api/memory/center/{id}`，`MemoryCenterController.java:47-52` | 缺失 | 中：中期记忆 DB 更新；USER 30/60s 初始值，待生产调优 |
| `DELETE /api/memory/center/{id}`，`MemoryCenterController.java:54-58` | 缺失 | 中：中期记忆删除；USER 30/60s 初始值，待生产调优 |
| `PATCH /api/memory/persona`，`MemoryCenterController.java:65-69` | 缺失 | 中：用户 persona 记忆写入；USER 20/60s 初始值，待生产调优 |
| `DELETE /api/memory/persona`，`MemoryCenterController.java:71-75` | 缺失 | 中：persona 记忆删除；USER 20/60s 初始值，待生产调优 |
| `PATCH /api/memory/life-graph/{id}`，`MemoryCenterController.java:83-88` | 缺失 | 中：生活图谱记忆更新；USER 30/60s 初始值，待生产调优 |
| `DELETE /api/memory/life-graph/{id}`，`MemoryCenterController.java:90-94` | 缺失 | 中：生活图谱记忆删除；USER 30/60s 初始值，待生产调优 |
| `POST /api/room/create`，`SituationRoomController.java:25-30` | 缺失 | 中：房间 DB 写入；USER 10/60s 初始值，待生产调优 |
| `POST /api/room/join`，`SituationRoomController.java:32-36` | 缺失 | 中：房间成员状态 DB 写入；USER 20/60s 初始值，待生产调优 |
| `POST /api/room/start`，`SituationRoomController.java:38-43` | 缺失 | 中：房间状态 DB 写入；USER 10/60s 初始值，待生产调优 |
| `POST /api/room/scenarios/submit`，`SituationRoomController.java:45-48`；`SituationRoomServiceImpl.java:363-372` | 缺失 | 中：情景 DB 写入；USER 10/60s 初始值，待生产调优 |
| `PUT /api/room/scenarios/{id}`，`SituationRoomController.java:55-58`；`SituationRoomServiceImpl.java:374-389` | 缺失 | 中：情景 DB 更新；USER 10/60s 初始值，待生产调优 |
| `DELETE /api/room/scenarios/{id}`，`SituationRoomController.java:60-64`；`SituationRoomServiceImpl.java:391-400` | 缺失 | 中：情景软删除 DB 写入；USER 10/60s 初始值，待生产调优 |
| `POST /api/room/scenarios/{id}/resubmit`，`SituationRoomController.java:66-69`；`SituationRoomServiceImpl.java:402-415` | 缺失 | 中：情景重新提交 DB 写入；USER 10/60s 初始值，待生产调优 |
| `POST /api/room/cancel`，`SituationRoomController.java:81-86` | 缺失 | 中：房间状态 DB 写入；USER 10/60s 初始值，待生产调优 |
| `POST /api/room/vote-cancel`，`SituationRoomController.java:88-92` | 缺失 | 中：取消投票/状态 DB 写入；USER 20/60s 初始值，待生产调优 |
| `POST /api/room/submit`，`SituationRoomController.java:94-99`；`SituationRoomServiceImpl.java:137-167`；`SituationReportService.java:41-54` | 缺失 | 高：全部成员提交时异步生成报告并调用模型；USER 3/10min 初始值，待生产调优，并由 model admission 兜底 |
| `POST /api/room-chat/send`，`RoomChatController.java:52-99` | USER 60/60s | 已覆盖；中：房间消息 DB 写入并广播；另需考虑 WebSocket 广播并发 |
| `POST /api/soul-chat/send`，`SoulChatController.java:62-123` | USER 60/60s | 已覆盖；中：消息、product event、在线状态和广播写入 |
| `POST /api/soul-chat/read`，`SoulChatController.java:140-145` | 缺失 | 中：消息已读状态 DB 写入；USER 60/60s 初始值，待生产调优 |
| `POST /api/notifications/{notificationId}/read`，`NotificationController.java:55-59` | 缺失 | 中：通知状态 DB 写入；USER 60/60s 初始值，待生产调优 |
| `POST /api/notifications/read-all`，`NotificationController.java:64-68` | 缺失 | 中：批量通知状态 DB 写入；USER 20/60s 初始值，待生产调优 |
| `DELETE /api/notifications/{notificationId}`，`NotificationController.java:73-77` | 缺失 | 中：通知删除；USER 30/60s 初始值，待生产调优 |
| `POST /api/plaza/submit`，`SoulPlazaController.java:35-50` | 缺失 | 中：社区卡片 DB 写入；USER 10/60s 初始值，待生产调优 |
| `PUT /api/plaza/{cardId}`，`SoulPlazaController.java:73-79` | 缺失 | 中：社区卡片 DB 更新；USER 20/60s 初始值，待生产调优 |
| `DELETE /api/plaza/{cardId}`，`SoulPlazaController.java:81-86` | 缺失 | 中：社区卡片删除；USER 20/60s 初始值，待生产调优 |
| `POST /api/plaza/{cardId}/resonate`，`SoulPlazaController.java:88-97` | 缺失 | 中：社区共鸣写入；USER 30/60s 初始值，待生产调优 |
| `POST /api/plaza/signal`，`SoulPlazaController.java:101-107` | 缺失 | 中：跨用户信号 DB 写入；USER 10/60s 初始值，待生产调优 |
| `POST /api/plaza/signals/{signalId}/read`，`SoulPlazaController.java:125-130` | 缺失 | 中：信号已读状态写入；USER 60/60s 初始值，待生产调优 |

这些用户入口均有类级 `@Auth`，例如 `MatchController.java:27-32`、`MemoryCenterController.java:28-32`、`SituationRoomController.java:16-20`、`SoulChatController.java:37-42`；广场提交和各操作也在方法级标注 `@Auth`（`SoulPlazaController.java:35-36,65-74,81-102,119-126`）。

### 4.4 建议、用户和位置

| 接口（mapping 证据） | 当前限流 | 风险/事实与建议 |
|---|---|---|
| `POST /api/suggestions`，`SuggestionController.java:26-44` | IP 5/60s | 已覆盖；中：建议 DB 写入；匿名/认证混合场景继续以 IP 为基础 |
| `POST /api/user/register`，`UserController.java:40-49` | IP 10/60s | 已覆盖；中：用户 DB 写入 |
| `POST /api/user/register/send-code`，`UserController.java:52-57` | IP 3/60s | 已覆盖；外发验证码 |
| `POST /api/user/login`，`UserController.java:60-65` | IP 10/60s | 已覆盖；认证失败仍需账号/IP 维度审计 |
| `POST /api/user/refresh`，`UserController.java:67-73` | IP 30/60s | 已覆盖 |
| `POST /api/user/forgot-password/send-code`，`UserController.java:76-81` | IP 3/60s | 已覆盖；外发验证码 |
| `POST /api/user/forgot-password/reset`，`UserController.java:84-89` | IP 10/60s | 已覆盖 |
| `POST /api/user/update`，`UserController.java:91-96` | 缺失 | 中：用户资料 DB 更新；USER 20/60s 初始值，待生产调优 |
| `POST /api/user/logout`，`UserController.java:98-104` | 缺失 | 低：token/会话撤销；USER 30/60s 初始值，待生产调优 |
| `POST /api/location`，`UserLocationController.java:54-61` | 缺失 | 中：用户位置 DB 写入；USER 10/60s 初始值，待生产调优 |
| `PUT /api/location`，`UserLocationController.java:64-71` | 缺失 | 中：用户位置 DB 更新；USER 20/60s 初始值，待生产调优 |
| `DELETE /api/location/{locationId}`，`UserLocationController.java:74-80` | 缺失 | 中：用户位置删除；USER 30/60s 初始值，待生产调优 |

因此，真实写入口统计为：90 个；现有 `@RateLimiter` 覆盖其中 16 个写入口，另外 6 个注解点是读接口，其余 74 个写入口缺失。实施测试不应把“按文本统计 91”或 Controller 总数当作覆盖结论，必须按方法映射和注解实际关联验证。更直接的实施契约是：90 个映射逐一有记录，22 个现有注解逐一能解析；新增覆盖数量以测试执行时的源码契约为准。

## 5. Model Gateway Admission 复核

### 5.1 维度、窗口和当前配置

`ModelGatewayAdmissionProperties` 绑定 `model.gateway.admission`，未知配置字段不被忽略（`src/main/java/com/aseubel/yusi/config/ai/properties/ModelGatewayAdmissionProperties.java:14-16`）。默认启用、key prefix、窗口和 reservation TTL 分别为 `true`、`yusi:model:admission:`、60 秒和 300 秒（`ModelGatewayAdmissionProperties.java:18-24`）；维度只有 `user`、`model`、`provider`（`ModelGatewayAdmissionProperties.java:26-30`）。启动校验要求 prefix、窗口和 TTL 有效，max requests/tokens 不能为负；只有 enabled 且任一维度有正限制时才启用，0 表示关闭该维度限制（`ModelGatewayAdmissionProperties.java:32-68`）。

当前 prod 和 dev 配置键和值一致，均由环境变量覆盖：

| 维度 | max requests/60s | max tokens/60s | 配置证据 |
|---|---:|---:|---|
| user | 60 | 200,000 | `src/main/resources/application-prod.yml:28-34`；dev `:15-21` |
| model | 600 | 2,000,000 | `application-prod.yml:35-37`；dev `:22-24` |
| provider | 1,000 | 4,000,000 | `application-prod.yml:38-40`；dev `:25-27` |

应用默认 profile 为 prod（`src/main/resources/application.yml:1-5`）。这些是配置默认值，不代表当前生产已经以这些值运行或供应商配额已匹配；上线标准中应记录实际供应商配额、峰值并发、P95 token、拒绝率和调优版本。

### 5.2 拒绝路径与分类一致性

`ModelBudgetAdmission.reserve` 在没有配置限制时返回 noop；Redis client 缺失返回 `ADMISSION_STORE_UNAVAILABLE`（`src/main/java/com/aseubel/yusi/service/ai/model/ModelBudgetAdmission.java:104-117`）。它按 epoch/windowSeconds 生成窗口，并为 `user`、`model`、`provider` 分别生成 request/token charge key（`ModelBudgetAdmission.java:208-237`）。Redis Lua 原子预留返回 null/-2 时为 `RESERVATION_CONFLICT`，非正维度结果为 `LIMIT_EXCEEDED:<dimension>`，运行时异常为 `ADMISSION_STORE_UNAVAILABLE`（`ModelBudgetAdmission.java:131-147`）。

同步和 streaming 模型 permit 被拒绝时都调用 `recordBudgetDenied`，发布 `REJECTED` 的模型尝试事件；同步路径在 `ModelProxyFactory.java:259-268`，streaming 路径在 `:327-339`。

`YusiMetrics.recordBudgetDenied` 固定使用 `tool=system`、`operation=model_admission`、`result=denied`，并把原因归一化为 `admission_store_unavailable`、`reservation_conflict`、`limit_exceeded` 或 `unknown`（`src/main/java/com/aseubel/yusi/observability/metrics/YusiMetrics.java:168-179,205-223`）。`AlertPolicy.normalizeBudgetReason` 使用相同四分类，并将 `LIMIT_EXCEEDED:<dimension>` 的 dimension 丢弃（`src/main/java/com/aseubel/yusi/observability/alert/AlertPolicy.java:35-50`）。告警 evaluator 的 failure category 白名单也包含三种具体预算分类（`src/main/java/com/aseubel/yusi/observability/alert/AlertEvaluator.java:18-22`）。结论是当前实现语义一致；风险是指标 facade 和告警 policy 各有一份归一化代码，实施时需新增契约测试锁定一致性，后续可再提取共享纯函数。

告警预算初始门槛为 5 分钟窗口至少 10 次拒绝，30 分钟重复抑制等其它策略见 `AlertPolicy.java:20-32`；该阈值只用于告警，不改变 admission 限额，仍标为“初始值，待生产调优”。

### 5.3 适配性判断

- user 60 request/min 与 200k token/min 是单用户初始保护线，适合作为上线前的保守基线，但不能在没有真实 token 分布和供应商合同配额时视为已校准。
- model/provider 维度用于防止单模型或单供应商被跨用户打穿；应以供应商 RPM/TPM、实例数和故障切换策略回算，避免 fail-over 仍共同撞击 provider 上限。
- 300 秒 reservation TTL 是进程崩溃后的最后释放边界；结算失败只记录低敏分类、依赖 TTL，相关实现见 `ModelBudgetAdmission.java:150-199`。这不替代请求频率限流，也不应把 `budget_denied_total` 当作 `rate_limited_total`。
- admission key 内部会包含 scope value 的规范化值（`ModelBudgetAdmission.java:222-237`），实现阶段应继续确认 key 访问权限和日志不输出该值；指标和告警绝不携带 dimension 原文。

## 6. 与既有机制的关系

### 6.1 InterfaceUsageMonitor 不参与限流决策

`InterfaceMonitorAspect` 对所有 `com.aseubel.yusi.controller..*` 做 before 切点（`src/main/java/com/aseubel/yusi/monitor/InterfaceMonitorAspect.java:24-30`），读取用户、IP 和 `class#method` 后调用 usage monitor（`:31-50`）。`InterfaceUsageMonitor` 将日期、用户、IP、接口名拼到 Redis usage hash field 并递增（`src/main/java/com/aseubel/yusi/monitor/InterfaceUsageMonitor.java:44-69`），再把记录批量 upsert 到 `interface_daily_usage`（`:158-190`）；每 30 分钟由 `YusiScheduledTasks.syncInterfaceUsage` 触发（`src/main/java/com/aseubel/yusi/common/task/scheduler/YusiScheduledTasks.java:55-58`）。源码没有 usage count 读取或阈值调用 `RateLimiterAspect` 的路径，因此 usage 是报表/对账，不是准入控制，二者必须保持独立。

该机制当前将原始 user ID/IP 放入 Redis field 和后续 usage 表；本刀不直接改 usage 数据模型，但实施审计应禁止把这些高基数值复制到新指标或限流日志中。

### 6.2 violation 与频率限流边界

敏感词机制使用 `yusi:violation:` 和 `yusi:violation:count:%s` key（`src/main/java/com/aseubel/yusi/redis/common/RedisKey.java:25-26`），命中内容后按 user ID 累加、TTL 为 12 小时，并返回内容安全提示（`src/main/java/com/aseubel/yusi/common/utils/SensitiveWordUtils.java:48-75`）。它由 SSE 消息处理路径调用（`src/main/java/com/aseubel/yusi/controller/AiController.java:294-300`）。这是内容安全/惩罚计数，不是请求频率限流；不能用 violation 次数代替 `@RateLimiter`，也不能让 violation key/消息成为新指标标签。

### 6.3 管理端口与 actuator

prod 将业务端口配置为 611，管理端口通过 `MANAGEMENT_SERVER_PORT` 默认 20611，并绑定 `0.0.0.0`（`src/main/resources/application-prod.yml:5-24`）；dev profile 的 server port 也为 20611（`src/main/resources/application-dev.yml:5-12`）。Actuator 只暴露 `health,prometheus`，health `show-details: never`，readiness 组包含 `readinessState,db,redis,milvus,modelGateway,tasks`（`src/main/resources/application.yml:32-46`）。

管理端点不应复用业务 USER/IP 限流，否则可能误伤 Kubernetes probe 和 Prometheus scraper；应在部署层对 20611 只允许 probe、scraper 和受控运维身份，并在网络层限制公开访问。应用层可为管理写接口（`/api/admin`、`/api/model`、`/api/prompt`）增加管理员 USER 限流，但不能把 actuator 当作普通业务路由。

本次 HTTP 矩阵不包含 native WebSocket voice（`src/main/java/com/aseubel/yusi/controller/DiaryVoiceWebSocketHandler.java:39,88,111`）、STOMP `/soul-chat/status`（`src/main/java/com/aseubel/yusi/controller/WebSocketController.java:18-31`）或 gRPC MCP 方法（`src/main/java/com/aseubel/yusi/grpc/McpGrpcServiceImpl.java:44-84`）。这些入口需要独立的连接数、帧/消息大小、每身份速率和内部网络策略；不能以 90 个 HTTP 写入口全覆盖为结论。

## 7. 补齐方案

### 7.1 应用层注解与网关层职责

1. 对已认证用户写入口使用 `LimitType.USER`，未认证入口使用 `IP`；每个 key 是源码内固定枚举，不拼接 query、body、path 参数、user ID 或 IP。管理员写入口保留既有 `@Auth`/admin check，再使用低频 USER 限制。
2. 对高风险入口优先补应用注解：`/api/ai/memory-fusion/run`、`/api/room/submit`、`/api/admin/embeddings/full-sync`、`/api/image/urls`、`/api/image`、`/api/image/batch`；同时把日记创建/编辑、匹配反馈/举报、情境房间、key 批处理、用户位置等中风险写入口纳入固定 key 清单。
3. 应用 AOP 不能替代请求体层保护。Ingress/API gateway 负责 Multipart 总字节、单文件/批量文件数、分片并发、SSE 并发连接和跨实例粗粒度限流；应用层负责 user identity 和业务操作粒度。当前代码配置只证明应用端口/管理端口与 probe 路径，不证明生产已有网关限流部署，因此网关规则必须列为 deployment-only 验收项。
4. 高风险模型/OSS 入口在 Redis 限流不可用时不允许静默放大：继续保留本地 bounded fallback 作为短时可用性策略时，必须有明确的 `dependency` 计数和小于分布式上限的本地 cap；对 full-sync、批量删除、模型触发类操作可采用 fail-closed。具体按 endpoint policy 选择，不能把当前 Guava fallback 当作多副本一致性保证。
5. USER/IP key 的 subject 部分改为部署密钥 HMAC 的固定长度摘要，key 只由 `rate_limit:<fixed_operation>:<u|ip>:<digest>:<class>:<method>` 构成。摘要不能进入 logger、exception message、HTTP 响应或指标。

### 7.2 拒绝响应与指标契约

限流拒绝必须返回 HTTP 429、`ErrorCode.RATE_LIMIT_EXCEEDED` 和固定 `info` 文案“请求频率过快，请稍后再试”；不得返回 Redis/本地后端、当前 count、window、key、user ID、IP、model/provider 或异常 message。AOP 在 Controller 方法执行前拒绝，因此 SSE 尚未提交时也返回普通低敏 429；已提交的异步流不能再改写 HTTP 状态，只允许结束流，不发送内部原因。

新增 `rate_limited_total` counter 的标签白名单严格为：

| 标签 | 允许值示例 | 禁止内容 |
|---|---|---|
| `tool` | `system` | user ID、IP、token |
| `operation` | 固定 `chat_stream`、`oss_write`、`business_write`、`admin`、`auth` 等枚举 | 原始 URL、query、path 参数、方法签名 |
| `result` | `rejected` | body、异常 message |
| `failure_category` | `limit_exceeded`、`dependency`、`unknown` | Redis key、dimension 原文、provider/model |

计数只在实际拒绝时递增；Redis 不可用导致的拒绝归类为 `dependency`，规则限额打满归类为 `limit_exceeded`。该指标与 `budget_denied_total` 分开，后者继续使用 `model_admission` 和四种预算分类。实现测试必须断言四键集合精确相等，并用 `fixture-user-rate`、`fixture-query-rate`、`fixture-content-rate`、`fixture-token-rate`、`fixture-object-key-rate` sentinel 做全字符串 JSON/日志扫描；sentinel 不能以 `contains` 弱化为部分断言。

### 7.3 初始阈值（全部待调优）

下表是设计初值，不是生产批准值：

| 入口类别 | 初始策略 | 目的 |
|---|---|---|
| 模型入口（chat stream） | USER 20/60s（保持现状）+ model admission | 限请求数，预算限制 token/request |
| 直接触发模型的 memory fusion | USER 2/600s | 防止手工反复融合；初始值，待生产调优 |
| 情境报告最终提交 | USER 3/600s | 防止重复触发异步模型报告；初始值，待生产调优 |
| embedding full-sync | 超级管理员 USER 1/3600s + 管理网络 allowlist | 防止全量 Milvus 重建；初始值，待生产调优 |
| 图片 URL 批量签名 | USER 60/60s + 单次列表上限 | 限制签名放大；初始值，待生产调优 |
| 图片单删/批删 | USER 30/60s、5/60s + 列表上限 | 限制 OSS mutation；初始值，待生产调优 |
| 分片上传 | 保持 120/60s，并补总字节/并发 gateway 规则 | 请求次数不能替代字节配额；初始值，待生产调优 |
| 普通用户 DB 写入 | USER 20-30/60s | 防止重复写入；初始值，待生产调优 |
| 管理配置写入 | 管理员 USER 10/60s | 低频操作；初始值，待生产调优 |
| 验证码/认证 | 保持现有 IP 3-10/60s，并在部署侧观察失败率 | 防爆破；初始值，待生产调优 |

阈值调优必须基于匿名化计数、P95/P99、429 比率、Redis 可用性和业务成功率；不允许导出用户、query 或正文样本作为调参材料。

## 8. 本地验证与 deployment-only 边界

### 8.1 H2/Mockito 可验证

- 静态契约：90 个真实 mapping 均在覆盖 manifest 中，22 个既有限流点参数与方法关联准确；注释 `MatchController:95` 不被计数。
- Aspect 单测：Redis `RRateLimiter` 的 count/time/type、允许/拒绝、固定 key 组成、HMAC subject 不含明文；Redis 异常进入 bounded fallback，fallback 失败拒绝。
- Controller/MockMvc 或 Mockito：429 状态、固定错误码/文案、无 backend/key/阈值/message 泄露；SSE 方法执行前拒绝；认证和 admin check 不被绕过。
- 指标契约：`rate_limited_total` 精确四标签白名单，固定值集合，拒绝分类归一化；报告标 `mock-contract-only`，不能写“真实限流生效”。
- H2 只用于有 DB 写入的 Controller 回归和事务边界，不把 H2 结果解释为真实 Redis 多副本限流证据。
- admission 单测：user/model/provider request/token charge、noop/deny、三维拒绝分类与 `budget_denied_total`/AlertPolicy 分类一致；不使用真实模型或真实 Redis。

### 8.2 只能 deployment-only

- 真实并发压测、SSE 长连接并发、Multipart 分片/总字节压力和 OSS provider 真实成本。
- Kubernetes Ingress/API gateway 的 route/byte/concurrency 规则、WAF/边缘 IP 识别和跨入口组合限流。
- 多实例共享 Redis 时的严格窗口一致性、Redis 故障恢复、网络分区及 local fallback 放大风险。
- model/provider 真实 RPM/TPM、token 分布、fail-over 后供应商配额和初始阈值校准。
- 真实 Milvus/embedding full-sync、真实 OSS 删除/批量签名的容量与恢复行为。
- management port 20611 的网络 allowlist、probe/scraper 身份和外网不可达性。
- WebSocket/gRPC/internal MCP 的连接、帧、消息和内部认证压力。

本地 Mockito 的 permit/HTTP/指标通过只证明应用契约；不产生 deployment-only PASS，不勾选 roadmap，不替代上线压测和管理端口验收。

## 9. 未决风险与非目标

1. 现有 USER/IP Redis key 直接含 user ID/IP，且本地 fallback 非分布式；属于实施必须关闭的敏感/一致性风险。
2. usage 统计字段保留 user ID/IP，且与限流完全独立；本刀不改 usage 数据保留/脱敏模型，后续需单独处理。
3. 90 个 MVC 写入口不覆盖 WebSocket、gRPC、scheduler 和内部 worker；这些通道必须有独立策略。
4. 当前配置值没有真实生产调优证据；在生产数据和供应商配额验收前，不得宣称“阈值适配上线标准”。
5. 当前 `RateLimiterAspect` 的异常日志仍包含 throwable（`RateLimiterAspect.java:91-97,122-126`），属于已完成的敏感日志切片范围之外的延迟日志政策项；本刀不顺手修改。
6. 本设计不修改生产代码、测试、CI、migration、roadmap 或既有质量评测文件；实施阶段需按本设计另建测试和变更清单。
