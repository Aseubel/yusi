# Yusi 账号注销与隐私全路径自检实施计划

> **For agentic workers:** 本计划只能在设计文档评审通过后执行；当前任务只创建设计与计划文档。仓库约束禁止子 agent 和 auto-review，按任务顺序 inline 执行。每一步使用 checkbox 追踪，并保留先红后绿证据。

**Goal:** 将管理员账号注销从“部分 SQL 清理”升级为可重试、失败闭锁、跨 MySQL/Milvus/Redis/OSS 的全路径删除流程，并用分层证据证明删除边界。

**Architecture:** 新增账号删除协调器和受限 deletion request 台账。协调器先冻结目标账号和后台写入，收集所有关系/对象/向量/key 清单，再执行外部副本清理和 MySQL child-first 删除；任何外部或 invariant 失败都保持待处理状态，不记录成功。MySQL/H2 只验证应用级完整性，Milvus/Redis/OSS 的真实数据消失和备份/第三方副本处理独立标记 deployment-only。

**Tech Stack:** Java 21、Spring Boot 3.4.5、JPA/JdbcTemplate、H2、JUnit 5、Mockito、Milvus v2 client、Redisson、阿里云 OSS SDK、MySQL 8、PowerShell deployment rehearsal。

## Global Constraints

- 当前执行只允许新增本计划和对应设计文档；不得在本刀修改 `src/main`、测试、migration、CI、roadmap、评测套件、fixture/report 或 `QualityGatePolicy`，不得运行 Maven 或启动服务。
- 后续实现不得保留 `AdminServiceImpl.java:307-317` 的逐条吞错并记录成功行为；外部清理失败、数据库失败、残留计数非零或 orphan 非零都必须阻止完成。
- 目标用户和控制用户测试 ID 使用 `fixture-*` 固定脱敏值；fixture 不含自然语言正文、profileText、reason、letter、query、prompt、token、对象内容或完整 object key。
- 日志和测试报告只记录分类、计数、时间、request id、exception type；不输出 userId 列表、正文、Redis value、完整 object key、模型响应或 SQL 参数。
- 安全审计保留动作/时间/结果/固定原因分类；永久记录使用独立 deletion request id，不保留可直接检索的 target userId。保留期限和去标识化规则需在 deployment/compliance 验证中确认。
- H2 结果只标 `application-invariant-only`；Mockito/fake 结果只标 `mock-contract-only`。任何本地 mock 通过不得生成真实依赖 PASS。
- 三个 Milvus collection、Redis 用户 key family、OSS object/version、异步 worker、备份 artifact 和第三方副本必须有明确 deployment-only 检查；roadmap `L634` 在所有真实证据齐全前保持未勾。

## File Map

### 未来生产实现文件

- Create: `src/main/java/com/aseubel/yusi/service/privacy/AccountDeletionCoordinator.java`：暴露 `DeletionResult requestDeletion(String targetUserId, String adminUserId)`，编排冻结、inventory、外部清理、MySQL 清理、invariant 和完成状态。
- Create: `src/main/java/com/aseubel/yusi/service/privacy/AccountDeletionInventory.java`：保存表行 ID、关系/事件/run ID、Redis family field 和 OSS/Milvus 引用；禁止实现 `toString()` 输出敏感值。
- Create: `src/main/java/com/aseubel/yusi/service/privacy/AccountDeletionExternalPort.java`：定义 `deleteMilvus(AccountDeletionInventory)`, `deleteRedis(AccountDeletionInventory)`, `deleteObjects(AccountDeletionInventory)` 三个可替换边界。
- Create: `src/main/java/com/aseubel/yusi/pojo/entity/AccountDeletionRequest.java`、`src/main/java/com/aseubel/yusi/repository/AccountDeletionRequestRepository.java`：受限、可重试的删除台账；完成后清除 target identifier，永久审计只引用 request id。
- Create: `src/main/resources/db/migration/V20260820__add_account_deletion_request.sql`：建立 pending/running/retry/completed 状态、重试次数、时间和受限目标引用；不得存正文、query、token 或完整 object key。
- Modify: `src/main/java/com/aseubel/yusi/service/user/impl/AdminServiceImpl.java:185-318`：保留权限校验和入口兼容性，委托协调器，不再直接吞外部/SQL 异常并记录成功。
- Modify: `src/main/java/com/aseubel/yusi/service/oss/OssService.java:199-227,515-570`：增加只接受 inventory 且按 image/audio/attachment/chunk allowlist 校验的账号清理适配，不放宽当前用户对象 key 校验。
- Modify: `src/main/java/com/aseubel/yusi/redis/service/RedissonService.java:84-145` 或新增受限 Redis cleaner：支持精确 key 删除和 usage hash field 删除，不新增无边界 wildcard 删除 API。

