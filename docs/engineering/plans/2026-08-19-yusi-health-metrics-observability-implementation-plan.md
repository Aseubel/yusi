# Yusi Health Metrics Observability Implementation Plan

> **For agentic workers:** Execute this plan inline after design review. The repository instructions prohibit subagents and auto-review for this work. Steps use checkbox syntax; keep the implementation in the current worktree, use TDD, and stop before changing roadmap items.

**Goal:** 引入受安全边界约束的 actuator/Micrometer 可观测面，覆盖依赖健康、关键任务、低敏指标和 traceId 传播，并用测试证明管理端点隔离、标签白名单和业务行为不回归。

**Architecture:** 采用 Spring Boot actuator + Micrometer Prometheus registry；liveness/readiness 使用独立 health groups，自定义 contributor 适配实际 Redisson、Milvus、ModelStateCenter 和任务台账；指标由集中 binder/facade 注册并归一化固定标签；HTTP/SSE/gRPC/WebSocket/scheduler 在入口建立 traceId，现有 ThreadPoolConfig 只负责异步复制和清理。

**Tech Stack:** Java 21, Spring Boot 3.4.5, Spring Boot Actuator, Micrometer, Micrometer Prometheus registry, Hikari/JDBC, Redisson, Milvus SDK 2.6.16, JUnit 5, Mockito, Spring Boot test, Maven Wrapper.

## Global Constraints

- 不修改 roadmap、CI、migration、既有 Phase 4 fixture/loader/report、QualityGatePolicy 或 post-release backlog。
- 不启动服务或外部依赖；只运行 Maven focused/full tests。需要真实 Kubernetes、MySQL、Redis、Milvus、模型网关的验证必须记录为部署验收，不在本地测试中伪造为通过。
- 管理端点只允许 liveness、readiness 和内部 Prometheus；不暴露 env、beans、configprops、mappings、loggers、heapdump、threaddump、scheduledtasks、conditions 或 wildcard endpoint。
- health detail、日志和指标不得包含 query、keyword、正文、prompt、工具参数/结果、SQL、连接串、密码、cache key、模型响应、异常 message/stack、userId、requestId、runId、traceId 或动态 request 参数。
- 工具检索指标标签只允许工具名、操作名、结果分类、失败分类；模型/任务标签同样只允许固定配置/枚举白名单；未知值归一化为 unknown。
- liveness 不依赖外部依赖；readiness 才检查依赖和关键任务。模型健康检查默认不发起真实模型请求。
- traceId 只用于 MDC/响应关联，不作为 Micrometer tag 或持久化用户数据；所有入口和线程复用路径必须 finally 清理。
- 受控堆栈采样、最小告警通道、备份恢复和上线运维不在本计划实现。

## File Map

### New production files

- src/main/java/com/aseubel/yusi/observability/health/RedisHealthIndicator.java: 使用实际 Redisson client 的只读 ping contributor。
- src/main/java/com/aseubel/yusi/observability/health/MilvusHealthIndicator.java: 对固定集合执行只读 existence/health 检查 contributor。
- src/main/java/com/aseubel/yusi/observability/health/ModelGatewayHealthIndicator.java: 根据有效路由和 ModelStateCenter 判断必需 tier 是否有可用 candidate。
- src/main/java/com/aseubel/yusi/observability/health/TaskHealthIndicator.java: 汇总固定任务白名单的 pending/running/retry/failed 与最后成功状态。
- src/main/java/com/aseubel/yusi/observability/task/TaskHealthRegistry.java: 记录 scheduler/worker 的固定 task name 状态、最后成功时间和失败分类，不保存正文。
- src/main/java/com/aseubel/yusi/observability/metrics/YusiMetrics.java: 集中注册工具、模型、任务、依赖指标并强制标签白名单。
- src/main/java/com/aseubel/yusi/observability/trace/TraceIdSupport.java: inbound header 校验、生成、MDC 常量和清理辅助。
- src/main/java/com/aseubel/yusi/observability/trace/TraceIdWebFilter.java: HTTP/SSE traceId 注入与响应头回写。
- src/main/java/com/aseubel/yusi/observability/trace/TraceIdGrpcInterceptor.java: gRPC server 入口 traceId 注入/清理，按实际 grpc starter 注册 API 适配。
- src/main/java/com/aseubel/yusi/observability/trace/TraceIdWebSocketInterceptor.java: STOMP/WebSocket 消息入口 traceId 窄范围注入/清理。
- src/main/java/com/aseubel/yusi/config/ObservabilityConfig.java: health groups、management exposure、registry binder 和测试条件配置；不放业务逻辑。

