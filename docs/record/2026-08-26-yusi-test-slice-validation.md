# Yusi-test 切片验证记录

> **Status:** In progress — `988fbda` 已部署；B/A 注销、测试 fixture 清理和生产只读复查已完成，roadmap Phase 5 仍有其他 deployment-only 子项待验收
> **Date:** 2026-08-26
> **Scope:** 仅使用 `yusi-test` 做验证；生产库 `yusi` 只读
> **Runtime:** 远端运行实例源码 release SHA `988fbda`
> **Related:** [Yusi Agent 产品与工程演进计划](../plans/2026-08-04-yusi-agent-product-roadmap.md)

## 验证边界

- 本轮没有向生产库 `yusi` 写入、删除或修复数据。
- 所有账号、权限、跨用户访问和注销验证均以测试库 `yusi-test` 为目标。
- Milvus 使用官方云端实例；本轮只按测试账号范围核对和清理，不把它视为 MySQL 事务的一部分。
- OSS、Redis 和 Milvus 的既有测试副本可以在重测前按账号重建；不会把测试账号密码写入记录。
- 当前本地工作区已完成注销事务边界、待重试语义、usage 原始 field 清理、历史待重试引用收敛、
  删除期间 usage 写入抑制、`SecurityException` 响应映射、共享 match 状态清理和用户级动态缓存
  清理；当前已提交为 `988fbda` 并推送到 `origin/main`。远端已执行
  `/root/projects/yusi/rebuild.sh`，Maven 构建成功、容器重建并启动，健康检查通过。远端原有的
  `build_yusi_mcp.sh`、`frontend` 用户改动保持不变。

## 续接起点：用户级缓存修复已部署

- 本次验证严格以 `yusi-test` 为写入、删除和 fixture 重建范围；生产库 `yusi` 只做只读复查。
- 已确认本地提交 `70977bf5fe0783845d18d85ea09594239d3dae11` 已推送，远端
  `/root/projects/yusi/rebuild.sh` 返回成功，管理端口 readiness 为 `UP`。
- 本阶段先记录再开始依赖复测；验证重点是动态用户级缓存、orphan alias/mention、Redis usage
  原始 field、历史删除台账去标识化，以及 MySQL、Redis、OSS、Milvus 的真实注销闭环。

## 测试账号

本轮使用三个 fixture 账号：

| 账号 | 用户 ID | 用途 |
| --- | --- | --- |
| `fxAuthzA260826` | `01a03ce1303377fd805ae7dc43a39961` | 普通用户 A，保留控制数据用于交叉核验 |
| `fxAuthzB260826` | `01a03ce137627ae59b56d8dedde835e6` | 普通用户 B，作为注销目标 |
| `fxAuthzC260826` | `01a03ce13c327480b295fff714cf8733` | 权限等级 99 的管理员 |

## 已通过的真实 HTTP 验证

- 匿名访问 diary、AI、model 和 prompt 接口均返回固定未认证错误 `40103`。
- 普通用户访问 admin、model 和 prompt 管理入口均返回 `403`。
- 用户 B 不能读取、删除用户 A 的日记或图片，也不能修改、删除用户 A 的广场卡片。
- 非参与者不能访问 match、room 和 room-chat。
- 用户 B 查询用户 A 的 LifeGraph BFS 返回空图，没有发现数据泄露。
- 用户 B 对用户 A 的通知执行 read/delete 不改变状态。
- 管理员 C 的管理入口可正常访问。

## 发现的问题

### 1. LifeGraph 跨用户关系删除返回 500

跨用户删除关系时，业务层抛出 `SecurityException`，但全局异常处理没有将它映射到现有授权失败协议，因此 HTTP 返回 `500`。预期应为固定 `403`，且不能暴露内部异常语义。

### 2. 他人日记详情错误语义偏弱

跨用户读取日记详情返回 `200 + data:null`。当前没有泄露正文，但资源不存在、无权访问和空数据被混用了，后续需要根据现有 API 协议决定统一为明确的授权/资源错误。

### 3. 广场 signal 存在授权候选问题

`/api/plaza/signal` 当前允许请求中的 `cardId` 所属用户与 `toUserId` 不匹配，已记录为候选问题 `AUTHZ-CANDIDATE-001`。本轮没有擅自修改；此前验证产生的候选 fixture signal 需要在最终清理中核对。

