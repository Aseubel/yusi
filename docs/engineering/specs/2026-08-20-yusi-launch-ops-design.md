# Yusi 上线运维准备设计

> 状态：待评审设计
>
> 范围：Phase 5 上线运维准备，覆盖灰度与回滚、模型网关及关键依赖降级、上线前置清单和应急流程。本轮只新增本设计与实施计划，不修改 roadmap、CI、生产配置、migration、代码或测试。
>
> 低敏边界：本文只记录固定组件名、版本引用、状态、计数、时间窗口和操作类别。凭据只以环境变量名引用；不记录 webhook 地址、密钥、内网地址、用户标识、请求正文、模型响应、完整对象 key 或异常 message/stack。

## 1. 结论摘要

当前部署能力不是一个已经验证的灰度/蓝绿平台：

- 旧的 SSH 部署 workflow 只允许手动触发，进入部署机执行 `bash rebuild.sh`，见 `.github/workflows/build_deploy.yml:1-27`。该路径使用本地 Compose 的 `yusi:latest`，见 `docker-compose.yml:4-18`，没有灰度、蓝绿、rollout 或 rollback 步骤。
- 当前 GitOps workflow 在合并到 `main` 后先执行后端和前端验证，构建并推送 backend/frontend/MCP 的 commit SHA 镜像，同时推送 `latest`，见 `.github/workflows/deploy_k8s.yml:3-6,29-58,90-122`；随后只把 SHA 写入外部 `yusi-infra/overlays/prod` 并提交回该仓库，见 `.github/workflows/deploy_k8s.yml:124-151`。
- 本仓库不包含 `yusi-infra` 的实际 Deployment、Service、Ingress、ArgoCD Application 或 rollout 参数，因此无法从代码独立证明当前生产存在 canary、蓝绿、`maxUnavailable`、`maxSurge` 或自动回滚。`docs/devops/gitops_proposal.md:9-18,149-232,442-454` 是目标架构建议，不是当前部署证据。
- 最小可落地方案是保留不可变 commit SHA 作为发布和回滚引用，在外部 GitOps overlay 中先完成单副本/小流量观察，再逐步放量；在实际 rollout 资源未被部署方提供和验证前，灰度、双副本一致性、真实 readiness 放量和回滚均标 `deployment-only`。

Phase 5 的本地门槛已经提供应用层合同，但没有替代真实部署证据：健康与指标的部署验收仍待完成，见 roadmap `docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md:622-626`；告警默认关闭且真实 Secret、送达、双副本去重和调优待完成，见 roadmap `:627-635`；备份恢复真实 RTO 待完成，见 roadmap `:636-637`；注销删除外部副本确认、鉴权越权和 Trace 复查待完成，见 roadmap `:638-643`；限流的生产 Secret、压测、Redis 多副本、供应商配额和管理端口 allowlist 待完成，见 roadmap `:644-653`。

## 2. 部署链路现状

### 2.1 CI/CD 与镜像

| 链路 | 代码事实 | 当前能力结论 |
| --- | --- | --- |
| 旧 SSH 链路 | `build_deploy.yml:1-5` 标注 workflow 暂时弃用且只配置 `workflow_dispatch`；`:16-27` 通过 SSH 到部署机执行 `cd /root/yusi` 和 `bash rebuild.sh`。 | 没有仓库内的灰度、蓝绿、回滚或发布后健康门禁。具体部署机脚本状态需 deployment-only 核实。 |
| GitOps 验证 | `deploy_k8s.yml:13-38` 执行 checkout、Maven 测试并归档离线报告；`:40-58` 执行前端测试和 TypeScript 检查。 | 有发布前自动验证，但不等同于生产 smoke 或 rollout 验证。 |
| backend 镜像 | `deploy_k8s.yml:90-102` 用 `Dockerfile` 构建并推送 `${github.sha}` 和 `latest` 两个 tag。 | SHA 可作为不可变回滚引用；`latest` 不得作为回滚依据。 |
| frontend 镜像 | `deploy_k8s.yml:104-112` 用 `frontend/Dockerfile` 构建并推送 SHA 和 `latest`。 | 同上；前端发布也必须保存 SHA。 |
| MCP 镜像 | `deploy_k8s.yml:114-122` 用 `mcp/Dockerfile` 构建并推送 SHA 和 `latest`。 | MCP 与 backend/frontend 必须作为同一 release manifest 记录，不能只回退其中一端而忽略协议兼容性。 |
| GitOps 回写 | `deploy_k8s.yml:124-151` checkout 外部 `yusi-infra`，在 `overlays/prod` 用 Kustomize 写入三个 SHA，然后提交并 push。 | 当前仓库只证明“提交配置变更”的链路，不证明 Argo/K8s 已同步或已健康。 |

