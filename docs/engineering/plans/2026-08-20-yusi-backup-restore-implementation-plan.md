# Yusi 数据备份与恢复实施计划

> **For agentic workers:** 本计划必须在设计文档评审通过后按任务顺序执行。当前这一步只新增设计与计划文档；不得执行后续任务、修改 roadmap、生产代码、migration、CI、测试或评测文件。步骤使用 checkbox 追踪，真实依赖恢复和生产 RTO 只能记录为 deployment-only。

**Goal:** 为 MySQL、Milvus、Redis 和 OSS 建立可校验、可恢复、可记录 RPO/RTO 的备份与恢复流程，并完成至少一次真实环境演练。

**Architecture:** 备份由独立运维脚本/托管能力负责，应用数据库作为权威源，Milvus 是带 schema/index manifest 的派生副本，Redis 按 key family 分层恢复，OSS 以版本化对象和 MySQL object-key inventory 绑定。恢复流程先恢复 MySQL/OSS，再恢复或重建 Milvus，最后按安全策略恢复 Redis；低敏 H2 测试只验证应用级完整性规则，不冒充真实依赖恢复。

**Tech Stack:** MySQL 8 `mysqldump`/binlog 或 RDS PITR、Milvus 版本匹配的 `milvus-backup` 或 collection export/import、Redis RDB/AOF 或托管快照、阿里云 OSS Versioning/Inventory/复制、PowerShell rehearsal wrapper、JUnit 5 + Spring Boot test + H2。

## Global Constraints

- 不修改 `src/main`、生产配置、migration、CI、roadmap、既有评测套件、fixture、report 或 `QualityGatePolicy`。
- 备份 artifact 必须加密、校验 checksum、最小权限访问并存放在生产故障域之外；日志和 manifest 只记录低敏计数、版本、时间和分类。
- 任何脚本不得输出用户 ID 列表、query、正文、prompt、token、cache value、模型响应、完整 object key 或异常正文。
- MySQL `ddl-auto: none` 是生产事实；恢复不能依赖 Hibernate 自动建表。迁移目录没有 Flyway runtime wiring，schema 版本由恢复 runbook 和数据库变更台账显式记录。
- H2 `application-test.yml:18-42` 只用于应用级 invariant 测试；真实 MySQL dump、Milvus、Redis、OSS 恢复不通过 mock、空 collection 或 H2 伪造 PASS。
- 初始目标为 MySQL/Redis 安全状态 RPO 15 分钟、RTO 60 分钟；第一次真实演练后以实际数据修订目标，不凭设计文档宣称达标。
- 当前任务只写两份文档；本计划的后续文件均为评审后的实施产物，不在本次工作树中创建。

## File Map

### 后续新增运维文件

- Create: `ops/backup/backup-manifest.schema.json`：定义四类 artifact 的低敏元数据、checksum、计数、版本和恢复点字段。
- Create: `ops/backup/mysql-backup.ps1`：封装 `mysqldump`/binlog 或托管 PITR 触发、压缩、加密、checksum 和 retention。
- Create: `ops/backup/milvus-backup.ps1`：封装三 collection export、schema/index manifest、checksum 和保留策略；不自行实现向量导出协议。
- Create: `ops/backup/redis-backup.ps1`：封装 RDB/AOF/托管快照触发和 key-family 低敏计数；不导出 token/value 到日志。
- Create: `ops/backup/oss-inventory.ps1`：生成对象数量/size/hash inventory，校验 versioning/replication 状态并绑定 MySQL backupId。
- Create: `ops/backup/restore-rehearsal.ps1`：在隔离 MySQL 目标库上恢复 dump 并执行行数/orphan 校验，编排可选的真实 Redis/Milvus/OSS deployment-only 步骤。
- Create: `docs/engineering/runbooks/yusi-backup-restore-runbook.md`：记录凭据、权限、冻结入口、恢复顺序、回滚和演练记录链接；不写秘密。

### 后续新增测试文件

- Create: `src/test/java/com/aseubel/yusi/backup/BackupManifestContractTest.java`：只验证低敏 manifest 的 schema、必填字段、checksum 格式和禁止字段。
- Create: `src/test/java/com/aseubel/yusi/backup/BackupRestoreInvariantTest.java`：在 H2 合成数据上验证 orphan/计数校验器会对坏数据失败；不读取真实 dump，不连接外部依赖。
- Create: `src/test/resources/evaluation/backup-restore-invariant-fixtures.json`：如测试需要 fixture，只含固定合成 ID、计数和状态枚举，不含自然语言正文、query、prompt、token、profileText 或对象内容。

## Task 1: 固化 artifact manifest 和现状审计

