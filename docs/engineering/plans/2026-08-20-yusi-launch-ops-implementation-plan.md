# Yusi 上线运维准备实施计划

> **For agentic workers:** 本计划用于评审通过后的 deployment-only 执行；当前设计阶段不得启动服务、运行 Maven、修改生产代码/配置、修改 CI、migration 或 roadmap。仓库指令禁止子 agent 和 auto-review；每个任务必须保留低敏证据并在外部部署环境独立验收。

## 部署形态决策订正（2026-08-22）

- 实际生产唯一采用单机 Compose：`.github/workflows/build_deploy.yml` 手动触发，在部署机执行 `rebuild.sh` 的 `docker compose up -d --force-recreate`。该决策登记于 roadmap 订正提交 `b1b55dd`，对应 roadmap `:654-660`。
- K8s/GitOps 已验证可用，但因服务器配置不足暂时搁置；K8s、灰度、蓝绿、多副本和镜像回滚仅保留为搁置蓝图，不进入本轮执行或 PASS。
- 本轮不做灰度、不做多副本、不实施镜像回滚。单机发布以重建后的观察窗口验收，当前无切回能力是残余运行风险。

**目标：** 基于单机 Compose 发布链路建立可审计的发布后观察、依赖降级与恢复动作、上线前置清单和低敏应急流程；K8s/GitOps 只保留未来蓝图。

**架构：** 当前发布记录由源码 commit、Compose/rebuild 操作引用、构建/镜像身份（若外部记录提供）、配置/Secret 版本和 schema 台账组成。运行时沿用已有 readiness、ModelStateCenter、bounded local limiter、ModelBudgetAdmission、AlertEvaluator 和 backup/privacy runbook，不新增业务降级旁路；GitOps rollout、流量权重和多副本只在未来蓝图恢复时另行执行。

**技术栈：** GitHub Actions、单机 Docker Compose、Spring Boot Actuator/Micrometer、Redis/Redisson、Milvus、OSS、mysqldump/MySQL restore、飞书 webhook Secret manager；本地只做静态文档审查，不执行部署命令。GHCR/Kustomize/Kubernetes 仅作为搁置蓝图的既有事实。

## 全局约束

- 本计划不修改 `src/main`、`src/test`、`src/main/resources`、`pom.xml`、`.github/workflows`、`ops/backup`、migration、评测/QualityGatePolicy 或 roadmap。
- 当前仓库最终只新增本计划和对应设计文档；外部 `yusi-infra`、Kubernetes、Secret manager、数据库、Redis、Milvus、OSS、供应商和飞书环境必须在部署方变更系统中审计。
- 单机 release record 必须保存源码 commit、构建/镜像身份、配置/Secret 版本和 schema/migration 状态；当前 `latest`/旧镜像不构成可用回滚引用。K8s/GitOps 恢复时才按蓝图要求使用 SHA/digest，不能把该要求伪装成当前 Compose 能力。
- 所有阈值标记“初始值，待生产调优”：readiness DOWN 2 分钟；模型 5 分钟、失败率 20%、最小 20 次；任务 15/60 分钟并持续 5 分钟；预算拒绝 5 分钟至少 10 次；告警 fingerprint 抑制 30 分钟、最多 3 次发送尝试。来源 `AlertPolicy.java:20-32`。
- 不得把本地 mock、H2、空 collection、静态 YAML、健康探针存在性或 workflow 成功写成生产送达、真实恢复、真实配额、Compose 观察窗口 PASS 或灰度 PASS。
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
- Read: `.github/workflows/build_deploy.yml:1-27` for the actual path; `.github/workflows/deploy_k8s.yml:29-58` for the CI verification contract and `:93-151` as the shelved blueprint only。
- Record externally: release/change record in the deployment system; no repository file is changed.

**Interfaces:**
- Consumes: successful CI verification, current source/build reference and the single-machine Compose operation record.
- Produces: one low-sensitivity release record containing release SHA/build reference, Compose operation reference, configuration/Secret version references, schema/migration state, operator role and reviewer role. A previous image is historical context only and is not a rollback capability in this path.