### 2.2 后端 Docker/Compose 路径

- `Dockerfile:1-27` 从 `target/*.jar` 构建 Java 21 runtime image，最终只复制分层并执行 JarLauncher；没有 `HEALTHCHECK`、版本标签、回滚脚本或流量切分指令。
- `docker-compose.yml:4-18` 使用本地 build 和 `image: yusi:latest`，容器 `restart: on-failure`，发布端口为应用端口；`:21-34` 只有日志卷和 external bridge network，没有副本、readiness、滚动更新或旧版本服务定义。
- `rebuild.sh:7-17` 固定 Compose project/service/image 为 `yusi`、`yusi`、`yusi:latest`；`:98-146` 默认 `git pull --ff-only`、安装/选择 JDK 并执行 `./mvnw clean package -DskipTests`；`:148-176` 构建后执行 `docker compose up -d --force-recreate --remove-orphans`；`:178-183` 新容器启动后尝试删除旧 image ID。
- 因此本地 Compose 路径的旧镜像通常在切换后被清理，且脚本没有“切回旧 image ID”的命令。设计上的最低要求是在 deployment-only 发布前保存旧 SHA/image digest 并暂缓清理，不能把当前脚本描述成已具备回滚。

### 2.3 前端 Docker/Compose 路径

- `frontend/Dockerfile:1-20` 用 Node 22 构建静态资源，`:22-40` 用 Nginx runtime image、模板和 entrypoint 启动，未定义 rollout 或版本回退。
- `frontend/docker-compose.yml:1-18` 固定 `image: yusi-front:latest`、`restart: always`、单一 named container 和固定端口；`:21-24` 使用 external bridge network，没有副本或健康门禁。
- `frontend/rebuild.sh:7-19` 固定 Compose project、service、container 和 `yusi-front:latest`；`:149-178` 可执行 `git pull --ff-only`、构建和 `up -d --force-recreate`；`:180-185` 尝试删除旧 image。该脚本也没有 rollback 或上一版本选择参数。
- `frontend/nginx.conf:32-65` 将 API 和两个 WebSocket 路径代理到 backend service，并设置长连接超时；这说明前端回滚必须与 backend/MCP 协议版本一起观察，不能只依据静态页面构建成功。

### 2.4 灰度/蓝绿/回滚事实边界

1. 仓库内没有当前生产 K8s manifest，因此不能声称已有 canary、蓝绿、Service selector 双轨或 Argo Rollouts。
2. GitOps workflow 已经提供 SHA 发布引用和外部 overlay 更新点，足以承载“外部配置先小范围放量、再扩大”的最小方案，但放量机制、probe、replica 和 Ingress 权重必须由部署方在 `yusi-infra` 中核验。
3. `docs/devops/gitops_proposal.md:154-232,323-389` 展示了建议中的 Deployment、probe 和 RollingUpdate；文档明确是 proposal，不能作为现状 PASS。
4. 本地 Compose 只有 recreate 语义，没有无损切换。生产发布不得用 `latest` 做审计或回滚标识；必须记录 backend/frontend/MCP 的完整 commit SHA 或 digest、旧版本引用、配置版本和数据库迁移状态。

## 3. 现有降级事实

### 3.1 限流与 subject 安全

- `RateLimiterAspect.java:58-83` 在 USER/IP 限流缺少 subject HMAC 配置时直接拒绝；Redis 可用时先走分布式限流，Redis 不可用时转到有界本地桶。
- `RateLimiterAspect.java:88-113` 捕获 Redis 路径异常后标记不可用并使用本地限流；`:119-143` 本地限流本身异常时返回拒绝；`:149-168` 每 30 秒重新探测 Redis。
- `RateLimiterSubjectEncoder.java:17-18,24-58` 以配置的 HMAC secret 生成固定长度摘要；`application.yml:10-11` 只通过 `YUSI_RATE_LIMIT_HMAC_SECRET` 绑定。生产未注入时 subject-scoped traffic 的 fail-closed 是硬性前置，来源也被记录在 roadmap `:650-651`。

### 3.2 模型预算准入

