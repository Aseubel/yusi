# Yusi 账号注销与数据删除全路径隐私自检设计

- 状态：待评审
- 日期：2026-08-20
- 前置提交：`f178ad1 docs: add backup restore design and implementation plan`
- 路线图依据：`docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md:634`
- 本刀边界：只勘察并设计账号注销、派生数据删除、审计保留和跨存储验证；本次不修改生产代码、测试、migration、CI、评测套件、roadmap，也不启动服务或外部依赖。

## 1. 结论先行

当前存在管理员整户注销入口，但它不是全路径删除实现，不能作为 roadmap L634 的完成证据。当前链路只删除一个 Milvus collection、一个 LangChain Redis key 以及一组原生 SQL 行；它没有覆盖两个向量 collection、多个 Redis 用户数据族、OSS 最终对象、若干用户相关 MySQL 表和共享资源中的用户内容。部分 SQL 异常被捕获后继续执行，方法最后仍记录成功，因此失败时还可能留下无法解释的残留。

这次设计采用三条诚实边界：

1. H2 只证明 MySQL 删除顺序、行数和应用级孤儿查询；Mockito 只证明 Milvus/Redis/OSS 的删除调用契约。
2. 真实 Milvus 向量消失、Redis key/field 清除、OSS 对象及版本删除、备份副本保留期和异步 worker 竞态都必须单列 deployment-only，不把 mock PASS 当全路径 PASS。
3. 安全审计记录不按普通用户表无条件物理删除。保留动作、时间、结果和固定原因分类，但在合规批准的去标识化流程中移除 actor、subject、resource 和 scope 的可识别关联。

## 2. 现状勘察

以下行号以当前 HEAD `f178ad1` 为准。负向结论来自对 `src/main`、`src/main/resources/db/init.sql`、`src/main/resources/db/migration` 的源码扫描；`init.sql` 只在 `:3` 和 `:1063` 开关 `FOREIGN_KEY_CHECKS`，未找到 `FOREIGN KEY`/`REFERENCES` 定义，因此不能把数据库本身当作孤儿约束证明。

### 2.1 注销入口和当前执行链

| 链路 | 当前事实 | 当前风险 |
| --- | --- | --- |
| 管理员入口 | `src/main/java/com/aseubel/yusi/controller/AdminController.java:254-258` 暴露 `POST /users/{userId}/deregister`，先执行超级管理员校验，再调用 `AdminService.deregisterUser`。接口声明在 `src/main/java/com/aseubel/yusi/service/user/AdminService.java:25-28`。 | 这是管理员注销入口，不是用户自助注销入口；没有在本次扫描的 controller 中发现另一个整户删除入口。 |
| 权限与事务 | `src/main/java/com/aseubel/yusi/service/user/impl/AdminServiceImpl.java:184-205` 使用 `@Transactional(rollbackFor = Exception.class)`，拒绝找不到用户、自注销和不低于当前管理员权限的目标。 | 后续外部清理发生在事务方法内，但外部系统不受 MySQL 事务控制。 |
| 认证状态 | `AdminServiceImpl.java:209-216` 调用 `deleteRefreshToken` 和 `removeAllDeviceTokens`。`TokenServiceImpl.java:70-72,107-115` 删除 refresh/device 集合，并把设备 token 加入 blacklist。 | blacklist 是按 token 的短期撤销状态，不带可反查 userId 的关系；注销后应按安全策略保留到 access token 过期，而不是声称 auth key 全部为零。 |
| Redis LangChain | `AdminServiceImpl.java:218-224` 只删除 `yusi:langchain:<userId>`。 | usage、violation、业务缓存、OSS 上传缓存没有在此处清理。 |
| Milvus | `AdminServiceImpl.java:226-235` 只向 `yusi_embedding_collection` 发出按 `metadata["userId"]` 的 delete。 | `yusi_mid_term_memory` 和 `yusi_match_profile` 未处理；调用发出也不能证明真实 segment 已刷新。 |
| Situation Room | `AdminServiceImpl.java:237-263` 查找成员房间；房主或成员数不大于 2 时删除 room message 和房间，否则从成员、提交和投票集合中移除用户。 | 共享房间的报告、提交和已有消息可能仍包含目标用户；只移除成员不是内容删除。 |
| MySQL 原生清理 | `AdminServiceImpl.java:265-297` 定义 30 条左右的固定 DELETE，`299-310` 逐条执行，`312-317` 无论逐条失败与否都记录成功并写管理员审计。 | 旧顺序不是 child-first；缺少表不会触发应用级失败；`307-310` 吞掉异常，使方法不会因为该条清理失败而回滚。 |