### 4. 注销失败产生半删除状态

调用：

```text
POST /api/admin/users/{userId}/deregister
```

以用户 B 为目标时返回 `500`，失败分类为 `database_invariant`。结果如下：

- B 的 MySQL 账号数据仍然存在。
- B 的 Redis refresh/device/langchain/violation key 已删除。
- B 的 Milvus 三个 collection 数据已删除。
- B 的 OSS 图片对象已删除。
- `account_deletion_request` 最终为 0，失败台账没有保留下来。

根因和风险：

1. `yusi-test` 在本轮注销时存在全局 orphan alias/mention 数据，`AccountDeletionInvariantValidator` 阻断了删除。
2. `AdminServiceImpl.deregisterUser` 的外层事务与协调器事务嵌套，失败后事务进入 rollback-only，最终以 `UnexpectedRollbackException` 结束，删除台账也被回滚。
3. 外部清理发生在 MySQL 事务前，导致数据库失败时出现“外部副本已删、MySQL 未删”的半删除状态。
4. `AdminServiceImpl` 当前忽略 `DeletionResult.PENDING_RETRY`，存在把未完成删除当作成功处理的风险。

### 5. usage hash 残留的根因

最新运行实例中，`yusi:usage:2026-08-26` 仍有 8 个 B 的目标 field；同时 B 的
`interface_daily_usage` MySQL 行数为 0。usage worker 因混合编码数据在 `HSCAN` 阶段解码失败，
未能把这些 Redis 统计落库，而协调器原先只从 MySQL 行反推 Redis field，因此这些 field 没有进入
inventory，不能被 `removeFromMap` 清理。该问题不是 MySQL 删除失败，但会阻止全路径隐私验收。

### 6. 历史 PENDING_RETRY 引用

`account_deletion_request` 中存在 4 条 B 的历史 `PENDING_RETRY`，其中
`database_invariant` 2 条、`external_or_database` 2 条。最新成功请求只清空了自己的
`target_user_ref`，旧失败请求仍保留目标引用；这会使成功收尾后的临时台账仍可直接检索目标账号。

## 本地追加修复与证据

- `RedissonService` 新增专用 `removeUsageFields`：扫描 `yusi:usage:*`，以 `StringCodec` 只读取
  hash field，支持 JSON 包裹和历史裸 field，按目标用户精确删除，不解码混杂的 hash value，
  也不删除 usage hash 或使用无边界 wildcard 删除。
- `DefaultAccountDeletionExternalPort` 在清理 refresh/device、LangChain 和 violation 后调用该专用操作；
  MySQL inventory 中已有的 usage field 清理仍保留，作为精确删除兜底。
- 成功删除事务在写成功审计前，将同一目标的旧 `PENDING`、`RUNNING`、`PENDING_RETRY` 请求标为
  `SUPERSEDED` 并清空 `target_user_ref`；失败时事务回滚，仍保留可重试引用。
- 已先确认红测失败，再实现并通过：usage 原始 field 清理测试、历史待重试引用测试，以及删除隐私、
  外部契约、源覆盖、事务边界和管理员相邻测试。
- 本地 `\.\mvnw.cmd -q test` 退出码为 `0`；追加修复已部署到 `eece5e7`，容器内 readiness 返回 `UP`。

## 第一轮真实注销结果：发现并修复共享 match 状态槽残留

- B 已通过真实登录访问自身 diary 接口；注销前新实例观察到 Redis 当日 usage hash 存在 1 个 B field，
  MySQL `interface_daily_usage` 存在 1 行。管理员 C 调用注销接口返回 `200 / success`。
- 第一轮注销后，B 的所有 MySQL 归属残留均为 0，B 的 Redis usage field、三类 Milvus collection
  查询结果均为 0；A 账号仍存在。共享 match、connection、room 保留控制用户数据并去除 B 身份。
- 真实 MySQL 复查发现共享 `soul_match.status_b` 仍为原值。根因是同一条 `UPDATE` 先修改
  `user_a_id/user_b_id`，再用已修改的 ID 判断 `status_a/status_b`；MySQL 的赋值顺序会跳过目标状态槽。