- `ModelGatewayAdmissionProperties.java:15-30` 定义 enabled、窗口、reservation TTL 以及 user/model/provider 三个 scope；`:32-62` 对窗口和各 scope 的非负值做启动校验。
- 生产初始配置在 `application-prod.yml:26-40`：窗口 60 秒、reservation TTL 300 秒，user/model/provider 同时有 request/token 上限，具体值由环境变量覆盖。
- `ModelBudgetAdmission.java:105-150` 在 Redis client 缺失时返回 `ADMISSION_STORE_UNAVAILABLE`；reservation 冲突返回 `RESERVATION_CONFLICT`；scope 超限对外统一为 `LIMIT_EXCEEDED`；Redis 运行时异常也归为 `ADMISSION_STORE_UNAVAILABLE`，日志只保留固定分类和 exception type。
- 这条链路没有把 Redis 缺失降级成无限放行；上线时应把 admission store unavailable 当成预算准入阻断信号，而不是继续放大模型流量。

### 3.3 模型代理失败与恢复

- `ModelProxyFactory.java:233-310` 按 route decision 遍历 candidate；不可用 state 会跳过，预算拒绝记录固定分类并继续候选；模型调用异常被归一化、记录失败、发布低敏 attempt event，并在允许时继续 fallback candidate。
- `ModelProxyFactory.java:313-422` 对 streaming candidate 保持相同的预算拒绝、未输出前 fallback、已输出后不重放的边界；外部调用异常交给固定分类的 downstream error。
- `ModelStateCenter.java:77-95` 只允许 UP 或到 probe 时间的 DOWN candidate；`:98-135` 用连续失败切到 DOWN、到 recovery probe interval 后 HALF_OPEN，并在达到 recovery success threshold 后回到 UP。生产阈值来自 `application-prod.yml:56-60`：failure threshold 3、recovery success threshold 2、probe interval 15000ms。
- `ModelGatewayHealthIndicator.java:19-25,39-81` 只根据路由和本地/远端 state 判断是否有可用 tier，明确不调用真实 chat/embedding 模型；因此 readiness UP 不是供应商端到端送达证明。

### 3.4 健康、指标和告警

- `application.yml:34-48` 只暴露 `health,prometheus`，`show-details: never`，liveness 只含 `livenessState`，readiness 含 `readinessState,db,redis,milvus,modelGateway,tasks`。
- Redis、Milvus、任务探针分别见 `RedisHealthIndicator.java:10-48`、`MilvusHealthIndicator.java:10-50`、`TaskHealthIndicator.java:10-40`；MySQL 使用 readiness 中的 `db` contributor，本仓库没有自定义 MySQL HealthIndicator。`AlertScheduler.java:169-185` 读取 readiness、`db`、`redis`、`milvus`、`modelGateway` 和 `tasks` 并写入 `dependency_health`。
- `YusiMetrics.java:159-188` 定义 dependency health、task due gap/lag；`:128-146` 定义 model call failure counter；`:194-205` 定义 budget denied counter。所有指标只能使用既有四键标签白名单。
- `AlertPolicy.java:20-32` 的初始阈值为：readiness DOWN 2 分钟；模型 5 分钟窗口、失败率 20%、最小 20 次调用；任务 warning 15 分钟、critical 60 分钟、持续 5 分钟；预算拒绝 5 分钟至少 10 次；重复抑制 30 分钟；发送最多 3 次。这些全部是“初始值，待生产调优”。
- `AlertScheduler.java:27-28,67-103` 只有 `yusi.alert.feishu.enabled=true` 时创建调度器，默认配置在 `application.yml:29-32` 和 `application-prod.yml:15-19` 为关闭、30 秒评估；`FeishuAlertNotifier.java:37-79` 异步入队并最多重试 3 次，失败只记录固定分类，不影响 readiness。

### 3.5 备份恢复现状

- `docs/engineering/runbooks/yusi-backup-restore-runbook.md:8-12` 明确 MySQL、Milvus、Redis、OSS 没有已接入生产的备份调度、真实恢复机制或演练记录。
- `ops/backup/mysql-backup.ps1:2-18,33-45` 只提供参数化 dump wrapper；`:48-106` 用环境密码生成 checksum manifest 并输出低敏计数。`restore-rehearsal.ps1:91-103` 拒绝生产库名，未 `-Execute` 时只输出 `DEPLOYMENT-ONLY`；`:112-138` 的真实 restore/invariant 检查需外部 MySQL。
- `ops/backup/milvus-backup.ps1:8-19`、`redis-backup.ps1:8-23` 和 `oss-inventory.ps1:8-15` 只做 collection/key-family/object-class 契约和 `DEPLOYMENT-ONLY` 标记。当前不能把这些脚本当成已完成的实际备份或恢复能力。

## 4. 关键依赖故障矩阵（现状）

“现有恢复手段”只登记已经有代码/文档证据的动作；没有自动恢复的地方明确写无。表中建议动作属于后文设计，不回写为现状。