### 未来测试文件

- Create: `src/test/java/com/aseubel/yusi/privacy/AccountDeletionPrivacyAuditTest.java`：H2 全表/共享数据/invariant/顺序测试，报告标 `application-invariant-only`。
- Create: `src/test/java/com/aseubel/yusi/privacy/AccountDeletionExternalContractTest.java`：Mockito/fake Milvus、Redis、OSS 调用契约，报告标 `mock-contract-only`。
- Create: `src/test/java/com/aseubel/yusi/privacy/AccountDeletionSourceCoverageTest.java`：静态覆盖表、三 collection、Redis key family 和审计政策文件；禁止用 allowlist 把未覆盖项隐藏。
- Optional Create: `src/test/resources/privacy/account-deletion-fixtures.json`：只在测试确实需要 fixture 时添加，字段限于脱敏 ID、枚举、计数和时间。

## Task 1: 先建立删除契约红线

**Files:**

- Create: `src/test/java/com/aseubel/yusi/privacy/AccountDeletionPrivacyAuditTest.java`
- Create: `src/test/java/com/aseubel/yusi/privacy/AccountDeletionExternalContractTest.java`
- Create: `src/test/java/com/aseubel/yusi/privacy/AccountDeletionSourceCoverageTest.java`
- No production file in this task

**Interfaces:**

- Consumes: 当前 `AdminController.java:254-258` 和 `AdminServiceImpl.java:185-318` 的行为，以及 `BackupRestoreInvariantTest.java:141-176` 的 orphan 查询范式。
- Produces: 失败的全路径删除契约，明确列出缺失表、缺失 collection、Redis family 和 OSS object 清单。

- [ ] **Step 1: 写 H2 失败测试。**

  用 `fixture-user-delete-target` 与 `fixture-user-delete-control` 构造 user、diary、chat memory、mid-term memory、match profile、图谱实体/alias/mention/relation/evidence、match/connection/event/scope、image mapping、task、trace、room、message、audit scope。断言目标用户清零、控制用户保留、共享资源策略明确、orphan 为零；保留一个故意断裂引用用例并断言 invariant 失败。

- [ ] **Step 2: 写外部契约失败测试。**

  捕获预期的三条 Milvus delete、Redis auth/usage/violation/LangChain/business/chunk family 调用和 OSS image/audio/attachment/chunk 删除调用。当前实现缺少这些 port/调用，测试应因接口或调用缺失失败，不得把没有调用当成通过。

- [ ] **Step 3: 写静态覆盖失败测试。**

  对源码/配置建立固定覆盖集合：MySQL 表组、`MilvusConfig.java:41-43` 三 collection、`RedisKey.java:12-26` key family、OSS 引用字段和 security audit 政策。测试必须识别 `life_graph_entity_evidence`、soul connection/event、product event/scope、task_execution、agent/model trace 和 OSS 最终对象未覆盖。

- [ ] **Step 4: 运行聚焦测试确认先红。**

  Run: `.\mvnw.cmd -q "-Dtest=AccountDeletionPrivacyAuditTest,AccountDeletionExternalContractTest,AccountDeletionSourceCoverageTest" test`

  Expected: non-zero。失败证据至少包含当前只处理 `yusi_embedding_collection`、未清理两个向量 collection/Redis family/OSS 对象以及现有 orphan/共享资源缺口。不得删除 sentinel 或把 mock 清单改成空集。

## Task 2: 实现冻结、台账和失败闭锁

**Files:**