### 2.2 其他删除入口不是整户删除

下列入口只删除单个资源或当前会话，不能替代账号注销全路径：

| 入口 | 代码位置 | 范围 |
| --- | --- | --- |
| 中期记忆、画像、单个图谱节点 | `MemoryCenterController.java:54-57,71-74,90-93` | 分别删除一条记忆、画像或图谱节点。 |
| 图谱节点/关系 | `LifeGraphController.java:166-171,214-219` | 通过用户上下文删除单个实体或关系。 |
| 图片/音频对象 | `ImageController.java:167-176` | 只接受调用方传入的 object key，并按当前用户校验后删除。 |
| 通知、情境剧本、广场卡片、地点 | `NotificationController.java:71-76`、`SituationRoomController.java:60-62`、`SoulPlazaController.java:82-84`、`UserLocationController.java:72-79` | 各自的单资源删除。 |
| 开发者 API key、Prompt | `DeveloperConfigController.java:49-55`、`PromptController.java:97-100` | 删除配置或 Prompt，不是账号及其所有派生数据删除。 |
| 普通 logout | `UserServiceImpl.java:154-159` | 只撤销当前设备 token 和 refresh token，不删除 MySQL、Milvus、Redis、OSS 数据。 |

### 2.3 MySQL 覆盖矩阵

表结构出处均为 `src/main/resources/db/init.sql`；“当前覆盖”指 `AdminServiceImpl.java:266-296` 或 `237-263` 的实际路径，不代表关系完整性已经验证。

#### 直接按用户可删除的数据