| 依赖 | 故障时系统行为（现有证据） | 现有检测手段 | 现有恢复手段 |
| --- | --- | --- | --- |
| MySQL | datasource 由 `application-prod.yml:287-307` 配置，Hikari 有 `connection-timeout` 和 `SELECT 1` test query；readiness 组含 `db`，见 `application.yml:44-48`。未发现应用级 DB failover 或写入队列。 | Spring Boot 的 `db` health contributor 被 readiness 引用；`AlertScheduler.java:174-185` 读取 `db` 并记录 `dependency_health`。本仓库没有自定义 DB contributor。 | 无应用内自动恢复。`ops/backup/restore-rehearsal.ps1:101-138` 只能在隔离目标库执行真实恢复演练；生产备份/恢复仍是 deployment-only，见 backup runbook `:33-45`。 |
| Redis | Rate limiter 有 bounded local fallback，见 `RateLimiterAspect.java:70-112`；budget admission 在 Redis 缺失/异常时拒绝并归类，见 `ModelBudgetAdmission.java:111-148`；模型状态中心依赖 Redis state map/topic，见 `ModelStateCenter.java:47-74,168-188`。 | `RedisHealthIndicator.java:10-48` 固定探针；readiness/`dependency_health` 由 `application.yml:47-48`、`AlertScheduler.java:174-185` 覆盖。 | 限流每 30 秒重探测并恢复分布式路径，见 `RateLimiterAspect.java:149-168`；其他 Redis 数据无应用内 RDB/AOF 恢复，`redis-backup.ps1:22-23` 明确 deployment-only。 |
| Milvus | 应用健康探针只检查固定 embedding collection，见 `MilvusHealthIndicator.java:10-38`；未发现本地 fallback 或真实向量重建入口作为故障恢复。 | `MilvusHealthIndicator.java:23-50`、readiness `milvus` 成员和 `AlertScheduler.java:174-185`。 | 无已接入生产的自动 export/import；`milvus-backup.ps1:18-19` 只输出 deployment-only，真实 schema/index/load/向量核验见 backup runbook `:35-41`。 |
| OSS | OSS client 仅在非 test profile 创建，见 `OssConfig.java:10-25`；上传路径调用 provider，失败输出固定分类并返回业务失败，见 `OssService.java:57-103`。 | 没有 `oss` readiness contributor：`application.yml:44-48` 的 readiness 列表不含 OSS；现有 OSS 日志是低敏操作结果，不是健康门禁。 | 无应用内版本恢复或跨区域恢复。`oss-inventory.ps1:14-15` 只记录真实 versioning/inventory/restore 为 deployment-only；临时分片和本地 merge 清理不等于最终对象恢复。 |
| 模型供应商/模型网关 | route candidate 按 state 和 admission 选择；模型调用失败在未产生输出且允许时继续 fallback，见 `ModelProxyFactory.java:245-310,313-422`；所有 candidate 不可用时请求失败。 | `ModelGatewayHealthIndicator.java:39-81` 的本地路由/state 探针、`model_call_failure_total`、readiness 和 AlertEvaluator；健康探针不发真实模型请求。 | 已有 candidate failover、circuit DOWN/HALF_OPEN/UP，见 `ModelStateCenter.java:77-135`。无供应商账户切换、配额购买或凭据自动轮换；供应商 quota calibration 是 deployment-only，见 rate runbook `:58-63`。 |
| 飞书 webhook | 默认开关关闭；启用且配置完整时告警异步发送，缺配置直接跳过，失败最多三次指数 backoff，见 `FeishuAlertProperties.java:13-33`、`FeishuAlertNotifier.java:37-79`。 | 没有 readiness contributor；发送失败只写固定分类日志，AlertScheduler 本身捕获 evaluator 异常，见 `AlertScheduler.java:72-103`。 | 当前只有同一进程内最多三次重试和下一个 30 秒调度周期；无本地送达确认、备用接收端或自动切换。真实送达与轮值确认待 deployment-only，见 roadmap `:633-635`。 |

## 5. 灰度与回滚设计

### 5.1 发布对象和最小灰度

发布单必须绑定以下不可变对象：backend SHA/digest、frontend SHA/digest、MCP SHA/digest、源码 commit、配置/Secret 版本引用、数据库 schema/migration 台账和旧版本回滚引用。workflow 已有 SHA tag 和外部 overlay 写回点，见 `.github/workflows/deploy_k8s.yml:93-151`；`latest` 只能作为兼容性发布标签，不能作为审计或回滚对象。

当前仓库缺少实际 K8s 资源，因此采用两级方案：

