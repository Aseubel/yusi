# Yusi 上线运维准备实施计划

> **For agentic workers:** 本计划用于评审通过后的 deployment-only 执行；当前设计阶段不得启动服务、运行 Maven、修改生产代码/配置、修改 CI、migration 或 roadmap。仓库指令禁止子 agent 和 auto-review；每个任务必须保留低敏证据并在外部部署环境独立验收。

**目标：** 基于现有 SHA/GitOps 链路建立可审计的最小滚动/灰度发布、依赖降级与恢复动作、上线前置门槛和低敏应急流程。

**架构：** 发布对象由 backend/frontend/MCP 的 commit SHA 或 digest、GitOps 配置变更引用、配置/Secret 版本和 schema 台账组成。当前仓库只负责构建并把 SHA 写入外部 `yusi-infra`；K8s rollout、流量权重、Secret manager、依赖演练和告警接收均在 deployment-only 环境完成。运行时沿用已有 readiness、ModelStateCenter、bounded local limiter、ModelBudgetAdmission、AlertEvaluator 和 backup/privacy runbook，不新增业务降级旁路。

**技术栈：** GitHub Actions、GHCR、Kustomize、外部 Kubernetes/GitOps 控制面、Spring Boot Actuator/Micrometer、Redis/Redisson、Milvus、OSS、mysqldump/MySQL restore、飞书 webhook Secret manager；本地只做静态文档审查，不执行部署命令。

## 全局约束

- 本计划不修改 `src/main`、`src/test`、`src/main/resources`、`pom.xml`、`.github/workflows`、`ops/backup`、migration、评测/QualityGatePolicy 或 roadmap。
- 当前仓库最终只新增本计划和对应设计文档；外部 `yusi-infra`、Kubernetes、Secret manager、数据库、Redis、Milvus、OSS、供应商和飞书环境必须在部署方变更系统中审计。
- backend/frontend/MCP 必须使用 commit SHA/digest 作为发布和回滚引用；`latest` 不能作为唯一发布对象。
- 所有阈值标记“初始值，待生产调优”：readiness DOWN 2 分钟；模型 5 分钟、失败率 20%、最小 20 次；任务 15/60 分钟并持续 5 分钟；预算拒绝 5 分钟至少 10 次；告警 fingerprint 抑制 30 分钟、最多 3 次发送尝试。来源 `AlertPolicy.java:20-32`。
- 不得把本地 mock、H2、空 collection、静态 YAML、健康探针存在性或 workflow 成功写成生产送达、真实恢复、真实配额或灰度 PASS。
- 凭据只允许通过环境变量/Secret 注入：`YUSI_RATE_LIMIT_HMAC_SECRET`、`YUSI_ALERT_FEISHU_ENABLED`、`YUSI_ALERT_FEISHU_WEBHOOK_URL`、`YUSI_ALERT_FEISHU_SIGNING_SECRET` 以及已有数据库/Redis/Milvus/OSS/模型 Secret；文档和日志不记录值。
- 低敏证据只允许 opaque incident/release ref、固定组件/操作、计数、窗口、分类、时间、SHA/digest 和状态；禁止 userId、query、正文、token、模型响应、完整 object key、内网地址、URL 值和异常 message/stack。

## File Map

### 当前新增文件

- Create: `docs/engineering/specs/2026-08-20-yusi-launch-ops-design.md`：事实勘察、架构选择、降级矩阵、回滚边界和 deployment-only 清单。
- Create: `docs/engineering/plans/2026-08-20-yusi-launch-ops-implementation-plan.md`：下述部署执行步骤、证据模板和责任槽位。

### 评审阶段只读文件

- Inspect: `.github/workflows/build_deploy.yml:1-27`、`.github/workflows/deploy_k8s.yml:1-151`。
- Inspect: `Dockerfile:1-27`、`docker-compose.yml:4-34`、`rebuild.sh:7-188`。
- Inspect: `frontend/Dockerfile:1-40`、`frontend/docker-compose.yml:1-24`、`frontend/rebuild.sh:7-190`、`frontend/nginx.conf:32-65`。
- Inspect: `src/main/resources/application.yml:10-48`、`application-prod.yml:15-40,287-345`。
- Inspect: `RateLimiterAspect.java:58-168`、`RateLimiterSubjectEncoder.java:17-58`、`ModelBudgetAdmission.java:105-150`、`ModelProxyFactory.java:233-422`、`ModelStateCenter.java:77-135`。
- Inspect: `AlertPolicy.java:20-32`、`AlertScheduler.java:27-123,169-185`、`FeishuAlertNotifier.java:37-79`、`FeishuAlertProperties.java:13-33`。
- Inspect: `ops/backup/*`、`yusi-backup-restore-runbook.md`、`yusi-rate-limit-admission-runbook.md`、`yusi-account-deletion-privacy-audit-runbook.md`。
- Inspect: roadmap `docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md:622-654`。

