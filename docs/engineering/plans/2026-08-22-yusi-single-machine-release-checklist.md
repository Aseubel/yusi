# Yusi 单机上线验收清单

> 状态：repo-contract 清单草稿，尚未执行部署验收
>
> 责任人：部署机责任人（用户本人）
>
> 本文只整合 Phase 5 已批准设计、计划和 runbook 中的 deployment-only 项，不新增门槛。当前所有项目初始状态均为 `NOT_RUN`。本轮不运行 Maven、不启动服务、不执行部署命令、不修改 roadmap。

## 1. 使用边界与证据字段

当前生产形态只有单机 Compose。K8s/GitOps、灰度、蓝绿、多副本和镜像切回能力属于搁置蓝图，不计入本清单的单机 PASS。

每个项目由部署机责任人执行并写入外部 release/incident record。证据字段只记录低敏引用、分类、计数、状态和时间；禁止写入凭据值、webhook URL、内网地址、userId、query、正文、token、完整 object key、异常正文或原始响应。

按上线运维修订版设计 §9 使用以下字段；每个项目只填写相关字段，未执行保持 `NOT_RUN` 或空值：

```text
release_sha=<immutable-commit-sha>
previous_release_sha=<historical-reference-or-empty>
gitops_change_ref=<not_applicable_single_machine|low-sensitivity-change-reference>
incident_ref=<opaque-reference-or-empty>
deployment_status=<NOT_RUN|PASS|BLOCKED>
rollout_mode=<single_machine_observation|rolling|canary|blue-green|NOT_RUN>
release_observation=<readiness-dependency-smoke-alert-window|NOT_RUN>
readiness_result=<UP|DOWN|NOT_RUN>
dependency_result=<classified-counts-only>
model_failure_window=<fixed-window-or-NOT_RUN>
task_backlog_result=<classified-counts-only>
budget_denial_result=<classified-counts-only>
feishu_delivery_result=<mock-contract-only|deployment-only|NOT_RUN>
backup_restore_rto=<elapsed-or-NOT_RUN>
data_integrity_result=<PASS|BLOCKED|NOT_RUN>
rollback_result=<not_available_single_machine|PASS|NOT_RUN|NOT_REQUIRED>
operator_role=<role>
reviewer_role=<role>
observed_at_utc=<timestamp>
```

`privacy_residuals` 与 `migration_status` 是本清单的扩展字段（不在设计 §9 模板内），
仅用于 OPS-06/OPS-10 与 AUTHZ-TRACE-05，初始值一律 `NOT_RUN`。

`single_machine_observation` 只表示 Compose `force-recreate` 后完成 readiness、依赖、只读 smoke 和完整告警窗口；它不是灰度或回滚结果。所有真实环境项目在证据产生前均保持 `NOT_RUN`。

## 2. 单机发布顺序

来源：上线运维修订版设计 §5.1-§5.3、实施计划 Task 1-2；实际链路事实为 `.github/workflows/build_deploy.yml:1-27` 与 `rebuild.sh:148-183`。

1. **发布前记录**：确认 release SHA、Compose/rebuild 操作引用、构建或镜像身份（若外部记录可提供）、配置/Secret 版本、schema/migration 状态和责任槽位。`build_deploy.yml` 手动触发本身不是测试证据；发布前 CI 合同另按计划核对 `.github/workflows/deploy_k8s.yml:29-58`。
2. **单机重建**：由责任人在部署机执行批准的 `rebuild.sh` 流程，完成 `docker compose up -d --force-recreate --remove-orphans`。记录操作引用和状态，不记录原始日志或内部地址。
3. **观察一：readiness**：记录 readiness 是否 `UP`、观察时间和低敏状态分类。
4. **观察二：dependency_health**：记录 MySQL、Redis、Milvus、模型网关和关键任务的固定分类状态与计数；不记录响应正文、SQL、key 或模型内容。
5. **观察三：只读 smoke**：执行批准的关键只读检查，记录组件、操作、计数和状态，不把 smoke 响应正文写入 release record。
6. **观察四：告警窗口**：覆盖一个完整 30 秒评估周期，按 `AlertPolicy` readiness、模型失败、任务积压和预算拒绝初始阈值观察；阈值必须标记“初始值，待生产调优”。
7. **阻断规则**：readiness 持续 DOWN、依赖完整性异常、模型/任务/预算初始阈值触发、Secret 缺失或任一 deployment-only 项未完成时，停止继续发布或重建，状态保持 `BLOCKED` 或 `NOT_RUN`。