| 数据面 | 表与代码事实 | 当前状态 | 设计决策 |
| --- | --- | --- | --- |
| 身份与用户配置 | `user`：`init.sql:7-24`；`user_persona`：`26-47`；`agent_persona_config`：`1008-1023`；`developer_config`：`828-841`；`user_location`：`356-376`；`user_notification`：`800-826`。 | 当前删除了 persona、agent config、developer config、location、notification，并最后删除 `user`，对应 `AdminServiceImpl.java:267-270,273,296`。 | 保留 child-first 顺序，所有目标用户行清零；删除 `developer_config` 也要撤销其 API key。 |
| 日记与对话 | `diary`：`init.sql:49-77`；`chat_memory_message`：`673-692`，其 `memory_id` 注释说明通常是 userId；`mid_term_memory`：`923-947`。 | 当前删除 diary、按 `memory_id` 删除 chat message、删除 mid-term memory，见 `AdminServiceImpl.java:271,274,287`。 | 删除前收集 diary/chat 中的图片、音频和附件 object key；先停相关任务，再清 DB。 |
| 任务与统计 | `embedding_task`：`init.sql:448-475`；`life_graph_task`：`627-652`；`interface_daily_usage`：`79-100`。 | 当前删除 embedding task、life graph task 和 MySQL usage，见 `AdminServiceImpl.java:275,277,288`。 | Redis usage hash 的目标 field 也必须清理；正在运行/重试中的任务必须先取消或阻断重入。 |
| 认知冲突与报告 | `cognitive_conflict`：`init.sql:1047-1061`；`soul_report`：`1025-1045`。 | 当前删除两者，见 `AdminServiceImpl.java:272,293`。 | 报告和冲突是用户可反推的派生自然语言数据，不能只依赖基础日记删除。 |
| 广场与共鸣 | `soul_card`、`soul_resonance`：`init.sql:173-190,342-354`；`resonance_signal`：`990-1006`。 | 当前删除 card、resonance、from/to 两侧 signal，见 `AdminServiceImpl.java:288,294`。单卡删除还会先删 resonance，见 `SoulPlazaServiceImpl.java:322-337`。 | 账号删除要处理目标用户产生的卡片、共鸣和通知引用；另一用户的合法内容不能因整行删除而无解释丢失。 |
| 匹配与反馈 | `match_profile`：`init.sql:305-319`；`match_feedback`：`968-988`；`soul_match`：`192-218`。 | 当前删除 profile、feedback 和两端参与的 match，见 `AdminServiceImpl.java:285-291`。 | profile 是高敏派生画像，必须同时清 MySQL 与 Milvus；双用户 match 要采用“目标用户字段/内容去标识化，保留对方合法记录”的明确策略，不能默认整行删除。 |
| 匹配消息 | `soul_message`：`init.sql:321-340`。 | 当前按 sender/receiver 删除，见 `AdminServiceImpl.java:292`。 | 目标用户发送/接收的正文必须不可读；若对方需要保留会话生命周期，只保留无目标身份和无正文的固定事件。 |
| 人生图谱主体 | `life_graph_entity`、`life_graph_entity_alias`、`life_graph_relation`、`life_graph_relation_evidence`、`life_graph_mention`、`life_graph_task`、`life_graph_merge_judgment`：`init.sql:477-652,749-777`。 | 当前覆盖 entity、alias、mention、merge judgment、relation evidence、relation、task，见 `AdminServiceImpl.java:278-284,295`。 | 删除关系证据、mention、alias、merge judgment 后，再删 relation 和 entity；必须做端点和证据 orphan 查询。 |
| 图谱实体来源证据 | `life_graph_entity_evidence`：`init.sql:584-607`，包含 `entity_id`、`source_id`、snippet 和扩展属性。 | 当前 deleteQueries 没有该表。 | 明确补入目标用户删除；这是当前最直接的图谱残留缺口之一。 |
| 图片映射 | `image_file`：`init.sql:949-966` 保存 `object_key`、用户、文件哈希。 | 当前只删除 `image_file` 行，见 `AdminServiceImpl.java:274`。 | 删除前收集 object key；若同一 OSS 对象仍被其他 `image_file` 行引用，先保留对象，避免跨用户误删。 |

#### 当前未覆盖、部分覆盖或需要保留政策的表

| 数据面 | 代码事实 | 当前结论与风险 |
| --- | --- | --- |
| 连接生命周期 | `soul_connection`：`init.sql:220-240`；`soul_connection_event`：`242-262`。两表以 `match_id`/`connection_id` 和 actor 字段关联。 | 当前 SQL 没有删除；删除 `soul_match` 后可能留下连接和事件孤儿。须先收集连接/事件 ID，删除目标用户内容，另按审计政策处理低敏连接状态。 |
| 产品事件 | `product_event_scope`：`init.sql:264-274`；`product_event`：`276-303`，含 user、actor、match、connection、run 和低敏 `payload_json`。 | 当前没有删除。事件 scope 可能继续让目标用户可见，事件中的 user/actor 也会保留。目标用户事件和 scope 删除或去标识化，保留的聚合事件不得带可识别关联。 |
| 共享房间 | `situation_room`：`init.sql:154-171` 的 members/submissions/report 是 JSON/Text；`room_message`：`128-140` 保存 sender、sender_name 和 content。 | 当前只在房主或小房间时删除 room，否则仅从 JSON 集合移除用户；共享房间中的消息、report 和提交仍有残留风险。须定义删房、脱敏和对方内容保留边界。 |
| 跨领域任务账本 | `task_execution`：`init.sql:378-411` 的 `owner_user_id` 可为空，且有 source、trigger event、run、checkpoint。 | 当前没有删除。按 owner 删除仍可能漏掉 owner 为空但 source/run 指向目标的任务；运行中的 worker 也可能在删除后重新写入。 |
| 安全审计 | `security_audit_event`、`security_audit_event_scope`：`init.sql:413-446`。实体注释称其为不可变低敏审计记录，见 `SecurityAuditEvent.java:29,82-85`。 | 当前没有删除；`AdminServiceImpl.java:312-317` 还会写带 actor/subject/resource user ID 的注销成功审计。不能把审计表当普通业务表清空，须去标识化并保留合规所需字段。 |
| Agent 轨迹 | `agent_run_trace`、`agent_tool_trace`：`init.sql:694-747` 都有 user_id，内容以低敏状态/计数为主。 | 当前没有删除。虽不保存完整响应，但 user/run/tool 关联仍能反推使用历史；按目标 user 删除，且检查 run_id 在其他表的残留。 |
| 模型调用轨迹 | `model_call_trace`：`init.sql:879-922` 的 user_id 可为空，另有 request/run/provider/model/error 分类。 | 当前没有删除。user_id 非空的行应删除或去标识化；user_id 为空但 request/run 可通过其他记录回指目标的行无法仅凭此表安全映射，列为残余风险。 |
| 无 user_id 的建议 | `suggestion`：`init.sql:654-671` 只有建议正文、邮箱、回复人等字段，没有 user_id。 | 当前无法可靠按账号删除。若业务在 API 层没有保存可验证的 owner 关联，不能声称已覆盖；需产品/合规决定按邮箱、账户绑定或独立 retention 处理。 |
| 全局配置/公告/Prompt | `prompt_template`：`init.sql:101-126`；`notification_announcement`：`781-798`；`model_runtime_config`/`model_config_change_log`：`843-877`。其中公告 publisher、模型 operator 可能是用户 ID，但记录是全局管理数据。 | 不应按目标用户整表删除；若目标用户是 operator/publisher，按去标识化或合规保留政策处理。全局 prompt 和模型配置不属于目标用户私有副本。 |