## Task 1：建立 release inventory 和责任矩阵

**Files:**
- Read: `.github/workflows/deploy_k8s.yml:93-151`。
- Record externally: release/change record in the deployment system; no repository file is changed.

**Interfaces:**
- Consumes: successful CI verification and three image SHA/digest references.
- Produces: one low-sensitivity release record containing release SHA, previous verified SHA, GitOps change reference, configuration/Secret version references, schema/migration state, operator role and reviewer role.

- [ ] **Step 1: Verify CI evidence.** Confirm the workflow's backend test and frontend test/type-check jobs have passed for the release commit, using `.github/workflows/deploy_k8s.yml:29-58` as the contract. Do not use a local Maven run as the production verification record.
- [ ] **Step 2: Record immutable image references.** Record backend/frontend/MCP commit SHA and registry digest from the build output. Keep the previous verified SHA/digest available; do not resolve a rollback target from `latest`.
- [ ] **Step 3: Record migration compatibility.** Compare the release's database change set with `application-prod.yml:313-321` (`ddl-auto: none`) and the backup design's no-Flyway-runtime fact. Mark destructive or incompatible schema work as a release blocker until DBA signs the expand/contract or restore/forward-fix path.
- [ ] **Step 4: Assign role slots.** Fill `release operator`, `platform/SRE`, `DBA/backup`, `model platform`, `security/privacy`, `on-call` and `release reviewer` in the external record. A named individual is not required in this document, but every slot must have an accountable deployment identity before promotion.

## Task 2：核验外部 GitOps rollout 与最小灰度

**Files:**
- Read-only source: `.github/workflows/deploy_k8s.yml:124-151` and `docs/devops/gitops_proposal.md:154-232`.
- Deployment-only files: external `yusi-infra` production overlay, Deployment/Service/Ingress/rollout resources.

**Interfaces:**
- Consumes: Task 1 release inventory and old/new SHA references.
- Produces: rollout evidence showing resource strategy, probes, replica behavior, traffic split (if any), and a reversible GitOps change.

- [ ] **Step 1: Verify resource existence.** In the deployment environment, inspect the external overlay and record whether backend/frontend/MCP Deployment, Service, Ingress and rollout resources exist. The current repository does not prove these resources; absence of a canary object is a blocker for claiming true gray release.
- [ ] **Step 2: Verify rolling safety.** Record replicas, readiness/liveness paths, `maxUnavailable`, `maxSurge`, termination grace and selector compatibility. The desired minimum is readiness-gated rolling behavior with no intentional loss of all ready old replicas; exact values must be taken from the external manifest, not invented locally.
- [ ] **Step 3: Run isolated pre-production release.** Apply the SHA-pinned overlay in a disposable/pre-production namespace, wait for readiness, and execute only low-sensitivity read-only smoke checks. Record component status/counts and timestamps, not endpoint addresses or response bodies.
- [ ] **Step 4: Run production canary when supported.** If the platform has an existing weight/header split, send the approved small traffic fraction to the new SHA and observe one full alert/model/task window. If no split exists, record `rollout_mode=rolling` and do not label the result canary/blue-green.
- [ ] **Step 5: Promote in bounded stages.** Increase the externally configured traffic/replica stage only after readiness, dependency health, model failure, task backlog and budget denial gates are clear. Each stage gets a release SHA, window, operator and reviewer record.

## Task 3：执行依赖降级与恢复演练

**Files:**
- Read-only application contracts listed in the File Map.
- Deployment-only test namespace and dependency controls; no source file is modified.

**Interfaces:**
- Consumes: health/metrics and alert signals from existing application contracts.
- Produces: one matrix row per MySQL, Redis, Milvus, OSS, model supplier and Feishu dependency with detection, automatic behavior, manual action, recovery confirmation and `PASS|BLOCKED|NOT_RUN`.

- [ ] **Step 1: MySQL failure rehearsal.** In an isolated namespace, make the database unavailable without deleting data; confirm `db` readiness and low-sensitivity alert classification. Verify the application does not report successful writes through an in-memory substitute. Restore connectivity and run read-only integrity/orphan checks.
- [ ] **Step 2: Redis failure rehearsal.** Confirm Redis health degradation, bounded local rate-limit behavior, subject-secret fail-closed behavior, and `ADMISSION_STORE_UNAVAILABLE` for budget admission. Do not call local fallback distributed-consistent. Restore Redis and verify probe, reservation and two-replica consistency.
- [ ] **Step 3: Milvus failure rehearsal.** Confirm fixed collection health becomes unavailable and that no empty collection/mock response is recorded as retrieval recovery. Restore schema/index/load and perform real collection/count/query checks.
- [ ] **Step 4: OSS failure rehearsal.** Confirm media operation failure is classified without claiming object persistence. Restore provider access and verify object HEAD/list/version/reference reconciliation; include shared-object retention policy.
- [ ] **Step 5: Model supplier rehearsal.** Exercise a configured candidate failure and verify state transition/fallback before output, admission remains active, and recovery requires the configured probe/success thresholds. Validate supplier quota and credentials through deployment-only evidence.
- [ ] **Step 6: Feishu channel rehearsal.** Keep the notifier out of readiness; inject credentials only through Secret manager, enable the channel for the isolated namespace, verify fixed payload/receiver behavior, retry and recovery. Record `deployment-only`, never “送达成功” from a mock client.