**Files:**

- Create: `ops/backup/backup-manifest.schema.json`
- Create: `src/test/java/com/aseubel/yusi/backup/BackupManifestContractTest.java`
- Create: `docs/engineering/runbooks/yusi-backup-restore-runbook.md`

**Interfaces:**

- Consumes: 四类组件备份工具输出、`2026-08-20` 设计文档中的字段约束。
- Produces: `backupId`、`component`、`sourceDataTimestampUtc`、`createdAtUtc`、`artifactSha256`、`artifactBytes`、`schemaVersion`、`toolVersion`、`counts`、`retentionClass` 和 `restorePoint`。

- [ ] **Step 1: 先写 manifest 失败测试。**

  让 `BackupManifestContractTest` 拒绝缺少 component/source time/checksum、checksum 非 64 位 hex、未知 component、包含 `userId`/`query`/`content`/`token`/`objectKey` 字段的 JSON；同时接受只含计数和版本的最小 manifest。

- [ ] **Step 2: 运行聚焦测试确认先红。**

  Run: `.\mvnw.cmd -q "-Dtest=BackupManifestContractTest" test`

  Expected: FAIL because manifest schema and validator do not yet exist. Do not weaken the forbidden-field assertions to obtain a green result.

- [ ] **Step 3: 写 schema、低敏校验器和 runbook 骨架。**

  manifest 允许的 component 只有 `mysql`、`milvus`、`redis`、`oss`；计数只允许整数、固定 enum 和 checksum/size/time。runbook 明确当前审计结论为四类“现有备份手段：无”，并列出 `docker-compose.yml:4-34`、`application-prod.yml`、`docs/devops/gitops_proposal.md:630-633` 的事实边界。

- [ ] **Step 4: 运行聚焦测试确认通过。**

  Run: `.\mvnw.cmd -q "-Dtest=BackupManifestContractTest" test`

  Expected: PASS with forbidden-field, required-field and checksum cases all covered.

## Task 2: 实现 MySQL 备份和恢复校验

**Files:**

- Create: `ops/backup/mysql-backup.ps1`
- Modify: `docs/engineering/runbooks/yusi-backup-restore-runbook.md`
- Test: `src/test/java/com/aseubel/yusi/backup/BackupRestoreInvariantTest.java`

**Interfaces:**

- Consumes: database name/endpoint from deployment secret injection、备份存储路径、`backup-manifest.schema.json`。
- Produces: compressed/encrypted MySQL dump、binlog/PITR metadata、table-count manifest、restore exit code。

- [ ] **Step 1: 先写恢复完整性测试。**

  用固定合成用户、日记、记忆、画像、图谱实体/关系、图片映射、连接和产品事件构造 H2 数据；校验器必须执行设计文档 §5.1 的 user、diary、mid-term memory、match profile、life graph endpoint、soul connection orphan 查询，并在任一 orphan 非零时失败。

- [ ] **Step 2: 运行 H2 聚焦测试确认先红。**

  Run: `.\mvnw.cmd -q "-Dtest=BackupRestoreInvariantTest" test`

  Expected: FAIL until the invariant query adapter and synthetic data setup exist. The test must include a deliberately broken reference case; do not remove it.

- [ ] **Step 3: 实现备份 wrapper。**

  `mysql-backup.ps1` 固定执行 `mysqldump --single-transaction --routines --events --triggers --hex-blob --databases yusi` 或显式调用已批准的 RDS PITR API；随后压缩、加密、计算 SHA-256、记录每表计数和 source timestamp。密码只从 secret store/environment 传入，禁止命令行回显。

- [ ] **Step 4: 实现隔离库恢复路径。**

  `restore-rehearsal.ps1` 先校验 manifest，再恢复到临时 MySQL database，按目标时间应用 binlog/PITR，执行表存在、关键表行数、主键/唯一键和 orphan 查询；任何非零 orphan、checksum mismatch 或 schema mismatch 都返回非零退出码。

- [ ] **Step 5: 运行聚焦测试和静态 scope 检查。**

  Run: `.\mvnw.cmd -q "-Dtest=BackupRestoreInvariantTest" test`；Expected: PASS。再用 `rg -n "userId|query|content|token|objectKey" ops/backup docs/engineering/runbooks` 检查日志/manifest 字段实现未泄露敏感字段。

## Task 3: 接入 Milvus collection export/import

**Files:**

- Create: `ops/backup/milvus-backup.ps1`
- Modify: `docs/engineering/runbooks/yusi-backup-restore-runbook.md`

**Interfaces:**