- 已新增 H2 行为断言和 SQL 赋值顺序 source contract；修复前 source contract 两个槽位均 RED，
  修复后 focused privacy/source tests 全部 GREEN。修复已提交为 `33f26ef`、推送并部署，
  第一轮真实结果因此进入后续复测阶段。

## 依赖复测基线：fixture 重建前

- `yusi-test` 全局完整性查询结果为 0：relation endpoint、entity/relation evidence、alias entity、
  mention entity，以及 alias/mention 的 user orphan 均为 0；因此本阶段没有需要删除的 orphan 行，
  清理前后计数均为 0。
- B 的 MySQL 归属残留只有 `interface_daily_usage` 1 行；覆盖的 user、diary、LifeGraph、记忆、
  profile、trace、task、OSS 映射和其他用户归属表均为 0。B 账号行当前为 0。
- B 的 Redis `yusi:usage:*` 仍有 8 个目标 field，usage hash 共 4 个；这些 field 尚未落库，
  将由本次真实注销验证专用 field 清理逻辑。
- `account_deletion_request` 当前有 4 条 B 的 `PENDING_RETRY`（`database_invariant` 2 条、
  `external_or_database` 2 条），另有 1 条无目标引用的 `COMPLETED` 历史记录。
- 上述账号计数使用文本 user ID 的正确编码重新核对；此前一次二进制字面量查询结果不作为证据。

## 孤儿数据说明

“生产 yusi 孤儿关系”是指关系、alias、mention 等记录仍引用已经不存在的用户或 LifeGraph 实体。例如 `life_graph_relation.source_id` 或 `target_id` 找不到对应实体。它是数据完整性问题，不等于生产库一定存在真实用户。

此前只读部署验收发现生产 `yusi` 的 `life_graph_relation` 有 7 条 source/target 悬挂关系；`yusi-test` 当时与生产一致。已在 `yusi-test` 验证关系及 evidence 的级联修复可以使该类 relation orphan 数量归零，但生产修复尚未执行，也不在本轮范围内。

本轮注销失败触发的是另一类全局 orphan alias/mention 校验问题。重测前只允许在 `yusi-test` 清理已确认的测试环境孤儿数据；不能据此修改或清理生产 `yusi`。

## 第二轮真实注销结果：发现动态 diary 分页缓存残留

- `33f26ef` 部署后，重新创建 B 的测试数据并通过真实登录、访问自身 diary、产生 usage、注销完成；B 的 MySQL 用户归属数据、usage field、Milvus 三个 collection 数据和共享 match 状态均已按预期收敛，`soul_match.status_b` 残留已消失。
- 复查 B 的 Redis 时仍发现一条确认属于 B 的动态缓存：`yusi:diary:list:v4:<B>:1:10:null:true`。
- 根因已定位到 `AccountDeletionCoordinator.collectExactCacheKeys` 只登记固定 key 和 diary detail key，没有覆盖 diary list 的分页参数组合；当前删除外部端口也没有一个按用户前缀、受限 allow-list 的清理入口。因此本次注销不能作为全路径闭环证据。
- 本轮 OSS 尚未注入真实图片对象与 `image_file` 映射，OSS 删除路径仍缺 deployment-only 证据；第二轮 Milvus 删除后的最终 count 也需要再单独确认。

## 当前追加切片

- 已为动态 diary list cache 先补红测，再实现按用户精确前缀的受限清理；同一 inventory 现在覆盖
  diary list、notification list、match list/status、Plaza 我的列表，并从目标用户参与/拥有的
  situation room 收集 `room:chat` exact key。没有纳入全局 `plaza:feed:*` 或其他共享公共 key。
- 本地 focused privacy 集合和全量 `\.\mvnw.cmd -q test` 均退出码 `0`；补丁已提交为
  `70977bf`、推送并部署，容器源码 SHA 与该提交一致，readiness 为 `UP`。
- 已按仓库要求检查 roadmap：Phase 5“安全与隐私自检”保持 `[ ]`，不能用本地测试替代真实
  OSS fixture、Milvus 最终 count、部署后全路径复测、测试数据清理和生产只读复核。

## 第三轮真实注销结果：用户级缓存清理已验证

- 在 `yusi-test` 重建 B/A/C、LifeGraph、共享 match/connection/room、Redis 和 Milvus fixture；
  B 通过真实登录和自身接口访问，真实上传接口生成 OSS 图片对象及 `image_file` 映射。