## Task 4：执行备份、删除和数据迁移门槛

**Files:**
- Read-only: `ops/backup/backup-manifest.schema.json`, `mysql-backup.ps1`, `restore-rehearsal.ps1`, `milvus-backup.ps1`, `redis-backup.ps1`, `oss-inventory.ps1`.
- Read-only runbooks: `docs/engineering/runbooks/yusi-backup-restore-runbook.md:20-45` and `yusi-account-deletion-privacy-audit-runbook.md:19-74`.

**Interfaces:**
- Consumes: Task 1 schema/migration state and deployment-only data access.
- Produces: backup restore RTO, component integrity results, deletion residual/orphan results, retention decision and a release block/unblock decision.

- [ ] **Step 1: Freeze writes and workers.** Use the deployment platform maintenance procedure to stop scheduler/worker writes before backup restore or deletion rehearsal; record only opaque run reference and times.
- [ ] **Step 2: Run isolated MySQL restore.** Use a non-production target database accepted by `restore-rehearsal.ps1` and validate checksum, row counts, orphan queries and schema compatibility. Record start/completion timestamps and RTO; no production database name is allowed.
- [ ] **Step 3: Validate Milvus/Redis/OSS.** Execute real collection schema/index/vector checks, Redis RDB/AOF/key-family checks, and OSS inventory/version/reference checks. The wrapper output alone remains `DEPLOYMENT-ONLY`.
- [ ] **Step 4: Run account-deletion rehearsal.** Follow the privacy runbook with target/control fixtures, verify MySQL zero residuals/orphans, all three Milvus collections, Redis families, OSS objects, worker non-recreation and backup retention boundaries.
- [ ] **Step 5: Apply migration rollback boundary.** If a schema change is already applied, do not run an unapproved reverse SQL script. Use forward-compatible repair or approved database restore after DBA review; application image rollback alone never claims data rollback.

## Task 5：配置告警和应急联动

**Files:**
- Read-only: `application.yml:29-32`, `application-prod.yml:15-19`, `AlertPolicy.java:20-32`, `AlertScheduler.java:106-123`, `FeishuAlertNotifier.java:37-79`.
- Deployment-only: Secret manager, Prometheus/scraper, receiver and on-call system.

**Interfaces:**
- Consumes: fixed health/metric signals and Task 3 recovery evidence.
- Produces: low-sensitivity alert delivery/recovery evidence, suppression behavior, receiver acknowledgement and a rollback decision record.

- [ ] **Step 1: Keep the default safe state.** Confirm `YUSI_ALERT_FEISHU_ENABLED` remains false in the baseline. Do not enable production delivery until webhook URL/signing secret are injected by Secret manager and the receiver owner approves the channel.
- [ ] **Step 2: Validate four alert gates.** Use the initial thresholds from `AlertPolicy.initial()` and record all as pending production tuning: readiness service unavailable, model failure rate, task backlog and budget denial. Confirm root readiness suppression and 30-minute fingerprint suppression.
- [ ] **Step 3: Verify low-sensitivity message.** Confirm only category/service/operation/level/window/count/value/classification/time/state are present. Do not include endpoint, Secret, user/request data, provider payload or exception text.
- [ ] **Step 4: Exercise emergency flow.** On a firing signal, stop promotion, create incident ref, preserve old SHA, choose dependency-specific degradation, and follow Task 6 rollback. On recovery, require two independent checks: health/metric recovery and functional/data integrity confirmation.
- [ ] **Step 5: Confirm receiver ownership.** Record receiver acknowledgement and on-call escalation path; a mock-contract-only result cannot satisfy this step.

## Task 6：回滚、恢复和放量验收

**Files:**
- Deployment-only: external GitOps overlay and Kubernetes control plane.
- Evidence-only: external release/incident record; no repository file modification.

**Interfaces:**
- Consumes: Tasks 1-5 evidence and previous verified SHA/digest.
- Produces: promotion or rollback decision, final low-sensitivity release record, and explicit residual blockers.

