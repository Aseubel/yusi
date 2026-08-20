# Yusi 数据备份与恢复运行手册

- 状态：实施基线，待真实环境演练
- 日期：2026-08-20
- 关联设计：[数据备份与恢复设计](../specs/2026-08-20-yusi-backup-restore-design.md)
- 关联路线图：`docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md:631-632`

## 1. 当前状态

代码与配置审计结论：MySQL、Milvus、Redis 和 OSS 当前均没有已接入生产的备份调度、快照导出、真实恢复机制或演练记录；本刀新增的 `ops/backup/*` wrapper 只做 manifest/参数契约和 `DEPLOYMENT-ONLY` 标记，不连接真实外部依赖。`backup-rsa-*` 是用户自定义密钥的加密备份配置，不是四类数据备份；GitOps 文档中的 RDS 自动备份是部署建议，不能作为现状证据。

在真实备份介质、独立权限、加密存储和恢复演练完成前，不得把本手册中的建议命令标记为生产 PASS，也不得勾选路线图 L631-L632。

## 2. Manifest 合同

所有组件 artifact 必须配套 `ops/backup/backup-manifest.schema.json`。manifest 只包含 component、时间、版本、checksum、大小、固定计数、保留级别和恢复点；不得包含 user ID 列表、query、正文、token、完整 object key 或模型内容。

允许的 component 只有 `mysql`、`milvus`、`redis`、`oss`；`artifactSha256` 必须是 64 位十六进制；schema version 当前为 `v1`。checksum 不通过时不得恢复。

## 3. 恢复顺序

1. 创建演练/事故记录，记录 backupId、源数据时间和开始时间。
2. 冻结应用写入、scheduler 和 worker；此步骤需要部署权限，属于 deployment-only。
3. 校验 manifest checksum、加密密钥、工具版本和 schema/index manifest。
4. 恢复 MySQL schema/data/PITR，并执行关键表行数和应用级 orphan 校验。
5. 恢复 OSS 最终对象及版本，按 MySQL object-key 引用做存在性、size/hash 对账。
6. 先确认 Milvus schema/function/index，再导入三个 collection；没有 export 时显式标记 `derived-rebuild`。
7. 分层恢复 Redis：缓存可清空，auth 快照不可信时全量 token 失效，usage 与 MySQL 对账，model runtime config 从 MySQL 重发布，pub/sub 不恢复。
8. 单实例只读 smoke 和 readiness 验证通过后，小流量放行，再恢复后台任务。

MySQL 逻辑备份由 `ops/backup/mysql-backup.ps1` 生成 dump 和低敏 manifest；数据库名、输出目录、host 和 user 必须显式传入或来自部署环境，密码只从 `YUSI_MYSQL_PASSWORD` 进入进程环境。恢复使用 `ops/backup/restore-rehearsal.ps1`，`TargetDatabase` 是必填参数且拒绝 `yusi` 等生产/系统库名；未显式指定 `-Execute` 时只做 artifact 校验并输出 `DEPLOYMENT-ONLY`。

## 4. 部署执行边界

以下检查不能由 H2、mock、空 collection 或配置读取替代，必须记录 `DEPLOYMENT-ONLY` 或真实结果：

- MySQL dump/binlog/PITR 恢复、大表耗时和容量；
- Milvus collection export/import、schema/index/load 和向量校验；
- Redis RDB/AOF/PITR 恢复、token/blacklist 安全处置和 usage 对账；
- OSS versioning、inventory、对象版本恢复和复制；
- KMS、ACL、恢复凭据、网络隔离、流量冻结、readiness 放流量和实际 RPO/RTO。

## 5. 演练记录

使用设计文档 §6 的模板记录 `startedAtUtc`、`recoveryCompletedAtUtc`、elapsed、RPO gap、四类组件状态、行数/对象/向量/key-family 完整性结果、偏差、operator 和 reviewer sign-off。没有实际时间戳、完整性结果和签字，不得写恢复 PASS。
