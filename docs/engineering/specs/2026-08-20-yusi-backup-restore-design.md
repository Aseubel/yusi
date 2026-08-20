# Yusi 数据备份与恢复设计

- 状态：待评审
- 日期：2026-08-20
- 所属阶段：Phase 5 上线工程准备
- 路线图依据：`docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md:631-632`
- 本刀范围：MySQL、Milvus、Redis、对象存储的备份周期、恢复顺序、恢复演练和 RTO 记录格式

## 1. 目标与边界

本设计回答四个问题：哪些数据是权威数据、哪些数据可以重建、当前是否已经有备份、发生故障时如何恢复并证明完整性。当前仓库没有一套已经落地且可执行的四类数据备份/恢复机制；因此本设计的第一结论是风险而不是能力声明：在真实备份任务、异地保存、恢复权限和至少一次真实演练完成前，路线图的“制定并演练数据备份与恢复”不能视为完成。

本刀只输出设计和实施计划，不修改生产代码、migration、CI、测试、roadmap 或评测套件，不启动服务，不连接 MySQL、Redis、Milvus、OSS 或模型网关。健康检查与指标已在前一刀完成，告警、隐私全路径复核、限流和上线运维保持独立边界。

## 2. 现状勘察

### 2.1 备份现状总表

| 数据类 | 当前接入事实 | 当前备份手段 | 当前风险 |
| --- | --- | --- | --- |
| MySQL | JPA/MySQL 生产数据源位于 `application-prod.yml:spring.datasource.url`，生产 `ddl-auto` 为 `none` | 无。仓库未找到可执行的 `mysqldump`、binlog、PITR、备份调度或恢复脚本/配置 | 关系数据、正文/密文、事件、任务台账和对象引用均可能同时丢失；无已测 RPO/RTO |
| Milvus | `MilvusConfig` 在非 test profile 创建 `milvusClientV2`，启动三个 collection | 无。没有 collection export/import、`milvus-backup` 或快照配置 | 向量可从部分 MySQL 来源重建，但模型版本、分块和时间会造成重建漂移；直接恢复路径缺失 |
| Redis | 非 test profile 直接创建 Redisson single-server client；key 由 `yusi:` 前缀族组成 | 无。应用配置没有 RDB/AOF/PITR 或托管快照配置 | 缓存、认证安全状态、短期统计、模型运行态混在同一实例；盲目恢复或盲目清空都有风险 |
| 对象存储 | 非 test profile 创建阿里云 `OSSClient`；图片、音频和附件以 object key 由 MySQL 引用 | 无。没有 OSS versioning、inventory、跨桶/跨区域复制或对象恢复配置 | 最终媒体对象不可从 MySQL 重建；只恢复数据库会留下不可用引用 |

“无”是代码与配置审计结论，不是“尚未验证”的委婉说法。可独立复核的搜索范围为：

```powershell
rg -n -i "flyway|mysqldump|mysqlbinlog|pitr|point.?in.?time|milvus-backup|snapshot|rdb|aof|versioning|restore" `
  pom.xml src/main src/test docker-compose.yml .github docs/devops