- Consumes: `MilvusConfig.java:31-44` 的三 collection、`MilvusConfig.java:47-100` 的 schema/index/function、`model.embedding.dimension`。
- Produces: 每个 collection 的 export artifact、schema/index manifest、row count/dimension 校验结果。

- [ ] **Step 1: 写失败前置检查。**

  wrapper 在 collection 清单不是 `yusi_embedding_collection`、`yusi_mid_term_memory`、`yusi_match_profile`，或 manifest dimension/schema/index 不完整时必须失败。

- [ ] **Step 2: 运行脚本 contract test/静态测试确认先红。**

  使用不完整的合成 manifest 调用 wrapper 的 dry-run contract；Expected: FAIL with non-zero exit code and no export marked PASS.

- [ ] **Step 3: 实现 export。**

  使用版本匹配的 `milvus-backup` 或官方 collection export/import API，不通过应用 bean 初始化来“备份”。每份导出保存 collection name、schema fields、dense/sparse dimension、index metric、function、Milvus/SDK/embedding identity、row count、source MySQL backupId。

- [ ] **Step 4: 实现恢复顺序和校验。**

  恢复时严格执行 schema/function/index 创建或确认 -> data import -> index build/load -> count/dimension/metadata ownership 校验。没有 export 时只允许显式 `derived-rebuild` 模式，并记录 `EmbeddingBatchService`、`MidTermMemoryVectorService`、`MatchProfileAssemblerImpl` 的模型版本和漂移风险。

- [ ] **Step 5: 在真实测试 endpoint 执行演练。**

  真实 Milvus export/import、索引构建耗时、权限和向量查询属于 deployment-only；本地没有真实 endpoint 时只运行 manifest contract，不写 PASS。

## Task 4: 实现 Redis 分层快照和 OSS 对象保护

**Files:**

- Create: `ops/backup/redis-backup.ps1`
- Create: `ops/backup/oss-inventory.ps1`
- Modify: `docs/engineering/runbooks/yusi-backup-restore-runbook.md`

**Interfaces:**

- Consumes: `RedisKey.java:8-26`、`InterfaceUsageMonitor.java:47-90`、`ModelConfigCenter.java:77-115`、`OssProperties.java:7-28` 和 `OssService.java:56-145` 的 key/对象事实。
- Produces: Redis snapshot metadata、key-family counts/TTL classes、OSS versioning/inventory/hash report、同一 MySQL `backupId` 的对账记录。

- [ ] **Step 1: 先写 key-family/对象引用失败案例。**

  contract test/脚本 dry-run 必须拒绝把 `auth` token/value 写进 manifest，必须把 `yusi:langchain:*`、`yusi:chunk:*`、`yusi:md5:*` 标为可重建，必须把 `auth`、usage、violation、model runtime config 标为需审查，必须拒绝把 pub/sub channel 计入可恢复消息。

- [ ] **Step 2: 实现 Redis snapshot wrapper。**

  使用 Redis RDB/AOF 或托管快照/PITR；只输出 key-family 数量、TTL 分类和 snapshot time。恢复 runbook 固定写明：缓存可清空，auth 快照不可信时全量 token 失效，usage 与 MySQL 对账，model runtime config 以 MySQL 为源重发布，pub/sub 不恢复。

- [ ] **Step 3: 实现 OSS protection/inventory wrapper。**

  启用并验证 bucket versioning、inventory、加密和复制；按 MySQL 引用生成待校验对象集合，执行 HEAD 的存在/size/hash/content-type 检查，记录缺失和无引用对象计数。最终对象和 object-key 映射必须同一恢复记录，分片与本地临时目录不计入最终备份完整性。

- [ ] **Step 4: 运行 focused contract checks。**

  Run: `.\mvnw.cmd -q "-Dtest=BackupManifestContractTest,BackupRestoreInvariantTest" test`

  Expected: PASS for low-sensitivity policy checks. Redis/OSS real snapshot, versioning and object restore remain deployment-only until an isolated dependency environment is available.

## Task 5: 执行本地 H2 + 临时 MySQL 恢复演练

**Files:**

- Modify: `ops/backup/restore-rehearsal.ps1`
- Modify: `docs/engineering/runbooks/yusi-backup-restore-runbook.md`
- Test: `src/test/java/com/aseubel/yusi/backup/BackupRestoreInvariantTest.java`

**Interfaces:**

- Consumes: 四类 manifest、MySQL dump/PITR artifact、低敏合成 fixture。
- Produces: exit code、恢复表计数、orphan 计数、对象/向量/key-family 校验摘要和 RTO 记录草稿。