当前 `deleteQueries` 把 `user` 放在最后，但没有 child-first 的完整依赖顺序，也没有数据库 FK 兜底。完整删除必须依赖显式 ID 集合和 orphan 检查，而不是只检查最后的 `user` 行数。

### 2.4 Milvus 三 collection

| collection | 现状证据 | 当前删除 | 设计要求 |
| --- | --- | --- | --- |
| `yusi_embedding_collection` | `MilvusConfig.java:31-44` 初始化；`EmbeddingBatchService.java:200-246` 写入 userId、diaryId、source revision 等 metadata。 | `AdminServiceImpl.java:226-235` 按 metadata userId delete。 | 保留按 userId 的删除请求；真实查询/flush 后确认零目标 metadata。 |
| `yusi_mid_term_memory` | `MilvusConfig.java:41-43`；`MidTermMemoryVectorService.java:24-58` 写入 summary，metadata 含 userId、memoryId、matchAllowed、hidden。 | 当前整户注销没有删除；单记忆删除只按 memoryId，见 `MidTermMemoryVectorService.java:61-68`。 | 按 metadata userId 删除整户副本；不要把单 memoryId 删除路径当作全户覆盖。 |
| `yusi_match_profile` | `MilvusConfig.java:41-43`；`MatchProfileAssemblerImpl.java:259-293` 以 userId 为 id 并写入 profile text 和 metadata。 | 当前整户注销没有删除；画像刷新时只删除当前画像 id，见 `MatchProfileAssemblerImpl.java:263-267`。 | 按主键和 metadata 双重覆盖，真实环境确认旧版本/segment 不再可查询。 |

三 collection 共用 schema、dense/sparse index 和 embedding dimension，见 `MilvusConfig.java:47-100`。删除验证必须确认 collection 名、filter 字段和 flush/一致性等待，不使用空 collection 或 mock 结果伪造真实 PASS。

### 2.5 Redis key 族与当前覆盖

固定前缀见 `src/main/java/com/aseubel/yusi/redis/common/RedisKey.java:8-26`；普通删除能力只有 `RedissonService.java:84-92` 的单 key 和 pattern 删除。

