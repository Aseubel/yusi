# Yusi-test 切片验证记录

> **Status:** In progress — `eece5e7` 已部署并完成第一轮真实注销；发现共享 match 状态槽残留，修复已在本地红绿验证，待提交部署
> **Date:** 2026-08-26
> **Scope:** 仅使用 `yusi-test` 做验证；生产库 `yusi` 只读
> **Runtime:** 远端运行实例源码 release SHA `eece5e76ba7bc376a4cfdf0ce471c882e8ed4e35`
> **Related:** [Yusi Agent 产品与工程演进计划](../plans/2026-08-04-yusi-agent-product-roadmap.md)

## 验证边界

- 本轮没有向生产库 `yusi` 写入、删除或修复数据。
- 所有账号、权限、跨用户访问和注销验证均以测试库 `yusi-test` 为目标。
- Milvus 使用官方云端实例；本轮只按测试账号范围核对和清理，不把它视为 MySQL 事务的一部分。
- OSS、Redis 和 Milvus 的既有测试副本可以在重测前按账号重建；不会把测试账号密码写入记录。
- 当前本地工作区已完成注销事务边界、待重试语义、usage 原始 field 清理、历史待重试引用收敛、
  删除期间 usage 写入抑制和 `SecurityException` 响应映射修复；已提交为
  `eece5e76ba7bc376a4cfdf0ce471c882e8ed4e35` 并推送到 `origin/main`。远端已执行
  `/root/projects/yusi/rebuild.sh`，Maven 构建成功、容器重建并启动，健康检查通过。远端原有的
  `build_yusi_mcp.sh`、`frontend` 用户改动保持不变。

## 续接起点：追加修复已部署

- 本次验证严格以 `yusi-test` 为写入、删除和 fixture 重建范围；生产库 `yusi` 只做只读复查。
- 已确认本地提交 `eece5e76ba7bc376a4cfdf0ce471c882e8ed4e35` 已推送，远端
  `/root/projects/yusi/rebuild.sh` 返回成功，管理端口 readiness 为 `UP`。
- 本阶段先记录再开始依赖复测；验证重点是 orphan alias/mention 清理、Redis usage 原始 field、
  历史删除台账去标识化，以及 MySQL、Redis、OSS、Milvus 的真实注销闭环。

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
- 本地 focused privacy 集合和全量 `\.\mvnw.cmd -q test` 均退出码 `0`；当前补丁已完成本地提交，尚未推送或部署。
- 已按仓库要求检查 roadmap：Phase 5“安全与隐私自检”保持 `[ ]`，不能用本地测试替代真实
  OSS fixture、Milvus 最终 count、部署后全路径复测、测试数据清理和生产只读复核。
- 下一步是提交推送并执行远端 `rebuild.sh`，然后重建 B fixture，补 OSS 对象映射和最终依赖残留核对。

## 待完成切片

1. [x] 为注销失败台账、事务边界、`PENDING_RETRY` 返回语义和 LifeGraph `SecurityException` 映射补充回归测试，并确认测试先 RED。
2. [x] 修复删除台账独立提交、协调器失败不被外层事务吞掉、待重试不返回普通成功，以及 LifeGraph 跨用户删除固定返回 `403`。
3. [x] 只在 `yusi-test` 核对已确认的 orphan alias/mention；当前无 orphan，清理前后计数均为 0。
4. [x] 修复 usage hash 未落库时的 Redis field 清理，并在成功删除时收敛旧待重试台账引用；本地红绿测试和全套 Maven 已通过，待部署复测。
5. [x] 提交并推送追加修复，远端执行 `rebuild.sh`。
6. [ ] 重建用户 B 的 Redis、OSS、Milvus fixture，再次执行真实注销（Redis/Milvus 已重建并完成第一轮；OSS 本轮尚未注入对象 fixture）。
7. [ ] 核对 B 的所有 MySQL 用户数据为 0；A 的控制数据仍存在；共享 match/connection/room 只做正确去标识化；B 的三个 Milvus collection、Redis key 和 OSS 对象均为 0；orphan 查询为 0；删除台账为 `COMPLETED`。
8. [ ] 通过管理员 C 删除 A，清理 C 与剩余 fixture，避免留下测试数据。
9. [ ] 只读复查生产 `yusi` 未发生变化。
10. [ ] 根据最终证据更新 roadmap；在闭环完成前不勾选账号删除、安全授权或数据完整性条目。

## 当前结论

授权边界的大部分 HTTP 验证已通过；上一轮追加修复已部署，本轮用户级缓存补丁已在本地全量
通过但尚未部署，账号删除链路仍待真实 fixture 注入、注销和全路径残留核对，不能提前声称达到
完整闭环，也不能将生产数据完整性标记为通过。