## 3. OPS-01~11 单机清单

| 编号 | 验收动作或观察点 | 证据字段（按 §9 相关子集） | 责任槽位 | 初始状态 | 出处 |
| --- | --- | --- | --- | --- | --- |
| OPS-01 | 手动触发 `build_deploy.yml`，在部署机完成 `rebuild.sh` force-recreate；依次记录 readiness、`dependency_health`、关键只读 smoke 和一个 30 秒告警窗口。灰度、蓝绿、多副本和镜像回滚均不填 PASS。 | `release_sha`; `deployment_status`; `rollout_mode`; `release_observation`; `readiness_result`; `dependency_result`; `model_failure_window`; `task_backlog_result`; `budget_denial_result`; `operator_role`; `reviewer_role`; `observed_at_utc` | release operator；platform/SRE | `NOT_RUN` | 上线运维修订版设计 §7 OPS-01；§5.1；实施计划 Task 2 |
| OPS-02 | 在 Secret manager/部署环境确认 `YUSI_RATE_LIMIT_HMAC_SECRET` 已注入，不读取或记录值；在隔离窗口验证 subject-scoped 限流缺失该 Secret 时 fail-closed，不得无限放行。 | `release_sha`; `deployment_status`; `dependency_result`; `incident_ref`; `operator_role`; `reviewer_role`; `observed_at_utc` | security/privacy；platform/SRE | `NOT_RUN` | 上线运维修订版设计 §7 OPS-02；限流设计 §7.1、§8.2；roadmap `:650-653` |
| OPS-03 | 从允许的 probe/scraper 身份观察管理端口 20611 的 readiness 与 Prometheus 抓取；从未授权网络路径确认不可达；确认只暴露 `health,prometheus`、`show-details: never`，不暴露 wildcard actuator 端点。 | `release_sha`; `deployment_status`; `readiness_result`; `dependency_result`; `incident_ref`; `operator_role`; `reviewer_role`; `observed_at_utc` | platform/SRE | `NOT_RUN` | 上线运维修订版设计 §7 OPS-03；健康指标设计 §4.3；roadmap `:622-626` |
| OPS-04 | 按备份 runbook 使用非生产隔离目标执行 MySQL dump、checksum、restore rehearsal、关键行数/外键/orphan/invariant 校验；记录开始时间、完成时间和 RTO。禁止使用生产库名作为恢复目标。 | `release_sha`; `deployment_status`; `backup_restore_rto`; `data_integrity_result`; `incident_ref`; `operator_role`; `reviewer_role`; `observed_at_utc` | DBA/backup | `NOT_RUN` | 上线运维修订版设计 §7 OPS-04；`docs/engineering/runbooks/yusi-backup-restore-runbook.md:8-12,33-45`；备份设计 §5-§6；roadmap `:636-637` |
| OPS-05 | 在隔离环境执行 Milvus collection schema/index/load 与向量检查、单机 Redis RDB/AOF/key-family 检查、OSS version/inventory/reference 对账；脚本 wrapper 的 `DEPLOYMENT-ONLY` 输出不能单独转为 PASS。 | `release_sha`; `deployment_status`; `dependency_result`; `data_integrity_result`; `backup_restore_rto`; `operator_role`; `reviewer_role`; `observed_at_utc` | data platform；DBA/backup | `NOT_RUN` | 上线运维修订版设计 §7 OPS-05；`ops/backup/milvus-backup.ps1:8-19`、`redis-backup.ps1:8-23`、`oss-inventory.ps1:8-15`；备份设计 §5.2；roadmap `:636-637` |
| OPS-06 | 按隐私 runbook 做真实账号删除与 worker 竞态演练：核对 MySQL、三类 Milvus collection、Redis key 族、OSS 对象、worker 不重建、备份 artifact 保留期和第三方残留结论。 | `release_sha`; `deployment_status`; `privacy_residuals`; `data_integrity_result`; `backup_restore_rto`; `incident_ref`; `operator_role`; `reviewer_role`; `observed_at_utc` | security/privacy；data platform；DBA/backup | `NOT_RUN` | 上线运维修订版设计 §7 OPS-06；`docs/engineering/runbooks/yusi-account-deletion-privacy-audit-runbook.md:30-74`；隐私设计 §6.3、§7；roadmap `:638-643` |
| OPS-07 | 按限流 runbook 执行真实 HTTP/SSE/multipart/gateway 压测与字节/并发检查；观察单机 Redis 故障时 bounded local fallback 或 fail-closed；核对供应商 quota、管理端口 20611 allowlist、WebSocket/gRPC 内部认证边界。 | `release_sha`; `deployment_status`; `dependency_result`; `model_failure_window`; `data_integrity_result`; `incident_ref`; `operator_role`; `reviewer_role`; `observed_at_utc` | SRE；model platform | `NOT_RUN` | 上线运维修订版设计 §7 OPS-07；`docs/engineering/runbooks/yusi-rate-limit-admission-runbook.md:45-63`；限流设计 §8.2；roadmap `:644-653` |
| OPS-08 | 通过 Secret manager 注入 `YUSI_ALERT_FEISHU_ENABLED`、`YUSI_ALERT_FEISHU_WEBHOOK_URL`、`YUSI_ALERT_FEISHU_SIGNING_SECRET`；核对四类告警真实送达、接收人确认、30 分钟抑制、恢复通知和失败重试。默认开关关闭状态先记录，不把 mock 调用写成送达。 | `release_sha`; `deployment_status`; `feishu_delivery_result`; `model_failure_window`; `task_backlog_result`; `budget_denial_result`; `incident_ref`; `operator_role`; `reviewer_role`; `observed_at_utc` | on-call；security/privacy | `NOT_RUN` | 上线运维修订版设计 §7 OPS-08；告警设计 §5-§8；roadmap `:627-635` |
| OPS-09 | 在隔离/生产演练窗口分别制造或观察 MySQL、Redis、Milvus、模型供应商和关键 worker 故障；记录 readiness、降级行为、告警分类、恢复确认。模型健康探针和本地 mock 不作为供应商端到端 PASS。 | `release_sha`; `deployment_status`; `readiness_result`; `dependency_result`; `model_failure_window`; `task_backlog_result`; `incident_ref`; `operator_role`; `reviewer_role`; `observed_at_utc` | model platform；platform/SRE | `NOT_RUN` | 上线运维修订版设计 §6、§7 OPS-09；健康指标设计 §4；告警设计 §8.2；roadmap `:622-626,627-635` |
| OPS-10 | 核对已应用 schema/migration、`ddl-auto: none` 和无 Flyway runtime 边界；涉及 schema 变更时记录 expand/contract 兼容性、前向修复或经 DBA 批准的备份恢复路径，不执行未经批准的反向 SQL。 | `release_sha`; `deployment_status`; `migration_status`; `data_integrity_result`; `rollback_result`; `incident_ref`; `operator_role`; `reviewer_role`; `observed_at_utc` | DBA/backup；release operator | `NOT_RUN` | 上线运维修订版设计 §5.5、§7 OPS-10；`application-prod.yml:313-321`；备份设计 `:40-43,265` |
| OPS-11 | 填写低敏 release record 与 incident 模板，确认 release operator、platform/SRE、DBA/backup、model platform、security/privacy、on-call、release reviewer 责任槽位和处置权限；确认阻断、前向修复/停机维护和复盘引用可填写。 | `release_sha`; `incident_ref`; `deployment_status`; `release_observation`; `rollback_result`; `operator_role`; `reviewer_role`; `observed_at_utc` | on-call；release reviewer | `NOT_RUN` | 上线运维修订版设计 §8-§9、§7 OPS-11；实施计划 Task 1、Task 6-7；备份 runbook `:43-45` |