- [ ] **Step 1: Check rollback trigger.** Block promotion for readiness DOWN 2m, model failure threshold, task critical lag, budget denial threshold, dependency integrity failure, missing Secret, or any unverified deployment-only prerequisite.
- [ ] **Step 2: Revert GitOps reference.** Change all three component image references to the previous verified SHA/digest in the external overlay, or use the platform's audited rollout undo. Do not use `latest` and do not reverse database migration automatically.
- [ ] **Step 3: Wait for old release readiness.** Verify old Pods are ready, run read-only smoke and dependency checks, and confirm alert recovery. A green readiness probe alone is insufficient for data integrity.
- [ ] **Step 4: Reconcile data and workers.** Check pending task states, MySQL invariants, Milvus/Redis/OSS residuals and backup/deletion status before reopening writes.
- [ ] **Step 5: Record outcome.** Fill release SHA, previous SHA, rollout mode, trigger category, timestamps, dependency status, RTO if applicable, operator/reviewer roles and unresolved blockers. Mark every unavailable check `NOT_RUN` or `BLOCKED`.

## Task 7：最终上线清单与交接

**Files:**
- Read-only sources: roadmap `:622-654` and the Phase 5 runbooks.
- External record: checklist with OPS-01 through OPS-12 from the design document; no repository changes.

- [ ] **Step 1: Reconcile health/metrics evidence.** Require real management-port network isolation, Prometheus scrape and dependency connectivity; local contract tests are not deployment evidence. Source: roadmap `:622-626`.
- [ ] **Step 2: Reconcile alert evidence.** Require Secret injection, real Feishu delivery, receiver confirmation, dual-replica deduplication and threshold tuning; default off remains the safe baseline. Source: roadmap `:627-635`.
- [ ] **Step 3: Reconcile backup/privacy evidence.** Require MySQL restore RTO, Milvus/Redis/OSS real checks, deletion residual/orphan checks, worker race and retention decisions. Sources: roadmap `:636-643` and both runbooks.
- [ ] **Step 4: Reconcile rate/admission evidence.** Require production HMAC secret, real HTTP/SSE/multipart/gateway/Redis multi-replica/provider quota/20611/WebSocket/gRPC checks. Source: roadmap `:644-653` and rate runbook `:45-63`.
- [ ] **Step 5: Sign the release decision.** Release reviewer marks each item `PASS`, `BLOCKED` or `NOT_RUN`; no empty status is accepted. Unmet deployment-only items remain blockers and roadmap is not changed by this plan.
- [ ] **Step 6: Handoff and stop.** Store the low-sensitivity checklist, rollback reference, incident template and residual risk list in the deployment system. Do not create a repository commit as part of this deployment-only plan unless a separately approved documentation-only change is requested.

## Deployment-only 清单

The following cannot be proven by local source inspection or tests and must remain explicit deployment evidence:

1. External `yusi-infra` Deployment/Service/Ingress/Argo rollout strategy, probes, replicas, canary/blue-green weights and rollback permissions.
2. Real GHCR image pull, K8s rollout, readiness-gated traffic shift and restoration to the previous SHA/digest.
3. `YUSI_RATE_LIMIT_HMAC_SECRET` and `YUSI_ALERT_FEISHU_*` Secret manager injection; no credential value may enter the repository or logs.
4. Management port network allowlist, Prometheus scraping, real MySQL/Redis/Milvus connectivity and model gateway reachability.
5. HTTP/SSE/multipart/gateway/WebSocket/gRPC concurrency and byte-limit tests, Redis multi-replica enforcement/failure recovery and supplier quota calibration.
6. MySQL dump/restore/RTO, Milvus collection export/import or derived rebuild, Redis RDB/AOF restore, OSS versioning/inventory/replica checks.
7. Account deletion residual/orphan checks across all external copies, worker non-recreation, backup artifact retention and third-party/provider retention policy.
8. Real Feishu delivery, receiver acknowledgement, alert suppression/recovery, on-call escalation and message latency.
9. Any migration already applied to production and its expand/contract compatibility or approved forward-fix/restore path.

## Handoff evidence template

```text
release_sha=<immutable-commit-sha>
previous_release_sha=<verified-previous-sha>
gitops_change_ref=<opaque-change-reference>
rollout_mode=<rolling|canary|blue-green|NOT_RUN>
deployment_status=<PASS|BLOCKED|NOT_RUN>
readiness=<UP|DOWN|NOT_RUN>
dependency_checks=<classified-counts-only>
alert_delivery=<deployment-only|mock-contract-only|NOT_RUN>
backup_restore_rto=<elapsed|NOT_RUN>
privacy_residuals=<classified-counts-only>
migration_status=<compatible|forward-fix-required|NOT_RUN>
rollback_reference=<opaque-change-reference-or-empty>
operator_role=<role>
reviewer_role=<role>
observed_at_utc=<timestamp>
```

No Maven command, service startup, external dependency call or roadmap edit is part of the current design turn. After review, execution must start at Task 1 and stop at the first unmet deployment-only gate.