### Modified production files

- pom.xml: 添加 spring-boot-starter-actuator 与 micrometer-registry-prometheus，不引入第二套 metrics framework。
- src/main/resources/application.yml: 管理端口/基础 endpoint exposure、health groups、Prometheus 路径和固定 health 阈值默认值。
- src/main/resources/application-prod.yml: 生产管理端口和网络隔离所需显式配置，禁止 details 显示；端口值通过环境变量注入，不写秘密。
- src/main/java/com/aseubel/yusi/config/ThreadPoolConfig.java: 保留当前 MDC 快照行为并补充 traceId 专项测试；只有在测试证明必要时增加 context 恢复而不改变 UserContext 语义。
- src/main/java/com/aseubel/yusi/common/task/DistributedJobRunner.java: 在固定 job name 边界创建/清理 scheduler traceId，并向 TaskHealthRegistry 报告 start/success/failure 分类；不传 throwable 或 message。
- src/main/java/com/aseubel/yusi/common/task/scheduler/YusiScheduledTasks.java: 对关键固定任务包裹 registry 记录；保持 cron、leader lock 和业务调用顺序。
- src/main/java/com/aseubel/yusi/service/ai/model/ModelStateCenter.java: 暴露只读低敏快照/候选状态所需接口，禁止暴露 lastError 原文；保持现有状态机。
- src/main/java/com/aseubel/yusi/service/ai/runtime/ModelCallTraceService.java 或模型调用发布边界：调用 YusiMetrics 记录低敏模型计数/延迟，不改变持久化事件。
- src/main/java/com/aseubel/yusi/service/ai/tool/DiarySearchTool.java、src/main/java/com/aseubel/yusi/service/memory/MidTermMemorySearchService.java、src/main/java/com/aseubel/yusi/grpc/McpGrpcServiceImpl.java、src/main/java/com/aseubel/yusi/service/lifegraph/LifeGraphTool.java: 在实际检索成功/空结果/失败边界调用工具指标，继续遵守已验收低敏日志政策。
- src/main/java/com/aseubel/yusi/config/WebSocketConfig.java 与实际 gRPC starter 配置位置：注册入口 interceptor；若 starter 要求 bean/annotation 注册，按已编译 API 采用最小适配，不扩展认证边界。
- src/main/java/com/aseubel/yusi/repository/EmbeddingTaskRepository.java、src/main/java/com/aseubel/yusi/repository/LifeGraphTaskRepository.java、src/main/java/com/aseubel/yusi/repository/TaskExecutionRepository.java: 仅补充固定 status 聚合查询，若现有方法不足以提供任务健康事实。
- src/test/resources/application-test.yml: 仅在 actuator 上下文测试需要时设置随机 management port 和 test-safe endpoint 配置。

### New tests

- src/test/java/com/aseubel/yusi/observability/health/HealthIndicatorTest.java
- src/test/java/com/aseubel/yusi/observability/health/HealthEndpointExposureTest.java
- src/test/java/com/aseubel/yusi/observability/metrics/YusiMetricsTest.java
- src/test/java/com/aseubel/yusi/observability/trace/TraceIdSupportTest.java
- src/test/java/com/aseubel/yusi/observability/trace/TraceIdWebFilterTest.java
- src/test/java/com/aseubel/yusi/observability/trace/AsyncTracePropagationTest.java
- src/test/java/com/aseubel/yusi/observability/task/TaskHealthRegistryTest.java
- src/test/java/com/aseubel/yusi/security/ObservabilitySensitiveDataTest.java: health/metrics/trace 低敏 sentinel 门槛；不新增 evaluator fixture/report。

## Task 1: Lock the failing safety and contract tests

**Files:**
- Create all new test files listed in File Map.
- Modify no production file in this task.

**Interfaces:**
- Tests define required public behavior for TraceIdSupport, YusiMetrics, TaskHealthRegistry, the five health components and endpoint exposure.

- [ ] **Step 1: Write tests that fail on missing observability classes and contracts.**

  Add assertions for:

  ~~~java
  assertThat(TraceIdSupport.acceptInbound("trace-abc_123")).isEqualTo("trace-abc_123");
  assertThat(TraceIdSupport.acceptInbound("fixture-query\n")).isNotEqualTo("fixture-query\n");
  assertThat(TraceIdSupport.isValid("x".repeat(129))).isFalse();
  assertThat(YusiMetrics.allowedSearchTags()).containsExactly("tool", "operation", "result", "failure_category");
  assertThat(YusiMetrics.allowedSearchTags()).doesNotContain("userId", "query", "traceId");
  ~~~

  Use fixed sentinels only in memory. Assert health details do not contain a URI, SQL, cache key, exception message, query, prompt or response sentinel.