- 三个 Milvus collection 各插入 1 条 B 数据；管理员 C 真实调用注销 B 返回 `200 / success`。
- 注销后 B 的 MySQL 用户归属数据、Redis 用户级 key、usage hash field、`room:chat` 缓存和三个
  Milvus collection 查询结果均为 `0`；删除台账最新记录为 `COMPLETED`。
- 共享数据正确去标识化：`soul_match` 保留 A 并清空 B 槽位及私有推荐内容，
  `soul_connection` 保留控制记录并将 B 替换为 `account-deleted`、状态置为 `BLOCKED`，
  房间保留 A 并移除 B 的成员、提交和投票。
- relation endpoint、entity/relation evidence、alias/mention、product event、
  connection/match、feedback/message、task/event 和 audit scope 的 orphan 查询均为 `0`。
- 2026-08-27 使用远端 OSS SDK 认证对账：本轮已知的两个 B 对象 key 均返回
  `HEAD=404 / NoSuchKey`，但 B 用户图片前缀下仍列出 `2` 个 `HEAD=PRESENT` 对象，且
  `image_file` 映射为 `0`。这确认当前注销流程只按 MySQL/正文引用收集对象，未覆盖用户前缀下
  的无映射对象；该残留是本轮发现的真实隐私缺口，不能将 B 的 OSS 删除验收标为通过。
- presigned URL 在注销前后均返回 `403`，不再作为对象存在性证据；后续需要先补充受限的用户
  前缀 inventory/删除逻辑并通过本地红绿测试，再重新部署和复测。

## 第三轮收尾：A 清理与 OSS 根因

- 在 `70977bf` 实例上，管理员 C 真实注销 A 返回 `HTTP 200 / code 200 / success`；A/B 的
  MySQL 用户归属引用、共享 fixture、用户级 Redis key 和关系 orphan 查询均为 `0`，C 仍保留。
- C 的 4 条测试管理员审计事件和 13 条 usage 记录仍在，这是当前测试账号继续执行管理请求的
  结果；它们属于测试清理范围，不作为删除失败证据。生产库 `yusi` 的 fixture 事件、fixture
  审计和测试账号查询均为 `0`。
- A 删除后复查发现 B 的两个已知上传 key 均为 `HEAD=404 / NoSuchKey`，但 B 图片前缀仍有
  `2` 个 `HEAD=PRESENT` 对象，且 `image_file` 映射为 `0`。这两个对象是当前真实 OSS 孤儿，
  证明仅按 MySQL/正文引用收集 object inventory 不足。

## 本地修复：用户前缀 OSS inventory

- 先写 `OssServiceDeletionTest` 红测：当前源码缺少前缀 inventory API，编译失败；随后实现最小
  受限前缀清理，并补 adapter 契约断言。
- `OssService` 现在按页列举并校验三个前缀：图片 `imageFolder/<user>/`、音频
  `audioFolder/<user>/`、分片 `imageFolder/chunks/<user>/`。图片仍被其他用户 `image_file`
  映射引用时保留，否则删除；列表或删除异常直接向协调器传播，保持 `PENDING_RETRY`。
- focused privacy/OSS 集合退出码 `0`；全量 `\.\mvnw.cmd -q test` 退出码 `0`，报告汇总为
  `537 tests / 0 failures / 0 errors / 0 skipped`。本地修复尚未提交、推送或部署。
- 当前 remote 仍运行 `70977bf`；下一步必须提交推送、执行 `/root/projects/yusi/rebuild.sh`，
  在 `yusi-test` 重建 B 并再次通过真实注销验证三个 OSS 前缀均为 `0`，再清理 C 和临时测试对象。

## 待完成切片