| key 族 | 数据性质 | 当前证据/覆盖 | 删除政策 |
| --- | --- | --- | --- |
| `yusi:auth:refresh:*`、`yusi:auth:devices:*` | 用户认证安全状态 | 族定义在 `RedisKey.java:12-15`；当前通过 `TokenServiceImpl.java:70-72,107-115` 间接处理。 | 删除 refresh/device；设备 token 加入 blacklist 直到 access token TTL 到期。 |
| `yusi:auth:blacklist:*` | 撤销状态，key 由 token 组成 | `TokenServiceImpl.java:75-84,109-114` 创建；不能仅靠 userId 扫描安全删除。 | 保留可信撤销条目至 TTL，验证“不再有 active refresh/device”，不要求 blacklist 全库为零。 |
| `yusi:usage:<date>` | Redis hash 的短期用户/IP/接口聚合 | `InterfaceUsageMonitor.java:47-65` 把 userId、IP、接口编码到 field；`:230-242` 设置两天 TTL。 | 删除目标用户 fields，并与 `interface_daily_usage` 对账；不能只删 MySQL 行。 |
| `yusi:violation:count:<userId>` | 12 小时安全违规计数 | 定义/TTL 在 `SensitiveWordUtils.java:20-23`，读取和删除方法在 `82-90`；Admin 当前未调用。 | 删除目标计数；失败必须进入待处理状态，不泄露计数值。 |
| `yusi:model:*` | 全局模型运行态、配置副本和 pub/sub channel | `RedisKey.java:19-23`；模型配置权威快照在 MySQL，Redis 是运行副本。 | 不按目标用户清除全局 key；从 MySQL 重载运行配置，channel 历史不作为用户数据恢复。 |
| `yusi:langchain:<userId>` | 可由 MySQL chat message 重建的缓存 | `PersistentChatMemoryStore.java:47-48,57-88,148-155`。当前只删这一族。 | 删除目标缓存；若重建竞态可能回填，先冻结 worker，再二次确认。 |
| `@QueryCache` 业务缓存 | 可重建但可能含用户正文或画像 | `CacheAspect.java:44-47,132-160`；用户 key 示例在 `DiaryServiceImpl.java:233-286`、`NotificationService.java:246-295`、`MatchServiceImpl.java:755-877`。 | 对已登记的用户 key family 做精确清理；不能使用未经审查的全局 wildcard。Admin 的 JDBC 直删不会自动触发 CacheAspect。 |
| `yusi:chunk:*`、`yusi:md5:*` | 分片会话和秒传缓存，可能映射 OSS object | `OssService.java:48-51,515-539,567-570`。 | 删除目标用户的上传会话和 MD5 映射，并确认其引用的临时 chunk 对象也处理。 |

### 2.6 OSS 与本地文件

OSS client 在非 test profile 创建，见 `src/main/java/com/aseubel/yusi/config/oss/OssConfig.java:10-25`；配置键在 `src/main/resources/application.yml:17-26` 和生产配置 `application-prod.yml:258-267`。图片 key 由 `OssService.java:56-62` 生成，音频 key 由 `113-142` 生成，`OssProperties.java:22-24` 定义 image/audio folder。

用户引用分布如下：

- `diary.images`、`diary.audio_object_key`、`diary.attachment_bindings` 在 `init.sql:51-77`；JPA 字段在 `Diary.java:131-151`。
- `chat_memory_message.images` 在 `init.sql:676-692` 和 `ChatMemoryMessage.java:37-38`。
- `image_file.object_key` 在 `init.sql:951-966`。

当前单对象删除只在 `OssService.java:203-227` 通过已拥有 key 执行；日记编辑时只清理从旧图片列表移除的图片，见 `DiaryServiceImpl.java:179-200`。账号注销的 `AdminServiceImpl.java:274` 仅删 `image_file` 行，没有读取日记/聊天/附件引用，也没有删除 OSS 最终图片、音频、附件、分片和历史版本。

代码中发现的是合并上传时的临时目录：`OssService.java:425-430` 创建、`:480` 附近清理；没有发现应用持久化本地媒体目录。因此本设计把 OSS 作为权威对象存储，把临时目录视为短生命周期资源，deployment-only 仍需检查异常退出遗留临时文件。

## 3. 派生数据与审计保留边界