- Create: `AccountDeletionRequest.java`、`AccountDeletionRequestRepository.java`、`V20260820__add_account_deletion_request.sql`
- Create: `AccountDeletionCoordinator.java`、`AccountDeletionInventory.java`、`AccountDeletionExternalPort.java`
- Modify: `AdminServiceImpl.java:185-318`
- Test: `AccountDeletionPrivacyAuditTest.java`

**Interfaces:**

- Consumes: 管理员 userId、target userId、已有 `@Transactional` 入口和固定脱敏测试 ID。
- Produces: pending/running/retry/completed deletion request；完成前不写成功审计；协调器可被 fake external port 驱动。

- [ ] **Step 1: 先为冻结与失败状态补红色断言。**

  在测试中让 external port 的第一个清理抛出 fixture exception，断言 MySQL owner rows 仍完整或事务回滚、deletion request 为 retry、没有 success audit；让数据库 invariant 失败时断言同样不能完成。

- [ ] **Step 2: 实现受限台账和冻结状态。**

  创建 request 时只保存必要的目标引用、状态、重试次数和时间；冻结期间拒绝新写入/新任务认领。target identifier 只在 pending/running 期间受限保存，完成后清除；异常信息只保存固定 failure category，不保存 exception message。

- [ ] **Step 3: 让 AdminService 委托协调器。**

  保持 `AdminController` 路由和权限行为不变，移除 `AdminServiceImpl` 中直接执行外部清理和 `deleteQueries` 的成功假设。外部失败必须抛出可分类异常或返回 retry 状态，不能被 `catch` 后继续写 `ADMIN_USER_DEREGISTERED/SUCCESS`。

- [ ] **Step 4: 运行聚焦测试确认冻结契约通过。**

  Run: `.\mvnw.cmd -q "-Dtest=AccountDeletionPrivacyAuditTest" test`

  Expected: PASS for failure-closed, retry state, no-success-audit and target-write-freeze assertions。报告仍标 `application-invariant-only`。

## Task 3: 实现 inventory 与 MySQL child-first 删除

**Files:**

- Modify: `AccountDeletionCoordinator.java`、`AccountDeletionInventory.java`
- Modify: `AdminServiceImpl.java` only for delegation cleanup
- Test: `AccountDeletionPrivacyAuditTest.java`
- Reuse: `BackupRestoreInvariantTest.java:99-176` query style; do not modify that file

**Interfaces:**

- Consumes: target user and H2/JPA/JdbcTemplate repositories; account deletion request state。
- Produces: complete resource inventory and one transactionally ordered MySQL cleanup.

- [ ] **Step 1: Extend red data setup and residual assertions.**

  Add all user-owned tables from design §2.3, including `life_graph_entity_evidence`, `task_execution.owner_user_id`, both agent traces, `model_call_trace.user_id`, product event scopes, connection events and security audit scopes. Add target/control rows to shared room/match and assert the policy outcome instead of asserting accidental whole-row deletion.

- [ ] **Step 2: Implement pre-delete inventory.**

  Before deleting rows, collect graph/entity/relation IDs, match/connection/event/run/task IDs and OSS keys from diary.images, diary.audio_object_key, diary.attachment_bindings, chat_memory_message.images and image_file.object_key. Inventory must use sets, be idempotent, and never log values.

- [ ] **Step 3: Implement child-first SQL order.**

  Delete scope/referrer rows and messages/tasks/traces first; delete graph evidence/mentions/aliases/merge judgments, then graph relations/entities; delete connection/event/feedback/match dependent data using the collected IDs; delete user-owned profiles/memories/diaries and `user` last. For each statement, fail the transaction on unexpected error instead of continuing.

- [ ] **Step 4: Implement orphan validator.**

  Add the LEFT JOIN checks from design §5.2 for user ownership, graph endpoints/evidence, match/connection/event, image mapping and audit scope. Return only low-sensitivity counts. A non-zero count throws a classified deletion failure.

- [ ] **Step 5: Run focused H2 verification.**

  Run: `.\mvnw.cmd -q "-Dtest=AccountDeletionPrivacyAuditTest" test`

  Expected: PASS for target rows zero, control rows retained, child-first order and all orphan counts zero; the deliberate broken-reference test remains a negative test. No full-path claim is allowed.