## 4. Authz/Trace 五项补充账本

以下五项来自 authz/trace 设计 §8，独立于本地 `application-invariant-only` 与 `mock-contract-only` 证据；本地测试不能替代真实环境结论。

| 编号 | 验收动作或观察点 | 证据字段（按 §9 相关子集） | 责任槽位 | 初始状态 | 出处 |
| --- | --- | --- | --- | --- | --- |
| AUTHZ-TRACE-01 | 使用两个真实测试账号和实际 JWT，经真实反向代理访问 diary、match、room、image、lifegraph、notification、plaza 以及 admin/model/prompt 命名空间；核对水平/垂直/匿名访问均得到固定授权结果，不能只凭本地 service mock。 | `release_sha`; `deployment_status`; `data_integrity_result`; `incident_ref`; `operator_role`; `reviewer_role`; `observed_at_utc` | security/privacy；release reviewer | `NOT_RUN` | authz/trace 设计 §3、§6.1、§8；实施计划 Task 7 Step 4 |
| AUTHZ-TRACE-02 | 从真实代理和管理网络路径验证 20611 allowlist、代理 header 清理、CORS/CSRF 与 actuator 路径保护；未授权来源不得访问管理面或改变授权语义。 | `release_sha`; `deployment_status`; `readiness_result`; `dependency_result`; `incident_ref`; `operator_role`; `reviewer_role`; `observed_at_utc` | platform/SRE；security/privacy | `NOT_RUN` | authz/trace 设计 §8；健康指标设计 §4.3；上线运维修订版设计 §7 OPS-03 |
| AUTHZ-TRACE-03 | 通过真实 STOMP CONNECT、topic 订阅/发布和日记语音 native handshake 验证 token、参与者/成员关系、topic 授权与并发断连；不得以 158 个 HTTP mapping 覆盖 WebSocket。 | `release_sha`; `deployment_status`; `dependency_result`; `incident_ref`; `operator_role`; `reviewer_role`; `observed_at_utc` | security/privacy；platform/SRE | `NOT_RUN` | authz/trace 设计 §2、§8；限流设计 §8.2；实施计划 Task 7 Step 4 |
| AUTHZ-TRACE-04 | 在真实日志采集、集中检索和保留期链路中制造脱敏测试事件，检查 formatted message、throwable/message/stack 和 retention 结果；ListAppender/mock 只能记 `mock-contract-only`。 | `release_sha`; `deployment_status`; `data_integrity_result`; `incident_ref`; `operator_role`; `reviewer_role`; `observed_at_utc` | security/privacy；platform/SRE | `NOT_RUN` | authz/trace 设计 §5.2、§8；实施计划 Task 7 Step 4；既有低敏日志政策设计 |
| AUTHZ-TRACE-05 | 在真实 Milvus、Redis、OSS、模型网关和 worker 环境核对跨用户删除/Trace 关联后的残留为零或有批准解释；确认异步 worker 不重建已删除用户数据，不能用 H2/Mockito 或空 collection 冒充。 | `release_sha`; `deployment_status`; `dependency_result`; `privacy_residuals`; `data_integrity_result`; `incident_ref`; `operator_role`; `reviewer_role`; `observed_at_utc` | security/privacy；data platform；model platform | `NOT_RUN` | authz/trace 设计 §5、§8；隐私设计 §6.3；上线运维修订版设计 §7 OPS-05/06 |