```

该搜索会命中非数据备份内容，需按下列边界解释：`application-prod.yml:249-250` 的
`backup-rsa-public-key-spki-base64` 与 `backup-rsa-private-key-pkcs8-base64` 是用户自定义密钥的加密备份配置，不是四类数据备份；`docs/devops/gitops_proposal.md:630-633` 的 RDS 自动备份是部署建议，不是当前已配置的机制；`docker-compose.yml:4-34` 只定义应用容器、日志卷和网络，没有 MySQL、Redis、Milvus、OSS 备份卷或作业。

### 2.2 MySQL：权威关系数据

事实依据：

- `pom.xml:52-82` 引入 `spring-boot-starter-data-jpa` 和 MySQL runtime driver；未引入 Flyway runtime 依赖。
- 生产连接使用 `application-prod.yml:spring.datasource.url`，默认地址为 `jdbc:mysql://mysql:3306/yusi`；驱动、Hikari 和 `spring.jpa.hibernate.ddl-auto: none` 位于 `application-prod.yml:281-315`。
- 开发配置仍使用 MySQL，见 `application-dev.yml:260-294`；测试才使用 H2 `jdbc:h2:mem:yusi_test` 和 `ddl-auto: create-drop`，见 `application-test.yml:18-42`。
- 仓库有 `src/main/resources/db/migration/` SQL 文件，例如 `V20250321__add_image_file_table.sql:1-16`、`V20260729__complete_phase4.sql:1-8`、`V20260811__add_diary_attachment_bindings.sql:1-3`；但没有 Flyway 依赖或运行时接线。`docs/devops/gitops_proposal.md:591` 仍把引入 Flyway/Liquibase 写成建议。
- `src/main/resources/db/init.sql:7-24` 定义用户表，`:51-77` 定义日记表，`:194-319` 定义匹配、连接、产品事件和匹配画像，`:479-607` 定义人生图谱及来源证据，`:927-960` 定义中期记忆和图片文件映射。
- `init.sql:3` 与 `:1063` 只切换 `FOREIGN_KEY_CHECKS`；对 `src/main/resources/db/init.sql` 和 `src/main/resources/db/migration` 搜索 `FOREIGN KEY`/`REFERENCES` 没有命中。因此恢复校验不能声称由数据库外键自动保证，必须执行应用级孤儿引用检查。

数据性质：MySQL 是权威源，属于不可丢数据。日记、聊天消息、中期记忆、人生图谱、匹配/连接/事件、模型运行配置快照、任务执行台账、审计和对象引用不能依赖 Milvus/Redis/OSS 重建。部分摘要或向量可以再计算，但无法保证原始版本、时间、模型输出和用户可见历史完全一致。

### 2.3 Milvus：三类可重建但需要保护的派生数据

`src/main/java/com/aseubel/yusi/config/ai/MilvusConfig.java:25-44` 的 `@Profile("!test")` 配置创建 bean `milvusClientV2`，并初始化以下三个 collection：

1. `yusi_embedding_collection`：`EmbeddingBatchService.java:200-255` 删除旧日记向量、调用 embedding gateway 并写入；metadata 至少包含 `userId`、`diaryId`、分块信息、可选 `sourceRevision` 和 `entryDate`，见 `EmbeddingBatchService.java:219-246`。
2. `yusi_mid_term_memory`：`MidTermMemoryVectorService.java:24-58` 从 MySQL `MidTermMemory` 的 summary 生成 embedding 并写入，metadata 含 `userId`、`memoryId`、`matchAllowed`、`hidden`，见 `MidTermMemoryVectorService.java:37-58`。
3. `yusi_match_profile`：`MatchProfileAssemblerImpl.java:39-47` 固定 collection，`:51-93` 从 MySQL 画像来源组装并保存 `MatchProfile` 后同步，`:259-293` 写入画像文本、embedding 和 user/version metadata。

三个 collection 的通用 schema、索引和 BM25 function 在 `MilvusConfig.java:47-100`：`id` 是非自动 ID 的 VarChar 主键，另有 `text`、JSON `metadata`、指定 dimension 的 dense vector 和 sparse vector；dense 使用 HNSW/COSINE，sparse 使用 BM25 inverted index。dimension 来自 `model.embedding.dimension`，默认值见 `EmbeddingModelConfigProperties.java:7-18`，生产配置键为 `model.embedding.dimension`，见 `application-prod.yml:39`。Milvus 连接配置键为 `milvus.uri`、`milvus.token`、`milvus.host`、`milvus.port`、`milvus.username`、`milvus.password`，见 `application-prod.yml:333-339`。

数据性质：三者是“有条件可重建”的派生副本，不是唯一权威源；恢复优先使用 collection 导出，导出缺失时才从 MySQL 重建。重建依赖相同的 schema、dimension、分块规则、embedding 模型和版本，且 collection 中含用户正文/摘要和 metadata，不能因为可重建就不备份。

### 2.4 Redis：同一实例中的分层数据