## Task 4: Cover Milvus, Redis and OSS external boundaries

**Files:**

- Create: implementation of `AccountDeletionExternalPort` under `src/main/java/com/aseubel/yusi/service/privacy/`
- Modify: `OssService.java:199-227,515-570` if an account-scoped adapter is required
- Modify: `RedissonService.java:84-145` only for precise hash-field/key operations if required
- Test: `AccountDeletionExternalContractTest.java`

**Interfaces:**

- Consumes: `AccountDeletionInventory` and existing `MilvusClientV2`, Redisson and OSS beans.
- Produces: idempotent delete requests for all three collections, all user-scoped Redis families and all collected object keys.

- [ ] **Step 1: Add Milvus contract assertions.**

  Assert collection names exactly equal `yusi_embedding_collection`, `yusi_mid_term_memory`, `yusi_match_profile`; assert filter/id covers user metadata for the first two and both id/metadata for the profile. Do not create an empty collection or query a mock result as evidence of disappearance.

- [ ] **Step 2: Add Redis family cleanup.**

  Delete refresh/device/LangChain/violation keys, remove target fields from each `yusi:usage:<date>` hash, and evict only inventory-derived business cache keys. Preserve blacklist entries until their security TTL; do not delete global `yusi:model:*` runtime/config/channel keys.

- [ ] **Step 3: Add OSS inventory cleanup.**

  Delete image/audio/attachment objects and upload chunks represented by the inventory, subject to prefix/ownership validation and remaining `image_file` reference count. Do not delete a shared object solely because the target user row was removed. Keep object keys out of logs and failure messages.

- [ ] **Step 4: Run mock contract verification.**

  Run: `.\mvnw.cmd -q "-Dtest=AccountDeletionExternalContractTest" test`

  Expected: PASS with report label `mock-contract-only`; the test output must state that real Milvus/Redis/OSS disappearance remains deployment-only.

## Task 5: Implement derived-data and audit retention policy

**Files:**

- Modify: `AccountDeletionCoordinator.java`
- Modify: `SecurityAuditService.java` and audit write adapter as required by the approved retention policy
- Modify: global operator/event handling only for target user de-identification; do not delete global prompt/model configuration rows indiscriminately
- Test: `AccountDeletionPrivacyAuditTest.java`, `AccountDeletionSourceCoverageTest.java`

**Interfaces:**

- Consumes: security audit retention rule, deletion request id, target/control data setup.
- Produces: deleted derived user data, de-identified retained security event, no permanent target user link in deletion success record.

- [ ] **Step 1: Lock red retention assertions.**

  Insert audit events for target actor/subject/scope, a product event, model operator record and a global announcement publisher. Assert the approved result: private/derived data is gone; retained security evidence contains action/time/outcome/category and request id only; unrelated global rows remain.

- [ ] **Step 2: Implement de-identification boundary.**

  Preserve `SecurityAuditEvent` retention semantics and `cleanupExpired` behavior from `SecurityAuditService.java:148-157`, but remove direct target identifiers from the permanent deletion success record. Treat `suggestion` without user_id, null-user model traces and owner-null task records as explicit unresolved cases, not silently deleted rows.

- [ ] **Step 3: Run focused privacy audit.**

  Run: `.\mvnw.cmd -q "-Dtest=AccountDeletionPrivacyAuditTest,AccountDeletionSourceCoverageTest" test`

  Expected: PASS for derived-data deletion and audit policy; source coverage reports no unclassified table/key/collection. Any unresolved mapping remains in the report as a named residual risk.

## Task 6: Add deployment rehearsal and evidence separation

**Files:**

- Create: `docs/engineering/runbooks/yusi-account-deletion-privacy-audit-runbook.md`
- Create: deployment rehearsal script or checked-in command template under `ops/privacy/` only after scope approval
- Test: no new mock PASS; consume existing test reports only as application/mock evidence

**Interfaces:**

- Consumes: deletion request id, low-sensitivity inventory counts, deployment credentials from secret injection, real dependency endpoints.
- Produces: deployment-only evidence with start/end time, dependency, scope classification, counts, invariant result, operator and rollback/blocked status.