1. **当前可由 GitOps 链路承载的最小方案：受控滚动放量。** 在外部 `yusi-infra` 先把 release SHA 写入非生产/隔离环境，执行 readiness、只读 smoke、模型路由状态和关键指标观察；再在生产 overlay 以一个受控批次更新，要求新 Pod readiness 通过后才减少旧 Pod。具体 Deployment 的 `replicas`、`maxUnavailable`、`maxSurge`、probe 和 Service selector 必须由平台 owner 在外部仓库核验，不能用 proposal 文档替代。
2. **真正用户灰度/蓝绿：deployment-only。** 如果外部 Ingress/Service 支持按权重或 header 将固定小比例流量送到新 SHA，则先放 1 个观察单元，再按预先记录的阶段扩大；否则只能做滚动更新，不能声称有 canary/blue-green。现仓库没有流量权重、双 Service 或回滚控制器证据。

### 5.2 发布前门槛

- CI verification 通过：backend `./mvnw test` 和 frontend `pnpm test`/`tsc -b`，证据来源 `.github/workflows/deploy_k8s.yml:29-58`。
- 三个镜像均可按 release SHA 定位，且保留旧 SHA/digest；不要只保留 `latest`。
- `YUSI_RATE_LIMIT_HMAC_SECRET` 已由 Secret 注入；缺失时 subject-scoped 限流 fail-closed，见 `RateLimiterSubjectEncoder.java:17-58` 和 roadmap `:650-651`。
- 飞书开关、URL、签名 secret 只通过 `YUSI_ALERT_FEISHU_ENABLED`、`YUSI_ALERT_FEISHU_WEBHOOK_URL`、`YUSI_ALERT_FEISHU_SIGNING_SECRET` 注入；默认开关仍为 false，真实送达尚未由本地证据证明。
- 管理端口 `20611` 的网络 allowlist、Prometheus 抓取、真实 MySQL/Redis/Milvus/模型连通性、备份 RTO、外部删除残留检查和压测全部有 deployment-only 记录后才可放量，清单见第 7 节。

### 5.3 回滚触发条件

以下条件任一满足即停止继续放量并进入回滚/阻断评估：

- 新版本 readiness 在发布观察窗口内持续 DOWN；应用初始告警阈值为 DOWN 2 分钟，见 `AlertPolicy.java:20-24`。
- 模型调用失败率在 5 分钟窗口达到 20% 且调用数至少 20，或所有可用 route tier 均进入 DOWN；阈值和状态转移见 `AlertPolicy.java:23-25`、`ModelStateCenter.java:77-135`。
- 任务 due gap/lag 达到 warning 15 分钟或 critical 60 分钟并持续 5 分钟，或出现未登记的任务恢复/重复执行；初始值见 `AlertPolicy.java:26-29`。
- 预算拒绝 5 分钟达到 10 次，或 admission store unavailable 导致模型请求无法按预算准入；初始值见 `AlertPolicy.java:29-30`、`ModelBudgetAdmission.java:111-148`。
- MySQL/Redis/Milvus readiness DOWN、数据完整性/orphan 检查异常、外部删除副本残留、Secret 缺失，或限流/网关 deployment-only 约束未满足。

阈值全部标记“初始值，待生产调优”。真实调优必须记录 before/after 窗口、release SHA、操作者和回滚引用，不能用本地测试计数代替。

### 5.4 回滚步骤

1. On-call 以低敏 incident ref 记录分类、开始时间、当前 release SHA、旧 SHA、受影响组件和触发信号；不得记录用户/请求/模型正文。
2. 暂停继续放量，冻结新的 GitOps promotion；若是外部 Ingress 灰度，先把新版本权重降为 0，再处理部署版本。
3. 将外部 `yusi-infra` production overlay 的 backend/frontend/MCP image tag 恢复到已验证的旧 SHA/digest，提交带 incident ref 的回退变更；必要时使用平台既有 rollout undo，但不得使用 `latest` 猜测版本。
4. 等待旧版本 Pod readiness 通过，再执行只读 smoke、`dependency_health`、model failure、task lag 和 budget denial 观察；健康恢复不等于数据恢复。
5. 对 MySQL、Redis、Milvus、OSS 做只读完整性检查。发现 schema、对象、向量或异步任务不一致时阻断流量，转入备份/恢复 runbook；不得把应用回滚当作数据回滚。
6. 发布记录包含旧/新 SHA、GitOps commit、rollout 状态、告警状态和恢复时间。真实 K8s 命令、平台权限和结果是 deployment-only。

### 5.5 数据迁移不可逆边界