- [ ] **Step 1: 运行 H2 invariant 回归。**

  Run: `.\mvnw.cmd -q "-Dtest=BackupRestoreInvariantTest" test`

  Expected: PASS for synthetic valid data and FAIL for each deliberately injected orphan. H2 result must be labeled `application-invariant-only`。

- [ ] **Step 2: 在隔离 MySQL 执行 dump restore。**

  `restore-rehearsal.ps1 -DumpPath <approved-artifact> -ManifestPath <approved-manifest> -TargetDatabase <temporary-name>` 先校验 checksum，再恢复临时库、执行 row-count/orphan checks，并在全部检查通过后返回 0。命令参数不得包含密码，目标 database 必须不是生产库。

- [ ] **Step 3: 生成 RTO 记录。**

  填写设计文档 §6 模板的开始/完成 UTC 时间、elapsed、RPO gap、MySQL/OSS/Milvus/Redis 状态、完整性结果和签字；没有实际开始/完成时间不得填 PASS。

- [ ] **Step 4: 明确本地边界。**

  若没有真实 Redis、Milvus 或 OSS endpoint，只记录 `DEPLOYMENT-ONLY`，不得用 mock、H2、空集合或只检查配置来替代真实恢复。

## Task 6: 测试/预生产真实恢复演练与上线交接

**Files:**

- Modify: `docs/engineering/runbooks/yusi-backup-restore-runbook.md`
- Create: `docs/engineering/records/2026-08-20-yusi-backup-restore-rehearsal.md`

**Interfaces:**

- Consumes: Task 1-5 的 artifact、权限、备份存储和测试/预生产依赖。
- Produces: 真实演练记录、实际 RPO/RTO、残余风险、回滚决策和上线交接清单。

- [ ] **Step 1: 预约维护窗口并冻结写入。**

  由部署负责人停止应用写入口、scheduler 和 worker，记录冻结时间；这一步不在本地 Maven 测试中伪造通过。

- [ ] **Step 2: 按固定顺序恢复四类数据。**

  MySQL schema/dump/PITR -> OSS 版本对象与引用对账 -> Milvus schema/index/import/load 或标记 derived rebuild -> Redis 分层恢复/清空/重发布 -> 单实例 read-only smoke -> 小流量放行。

- [ ] **Step 3: 验证安全和业务完整性。**

  验证 token/blacklist 策略、usage 对账、对象引用、Milvus dimension/count、应用读取和 readiness；输出只含计数/分类的记录。

- [ ] **Step 4: 记录并审查 RTO。**

  用实际 `startedAtUtc` 和 `recoveryCompletedAtUtc` 计算 RTO，用源数据时间和目标恢复时间计算 RPO gap。未达到初始目标 `RPO 15 min / RTO 60 min` 时，交接报告必须列出原因、owner、修正措施和下一次演练窗口。

- [ ] **Step 5: 检查 roadmap 后再停下。**

  只读确认 `docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md:631-632` 的 checkbox 状态；本计划不勾选。只有评审方在真实演练记录、RTO 和完整性证据齐全后才决定是否勾选。

## Deployment-only 清单

- 生产/预生产真实 MySQL dump、binlog/PITR 恢复，大表耗时和容量校准。
- 三个 Milvus collection 的真实 export/import、schema/function/index/load、向量一致性和 embedding 重建耗时。
- Redis RDB/AOF/托管 PITR 恢复，auth refresh/blacklist/device token 安全处置，usage 增量对账。
- OSS versioning、inventory、hash/size 对账、版本恢复和跨桶/跨区域复制。
- KMS、备份 artifact 加密、ACL、恢复账号、密钥轮换和备份网络隔离。
- 停止流量、scheduler、worker，维护入口、DNS/Ingress 切换、readiness 放流量和回滚。
- 实测 RPO/RTO、保留周期、备份容量、恢复吞吐、真实依赖连通性和 operator/reviewer sign-off。

## Final Verification and Handoff

- [ ] `.\mvnw.cmd -q "-Dtest=BackupManifestContractTest,BackupRestoreInvariantTest" test` 通过；只验证低敏 manifest/invariant，不宣称真实依赖恢复。
- [ ] `git diff --check` 通过，变更只包含本计划列出的运维/测试/文档文件；当前设计阶段只允许两份新增文档。
- [ ] 四类 artifact 均有 checksum、来源时间、保留策略和恢复权限记录。
- [ ] 恢复演练记录包含开始时间、完成时间、实际 RTO、RPO gap、计数/orphan/hash/schema 结果和签字。
- [ ] 所有 deployment-only 项目单独列出，未把本地 H2、mock 或配置检查写成 PASS。
- [ ] 评审方决定 roadmap L631-L632 是否勾选；执行者不得自行修改 roadmap。