连接事实：`RedisClientConfig.java:15-42` 在非 test profile 创建 Redisson single-server client，使用 `JsonJacksonCodec`；绑定前缀为 `redis.sdk.config`，见 `RedisClientConfigProperties.java:6-31`；生产连接键位于 `application-prod.yml:317-331`。应用没有在这些配置处声明 RDB、AOF、PITR 或快照保留。

固定 key 族见 `RedisKey.java:8-26`：

| key 族 | 代码事实 | 数据性质与恢复策略 |
| --- | --- | --- |
| `yusi:auth:refresh:*`、`yusi:auth:blacklist:*`、`yusi:auth:devices:*` | `RedisKey.java:12-15`；refresh/blacklist/device token 的使用见 `TokenServiceImpl.java:35-122` | 安全状态，不能无审查丢失。应恢复可信时间点；若快照不可信，宁可清空并让所有会话重新认证，不得恢复可能已撤销的 token |
| `yusi:usage:<date>` | `RedisKey.java:17`；`InterfaceUsageMonitor.java:47-69` 写入，`:80-90` 和 `:230-242` 同步 MySQL 并设置两天过期 | 短期统计和未同步增量；不是普通缓存。恢复后须与 `interface_daily_usage` 对账，明确 RPO 内可能丢失的增量 |
| `yusi:model:state:instances`、`yusi:model:state:channel` | `RedisKey.java:19-23`；`ModelStateCenter.java:47-75` 读取/恢复实例窗口，`:98-175` 更新运行状态 | 运行态，可从当前模型配置和新调用重建；channel 是 pub/sub 事件流，不承诺可恢复。旧健康窗口丢失必须在恢复记录中注明 |
| `yusi:model:runtime:config`、`yusi:model:config:channel` | `RedisKey.java:20-23`；`ModelConfigCenter.java:77-115` 优先从 MySQL 快照加载并监听 Redis，`:193-241` 写 MySQL/Redis 快照并发布 | MySQL `model_runtime_config` 是可审计权威快照，Redis 是运行副本；先恢复 MySQL，再重新发布 Redis，不把 channel 当备份 |
| `yusi:violation:count:*` | `RedisKey.java:25-26`；过期时间为 12 小时，见 `SensitiveWordUtils.java:20-23`、`:54-60` | 短期安全计数，恢复时保守保留可信快照；丢失需作为安全策略偏差记录 |
| `yusi:langchain:<memoryId>` | `PersistentChatMemoryStore.java:47-48`、`:57-88`、`:93-105`、`:153-155`；TTL 30 分钟，MySQL `chat_memory_message` 在 `:118-143` 持久化 | 可重建缓存。恢复时可清空，应用从 MySQL 重建，不把 Redis 中的聊天缓存当唯一数据 |
| `yusi:chunk:*`、`yusi:md5:*` | `OssService.java:48-51`、`:281-339`、`:567-570`；分片 24 小时、MD5 缓存 30 天 | 分片上传会话和秒传缓存，可丢失并重新上传/重建；最终 OSS 对象及 MySQL object key 不能丢失 |
| `@QueryCache`/`@UpdateCache` 生成的业务缓存 | `CacheAspect.java:44-47` 默认 key prefix `yusi:`、TTL `1s`，具体用户/日记/通知等 key 见 `DiaryServiceImpl.java:90-286`、`NotificationService.java:60-295` | 可重建缓存；恢复可选择不导入，启动后按需回填 |

因此 Redis 建议采用“全库快照但分层恢复”：缓存可以清空，认证/安全状态需恢复或全量失效，usage 需对账，模型 config 从 MySQL 重发布，pub/sub 不恢复历史消息。当前无任何已配置的 RDB/AOF/PITR 机制，不能把 Redisson client 或 Redis key 常量误当作备份能力。

### 2.5 对象存储与本地文件