- [ ] **Step 1: Verify CI evidence.** Confirm the workflow's backend test and frontend test/type-check jobs have passed for the release commit, using `.github/workflows/deploy_k8s.yml:29-58` as the contract. Do not use a local Maven run as the production verification record.
- [ ] **Step 2: Record release/build references.** Record the release source commit and the Compose/rebuild operation reference; record build/image identity only when the deployment record provides it. Do not claim that `latest` or a retained previous image is a rollback target: the current script cleans old images and has no image switch command.
- [ ] **Step 3: Record migration compatibility.** Compare the release's database change set with `application-prod.yml:313-321` (`ddl-auto: none`) and the backup design's no-Flyway-runtime fact. Mark destructive or incompatible schema work as a release blocker until DBA signs the expand/contract or restore/forward-fix path.
- [ ] **Step 4: Assign role slots.** Fill `release operator`, `platform/SRE`, `DBA/backup`, `model platform`, `security/privacy`, `on-call` and `release reviewer` in the external record. A named individual is not required in this document, but every slot must have an accountable deployment identity before promotion.

## Task 2：核验单机 Compose 发布与观察窗口

**Files:**
- Read-only source: `.github/workflows/build_deploy.yml:1-27`, `docker-compose.yml:4-34`, `rebuild.sh:148-183`, `application.yml:29-48`, `AlertPolicy.java:20-32`.
- Deployment-only evidence: the single-machine Compose host and its low-sensitivity release/observation record.

**Interfaces:**
- Consumes: Task 1 release inventory and the approved Compose operation.
- Produces: evidence for force-recreate completion, readiness, dependency health, read-only smoke and one complete alert evaluation window. It must explicitly record that gray release, multi-replica rollout and image rollback are unavailable in the current path.

- [ ] **Step 1: Verify the approved Compose path.** Confirm the manual workflow and `rebuild.sh` force-recreate contract against the deployment host; record only the operation reference and status.
- [ ] **Step 2: Run the single-machine release.** Run the approved `rebuild.sh` procedure on the production host, record force-recreate completion, and do not claim a canary, blue-green, multi-replica or rollback result.
- [ ] **Step 3: Observe readiness and dependencies.** Confirm readiness UP and record fixed `dependency_health` classifications for MySQL, Redis, Milvus, model gateway and tasks. No endpoint address or response body enters the record.
- [ ] **Step 4: Run read-only smoke and alert window.** Execute the approved low-sensitivity read-only smoke checks, then observe one complete 30-second alert evaluation window using the initial `AlertPolicy` thresholds. Mark all thresholds “初始值，待生产调优”.
- [ ] **Step 5: Record single-machine outcome.** Fill `rollout_mode=single_machine_observation`, release/operation references, observed status and any blocker. If a gate fails, stop further release activity and use the single-machine forward-fix or maintenance procedure; do not invent an image rollback.

## Task 3：执行依赖降级与恢复演练

**Files:**
- Read-only application contracts listed in the File Map.
- Deployment-only dependency controls in the approved single-machine or isolated test environment; no K8s namespace is assumed and no source file is modified.

**Interfaces:**
- Consumes: health/metrics and alert signals from existing application contracts.
- Produces: one matrix row per MySQL, Redis, Milvus, OSS, model supplier and Feishu dependency with detection, automatic behavior, manual action, recovery confirmation and `PASS|BLOCKED|NOT_RUN`.

- [ ] **Step 1: MySQL failure rehearsal.** In an isolated test environment, make the database unavailable without deleting data; confirm `db` readiness and low-sensitivity alert classification. Verify the application does not report successful writes through an in-memory substitute. Restore connectivity and run read-only integrity/orphan checks.
- [ ] **Step 2: Redis failure rehearsal.** Confirm Redis health degradation, bounded local rate-limit behavior, subject-secret fail-closed behavior, and `ADMISSION_STORE_UNAVAILABLE` for budget admission. Do not call local fallback distributed-consistent. Restore Redis and verify probe, reservation and single-instance state recovery.
- [ ] **Step 3: Milvus failure rehearsal.** Confirm fixed collection health becomes unavailable and that no empty collection/mock response is recorded as retrieval recovery. Restore schema/index/load and perform real collection/count/query checks.
- [ ] **Step 4: OSS failure rehearsal.** Confirm media operation failure is classified without claiming object persistence. Restore provider access and verify object HEAD/list/version/reference reconciliation; include shared-object retention policy.
- [ ] **Step 5: Model supplier rehearsal.** Exercise a configured candidate failure and verify state transition/fallback before output, admission remains active, and recovery requires the configured probe/success thresholds. Validate supplier quota and credentials through deployment-only evidence.
- [ ] **Step 6: Feishu channel rehearsal.** Keep the notifier out of readiness; inject credentials only through Secret manager, enable the channel for the isolated test environment, verify fixed payload/receiver behavior, retry and recovery. Record `deployment-only`, never “送达成功” from a mock client.