1. [x] 为注销失败台账、事务边界、`PENDING_RETRY` 返回语义和 LifeGraph `SecurityException` 映射补充回归测试，并确认测试先 RED。
2. [x] 修复删除台账独立提交、协调器失败不被外层事务吞掉、待重试不返回普通成功，以及 LifeGraph 跨用户删除固定返回 `403`。
3. [x] 只在 `yusi-test` 核对已确认的 orphan alias/mention；当前无 orphan，清理前后计数均为 0。
4. [x] 修复 usage hash 未落库时的 Redis field 清理，并在成功删除时收敛旧待重试台账引用；本地红绿测试和全套 Maven 已通过，部署后真实复测通过。
5. [x] 提交并推送追加修复，远端执行 `rebuild.sh`。
6. [x] 在 `70977bf` 上重建 B/A/C 并执行 B、A 的真实注销；MySQL、Redis、Milvus、共享数据和
   orphan 查询通过，但 SDK 发现 B 图片前缀仍有 `2` 个无映射对象。
7. [x] 为图片、音频、分片用户前缀 inventory 先补红测，再完成本地最小修复；focused 和全量
   Maven 均通过，待部署复测。
8. [x] 提交推送本地 OSS 前缀修复，远端执行 `rebuild.sh`，重建 B fixture 并再次真实注销；三个
   OSS 前缀、MySQL、Redis、Milvus 和共享数据必须全部收敛。
9. [x] 通过管理员 C 清理 C 与剩余测试 fixture/对象，保留必要审计边界并核对测试库无用户残留。
10. [x] 只读复查生产 `yusi` 未发生变化，并清理远端临时验证工具。
11. [ ] 根据最终证据更新 roadmap；在闭环完成前不勾选账号删除、安全授权或数据完整性条目。

## 阶段性结论（第四轮后）

授权边界的大部分 HTTP 验证已通过；`70977bf` 上 B/A 的真实注销在 MySQL、Redis、Milvus、
共享数据和 orphan 查询范围内通过，但真实 OSS SDK 发现 B 用户前缀仍有 `2` 个无映射对象。
本地已补充前缀 inventory 修复并通过 `537` 项全量测试，尚未部署复测；C 测试数据清理和生产
只读复查也尚未完成，因此仍不能声称 Phase 5 完整闭环或勾选 roadmap。

## 第四轮：OSS 前缀修复部署与分片会话 codec 缺陷

- 本地 `ea9d284` 已完成用户图片、音频和分片 OSS 前缀 inventory 修复，已执行
  `commit + push`；远端执行 `/root/projects/yusi/rebuild.sh` 成功，容器源码 SHA 与
  `ea9d284` 一致，readiness 返回 `UP`。
- 首次 OSS 音频夹具误放在 `yusi/audio/<user>/`；核对生产配置后确认实际前缀是
  `audio/<user>/`，错误对象已删除。之后按正确前缀重新注入图片、音频、分片对象和有效
  分片会话进行注销验证。
- 第二次真实注销返回 `HTTP 50002`，删除台账为 `PENDING_RETRY / external_or_database`。
  MySQL 中 B、`image_file` 和 diary 数据因事务回滚而保留；Milvus 已完成删除；日志显示
  图片和音频对象已删除，随后在读取分片会话时失败；Redis 分片会话 key 仍有残留。
- 根因已确认：`OssService.uploadChunk` 通过 `StringRedisTemplate` 写入原始字符串，
  `DefaultAccountDeletionExternalPort.deleteSessionChunkObjects` 却通过
  `IRedisService.getValue()` 使用 Redisson 默认 JSON codec 读取 `totalChunks` 和分片
  object key，导致 codec/type 不兼容并被归类为 `external_or_database`。当前会话的
  `:0`、`:1`、`:reserved`、`:size` 等用户限定 key 也没有统一纳入清理边界。
- 下一步先为原始 Redis 字符串读取和完整分片会话 key 清理补红测，再本地全量验证；之后
  按“提交、推送、远端 rebuild、yusi-test 重建夹具、真实注销”的流程复测 OSS 三个前缀、
  MySQL、Redis、Milvus 和共享数据，最后再清理测试账号并只读复查生产库。

## 第五轮：分片 codec 修复部署与真实注销闭环

- 本地先补红测：在 `AccountDeletionExternalContractTest` 中要求分片 session 使用专用原始
  字符串读取，在 `RedissonServiceUsageCleanupTest` 中锁定 `StringCodec`，并要求 inventory
  登记 `yusi:chunk:<user>:*`、`yusi:md5:<user>:*`；修复前测试编译失败，修复后 focused
  测试通过。