- `OssProperties.java:7-28` 的配置前缀为 `yusi.oss`，包含 bucket、region、endpoint、imageFolder 和 audioFolder；生产键见 `application-prod.yml:258-267`。生产显式配置 `image-folder: yusi/images/`，没有显式 `audio-folder`，因此使用 `OssProperties.java:22-24` 的默认 `audio/`。
- `OssConfig.java:10-25` 在非 test profile 创建阿里云 `OSSClient`。
- 图片最终对象 key 由 `OssService.java:56-98` 生成，形状为 `yusi/images/{userId}/{uuid}{extension}`；音频 key 由 `OssService.java:113-145` 生成，形状为 `audio/{userId}/{uuid}{extension}`。
- `Diary.java:130-155` 和 `init.sql:69-71` 保存图片 JSON、`audio_object_key` 和 `attachment_bindings`；`ChatMemoryMessage.java:34-41` 也保存聊天图片 object key。`ImageFile.java:24-40` 与 `V20250321__add_image_file_table.sql:1-16` 保存图片 object key、用户、MD5、大小和类型。
- 分片对象 key 是 `imageFolder + "chunks/" + userId + "/" + fileMd5 + "/" + chunkIndex`，见 `OssService.java:728-738`；合并只使用 `Files.createTempDirectory("yusi-merge-")`，见 `OssService.java:423-445`，并在 `:477-490` 清理。因此本地 merge 目录不是持久化备份；分片会话和分片对象是可重建的临时状态，最终对象不是。

数据性质：最终图片、音频、附件对象和 MySQL 中的 object key 映射均不可丢失；本地临时目录、分片会话和 MD5 秒传缓存可丢失。当前没有 bucket versioning、inventory、跨桶复制或恢复配置；OSS client 能上传/下载不代表存在备份。

## 3. 备份策略与周期

初始目标是部署验收前的工程基线，不能替代实际测量：MySQL/Redis 安全状态目标 RPO 15 分钟、RTO 60 分钟；Milvus/OSS 先以每日保护和可验证恢复为最低线。容量、吞吐和保留期必须由第一次演练数据校准。

| 数据类 | 建议备份内容 | 周期与保留 | 建议工具/方式 | 保护要求 |
| --- | --- | --- | --- | --- |
| MySQL | 全库逻辑备份、binlog/PITR、schema/migration 清单和每表行数 manifest | 每日完整 dump；binlog 连续归档，目标 RPO 15 分钟；完整 dump 至少保留 30 天；migration/大批量变更前额外快照 | `mysqldump --single-transaction --routines --events --triggers --hex-blob --databases yusi`；生产可用 RDS PITR；压缩、SHA-256、加密后存到独立备份存储 | 备份账号最小权限；备份与生产凭据分离；恢复前校验 checksum 和目标时间 |
| Milvus | 三个 collection 的数据导出、schema/index/function manifest、row count、dimension、server/SDK/embedding identity | 每日一次；保留 14-30 天；embedding 模型、dimension、schema 或分块规则变更前额外导出 | 与版本匹配的 `milvus-backup` 或 collection export/import API；不把仓库现有 SDK bean 当备份工具 | 导出文件加密；metadata 低敏化只用于日志，备份本体必须按用户数据等级保护 |
| Redis | 全库 RDB；安全状态和 usage 的时间点快照；不把 pub/sub 当可恢复数据 | 每日 RDB；生产建议 AOF 或托管 PITR，安全状态目标不超过 15 分钟；保留 7-14 天；缓存不设强 RPO | Redis RDB/AOF 或云 Redis 快照/PITR；恢复前按 key 族清单分层 | 快照加密和 ACL；不在日志/manifest 输出 token、cache value、query 或正文；恢复后审查 token 是否应全量失效 |
| OSS | 最终对象、对象版本、size/hash、object-key inventory、DB 引用对账清单 | 持续启用 bucket versioning；每日 inventory/hash/size 校验；每周跨桶/跨区域复制；最终对象至少保留 30-90 天版本 | OSS Bucket Versioning、Inventory、跨区域复制/同城复制；对象清单和 DB dump 绑定同一恢复点 | KMS/服务端加密、最小 ACL、备份 bucket 隔离；删除保护和恢复演练必须在部署环境验证 |