## Task 4：执行单机数据门槛与依赖恢复检查

**Files:**
- Read-only: `ops/backup/backup-manifest.schema.json`, `mysql-backup.ps1`, `restore-rehearsal.ps1`, `milvus-backup.ps1`, `redis-backup.ps1`, `oss-inventory.ps1`.
- Read-only runbooks: `docs/engineering/runbooks/yusi-backup-restore-runbook.md:20-45` and `yusi-account-deletion-privacy-audit-runbook.md:19-74`.

**Interfaces:**
- Consumes: Task 1 schema/migration state and deployment-only data access.
- Produces: backup restore RTO, component integrity results, deletion residual/orphan results, retention decision and a release block/unblock decision.

- [ ] **Step 1: Freeze writes and workers.** Use the single-machine maintenance procedure to stop scheduler/worker writes before backup restore or deletion rehearsal; record only opaque run reference and times.
- [ ] **Step 2: Run isolated MySQL restore.** Use a non-production target database accepted by `restore-rehearsal.ps1` and validate checksum, row counts, orphan queries and schema compatibility. Record start/completion timestamps and RTO; no production database name is allowed.
- [ ] **Step 3: Validate Milvus/Redis/OSS.** Execute real collection schema/index/vector checks, single-instance Redis RDB/AOF/key-family checks, and OSS inventory/version/reference checks. The wrapper output alone remains `DEPLOYMENT-ONLY`.
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
- [ ] **Step 4: Exercise emergency flow.** On a firing signal, stop release/rebuild activity, create incident ref, record the current build reference, choose dependency-specific degradation, and follow Task 6 single-machine forward-fix or maintenance handling. On recovery, require two independent checks: health/metric recovery and functional/data integrity confirmation.
- [ ] **Step 5: Confirm receiver ownership.** Record receiver acknowledgement and on-call escalation path; a mock-contract-only result cannot satisfy this step.

## Task 6：单机故障处置、恢复和上线验收

**Files:**
- Deployment-only: single-machine Compose host and maintenance controls.
- Evidence-only: external release/incident record; no repository file modification.

**Interfaces:**
- Consumes: Tasks 1-5 evidence and the current Compose release/build reference.
- Produces: an observation/fix decision, final low-sensitivity release record, explicit residual blockers and the honest single-machine rollback status.

- [ ] **Step 1: Check observation blocker.** Block completion for readiness DOWN 2m, model failure threshold, task critical lag, budget denial threshold, dependency integrity failure, missing Secret, or any unverified deployment-only prerequisite.
- [ ] **Step 2: Stop and choose single-machine handling.** Do not run a GitOps revert or claim an image switch. Stop further release activity and choose an approved forward fix with a new rebuild, or stop the service for maintenance. `rebuild.sh:69,179-181` and the absence of a cutback command must remain in the evidence.
- [ ] **Step 3: Re-observe after recovery action.** Verify the current service readiness, run read-only smoke and dependency checks, and confirm alert recovery. A green readiness probe alone is insufficient for data integrity; this is not an image rollback result.
- [ ] **Step 4: Reconcile data and workers.** Check pending task states, MySQL invariants, Milvus/Redis/OSS residuals and backup/deletion status before reopening writes.
- [ ] **Step 5: Record outcome.** Fill release/build reference, rollout mode, trigger category, timestamps, dependency status, RTO if applicable, operator/reviewer roles and unresolved blockers. Record `rollback_status=not_available_single_machine`; mark every unavailable check `NOT_RUN` or `BLOCKED`.

## Task 7：最终上线清单与交接

**Files:**
- Read-only sources: roadmap `:622-654` and the Phase 5 runbooks.
- External record: checklist with OPS-01 through OPS-11 from the design document; no repository changes.