- [ ] **Step 2: Run focused tests and capture the expected red result.**

  Run:

  ~~~powershell
  .\mvnw.cmd -q "-Dtest=HealthIndicatorTest,HealthEndpointExposureTest,YusiMetricsTest,TraceIdSupportTest,TraceIdWebFilterTest,AsyncTracePropagationTest,TaskHealthRegistryTest,ObservabilitySensitiveDataTest" test
  ~~~

  Expected: compilation failures for the not-yet-created production contracts. Do not weaken the sentinel assertions.

## Task 2: Add dependencies and management endpoint safety

**Files:**
- Modify pom.xml.
- Create/modify src/main/java/com/aseubel/yusi/config/ObservabilityConfig.java.
- Modify src/main/resources/application.yml and src/main/resources/application-prod.yml.
- Test HealthEndpointExposureTest.

**Interfaces:**
- management.server.port is separate from server.port in production and configurable by environment.
- Exposed actuator IDs are exactly health,prometheus; health groups are exactly liveness,readiness.

- [ ] **Step 1: Add the two managed dependencies.**

  Add only:

  ~~~xml
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
  </dependency>
  <dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
  </dependency>
  ~~~

- [ ] **Step 2: Configure the minimal endpoint surface.**

  Production configuration must include the equivalent of:

  ~~~yaml
  management:
    server:
      port: \${MANAGEMENT_SERVER_PORT:20611}
      address: \${MANAGEMENT_SERVER_ADDRESS:0.0.0.0}
    endpoints:
      web:
        exposure:
          include: health,prometheus
    endpoint:
      health:
        show-details: never
        probes:
          enabled: true
        group:
          liveness:
            include: livenessState
          readiness:
            include: readinessState,db,redis,milvus,modelGateway,tasks
  ~~~

  Do not put * in an exposure list. Test profile must remain external-dependency free and may set management port to 0 or use narrow mock contributors.

- [ ] **Step 3: Run endpoint/config tests.**

  Run:

  ~~~powershell
  .\mvnw.cmd -q "-Dtest=HealthEndpointExposureTest" test
  ~~~

  Expected: PASS with exact exposure assertions and no sensitive endpoint available. Do not start a service manually.

## Task 3: Implement dependency and task health contributors

**Files:**
- Create the five health/registry production files from File Map.
- Modify task scheduler/runner and model state files only as listed.
- Test HealthIndicatorTest and TaskHealthRegistryTest.

**Interfaces:**
- Every contributor returns only fixed detail keys: dependency, classification, observedAt only when needed; never raw exception text.
- TaskHealthRegistry.recordStart(String taskName), recordSuccess(String taskName), recordFailure(String taskName, String category) accept only fixed task names/categories and normalize unknown values.
- TaskHealthRegistry.snapshot() returns immutable low-sensitivity task states.

- [ ] **Step 1: Implement pure task registry behavior.**

  Use a fixed Set<String> task-name allowlist matching the central scheduler (usage-sync, memory-scan, room-cleanup, memory-fusion, proactive-greeting, embedding-cleanup, lifegraph-cleanup, task-execution-recovery, security-audit-cleanup, lifegraph-merge-suggestion, weekly-report, weekly-match) plus explicit worker names for embedding/lifegraph processing and model state sync. Unknown names become unknown and are never used as dynamic metric tags.

- [ ] **Step 2: Implement MySQL/Redis/Milvus probes with fixed failures.**

  MySQL uses the datasource/pool; Redis uses actual RedissonClient; Milvus uses a fixed collection and read-only SDK call. Catch runtime failures, classify timeout, connection_failure, or unavailable, and build Health.down().withDetail("dependency", fixedId).withDetail("classification", category) without attaching exception.

- [ ] **Step 3: Implement model gateway health from route candidates and ModelStateCenter.**

  Determine required tiers from effective routing config. A tier is healthy when at least one enabled member has no DOWN state or has an available state; it is unavailable only when every required candidate is DOWN/excluded. Never call ChatModel, EmbeddingModel or a network endpoint from a health check. Expose counts/categories, not model URLs or last errors.