- 生产 `spring.jpa.hibernate.ddl-auto` 为 `none`，见 `application-prod.yml:313-321`；仓库虽然有 `src/main/resources/db/migration` SQL，但 backup 设计已核实没有 Flyway runtime wiring，见 `docs/engineering/specs/2026-08-20-yusi-backup-restore-design.md:40-43,265`。
- 因此不能假设“发布旧镜像就自动回滚已应用 migration”。涉及 schema 的发布必须采用 expand/contract：先增加向后兼容结构，再发布读写兼容代码，观察后再执行清理型变更。
- 已应用的破坏性 migration 不允许直接反向执行；处理顺序是停止放量、校验 backup/checksum、在隔离环境恢复演练、由 DBA 决定前滚修复或按批准的备份恢复。任何恢复 MySQL 的操作都属于 deployment-only，并必须产生 RTO 记录。
- backend/frontend/MCP 版本也有协议边界：前端 Nginx 代理 API/WebSocket，见 `frontend/nginx.conf:32-65`；回滚时三端必须按兼容矩阵成组处理。

## 6. 降级策略矩阵（设计目标与现状边界）

| 依赖 | 检测信号 | 自动降级行为 | 人工动作 | 恢复确认 |
| --- | --- | --- | --- | --- |
| MySQL | readiness `db` DOWN、连接测试失败、业务错误计数；现有 `db` 路径见 `application.yml:44-48` 和 `AlertScheduler.java:174-185`。 | 不新增“静默写入”或内存伪持久化；停止高风险写入口，保留能明确安全的只读/健康响应。当前代码无统一 DB failover，故默认阻断而非虚报成功。 | DBA 检查实例、连接池、锁和备份；必要时冻结流量，执行隔离 restore rehearsal。 | `db` readiness UP、关键只读查询和应用级 orphan/invariant 为零；真实数据库恢复/RTO 记录 PASS 才能放量。 |
| Redis | `RedisHealthIndicator` 固定探针、`dependency_health{operation=redis}`、限流 backend failure；见 `RedisHealthIndicator.java:23-48`、`RateLimiterAspect.java:107-168`。 | 限流使用 bounded local fallback，不能无限放行；subject HMAC 缺失 fail-closed；model admission Redis 缺失返回 `admission_store_unavailable`，不绕过预算。 | 检查 Redis 多副本、连接/租约、RDB/AOF 和 key family；按业务决定冻结模型/高风险写入，不清理未知 key。 | Redis probe UP、两副本限流窗口一致、admission reservation/settle 正常、模型 state map/topic 恢复；真实多副本证据 deployment-only。 |
| Milvus | 固定 collection existence/health、readiness `milvus`、`dependency_health`；见 `MilvusHealthIndicator.java:23-50`。 | 不用空 collection 或 mock 结果冒充检索恢复；检索/embedding 相关入口返回已有低敏失败分类或暂时阻断，是否允许 DB-only 功能由发布 runbook决定。 | 核对 collection schema/dimension/index/load，必要时按备份 runbook 做 export/import 或 derived rebuild；先保护 MySQL source of truth。 | collection schema/index/load、计数和关键只读检索验证通过；真实向量一致性属于 deployment-only。 |
| OSS | 上传/删除调用异常、业务失败日志；当前 readiness 列表没有 OSS，见 `application.yml:44-48`。 | 不把 object 写入成功伪造为成功；保留 DB 引用不变或将业务请求标为可重试，避免盲删/盲重传造成孤儿对象。当前无统一 OSS health gate。 | 检查 provider bucket/versioning/inventory/ACL，按对象引用和共享引用策略处理；必要时停止媒体写入。 | HEAD/list、版本/delete marker、引用对账和跨区域复制检查通过；`oss-inventory.ps1` 的 deployment-only 证据必须完成。 |
| 模型供应商 | modelGateway readiness、candidate phase、`model_call_failure_total`、AlertEvaluator 5 分钟失败率；见 `ModelGatewayHealthIndicator.java:39-81`、`AlertPolicy.java:23-25`。 | 由 ModelStateCenter 将失败 candidate 置 DOWN、到 probe 时间 HALF_OPEN；ModelProxy 在未输出前切换到可用 fallback；预算拒绝仍保持拒绝。 | 确认供应商状态、配额、凭据和 route tier；调整权重/启用备用供应商必须有配置版本和回滚引用，不能在事故中绕过 admission 或 Secret 红线。 | 连续成功达到配置 threshold、phase 回到 UP、失败率回落并通过只读模型 smoke；真实供应商配额校准与端到端调用是 deployment-only。 |
| 飞书 webhook | notifier 固定分类日志、队列/重试结果；没有 readiness contributor，见 `FeishuAlertNotifier.java:37-79`。 | 告警发送失败只有限重试和丢弃分类；业务、健康、模型和后台任务不等待 webhook；开关关闭时不创建调度器。 | 通过 Secret manager 注入并校验 `YUSI_ALERT_FEISHU_*`，检查机器人权限/限流和接收人；告警通道故障期间使用既定低敏人工升级路径。 | mock 只能证明 request contract；真实送达、接收人确认和消息延迟必须有 deployment-only 记录，不能把 mock PASS 写成送达成功。 |

