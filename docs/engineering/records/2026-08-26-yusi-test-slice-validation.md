# Yusi-test 切片验证记录

> **Status:** In progress — 本地修复已完成，远端 `yusi-test` 闭环复测未完成
> **Date:** 2026-08-26
> **Scope:** 仅使用 `yusi-test` 做验证；生产库 `yusi` 只读
> **Runtime:** 远端运行实例源码 release SHA `2f67430f5b3eb3fc5d0dfadd909a74200d907ca6`
> **Related:** [Yusi Agent 产品与工程演进计划](../plans/2026-08-04-yusi-agent-product-roadmap.md)

## 验证边界

- 本轮没有向生产库 `yusi` 写入、删除或修复数据。
- 所有账号、权限、跨用户访问和注销验证均以测试库 `yusi-test` 为目标。
- Milvus 使用官方云端实例；本轮只按测试账号范围核对和清理，不把它视为 MySQL 事务的一部分。
- OSS、Redis 和 Milvus 的既有测试副本可以在重测前按账号重建；不会把测试账号密码写入记录。
- 当前本地工作区已完成注销事务边界、待重试语义和 `SecurityException` 响应映射修复；尚未 commit 或同步远端。已有用户改动保持不变。

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

## 孤儿数据说明

“生产 yusi 孤儿关系”是指关系、alias、mention 等记录仍引用已经不存在的用户或 LifeGraph 实体。例如 `life_graph_relation.source_id` 或 `target_id` 找不到对应实体。它是数据完整性问题，不等于生产库一定存在真实用户。

此前只读部署验收发现生产 `yusi` 的 `life_graph_relation` 有 7 条 source/target 悬挂关系；`yusi-test` 当时与生产一致。已在 `yusi-test` 验证关系及 evidence 的级联修复可以使该类 relation orphan 数量归零，但生产修复尚未执行，也不在本轮范围内。

本轮注销失败触发的是另一类全局 orphan alias/mention 校验问题。重测前只允许在 `yusi-test` 清理已确认的测试环境孤儿数据；不能据此修改或清理生产 `yusi`。

## 待完成切片

1. [x] 为注销失败台账、事务边界、`PENDING_RETRY` 返回语义和 LifeGraph `SecurityException` 映射补充回归测试，并确认测试先 RED。
2. [x] 修复删除台账独立提交、协调器失败不被外层事务吞掉、待重试不返回普通成功，以及 LifeGraph 跨用户删除固定返回 `403`。
3. [ ] 只在 `yusi-test` 清理已确认的 orphan alias/mention，并记录清理前后计数。
4. [ ] 重建用户 B 的 Redis、OSS、Milvus fixture，再次执行真实注销。
5. [ ] 核对 B 的所有 MySQL 用户数据为 0；A 的控制数据仍存在；共享 match/connection/room 只做正确去标识化；B 的三个 Milvus collection、Redis key 和 OSS 对象均为 0；orphan 查询为 0；删除台账为 `COMPLETED`。
6. [ ] 通过管理员 C 删除 A，清理 C 与剩余 fixture，避免留下测试数据。
7. [ ] 只读复查生产 `yusi` 未发生变化。
8. [ ] 根据最终证据更新 roadmap；在闭环完成前不勾选账号删除、安全授权或数据完整性条目。

## 当前结论

授权边界的大部分 HTTP 验证已通过，但账号删除链路尚未达到可接受的闭环标准。当前不能声称“注销成功”、不能把 `PENDING_RETRY` 当作完成，也不能将生产数据完整性标记为通过。