| 数据 | 删除/保留决策 | 理由 |
| --- | --- | --- |
| match_profile、三类 Milvus 向量 | 删除目标用户所有版本和 metadata；必要时保留无用户关联的系统统计。 | 画像正文、摘要和向量可反推用户，即使 MySQL 基础记录已删除也不能保留。 |
| life graph 实体、关系、聚合计数、来源 evidence、merge judgment | 删除目标用户实体、关系、端点、证据和聚合；不保留只剩聚合数字的孤儿。 | 聚合关系和 evidence 仍是可识别认知。来源替换/删除不能留下旧 source id 或 snippet。 |
| mid-term summary、soul report、cognitive conflict | 删除。 | 它们是从对话/日记派生的自然语言认知，不能用“可重建”作为保留理由。 |
| usage 统计、业务缓存、LangChain cache | 目标用户字段/key 删除；全局总量只在无法反推单个用户且无 user dimension 时保留。 | Redis usage field 直接含 user/IP/interface；业务缓存可能含正文。 |
| agent/model trace | 有 userId 的 trace 删除；不可映射但可通过 run/request 回指的记录进入人工审计；纯系统聚合保留。 | 低敏不等于无关联风险。 |
| product_event 与 scope | 用户作用域事件删除；需要产品统计的事件转为无用户、无正文、固定分类的聚合事件。 | `payload_json` 虽声明低敏，仍有 user/actor/match/run 外键。 |
| security_audit_event / scope | 保留合规要求的 action、occurred_at、outcome、reason/category 和独立 deletion request id；actor/subject/resource/scope 去标识化或按批准的保留策略删除。 | `SecurityAuditEvent` 的当前模型把它定义为不可变审计记录，且 `SecurityAuditService.java:148-157` 只按 retention 清理，不能因注销而抹掉安全证据。 |
| 注销成功本身的审计 | 不写目标 userId 作为永久可检索字段；使用一次性 request id、操作分类、结果和时间，目标映射只在受限删除台账中保留到演练/争议期结束。 | 当前 `AdminServiceImpl.java:312-317` 带目标 ID 的成功审计需要在实现刀中改变。 |
| 备份 artifact、异地副本、第三方日志 | 不在应用同步请求内伪造即时删除；按备份保留期、墓碑重放和供应商 DPA/删除协议执行，单列 deployment/compliance 风险。 | 上一刀确认四类备份当前没有已落地的真实恢复机制，且备份删除不是应用事务。 |

## 4. 目标删除编排

实现时采用可重试、幂等、失败闭锁的协调器；当前 `AdminServiceImpl` 的“逐条吞错后继续并记录成功”不能保留。

1. **授权与冻结。** 校验超级管理员和目标状态，创建受限 deletion request，阻止新的登录、写入、embedding/lifegraph/match/weekly worker 以目标用户为 owner 的任务，并撤销 refresh/device token。冻结状态必须跨实例可见；不能只依赖 JVM 内存锁。
2. **收集清单。** 在删除 MySQL 行前收集所有资源 ID、关系/连接/事件/run ID、diary/chat 中的 OSS object key、Milvus 主键和 Redis user-scoped key/usage field。清单只在受限内存/加密台账中传递，不进入日志。
3. **清理外部副本。** 依次对三个 Milvus collection 发 userId filter/primary-key delete；按 allowlist 删除 Redis auth/usage/violation/LangChain/业务缓存族；按收集的 image/audio/attachment/chunk key 删除 OSS 对象。每步失败都让 request 保持 `PENDING_RETRY`，返回失败/待处理，不进入成功审计。
4. **MySQL child-first 事务。** 先删除 scope、通知/事件引用、消息、反馈、连接事件、任务和 traces，再删除 evidence/mentions/aliases/merge judgments、relation、entity，最后删除 match/profile/memory/diary/user 相关行。共享 match/room 采用“删除目标正文和身份、保留对方合法数据”的已批准规则，不用无条件整行删除伤及另一用户。
5. **一致性检查。** 在事务提交前后执行目标用户残留查询、外键式 orphan 查询和资源清单比对；任一非零或未知资源都阻止完成。数据库没有 FK 时，这些查询是完成条件而不是诊断附加项。
6. **审计与收尾。** 记录无目标身份的删除 request 结果；成功后删除/去标识化临时台账中的 target identifier。异步 worker、OSS versioning、Redis TTL 和 Milvus segment 的真实确认在 deployment-only 完成前，状态不得标为全路径 PASS。