## 7. 上线前置聚合清单

责任槽位使用角色而不是个人姓名；每项必须在 release record 中填入实际 operator/reviewer 和低敏证据引用。

| ID | 前置项 | 责任槽位 | 证据/完成条件 | 来源 |
| --- | --- | --- | --- | --- |
| OPS-01 | 发布 SHA 与回滚 SHA/digest 成组登记，三端兼容矩阵确认 | Release operator + platform owner | backend/frontend/MCP SHA、GitOps commit、旧版本引用齐全；不使用 latest 作为唯一标识 | `.github/workflows/deploy_k8s.yml:93-151`; 本文 §5.1 |
| OPS-02 | K8s/Ingress 灰度或滚动参数核验 | Platform/SRE | 外部 `yusi-infra` 的 replicas、probe、`maxUnavailable/maxSurge`、selector/权重和 rollout status；若缺失则只能滚动放量并标阻断 | `.github/workflows/deploy_k8s.yml:124-151`; `docs/devops/gitops_proposal.md:154-232`（仅建议） |
| OPS-03 | HMAC secret 注入 | Security owner + platform owner | `YUSI_RATE_LIMIT_HMAC_SECRET` 已注入且 subject-scoped 限流不因缺失而放行 | `application.yml:10-11`; `RateLimiterSubjectEncoder.java:17-58`; roadmap `:650-651` |
| OPS-04 | 管理端口网络隔离与 Prometheus 抓取 | Platform/SRE | `MANAGEMENT_SERVER_PORT`/`MANAGEMENT_SERVER_ADDRESS` 按部署策略绑定；只暴露 health/prometheus；真实 scraper 连通性和 allowlist 记录 | `application.yml:34-48`; `application-prod.yml:21-24`; roadmap `:622-626,650-653` |
| OPS-05 | MySQL 备份与恢复 RTO 演练 | DBA + backup owner | dump checksum、隔离目标库恢复、orphan/invariant、开始/完成时间和 RTO；未完成不得宣称备份恢复 PASS | backup runbook `:8-12,33-45`; roadmap `:636-637` |
| OPS-06 | Milvus/Redis/OSS 真实恢复与数据对账 | Data platform owner + DBA | 三 collection、Redis key-family、OSS version/inventory/引用对账均有真实记录；wrapper 的 `DEPLOYMENT-ONLY` 不能替代 | `ops/backup/milvus-backup.ps1:8-19`; `redis-backup.ps1:8-23`; `oss-inventory.ps1:8-15`; backup runbook `:35-41` |
| OPS-07 | 账号删除外部副本与 worker 竞态演练 | Privacy owner + data platform owner | 真实 Milvus/Redis/OSS 残留为零或按共享引用策略解释，worker 不重建，备份副本保留期有合规结论 | privacy runbook `:30-74`; roadmap `:638-643` |
| OPS-08 | 限流与成本准入 deployment-only 验证 | SRE + model platform owner | HMAC、HTTP/SSE/multipart/gateway 压测、Redis 多副本/故障、供应商 quota 校准、20611 allowlist、WebSocket/gRPC 均有记录 | rate runbook `:45-63`; roadmap `:644-653` |
| OPS-09 | 告警 Secret、真实飞书送达和轮值确认 | On-call owner + security owner | 只通过 `YUSI_ALERT_FEISHU_ENABLED`、`YUSI_ALERT_FEISHU_WEBHOOK_URL`、`YUSI_ALERT_FEISHU_SIGNING_SECRET` 注入；四类告警真实送达、去重、恢复和接收人确认 | `application-prod.yml:15-19`; alert plan `:208-215`; roadmap `:627-635` |
| OPS-10 | 模型/关键依赖故障演练 | Model platform owner + SRE | MySQL、Redis、Milvus、供应商故障下的 readiness、降级、恢复和告警记录；模型探针不以 mock 当端到端证据 | `ModelGatewayHealthIndicator.java:19-25`; alert plan `:212-215`; 本文 §6 |
| OPS-11 | migration 不可逆边界确认 | DBA + release operator | 已应用 schema 版本、expand/contract 顺序、兼容旧 SHA 的证明；破坏性变更有 backup/forward-fix 方案 | `application-prod.yml:313-321`; backup design `:40-43,265` |
| OPS-12 | 应急联系人、回滚权限和 incident 记录模板 | On-call owner + release reviewer | 低敏 incident ref、发布/回滚权限、旧 SHA、告警分类、RTO/恢复确认字段和复盘 reviewer 已确认 | roadmap `:627-635,636-653`; backup runbook `:43-45`; rate runbook `:65-85` |