- [ ] **Step 1: Reconcile health/metrics evidence.** Require real management-port network isolation, Prometheus scrape and dependency connectivity; local contract tests are not deployment evidence. Source: roadmap `:622-626`.
- [ ] **Step 2: Reconcile alert evidence.** Require Secret injection, real Feishu delivery, receiver confirmation, single-instance suppression/recovery and threshold tuning; default off remains the safe baseline. Source: roadmap `:627-635`.
- [ ] **Step 3: Reconcile backup/privacy evidence.** Require MySQL restore RTO, Milvus/Redis/OSS real checks, deletion residual/orphan checks, worker race and retention decisions. Sources: roadmap `:636-643` and both runbooks.
- [ ] **Step 4: Reconcile rate/admission evidence.** Require production HMAC secret, real HTTP/SSE/multipart/gateway/single-instance Redis failure/provider quota/20611/WebSocket/gRPC checks. Source: roadmap `:644-653` and rate runbook `:45-63`.
- [ ] **Step 5: Sign the release decision.** Release reviewer marks each item `PASS`, `BLOCKED` or `NOT_RUN`; no empty status is accepted. Unmet deployment-only items remain blockers and roadmap is not changed by this plan.
- [ ] **Step 6: Handoff and stop.** Store the low-sensitivity checklist, rollback reference, incident template and residual risk list in the deployment system. Do not create a repository commit as part of this deployment-only plan unless a separately approved documentation-only change is requested.

## Deployment-only 清单

The following cannot be proven by local source inspection or tests and must remain explicit single-machine deployment evidence:

1. Actual Compose host execution of the manual workflow and `rebuild.sh` force-recreate, with readiness, `dependency_health`, low-sensitivity read-only smoke and one complete 30-second alert evaluation window.
2. `YUSI_RATE_LIMIT_HMAC_SECRET` and `YUSI_ALERT_FEISHU_*` Secret manager injection; no credential value may enter the repository or logs.
3. Management port network allowlist, Prometheus scraping, real MySQL/Redis/Milvus connectivity and model gateway reachability.
4. HTTP/SSE/multipart/gateway/WebSocket/gRPC concurrency and byte-limit tests, single-instance Redis failure/recovery behavior and supplier quota calibration.
5. MySQL dump/restore/RTO, Milvus collection export/import or derived rebuild, single-instance Redis RDB/AOF restore, OSS versioning/inventory/reference checks.
6. Account deletion residual/orphan checks across all external copies, worker non-recreation, backup artifact retention and third-party/provider retention policy.
7. Real Feishu delivery, receiver acknowledgement, alert suppression/recovery, on-call escalation and message latency.
8. Any migration already applied to production and its expand/contract compatibility or approved forward-fix/restore path.
9. The current single-machine image rollback gap and the decision to use forward fix or maintenance; this is an explicit residual risk, not a PASS claim.

## 搁置蓝图（不纳入本轮执行）

The following K8s/GitOps items remain recorded for a future deployment shape and are not prerequisites or PASS evidence for the current single-machine release:

1. External `yusi-infra` Deployment/Service/Ingress/Argo rollout strategy, probes, replicas, canary/blue-green weights and rollback permissions.
2. Real GHCR image pull, K8s rollout, readiness-gated traffic shift, multi-replica consistency and restoration to a previous SHA/digest.
3. Any K8s-specific rollout parameter, traffic split or image rollback rehearsal.

Source: `.github/workflows/deploy_k8s.yml:124-151`, `docs/devops/gitops_proposal.md:154-232`, roadmap `:655-660`.

## Handoff evidence template

```text
release_sha=<immutable-commit-sha>
previous_release_sha=<historical-reference-or-empty>
gitops_change_ref=<not_applicable_single_machine|opaque-change-reference>
rollout_mode=<single_machine_observation|rolling|canary|blue-green|NOT_RUN>
# single_machine_observation = Compose force-recreate + readiness/dependency/smoke/alert-window observation
deployment_status=<PASS|BLOCKED|NOT_RUN>
readiness=<UP|DOWN|NOT_RUN>
dependency_checks=<classified-counts-only>
alert_delivery=<deployment-only|mock-contract-only|NOT_RUN>
backup_restore_rto=<elapsed|NOT_RUN>
privacy_residuals=<classified-counts-only>
migration_status=<compatible|forward-fix-required|NOT_RUN>
rollback_reference=<opaque-change-reference-or-empty>
rollback_status=<not_available_single_machine|PASS|NOT_RUN|NOT_REQUIRED>
release_observation=<readiness-dependency-smoke-alert-window|NOT_RUN>
operator_role=<role>
reviewer_role=<role>
observed_at_utc=<timestamp>
```

`single_machine_observation` is the only current production rollout value. `rolling`, `canary` and `blue-green` are reserved for the shelved K8s/GitOps blueprint. The current path must record `rollback_status=not_available_single_machine`; it must not manufacture a rollback PASS.

No Maven command, service startup, external dependency call or roadmap edit is part of the current design turn. After review, execution follows Task 1 through the single-machine observation gate; K8s blueprint tasks remain out of scope.