- 新增 `IRedisService.getStringValue`；`RedissonService` 通过 `StringCodec` 读取
  `StringRedisTemplate` 写入的 raw string，分片 object key/totalChunks 改用该接口；删除
  inventory 增加用户限定的 chunk 和 MD5 key pattern。全量 Maven 为 `537 tests / 0
  failures / 0 errors / 0 skipped`。
- 修复已提交为 `988fbda`、推送到 `origin/main`，远端执行 `/root/projects/yusi/rebuild.sh`
  成功；Maven `BUILD SUCCESS`、Docker 容器重建完成，运行实例源码 SHA 为 `988fbda`，
  readiness 为 `UP`。
- 在 `yusi-test` 重建 B、共享 match/connection/room、图片映射和有效分片 session；通过
  正确的 `audio/<B>/`、`yusi/images/<B>/`、`yusi/images/chunks/<B>/` 前缀注入对象，并
  在三个 Milvus collection 各插入 1 条 B 数据。管理员 C 调用真实注销接口返回
  `HTTP 200 / success`。
- 注销后验收结果：B 的 MySQL 归属残留 `0`；共享 match 保留 1 条且 B ID/status 槽均为
  `0`；共享 connection 保留 1 条、B 已替换为 `account-deleted` 且状态为 `BLOCKED`；
  共享 room 不再含 B；审计事件保留 1 条但不再含 B；全局 orphan 汇总为 `0`。
- Redis 的 B chunk、MD5、diary/notification/match/plaza 动态缓存、refresh/device、
  LangChain、violation、room-chat 和 usage field 均为 `0`；三个 Milvus collection
  最终查询均为 `0`；已知图片、音频、分片对象均 `HEAD=404 / NoSuchKey`，三个用户前缀
  分页清单均为 `0`。
- 远端日志另有一条由旧测试夹具 `embedding_task.task_type=DIARY_EMBEDDING` 触发的
  worker enum 解析告警；该值不属于当前枚举，属于测试夹具兼容性问题，未影响本次注销
  请求或最终残留验收，后续清理夹具时一并移除。
- 本轮分片/OSS/外部副本切片已完成；仍未完成的是清理 C 和剩余测试夹具、生产 `yusi`
  只读复查及最终 roadmap/Phase 5 总体安全与隐私条目判定。

## 第六轮：测试收尾与生产只读复查

- 在 `yusi-test` 中按精确 ID 删除剩余两条 `FIXTURE` 审计事件
  （`fixture-authz-audit-b`、`fixture-audit-b-260826`）；C 注销后新增的 2 条
  `AdminController#deregisterUser` 测试 usage 行也已按 C 用户 ID 清理。
- 测试库最终复查：`fxAuthz%` 用户、fixture 产品事件/范围、任务、embedding task、
  room/scenario/message、审计事件均为 `0`；删除台账 `target_user_ref` 和
  `requested_by_ref` 均为空；关系端点、entity/relation evidence、alias/mention、
  产品 scope、匹配/连接/消息、chat/task/audit scope 全局 orphan 均为 `0`。
- C 的 Redis refresh/device、LangChain、violation、用户缓存、chunk/MD5 和 usage field
  均为 `0`；三个 Milvus collection 查询均为 `0`；OSS 的
  `yusi/images/<C>/`、`audio/<C>/`、`yusi/images/chunks/<C>/` 前缀对象数均为 `0`。
- 对生产 `yusi` 仅执行只读查询：本轮 fixture 用户、产品事件、审计事件、删除台账引用和
  fixture diary 均为 `0`，未发现本轮测试写入；生产既有 LifeGraph orphan 为关系端点
  `7`、alias `1`、mention `15`，未执行生产修复。
- 已删除远端 `/tmp` 中本轮的 token、fixture、响应文件、Milvus/OSS 探针和 classpath；
  系统临时文件未触碰。roadmap Phase 5“安全与隐私自检”仍保持 `[ ]`，因为真实鉴权/
  越权、Trace、代理/日志采集等其他 deployment-only 子项尚未完成。
- 收口前重新执行本地 Maven 全量测试：`maven_exit=0`，`538 tests / 0 failures /
  0 errors / 0 skipped`；远端容器内部 readiness 返回 `{"status":"UP"}`。

## 第七轮：定时任务异常与 readiness 误报定位