备份 artifact 的 manifest 只记录恢复所需的元数据：`backupId`、组件、来源时间、版本、文件 checksum、大小、表/collection/key-family/object 计数、schema/index 摘要和工具版本。不得记录用户 ID 列表、token、query、正文、prompt、模型响应或对象内容摘要以外的敏感内容。

## 4. 恢复步骤设计

### 4.1 全局前置

1. 创建 incident/rehearsal 记录，记录目标恢复时间、来源 backupId、操作者和开始时间。
2. 冻结写入口、定时任务和 worker；停止会产生新 MySQL、Milvus、Redis、OSS 写入的应用实例，保留维护通道。此步骤依赖部署编排和权限，属于 deployment-only。
3. 校验备份 artifact checksum、加密密钥、工具版本和 schema/index manifest；不使用未通过校验的快照。
4. 先恢复权威 MySQL 和最终 OSS 对象，再恢复/重建 Milvus，最后分层恢复 Redis；恢复完成后才启动应用做只读 smoke。

### 4.2 MySQL 恢复

依赖：可访问备份存储、目标 MySQL 实例、恢复账号、schema/migration 清单和 binlog/PITR 链。

1. Provision 与生产隔离的目标实例或目标 database，确认字符集、时区、MySQL major version 和 InnoDB 配置一致。
2. 先恢复 schema/完整 dump，再按目标时间应用 binlog 或托管 PITR。生产 `ddl-auto: none`，恢复流程不得依赖 Hibernate 自动建表。
3. 运行 `mysqlcheck`/只读 SQL 校验表存在、主键/唯一键和每表行数；把结果写入低敏 manifest。
4. 执行应用级 orphan/invariant 查询。至少检查 user、diary、chat_memory_message、mid_term_memory、match_profile、life_graph_entity、life_graph_relation、来源证据、image_file、soul_connection、product_event 和 task_execution 的关键引用；由于当前 schema 没有实际 FK，任何非零 orphan 都阻断放流量。
5. 记录 dump 完成时间、PITR 终点和源数据时间之间的 RPO gap；通过后再向 OSS/Milvus 恢复提供同一恢复点。

### 4.3 对象存储恢复

依赖：MySQL 已恢复并可读取对象引用，且可访问正确 bucket/version/replica。

1. 从 `image_file.object_key`、`diary.images`、`diary.audio_object_key`、`diary.attachment_bindings` 和 `chat_memory_message.images` 汇总需要存在的 object key；只输出数量和缺失分类，不输出 key 内容。
2. 在目标 bucket 保留原 key 和版本语义；优先恢复对应版本，不把临时分片对象当最终对象。
3. 用 OSS HEAD/下载校验每个对象的存在、size、content type 和记录的 hash/ETag；对缺失对象生成阻断清单。
4. 对账 MySQL 引用与实际对象：引用存在但对象缺失、对象存在但无引用都要分别计数。无引用对象不能在恢复演练中自动删除。
5. 确认签名 URL 只在应用重新启动后按现有权限生成；不把旧的短期 URL 作为备份数据。

### 4.4 Milvus 恢复

依赖：MySQL 恢复完成、Milvus endpoint/token、collection manifest、embedding dimension 和兼容的 Milvus/SDK 版本。

1. 停止写入 worker，核对三 collection 名称和 schema manifest。
2. **先创建或确认 collection schema、BM25 function、dense/sparse index 和 dimension，再导入数据。** 禁止先导入到自动推断 schema 的空 collection。
3. 从 export 导入三 collection，等待索引构建并 load；校验 collection 存在、field 类型、dimension、index metric 和 row count。
4. 读取低敏 metadata 计数，核对 user/diary/memory/profile 的归属总数和 source revision 范围；不把正文或用户 ID 写入日志。
5. 如果没有 export，按固定 embedding 模型、版本、dimension 和分块规则从已恢复 MySQL 重建：日记走 `EmbeddingBatchService`，中期记忆走 `MidTermMemoryVectorService`，匹配画像走 `MatchProfileAssemblerImpl`。这是降级恢复路径，必须单列模型调用成本、耗时和可能的向量漂移，不能作为“原样恢复”。