- [ ] **Step 4: Implement task health using read-only counts and registry freshness.**

  Add repository count methods only for fixed status aggregates if existing methods cannot be reused. Keep task names fixed and make thresholds configurable. Pending/retry/failed over threshold or stale last success yields OUT_OF_SERVICE; no task IDs, source IDs or error messages appear.

- [ ] **Step 5: Run health-focused tests.**

  Run:

  ~~~powershell
  .\mvnw.cmd -q "-Dtest=HealthIndicatorTest,TaskHealthRegistryTest,ObservabilitySensitiveDataTest" test
  ~~~

  Expected: PASS with healthy/degraded/down cases, no network call from model probe, and no sensitive details.

## Task 4: Implement the metrics facade and instrument low-sensitivity boundaries

**Files:**
- Create src/main/java/com/aseubel/yusi/observability/metrics/YusiMetrics.java.
- Modify model trace publication and the four actual search components listed in File Map.
- Test YusiMetricsTest and existing search/model tests.

**Interfaces:**
- YusiMetrics.recordToolSearch(String tool, String operation, String result, String failureCategory, long durationMs, int resultCount).
- YusiMetrics.recordModelCall(String result, String failureCategory, long latencyMs), internally using the fixed operation value model_call.
- YusiMetrics.recordTask(String taskName, String status).
- All methods normalize values through fixed allowlists and never throw into business code.

- [ ] **Step 1: Write meter assertions before instrumentation.**

  Assert counters/timers/summaries increment and every meter's tag keys are within the four-key allowlist tool/operation/result/failure_category. Model, dependency and task names must be fixed operation values, not extra tags. Call the facade with sentinel user/query/request values through rejected dynamic inputs and assert no meter contains them.

- [ ] **Step 2: Register the metric instruments once.**

  Use MeterRegistry builders with stable names and fixed tag keys. Avoid creating a meter per request; use normalized fixed values and bounded tag combinations. Preserve the reserved search names and document Prometheus suffix behavior in tests.

- [ ] **Step 3: Instrument actual search success/empty/failure branches.**

  Time the existing call boundary with Timer.Sample; call recordToolSearch exactly once per operation branch, including empty results and exceptions. Preserve return values, fallback behavior, and the existing low-sensitivity logging policy.

- [ ] **Step 4: Instrument model and task boundaries.**

  Record model counters/latency from ModelCallAttemptEvent fields, with failure classification only. Record task state transitions from registry/scheduler. Do not use exception message, model response, user ID or request ID as tags.

- [ ] **Step 5: Run focused metrics and functional regression.**

  Run:

  ~~~powershell
  .\mvnw.cmd -q "-Dtest=YusiMetricsTest,DiarySearchToolTest,SensitiveQueryLogSafetyTest,SensitivePayloadLogSafetyTest,SensitiveExceptionLogSafetyTest" test
  ~~~

  Expected: PASS; search responses and existing safety sentinels remain unchanged.

## Task 5: Inject and propagate traceId through all entry points

**Files:**
- Create TraceIdSupport, TraceIdWebFilter, TraceIdGrpcInterceptor, TraceIdWebSocketInterceptor.
- Modify ThreadPoolConfig, WebSocketConfig, actual gRPC server registration, DistributedJobRunner, and YusiScheduledTasks as needed.
- Test TraceIdSupportTest, TraceIdWebFilterTest, AsyncTracePropagationTest and narrow entry-point tests.

**Interfaces:**
- MDC key is exactly traceId; response header is exactly X-Trace-Id.
- TraceIdSupport.withTraceId(...) always restores prior MDC context in finally.

- [ ] **Step 1: Implement and test inbound validation/generation.**

  Accept only a bounded token such as [A-Za-z0-9_-]{1,128}. Invalid input generates a UUID; tests cover newline, spaces, overlength, empty and sentinel values.

- [ ] **Step 2: Implement HTTP filter and response correlation.**

  Put traceId before downstream filters, add X-Trace-Id, and clear/restore MDC in finally. Test normal response and downstream exception.

- [ ] **Step 3: Preserve and test async MDC behavior.**

  Snapshot MDC at task submission, run with traceId, restore any worker baseline, then clear/restore after execution. Test two sequential tasks with different trace IDs and a thrown task.

- [ ] **Step 4: Cover gRPC, WebSocket and scheduler boundaries.**

  Use the gRPC starter's server interceptor registration verified against the local dependency API; use STOMP channel/handler scope for WebSocket; wrap each scheduler job invocation. Tests must be narrow and must not start external services.