- 远端实例当前仍连接 `yusi-test`，运行源码为 `988fbda`；本轮没有向生产库 `yusi` 写入、删除或修复数据。
- 2026-08-27 远端只读检查确认：liveness 为 `UP`，readiness 为 `503`；`dependency_health` 中
  `db`、`redis`、`milvus`、`model_gateway` 为 `up`，只有 `tasks` 为 `down`，因此不是容器进程或外部依赖
  整体不可用。
- `task_due_gap` 显示唯一过期任务为 `memory-fusion`。调度入口实际是每日 03:00（Asia/Shanghai），
  但 `TaskScheduleCatalog` 将其按 1 小时计算；当天上午最后一次成功后超过两小时，`TaskHealthIndicator`
  的固定 2 小时 stale 阈值便把正常状态判成 `DOWN`，触发 `YUSI-SVC-READINESS`。
- 远端日志还确认一次历史 `task_execution.task_type=DIARY_EMBEDDING` 反序列化异常。该值是旧任务账本
  的兼容值，当前生产代码只生成 `EMBEDDING`；异常由 `YusiScheduledTasks.runTracked` 重新抛给 Spring
  定时器，因而产生完整的 `Unexpected error occurred in scheduled task` 堆栈，并可能终止该 fixed-delay
  worker。修复需保留旧值读取兼容、将新写入继续固定为 `EMBEDDING`，并在调度边界记录低敏单行失败后继续
  后续调度。
- 同一窗口发现 `dependency_health` Gauge 重复注册 WARN。`YusiMetrics.recordGauge` 每次更新都重新向
  Micrometer 注册同名 Gauge；后续改为同一标签键复用已注册 Gauge，避免健康轮询污染日志。

### 本轮修复边界

1. 为旧 `DIARY_EMBEDDING` 提供持久化枚举兼容和与 `EMBEDDING` 的幂等语义兼容；不向新任务写入旧值。
2. 按实际调度周期计算任务健康 stale 窗口；每日/每周任务不再因固定两小时阈值误报，真正失败仍保持
   readiness 降级。
3. 定时任务失败只保留任务名、异常类型和固定分类的单行日志，不向 Spring 继续抛出已记录异常；业务
   失败状态仍进入 `TaskHealthRegistry`，下一次成功后恢复。
4. 复用 Micrometer Gauge 注册对象，消除重复注册 WARN；不改变指标名称、标签白名单或业务结果。

按用户要求本轮不新增测试；现有健康端点、指标、任务账本和 Embedding 聚焦测试，以及全量 Maven
测试均已通过（退出码 `0`）。提交前已复查 roadmap 对应项，本轮不勾选任何 Phase 5
deployment-only 条目；远端部署和重建后的真实健康/日志验证仍待完成。

## 第八轮：限流、Token 成本与上下文组成优先切片

- 本轮范围收敛为三项：Redis/本地 HTTP 限流、模型 Token 预算与成本统计、聊天上下文组成与裁剪。
- 不执行远端 `rebuild.sh`，由部署责任人完成；不新增测试，不修改生产库 `yusi`，也不推进 Phase 5
  其他 deployment-only 条目。
- 已确认的实现缺口：Redis 限流 key 已存在时 `trySetRate` 不会更新代码配置；本地 fallback
  缓存无上限且速率等于分布式阈值；`GET /api/image/url` 未限流；非法 usage、无价格版本成本和
  `REJECTED` 未调用记录的未知成本边界不完整；预算相加存在溢出风险；上下文消息窗口、数据库加载量
  和动态 System Message 没有统一配置，空历史首次聊天可能没有 System Message，动态上下文没有总
  Token 预算。
- 本轮验收目标：配置变更能作用于既有 Redis 限流器；Redis 故障时本地 fallback 有界且拒绝安全；
  provider usage 只接受非负值并要求价格版本才能形成已知成本；预算统计不把准入拒绝当作未知成本；
  首次聊天始终带系统消息，短期历史与动态上下文按统一条数/token 边界组成并优先保留核心规则。
- roadmap 对应 Phase 5“限流与成本准入复核”仍不勾选：真实并发/SSE/Multipart、网关字节与并发、
  Redis 故障演练、供应商配额校准和生产阈值仍需部署环境证据；上下文切片也不替代其他 Phase 5
  安全、备份和运维验收。