### 4.5 Redis 恢复

依赖：MySQL/OSS/Milvus 已达到可读状态，Redis target 可写，且有可信 snapshot 时间点。

1. 恢复 RDB/AOF 到隔离 Redis；先不让应用连接生产流量。
2. 按 key family 审查：缓存（QueryCache、聊天 cache、MD5 cache）可清空；`auth` 安全状态恢复可信时间点，否则全部 token 失效并要求重新认证；`usage` 与 MySQL `interface_daily_usage` 对账；`violation` 保守保留；模型 runtime config 以 MySQL snapshot 为源重新发布。
3. 不恢复 pub/sub 历史消息；`model state` 运行窗口可接受重建，但要记录恢复后重新 warm-up 的时间。
4. 校验 key family 数量、TTL 分类和关键安全策略，不打印 key value/token。缓存可以在应用启动后按需回填，不能用缓存计数证明业务数据完整。

### 4.6 应用放流量前

1. 以维护模式启动一个实例，执行数据库读、对象 HEAD、Milvus query、Redis auth/config 读取和关键后台任务 readiness smoke。
2. 检查应用日志和指标只输出低敏计数/分类；不把恢复 artifact、用户正文或 token 发送到日志/指标。
3. 保持 worker/scheduler 停止，确认读取路径和安全策略通过后再恢复任务；先小流量，再解除维护入口。
4. 记录恢复完成时间、失败项、残余差异、RPO gap 和 operator sign-off。

## 5. 恢复演练方案

### 5.1 本地/测试可执行部分

本地演练只使用脱敏合成数据，例如 `backup-rehearsal-user-001`、`backup-rehearsal-diary-001` 和固定 hash；不写自然语言正文、query、prompt、token 或真实 object content。现有 `application-test.yml:18-42` 的 H2 是内存库、`create-drop`、`sql.init.mode=never`，适合验证应用级 invariant，不代表 MySQL dump 兼容性。

实施时提供一个显式传入 dump/manifest 的 `ops/backup/restore-rehearsal.ps1`，在用户选择的隔离 MySQL 8 实例或临时库上执行：

1. 校验 dump/manifest 的 SHA-256、来源时间、schema/migration 清单和预计表计数。
2. 向临时 database 恢复 dump；若有 binlog，恢复到指定测试时间点。
3. 执行关键表行数对比和应用级 orphan 查询，至少包括：

```sql
SELECT COUNT(*) AS orphan_diary_user
FROM diary d LEFT JOIN `user` u ON u.user_id = d.user_id
WHERE u.user_id IS NULL;

SELECT COUNT(*) AS orphan_memory_user
FROM mid_term_memory m LEFT JOIN `user` u ON u.user_id = m.user_id
WHERE u.user_id IS NULL;

SELECT COUNT(*) AS orphan_profile_user
FROM match_profile p LEFT JOIN `user` u ON u.user_id = p.user_id
WHERE u.user_id IS NULL;

SELECT COUNT(*) AS orphan_relation_endpoint
FROM life_graph_relation r
LEFT JOIN life_graph_entity s ON s.id = r.source_id
LEFT JOIN life_graph_entity t ON t.id = r.target_id
WHERE s.id IS NULL OR t.id IS NULL;

SELECT COUNT(*) AS orphan_connection_match
FROM soul_connection c LEFT JOIN soul_match m ON m.id = c.match_id
WHERE m.id IS NULL;
```

4. 用应用级校验器读取 object key 字段并对 OSS 执行 HEAD；输出对象总数、缺失数、size/hash 不一致数。
5. 用 H2 测试 profile 运行同一组低敏 invariant 查询的合成数据测试，证明校验器会拒绝 orphan 和行数不一致；测试不能声称已经恢复了真实 MySQL dump。
6. 对 Redis 只验证 snapshot manifest 的 key-family 计数、TTL 分类和安全恢复决策；对 Milvus 只在拥有真实测试 endpoint 的环境执行 collection schema/count/dimension 校验，不能用 Mockito 或空 collection 伪造恢复 PASS。