- [ ] **Step 5: Run trace-focused tests.**

  Run:

  ~~~powershell
  .\mvnw.cmd -q "-Dtest=TraceIdSupportTest,TraceIdWebFilterTest,AsyncTracePropagationTest,AgentToolInvocationContextPropagationTest,AsyncTaskCorrelationTest" test
  ~~~

  Expected: PASS, no MDC leakage, no changed requestId/runId semantics.

## Task 6: Wire health groups, registry and integration safety tests

**Files:**
- Modify ObservabilityConfig, production/test YAML and any contributor registration files.
- Test HealthEndpointExposureTest, ObservabilitySensitiveDataTest, YusiApplicationTests only if existing context requires an explicit mock bean.

**Interfaces:**
- Contributor IDs are exact: db, redis, milvus, modelGateway, tasks.
- Group IDs are exact: liveness, readiness.

- [ ] **Step 1: Register contributors conditionally without external calls in test.**

  Use @Profile("!test") or @ConditionalOnProperty for production-only custom clients where necessary; test profile must provide mocks/disabled checks and still load the actuator context. Do not silently skip the contract test.

- [ ] **Step 2: Verify health response redaction.**

  Assert the response contains only fixed IDs/status/classification and rejects sentinels for password, URL, SQL, cache key, exception message, query and prompt. Assert liveness remains UP when all dependency mocks are DOWN.

- [ ] **Step 3: Verify Prometheus endpoint and metric tag surface.**

  Assert the output contains the reserved tool metric names and no forbidden tag key/value. Confirm unknown labels collapse to unknown.

- [ ] **Step 4: Run the full existing test suite.**

  Run:

  ~~~powershell
  .\mvnw.cmd -q test
  ~~~

  Expected: exit code 0. No evaluation fixture/report or QualityGatePolicy file may change.

## Task 7: Final audit and handoff

**Files:**
- Modify only observability implementation/tests if audit reveals a defect; no roadmap or evaluation files.

- [ ] **Step 1: Run source and configuration audits.**

  ~~~powershell
  rg -n "management\.endpoints\.web\.exposure|show-details|health\.group|micrometer|actuator" pom.xml src/main/resources src/main/java
  rg -n "MDC\.put|MDC\.clear|traceId|X-Trace-Id" src/main/java src/test/java
  rg -n "userId|requestId|runId|traceId|query|prompt|content|message|cache|sourceId" src/main/java/com/aseubel/yusi/observability src/test/java/com/aseubel/yusi/observability
  .\mvnw.cmd -q "-DskipTests" validate
  git diff --check
  ~~~

  Classify every hit. The observability source must have zero direct forbidden values in metric tags or health details; tests may mention sentinels only in memory assertions.

- [ ] **Step 2: Record deployment-only checks without claiming local pass.**

  Handoff must separately list: management port network policy, Prometheus scrape identity, real MySQL/Redis/Milvus connectivity, model provider end-to-end availability, and task threshold tuning. These require deployment environment verification and are not local Maven evidence.

- [ ] **Step 3: Check roadmap before any future commit.**

  Confirm that the Phase 5 health/metrics checkbox remains unchecked during this slice. Do not edit it; the review owner decides when the focused and deployment evidence is accepted.

- [ ] **Step 4: Commit only after review approval.**

  Suggested commit:

  ~~~powershell
  git add pom.xml src/main/java/com/aseubel/yusi/observability src/main/java/com/aseubel/yusi/config src/main/java/com/aseubel/yusi/common/task src/main/java/com/aseubel/yusi/service/ai/model src/main/java/com/aseubel/yusi/service/ai/runtime src/main/java/com/aseubel/yusi/service/ai/tool src/main/java/com/aseubel/yusi/service/memory src/main/java/com/aseubel/yusi/grpc src/main/resources src/test/java/com/aseubel/yusi/observability src/test/java/com/aseubel/yusi/security/ObservabilitySensitiveDataTest.java docs/engineering/specs/2026-08-19-yusi-health-metrics-observability-design.md docs/engineering/plans/2026-08-19-yusi-health-metrics-observability-implementation-plan.md
  git commit -m "ops: add health metrics observability gate"
  ~~~

  Stop after commit and report focused/full exit codes, endpoint/health/metric audit evidence, deployment-only checks, and commit hash. Do not start the alert, backup/recovery, stack-sampling or roadmap slices.