## 5. 孤儿引用与残留检查

上一刀 `BackupRestoreInvariantTest` 已提供应用级范式：`src/test/java/com/aseubel/yusi/backup/BackupRestoreInvariantTest.java:141-176` 使用 `LEFT JOIN` 检查 diary/memory/profile/entity/image/product event user orphan、图谱 relation endpoint 和 connection-match orphan；测试定位及故意断裂样例在 `:22-65`。本刀复用同一思路，扩展为删除后检查。

### 5.1 目标用户残留查询

删除后对所有明确的 owner/participant 字段执行 `COUNT(*) WHERE ... = :targetUserId`：`user_persona`、`diary`、`interface_daily_usage`、`user_notification`、`user_location`、`developer_config`、`embedding_task`、`life_graph_*`、`match_profile`、`match_feedback`、`mid_term_memory`、`soul_card`、`soul_resonance`、`soul_report`、`cognitive_conflict`、`agent_*_trace`、`model_call_trace.user_id`、`task_execution.owner_user_id`、`product_event.user_id/actor_user_id`、`product_event_scope.user_id`、`security_audit_event_scope.user_id` 等。

对双向字段执行 `from/to/sender/receiver/user_a/user_b/actor/owner/member` 检查：`resonance_signal`、`soul_match`、`soul_message`、`soul_connection`、`soul_connection_event`、`room_message`、`situation_room` JSON、`situation_scenario.submitter_id`。JSON/Text 成员字段不能只查 SQL 等号，必须解析后确认目标成员、提交、投票和正文已按政策处理。

### 5.2 关系 orphan 查询

- 图谱：alias/mention/entity evidence 的 `entity_id` 必须存在；relation 的 `source_id/target_id` 必须存在；relation evidence 的 `relation_id` 必须存在；所有 evidence/mention 的 source id 不得指向已删除目标来源。
- 匹配与连接：`soul_connection.match_id` 必须存在；connection event、message、feedback 的 connection/match/event 引用必须存在或按共享数据政策被去标识化；product event scope 必须对应 event。
- 运行与审计：`task_execution.trigger_event_id`、run_id 与保留记录的关系要么仍有效，要么任务被取消并删除；`security_audit_event_scope.audit_event_id` 必须对应保留的已去标识化 audit event。
- 对象：每个仍存在的 diary/chat/image_file object key 必须在 OSS inventory 中存在；已收集的目标对象不应在最终 inventory 中出现，除非有另一用户仍在引用的共享对象记录。

孤儿查询结果必须输出低敏计数和分类，不输出 userId、正文、query、token 或完整 object key。

## 6. 可执行验证方案与诚实边界

### 6.1 application-invariant-only：H2

新增 `src/test/java/com/aseubel/yusi/privacy/AccountDeletionPrivacyAuditTest.java`，使用 test profile/H2 和固定脱敏 ID，例如 `fixture-user-delete-target`、`fixture-user-delete-control`。fixture 只含 ID、枚举、时间、计数和短固定标识，不放自然语言正文、profileText、reason、query、prompt、token 或完整 OSS 内容。

测试准备目标用户和控制用户的全套数据，至少覆盖 `BackupRestoreInvariantTest.java:99-132` 已使用的 user/diary/memory/profile/graph/relation/match/connection/image/product event 关系，再补入 entity evidence、scope、traces、task、room、soul message 和审计 scope。调用实际删除协调器后断言：

- 目标用户在全部可按 owner/participant 查询的 MySQL 表中为零；控制用户行仍保留；共享 match/room 的保留内容符合明确定义的去标识化策略。
- child-first 删除顺序被 recorder 捕获，任何数据库异常都会回滚或保持 `PENDING_RETRY`，不会产生成功审计。
- `BackupRestoreInvariantValidator` 扩展查询全部返回零 orphan；故意插入一个断裂引用的测试必须保持失败，不能删除以换取绿色。
- H2 结果命名为 `application-invariant-only`，不带“全路径删除通过”语义。