## 5. 残余风险与不计入项

1. **单机没有镜像回滚能力**：`rebuild.sh:69` 会清理悬空镜像，`:179-181` 会清理旧 `yusi` 镜像，当前路径没有可验证的切回命令。`rollback_result` 应诚实记录 `not_available_single_machine`；不得写成“无回滚风险”或伪造回滚 PASS。处置只能采用前向修复并重新重建，或停机维护。
2. **AUTHZ-CANDIDATE-001 待业务确认**：`SoulPlazaController`/`ResonanceSignalService` 的 `cardId` 与 `toUserId` 关系尚未得到产品语义确认。真实渗透应记录观察结果，不能擅自把候选项写成已修复或已通过。
3. **K8s 搁置蓝图不计入单机验收**：K8s/GitOps rollout、灰度/蓝绿、多副本一致性、GHCR digest、外部 overlay、Ingress 权重和 K8s 镜像回退均不作为本轮 PASS 或阻断单机路径的新增项目；未来恢复该部署形态时另行验收。
4. **本地证据与生产证据分层**：健康/指标、告警规则、H2/Mockito、备份脚本静态契约和本地 authz/Trace 测试只能证明 repo/application/mock contract；真实依赖故障、送达、渗透、日志采集和恢复 RTO 在本清单产生证据前保持 `NOT_RUN`。