### 5.2 真实依赖的演练边界

本地 H2、Redis mock、Milvus mock 和 OSS mock 不能证明真实备份介质、权限、网络、对象版本、索引重建或 RTO。以下项目必须使用测试/预生产或生产等价部署记录，不能由本地测试代替：

- 真实 MySQL dump/PITR/binlog 恢复和大表耗时；
- 真实 Milvus 三 collection export/import、schema/function/index/load 和向量查询；
- 真实 Redis RDB/AOF/托管快照恢复、token/blacklist 决策和未同步 usage 对账；
- 真实 OSS bucket versioning、对象版本恢复、inventory/hash 对账和跨桶/跨区域复制；
- KMS/备份加密、备份存储 ACL、恢复账号、网络隔离和凭证轮换；
- 生产流量冻结、scheduler/worker 停止、维护入口、DNS/Ingress 切换和 readiness 放流量；
- 大数据量下的容量、吞吐、恢复耗时、保留周期和实际 RPO/RTO。

## 6. RTO/RPO 演练记录模板

每次演练保存一份不含用户正文和密钥的记录：

```markdown
# Yusi Backup Restore Rehearsal

- rehearsalId:
- environment:
- operator:
- reviewer:
- sourceBackupId:
- sourceDataTimestampUtc:
- targetRecoveryTimestampUtc:
- plannedRpoMinutes: 15
- plannedRtoMinutes: 60
- startedAtUtc:
- recoveryCompletedAtUtc:
- elapsedMinutes:
- rpoGapMinutes:

## Component Results

| component | artifact/version | startedAtUtc | completedAtUtc | status | row/object/vector/key count check | integrity result |
| --- | --- | --- | --- | --- | --- | --- |
| MySQL | | | | PASS/FAIL | | |
| OSS | | | | PASS/FAIL | | |
| Milvus | | | | PASS/FAIL | | |
| Redis | | | | PASS/FAIL | | |

## Integrity Checks

- MySQL key-table counts equal manifest: PASS/FAIL
- MySQL application-level orphan counts are zero: PASS/FAIL
- OSS referenced objects missing: 0
- OSS size/hash mismatches: 0
- Milvus schema/dimension/index/count checks: PASS/FAIL/DEPLOYMENT-ONLY
- Redis security/cache classification and usage reconciliation: PASS/FAIL
- Read-only application smoke: PASS/FAIL/DEPLOYMENT-ONLY

## Deviations and Sign-off

- residual differences:
- failed or skipped checks:
- corrective action owner and due date:
- RPO/RTO accepted: YES/NO
- operator sign-off:
- reviewer sign-off:
```

`recoveryCompletedAtUtc - startedAtUtc` 是实际 RTO；`targetRecoveryTimestampUtc - sourceDataTimestampUtc` 是实际 RPO gap。没有时间戳、完整性结果和签字的“恢复成功”不计入路线图验收。

## 7. 风险、决策与边界

1. 当前最大风险是四类数据都没有已落地备份机制，且没有真实 RTO 基线；必须先落地独立、加密、可读回的 artifact，再谈上线恢复承诺。
2. MySQL 没有 Flyway runtime wiring，迁移顺序和 schema 版本需要由部署 runbook/数据库变更台账承担；本刀不补 Flyway，也不修改 migration。
3. Milvus 虽可重建，但重建不是原样恢复；如果没有 collection export，报告必须标记为“derived rebuild”，并把模型/版本漂移作为残余风险。
4. Redis 不能简单地“一律恢复”或“一律清空”：认证安全状态、usage 和 model config 要分别决策，缓存和 pub/sub 可以重建/丢弃。
5. OSS 与 MySQL 必须绑定同一恢复记录，否则数据库成功恢复仍可能产生媒体断链。
6. 本设计不实现告警、不新增 Micrometer 指标、不复用健康检查来伪造备份成功，也不修改 roadmap。路线图 L631-L632 只有在真实演练记录包含 RTO 和完整性结果后才具备勾选依据。