### 6.2 mock-contract-only：外部边界

用 Mockito/fake port 验证调用契约，不连接真实依赖：

- Milvus 捕获三个 collection 的 `DeleteReq`，确认 embedding/mid-term 按 metadata userId、match profile 按 id 和 metadata 覆盖；只断言请求字段，不断言真实查询结果。
- Redis 记录 refresh/device、usage hash field、violation、LangChain、业务 cache、chunk/md5 的清理分类；禁止用全库 wildcard 伪造成功，blacklist 按 TTL 撤销策略断言。
- OSS 捕获由 diary/chat/image_file inventory 解析出的 image/audio/attachment/chunk 删除请求；不把 object key 打到测试日志。

测试报告必须分开 `mock-contract-only` 和 `deployment-only`，mock 全部通过时仍不能写全路径 PASS。

### 6.3 deployment-only

下列项目必须在隔离测试/预生产依赖上执行，并保存低敏计数、时间和分类证据：

- 真实 MySQL 8：真实事务隔离、并发写入冻结、全表清零、共享 match/room 对方数据未误删和 orphan 查询。
- 真实 Milvus：三个 collection 查询/flush/一致性等待后确认目标 metadata/id 为零，含旧 segment/历史版本检查。
- 真实 Redis：按 key family `SCAN`、hash field、TTL 和 token 撤销状态确认；确认业务缓存不会被后台回填。
- 真实 OSS：images/audio/attachments、multipart 临时对象、delete marker、历史版本和跨区域副本的 HEAD/list 确认；不能只看 `DeleteObject` 返回成功。
- 异步 worker/scheduler：确认冻结窗口不会重新创建 embedding、lifegraph、match profile、usage、trace 或通知数据，并验证重试/重启幂等。
- 备份与第三方副本：执行上一刀 runbook 的 artifact 保留期、删除墓碑重放、恢复后再删除以及供应商日志/模型网关副本政策核验。

## 7. 诚实缺口与残余风险

1. 当前整户删除缺少 `life_graph_entity_evidence`、soul connection/event、product event/scope、task execution、security audit scope/event、agent trace、model call trace 和 OSS 最终对象清理。
2. `suggestion` 没有 user_id，无法可靠按账号定位正文和联系邮箱；在增加 owner 关联或独立 retention 规则前，不能声称已完成。
3. `model_call_trace.user_id` 可为空，`task_execution.owner_user_id` 可为空；只能通过 run/source 反查的行需要人工审计，无法自动映射的记录是残余风险。
4. 双用户 match、connection、room 和消息存在“删除目标数据”与“保留另一用户合法记录”的冲突，产品/合规政策未落地前不能用整行删除掩盖影响范围。
5. 当前逐条吞错并记录成功的行为会制造假完成；需要失败闭锁、可重试台账和完成前残留检查。
6. Redis blacklist、全局 model key、第三方 provider 日志、应用/平台日志和备份 artifact 不一定能按 userId 即时定位；其保留与去标识化必须由 deployment/compliance 流程闭环。
7. OSS 对象可能因 MD5 去重被多个用户的 `image_file` 行引用；没有引用计数前不能按单个目标用户前缀盲删。
8. 本次设计没有修改任何代码或配置，所以当前版本仍不能宣称 roadmap L634 完成。

## 8. 设计完成标准

- 代码层：新的删除协调器覆盖所有可定位数据面，外部失败不写成功审计，删除后目标残留和 orphan 均为零。
- 测试层：H2 为 `application-invariant-only`，Milvus/Redis/OSS 为 `mock-contract-only`，两者均不冒充真实依赖 PASS。
- 部署层：真实 MySQL/Milvus/Redis/OSS、worker、备份和第三方副本证据齐全后，才可把 roadmap L634 从未勾改为完成；本刀不改 checkbox。