## 6. 分组统计与引用核对

### 6.1 清单统计

| 分组 | 项目 | 数量 | 初始状态 |
| --- | --- | ---: | --- |
| 单机上线运维聚合 | OPS-01~OPS-11 | 11 | 全部 `NOT_RUN` |
| authz/Trace deployment-only 补充 | AUTHZ-TRACE-01~05 | 5 | 全部 `NOT_RUN` |
| **合计** | **Phase 5 deployment-only 清单** | **16** | **全部 `NOT_RUN`** |

既有四刀账本的覆盖位置：可观测/健康与依赖连通性由 OPS-01、OPS-03、OPS-09 覆盖；飞书告警 Secret/真实送达由 OPS-08 覆盖；备份恢复真实演练与 RTO 由 OPS-04、OPS-05 覆盖；隐私真实依赖残留由 OPS-06 与 AUTHZ-TRACE-05 覆盖；限流 HMAC Secret 注入由 OPS-02，限流其余真实验证由 OPS-07 覆盖。

### 6.2 引用出处核对表

| 来源范围 | 本清单引用位置 | 核对结论 |
| --- | --- | --- |
| 单机 Compose、观察窗口、回滚边界、OPS-01~11、证据字段 | `docs/engineering/specs/2026-08-20-yusi-launch-ops-design.md:109-246`；`docs/engineering/plans/2026-08-20-yusi-launch-ops-implementation-plan.md:45-185` | 逐项复用 OPS 编号和单机语义；未引入 K8s PASS 要求 |
| 健康/指标、管理端口、Prometheus、依赖/worker 真实证据 | `docs/engineering/specs/2026-08-19-yusi-health-metrics-observability-design.md:46-76,138-151`；roadmap `:622-626` | 映射至 OPS-03、OPS-09 |
| 四类告警、Secret 注入、真实飞书送达与接收人 | `docs/engineering/specs/2026-08-20-yusi-alert-channel-design.md:140-156,197-247`；roadmap `:627-635` | 映射至 OPS-08，并保留 mock/真实送达边界 |
| MySQL/Milvus/Redis/OSS 备份恢复、RTO | `docs/engineering/specs/2026-08-20-yusi-backup-restore-design.md:158-229`；`docs/engineering/runbooks/yusi-backup-restore-runbook.md:8-45` | 映射至 OPS-04、OPS-05 |
| 账号删除外部副本、worker 竞态、残留与保留政策 | `docs/engineering/specs/2026-08-20-yusi-account-deletion-privacy-audit-design.md:166-211`；`docs/engineering/runbooks/yusi-account-deletion-privacy-audit-runbook.md:30-74` | 映射至 OPS-06、AUTHZ-TRACE-05 |
| 限流 HMAC、真实压测、Redis 故障、配额、20611、WebSocket/gRPC | `docs/engineering/specs/2026-08-21-yusi-rate-limit-admission-review-design.md:241-306`；`docs/engineering/runbooks/yusi-rate-limit-admission-runbook.md:45-63` | 映射至 OPS-02、OPS-07 |
| 双用户渗透、管理/代理边界、WebSocket、真实日志采集、跨用户依赖残留 | `docs/engineering/specs/2026-08-22-yusi-authz-trace-boundary-design.md:145-203` | 映射至 AUTHZ-TRACE-01~05 |

本文不修改 roadmap、不提交代码。待部署机责任人逐项产生低敏证据并由 release reviewer 复核后，再决定是否更新 roadmap L654；本清单本身不构成上线 PASS。