- [ ] **Step 1: Define isolated rehearsal dataset.**

  Use one target and one control account in a disposable MySQL/Milvus/Redis/OSS namespace. Load non-natural-language sentinel identifiers and media objects; record only counts and opaque rehearsal run id.

- [ ] **Step 2: Verify real Milvus and Redis disappearance.**

  Query/flush all three collections and scan Redis key families/hash fields after worker freeze. Verify blacklist TTL policy separately. This step must not run in local unit tests.

- [ ] **Step 3: Verify real OSS and versions.**

  Check image/audio/attachment objects, multipart chunks, delete markers, historical versions and any configured replication destination. A successful delete API response alone is insufficient.

- [ ] **Step 4: Verify worker and backup boundaries.**

  Confirm scheduled embedding/lifegraph/matching/usage/trace jobs cannot re-create target data. Execute backup artifact retention, deletion tombstone replay and restore-then-delete check from the backup/restore runbook. Record all as `DEPLOYMENT-ONLY` until evidence is reviewed.

## Task 7: Audit, full verification and handoff

**Files:**

- Modify only the future privacy implementation/tests/runbook files listed above
- Do not modify roadmap or existing evaluation reports

- [ ] **Step 1: Run focused tests.**

  Run: `.\mvnw.cmd -q "-Dtest=AccountDeletionPrivacyAuditTest,AccountDeletionExternalContractTest,AccountDeletionSourceCoverageTest" test`

  Expected: exit code 0; report the application-invariant-only and mock-contract-only labels separately.

- [ ] **Step 2: Run the full suite.**

  Run: `.\mvnw.cmd -q test`

  Expected: exit code 0 and no changes to the existing quality-gate reports. Do not claim deployment-only checks passed from this command.

- [ ] **Step 3: Run static privacy scans.**

  Scan source, tests, scripts and reports for `userId` lists, query/content/prompt/token values, full object keys, raw Redis values, exception messages and SQL output. Classify test fixture field names separately from emitted data; all emitted sensitive values must be zero.

- [ ] **Step 4: Check roadmap and scope before commit.**

  Confirm `docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md:634` remains unchecked until deployment evidence is reviewed. Run `git diff --check` and verify only approved privacy implementation/test/runbook files changed. Do not start backup changes, alerting, or later operational work in this slice.

- [ ] **Step 5: Commit and stop.**

  After focused/full verification and reviewer acceptance, use:

  ```powershell
  git add src/main/java/com/aseubel/yusi/service/privacy `
    src/main/java/com/aseubel/yusi/service/user/impl/AdminServiceImpl.java `
    src/main/java/com/aseubel/yusi/service/oss/OssService.java `
    src/main/java/com/aseubel/yusi/redis/service/RedissonService.java `
    src/main/java/com/aseubel/yusi/service/security/SecurityAuditService.java `
    src/main/resources/db/migration/V20260820__add_account_deletion_request.sql `
    src/test/java/com/aseubel/yusi/privacy `
    docs/engineering/runbooks/yusi-account-deletion-privacy-audit-runbook.md `
    ops/privacy
  git commit -m "security: add account deletion privacy audit gate"
  ```

  Handoff must include focused/full exit codes, application-invariant-only metrics, mock-contract-only metrics, deployment-only checklist, residual-risk list, static scan result, `git diff --check` result and commit hash. Stop after handoff; roadmap checkbox remains the reviewer’s decision.

## Plan self-review

- Spec coverage: entry chain, all requested MySQL/Milvus/Redis/OSS data surfaces, derived and audit boundaries, orphan queries, H2/mock/deployment verification, honest gaps, failure-closed ordering and roadmap boundary are mapped to Tasks 1-7.
- Placeholder scan: no task treats a future real dependency check as locally passable; every external validation has a named deployment-only step and evidence class.
- Type consistency: `AccountDeletionInventory` feeds `AccountDeletionExternalPort`; `AccountDeletionCoordinator` owns the request lifecycle; tests consume the coordinator and its external port instead of copying clustering or deletion logic.
- Boundary check: this current slice creates only the two requested documents. It does not modify production code, tests, migration, CI, evaluation files, backup files, or roadmap, and it does not run Maven or start a service.