上述清单中没有本地真实环境证据的项目全部为 `deployment-only`。本地 Maven、H2、Mockito、空 collection、静态配置读取和 workflow YAML 检查只能证明合同或静态边界，不得将清单项改写为生产 PASS。

## 8. 应急流程与低敏证据

### 8.1 发现与分级

1. On-call 从 readiness、`dependency_health`、model failure、task lag/due gap、budget denial 和 rate-limited 计数确认固定分类；告警阈值按 `AlertPolicy.initial()`，状态是“初始值，待生产调优”。
2. 若 readiness DOWN，先把它作为根告警处理，避免对同一依赖产生告警风暴；告警状态 store 的 root suppression 和 30 分钟 fingerprint 抑制见 `AlertScheduler.java:106-123`、`RedisAlertStateStore.java:19-25,37-90`。
3. 建立低敏 incident ref，记录分类、服务、窗口、计数、级别、观察时间、release SHA 和动作，不记录 userId、query、正文、token、对象 key、URL 或异常正文。

### 8.2 处置顺序

1. 先判断是否继续放量；满足 §5.3 任一条件即停止 promotion。
2. 模型问题先确认 candidate failover/phase 和 admission 是否仍在生效；不得通过关闭预算、清空 state 或放开 Secret 绕过保护。
3. Redis 问题确认限流 bounded local 和 admission denied 的实际状态；不得把本地 fallback 描述成多副本一致。
4. 数据层问题冻结写入并转入备份/恢复或删除隐私演练；只回滚镜像不能恢复已写数据。
5. 使用 §5.4 回滚步骤回到已验证 SHA；回滚结束后做 readiness、只读 smoke 和依赖完整性确认。
6. 飞书不可达时按固定分类重试/人工升级；notifier 故障不能阻塞 readiness。真实接收人和备用通信路径由 on-call 在 deployment-only 记录中确认。

### 8.3 告警通道开关与凭据

- 当前 `YUSI_ALERT_FEISHU_ENABLED` 默认 false，见 `application.yml:29-32`、`application-prod.yml:15-19`。开关打开前必须在 Secret manager 中注入 URL 和签名 secret；文档、代码、测试、日志和 incident record 不允许保存其值。
- `FeishuAlertProperties.toString()` 只输出 enabled/endpointConfigured/credentialConfigured 布尔信息，见 `FeishuAlertProperties.java:29-33`；通知器 `WebhookRequest.toString()` 只输出 payload/signature 是否存在，见 `FeishuAlertNotifier.java:139-144`。应急日志沿用这一边界。
- 本地 mock-contract-only 只能证明固定十字段 payload、签名存在性、重试/队列失败语义；真实 webhook 送达、轮值确认、限流和延迟永远记录为 deployment-only。

## 9. 证据模板与验收边界

```text
release_sha=<immutable-commit-sha>
previous_release_sha=<verified-previous-sha>
gitops_change_ref=<low-sensitivity-change-reference>
incident_ref=<opaque-reference-or-empty>
deployment_status=<NOT_RUN|PASS|BLOCKED>
rollout_mode=<rolling|canary|blue-green|NOT_RUN>
readiness_result=<UP|DOWN|NOT_RUN>
dependency_result=<classified-counts-only>
model_failure_window=<fixed-window-or-NOT_RUN>
task_backlog_result=<classified-counts-only>
budget_denial_result=<classified-counts-only>
feishu_delivery_result=<mock-contract-only|deployment-only|NOT_RUN>
backup_restore_rto=<elapsed-or-NOT_RUN>
data_integrity_result=<PASS|BLOCKED|NOT_RUN>
rollback_result=<PASS|NOT_RUN|NOT_REQUIRED>
operator_role=<role>
reviewer_role=<role>
observed_at_utc=<timestamp>
```

只有外部部署、数据平台、Secret、轮值和真实恢复证据齐全时，才能把 roadmap L654 对应的上线运维准备标为完成。本文不修改 roadmap；评审方在验收后决定是否更新。
