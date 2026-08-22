# Yusi API 鉴权与低敏 Trace 边界实施计划

> For implementation: execute the tasks in order with TDD and a review checkpoint after each task. This plan does not require a subagent; all local evidence must retain its `application-invariant-only` or `mock-contract-only` label.

**Goal:** 用 MockMvc application-invariant-only 回归锁定 HTTP 认证、资源归属和管理员边界，并用 mock-contract-only 静态/写入点契约锁定 AgentRun、AgentTool、ModelCall Trace 与关联日志的低敏字段集合。

**Architecture:** 保留当前 `@Auth` + `AuthAspect` + `UserContext` 认证链，不引入 Spring Security 或新的认证层。越权回归用 H2 合成用户和真实 Controller/Service 边界；Trace 复查把 entity schema、repository save 投影、ListAppender 日志事件和任务错误字段分别校验，禁止把 mock 结果解释为真实依赖或部署渗透结果。

**Tech Stack:** Spring Boot 3.x、JUnit 5、MockMvc、Mockito、H2、AssertJ/JUnit assertions、JPA entity/repository、Logback ListAppender、现有 `SensitiveLogSourceAuditTest`/`SensitiveExceptionLogSafetyTest`/Observability 套件。

## Global Constraints

- 路由统计固定复用 `RateLimitCoverageContractTest.java:240-266` 扫描规则，当前基线为 22 Controller、158 映射、90 写、68 读，并排除注释 `POST /api/match/run`。
- 所有越权本地结果标 `application-invariant-only`；Trace 与日志替身结果标 `mock-contract-only`；不得报告真实 HTTP 渗透、真实日志采集或真实依赖验证 PASS。
- 先红后绿；不得删除、放宽或改写既有 Sensitive*/Observability/QualityGate 断言，不得用 `contains` 替代完整字段集合、完整 forbidden sentinel 或固定错误码断言。
- fixture 只使用 `fixture-*` 脱敏 ID/文本，不使用自然语言正文、真实 query、token、完整 object key 或真实用户资料。
- 确认的 AUTHZ-001 必须作为必修缺陷修复；AUTHZ-CANDIDATE-001 在目标关系语义确定前只能记录当前行为，不得擅自把候选改成已修复结论。
- 不修改 roadmap、评测套件、QualityGatePolicy、CI、migration、部署配置或 Phase 5 其他已验收切片文件；本计划允许的生产修改仅限确认的 prompt 入口授权修复及 trace 低敏修复，并在下一轮按本计划执行。
- 不使用真实 MySQL、Redis、Milvus、OSS、模型网关、WebSocket broker 或外部日志平台；任何真实环境步骤均列为 deployment-only。

---

### Task 1: 固化静态映射与鉴权矩阵契约

**Files:**
- Create: `src/test/java/com/aseubel/yusi/security/AuthzCoverageContractTest.java`
- Reference: `src/test/java/com/aseubel/yusi/common/ratelimit/RateLimitCoverageContractTest.java:240-266`
- Reference: `src/main/java/com/aseubel/yusi/common/auth/AuthAspect.java:42-122`

**Interfaces:**
- Consumes: Controller source tree, existing mapping scanner, `@Auth` annotation and `UserContext` contract.
- Produces: exact counts and an endpoint-to-auth classification that Task 2 can reuse without duplicating a second route parser.

- [ ] **Step 1: Write the failing contract test**

  Reuse the existing `Mapping` parser shape and assert:

  ```java
  assertThat(scanMappings()).hasSize(158);
  assertThat(scanMappings().stream().filter(this::isWrite).count()).isEqualTo(90);
  assertThat(scanMappings().stream().filter(this::isRead).count()).isEqualTo(68);
  assertThat(scanMappings()).noneMatch(mapping ->
          mapping.endpoint().equals("POST /api/match/run"));
  assertThat(requiredAuthMappings()).hasSize(146);
  assertThat(authContractMappings()).hasSize(154);
  assertThat(unannotatedPublicMappings()).containsExactlyInAnyOrder(
          "GET /api/geo/search", "GET /api/geo/reverse",
          "GET /api/health", "POST /api/suggestions");
  ```

  Also assert the exact controller count map: `AdminController=18`, `AiController=12`, `DeveloperConfigController=4`, `DiaryController=6`, `GeoController=2`, `ImageController=10`, `KeyManagementController=6`, `LifeGraphController=16`, `MatchController=8`, `MemoryCenterController=9`, `ModelManagementController=6`, `NotificationController=6`, `PingController=1`, `PromptController=6`, `RoomChatController=3`, `SituationRoomController=16`, `SoulChatController=4`, `SoulPlazaController=10`, `StatsController=1`, `SuggestionController=2`, `UserController=8`, `UserLocationController=4`.

- [ ] **Step 2: Run the focused contract before implementation changes**

  Run: `./mvnw -q "-Dtest=AuthzCoverageContractTest" test`

  Expected: PASS against the current source baseline. If it fails, stop and reconcile the scanner or source mapping with the exact `RateLimitCoverageContractTest` rule; do not change production code to make a count assertion vague.

- [ ] **Step 3: Implement only the test-side scanner and classification**

  Keep the parser in the test package, ignore comment-only mapping lines, and classify class-level `@Auth`, method-level `@Auth(required=false)`, and no annotation. Do not copy a new business authorization implementation into the test.

- [ ] **Step 4: Re-run the contract**

  Run the same focused command. Expected: PASS with exactly 158/90/68, 146 required-auth mappings, 154 auth-contract mappings, and four unannotated public mappings. The output remains a source contract, not runtime coverage.

### Task 2: Write red MockMvc authorization regressions

**Files:**
- Create: `src/test/java/com/aseubel/yusi/security/AuthzBoundaryMockMvcTest.java`
- Reference: `src/main/java/com/aseubel/yusi/common/auth/AuthAspect.java:60-122`
- Reference: `src/main/java/com/aseubel/yusi/controller/PromptController.java:27-50`
- Reference: `src/main/java/com/aseubel/yusi/controller/DiaryController.java:36-74`
- Reference: `src/main/java/com/aseubel/yusi/service/match/impl/MatchServiceImpl.java:891-897`
- Reference: `src/main/java/com/aseubel/yusi/service/room/impl/SituationRoomServiceImpl.java:318-322`

**Interfaces:**
- Consumes: Task 1 mapping/auth contract, H2 repositories, `UserContext` and existing Controller advice.
- Produces: named red tests for horizontal, vertical and anonymous access that Task 3 can fix without weakening assertions.

- [ ] **Step 1: Write the vertical red test for AUTHZ-001**

  Build a non-admin fixture-user-authz, mock a valid access token accepted by the existing auth test seam, call `GET /api/prompt/fixture-prompt-authz`, and verify the current service invocation. The first assertion must make the defect visible: current behavior reaches `promptService.getPrompt(...)` for a non-admin. Do not call this a pass.

- [ ] **Step 2: Write admin namespace and unauthenticated tests**

  Add exact fixed-code assertions for non-admin `GET /api/admin/me`, `GET /api/model/states`, and `GET /api/prompt/search`; add anonymous `GET /api/diary/{diaryId}`, `GET /api/ai/chat/history`, and `GET /api/model/states`. Verify service mocks are never called after the auth/role rejection.

- [ ] **Step 3: Write horizontal ownership tests**

  Use two H2 fixture users and no natural-language data. Cover diary detail, match action/history, room history, situation-room detail/report, image URL/delete with an object-key prefix, lifegraph entity/relation, notification read/delete, and plaza card update/delete. Assert fixed forbidden/not-found semantics and repository state unchanged for the other user.

- [ ] **Step 4: Write the signal candidate observation test**

  Capture `ResonanceSignalService.sendSignal` arguments for fixture-user-authz, fixture-user-other-authz and fixture-card-authz. Assert the current call path exactly; do not assert a new owner rule until the product/security decision records that `cardId` owner must equal `toUserId`.

- [ ] **Step 5: Run the focused red suite**

  Run: `./mvnw -q "-Dtest=AuthzCoverageContractTest,AuthzBoundaryMockMvcTest" test`

  Expected: non-zero because AUTHZ-001 is present. The failure must identify the non-admin prompt read, not be hidden by broad status assertions. H2 output is labeled `application-invariant-only`.

### Task 3: Fix confirmed authorization defect and turn MockMvc tests green

**Files:**
- Modify: `src/main/java/com/aseubel/yusi/controller/PromptController.java:45-50`
- Modify: `src/test/java/com/aseubel/yusi/security/AuthzBoundaryMockMvcTest.java`
- Test: `src/test/java/com/aseubel/yusi/security/AuthzBoundaryMockMvcTest.java`

**Interfaces:**
- Consumes: AUTHZ-001 red test and existing private `checkAdmin()` helper.
- Produces: admin-only prompt read contract; no new authorization framework.

- [ ] **Step 1: Add the minimum authorization call**

  Call the existing `checkAdmin()` at the start of `getPrompt(...)`. Preserve route, validation, locale normalization, service signature and existing fixed forbidden response. Do not move Controller methods or alter public User/Geo/health allowlist.

- [ ] **Step 2: Turn only the confirmed red assertion into a fixed contract**

  Assert non-admin receives the existing forbidden code and `promptService.getPrompt(...)` is never called; assert admin reaches the service exactly once. Keep the candidate signal test observational.

- [ ] **Step 3: Run the focused authorization suite**

  Run: `./mvnw -q "-Dtest=AuthzCoverageContractTest,AuthzBoundaryMockMvcTest" test`

  Expected: PASS, with no change to the 158/90/68 scanner counts and no service call after forbidden paths.

- [ ] **Step 4: Run existing authorization-adjacent regression tests**

  Run: `./mvnw -q "-Dtest=SensitiveLogSourceAuditTest,SensitiveExceptionLogSafetyTest,ObservabilitySensitiveDataTest" test`

  Expected: PASS; existing low-sensitivity and observability contracts remain unchanged.

### Task 4: Write the red Trace field and write-point contract

**Files:**
- Create: `src/test/java/com/aseubel/yusi/security/TraceBoundarySensitiveDataTest.java`
- Reference: `src/main/java/com/aseubel/yusi/pojo/entity/AgentToolTrace.java:25-116`
- Reference: `src/main/java/com/aseubel/yusi/pojo/entity/AgentRunTrace.java:23-90`
- Reference: `src/main/java/com/aseubel/yusi/pojo/entity/ModelCallTrace.java:44-132`
- Reference: `src/main/java/com/aseubel/yusi/service/ai/runtime/ModelCallAttemptEvent.java:5-29`

**Interfaces:**
- Consumes: exact allowed metadata field sets and existing repository/service tests.
- Produces: complete forbidden field/value contract for Task 5 fixes and logger cleanup.

- [ ] **Step 1: Define exact allowlists and forbidden sentinel set**

  Use a `Set<String>` equality assertion for the declared fields. The forbidden value set is exactly: `fixture-user-authz`, `fixture-query-authz`, `fixture-content-authz`, `fixture-token-authz`, `fixture-object-key-authz`, `fixture-prompt-authz`, `fixture-response-authz`, `fixture-input-authz`, `fixture-output-authz`, `fixture-exception-authz`, `fixture-message-authz`. Include field-name bans for `payload`, `query`, `content`, `prompt`, `response`, `input`, `output`, `arguments`, `results`, and `objectKey`; do not use partial `contains` to determine the field set.

- [ ] **Step 2: Add repository-save capture tests**

  Capture `AgentToolTraceRepository.save`, `AgentRunTraceRepository.save`, and `ModelCallTraceRepository.save`. Feed metadata-only events plus forbidden fixture values through the existing service APIs. Assert exact approved field values and that no saved object string/field projection contains a forbidden value.

- [ ] **Step 3: Add static associated-log inventory assertions**

  Scan only the named write points: `AiController.java:449,642,653`, `AgentRunTraceService.java:137`, `AgentToolExecutionAttemptRegistry.java:72`, `AgentToolIdempotencyMaintenance.java:26,36`, `ModelCallTraceService.java:44-45`, `DiaryVoiceWebSocketHandler.java:270-271`, `PlazaLifeGraphListener.java:93-94`, and `LifeGraphTaskBatchService.java:178,221`. Require each production logger projection to use fixed operation/category plus exception type, and require task retry error projection to exclude message text. Use exact location assertions so a new unreviewed logger cannot disappear from the inventory.

- [ ] **Step 4: Run the focused Trace contract to obtain red evidence**

  Run: `./mvnw -q "-Dtest=TraceBoundarySensitiveDataTest,AgentToolTraceServiceTest,AgentRunTraceServiceTest,ModelCallTraceServiceTest" test`

  Expected: non-zero for any unreviewed throwable/message or task error field. This is `mock-contract-only`; do not weaken a sentinel or turn a full field rejection into `contains`.

### Task 5: Converge Trace persistence and associated logs

**Files:**
- Modify only after the red contract identifies the exact finding: `src/main/java/com/aseubel/yusi/service/ai/runtime/ModelCallTraceService.java:40-45`, `src/main/java/com/aseubel/yusi/controller/AiController.java:448-453,635-654`, `src/main/java/com/aseubel/yusi/service/ai/runtime/AgentRunTraceService.java:134-138`, `src/main/java/com/aseubel/yusi/service/ai/runtime/AgentToolExecutionAttemptRegistry.java:69-73`, `src/main/java/com/aseubel/yusi/service/ai/runtime/AgentToolIdempotencyMaintenance.java:24-36`, `src/main/java/com/aseubel/yusi/controller/DiaryVoiceWebSocketHandler.java:267-272`, `src/main/java/com/aseubel/yusi/service/lifegraph/PlazaLifeGraphListener.java:89-94`, `src/main/java/com/aseubel/yusi/service/lifegraph/LifeGraphTaskBatchService.java:176-221`
- Modify: `src/test/java/com/aseubel/yusi/security/TraceBoundarySensitiveDataTest.java`
- Extend: `src/test/java/com/aseubel/yusi/service/ai/runtime/AgentToolTraceServiceTest.java`, `AgentRunTraceServiceTest.java`, `ModelCallTraceServiceTest.java`

**Interfaces:**
- Consumes: Task 4 exact failing locations and existing `LowSensitivityLogSummary` helpers.
- Produces: low-sensitivity logger projections, metadata-only Trace persistence, and a fixed task error classification contract.

- [ ] **Step 1: Replace message/throwable projections with low-sensitivity summaries**

  For each failing associated logger, retain operation, bounded length/count where useful, and `LowSensitivityLogSummary.exceptionType(exception)` only. Do not pass `Throwable`, `getMessage()`, request payload, tool request, model response, query, token, user ID, or full object key to logger arguments. For task retry persistence, store a fixed failure category/exception type projection rather than `exception.getMessage()`.

- [ ] **Step 2: Preserve Trace entity contracts**

  Keep AgentTool/AgentRun/ModelCall entity fields metadata-only. If an event or request object includes prompt/message/response/input/output, project only approved count/category fields before repository save; never add a new raw field to make a test pass.

- [ ] **Step 3: Re-run the focused Trace suite**

  Run the Task 4 focused command. Expected: PASS with all forbidden values absent, exact field sets unchanged, no throwable proxies for the audited logger events, and task error fields free of fixture-message-authz. Result label remains `mock-contract-only`.

- [ ] **Step 4: Re-run the existing sensitive/trace suites**

  Run: `./mvnw -q "-Dtest=SensitiveLogSourceAuditTest,SensitiveExceptionLogSafetyTest,ObservabilitySensitiveDataTest,AgentToolTraceCorrelationTest,AgentToolInvocationContextPropagationTest,TraceIdWebFilterTest,TraceIdSupportTest,AsyncTracePropagationTest" test`

  Expected: PASS with all existing sentinel and trace-id propagation assertions intact.

### Task 6: Add the explicit boundary report contract

**Files:**
- Modify: `src/test/java/com/aseubel/yusi/security/AuthzBoundaryMockMvcTest.java`
- Modify: `src/test/java/com/aseubel/yusi/security/TraceBoundarySensitiveDataTest.java`
- Reference only: `docs/engineering/specs/2026-08-22-yusi-authz-trace-boundary-design.md`

**Interfaces:**
- Consumes: green local tests and the design's confirmed/candidate/deployment-only classifications.
- Produces: machine-readable test labels and a handoff summary that cannot merge local mock evidence with deployment evidence.

- [ ] **Step 1: Assert evidence labels**

  Require authorization test output/report metadata to contain `application-invariant-only` and Trace output/report metadata to contain `mock-contract-only`; require that neither contains `full-path`, `delivery-success`, `real-penetration`, or equivalent deployment claims.

- [ ] **Step 2: Assert defect and candidate status**

  Record AUTHZ-001 as fixed only after the non-admin prompt test proves no service call. Record AUTHZ-CANDIDATE-001 as `candidate-unresolved` until the target relationship decision is supplied; do not convert it to PASS by observing a mock.

- [ ] **Step 3: Run the focused aggregate command**

  Run: `./mvnw -q "-Dtest=AuthzCoverageContractTest,AuthzBoundaryMockMvcTest,TraceBoundarySensitiveDataTest" test`

  Expected: PASS with exact route counts, attack-surface labels, zero confirmed authz defects, and any candidate/deployment-only item listed explicitly.

### Task 7: Full verification and handoff

**Files:**
- No additional files; only the files listed in Tasks 1-6 may be changed.

- [ ] **Step 1: Run the full test suite**

  Run: `./mvnw -q test`

  Expected: exit code 0; existing Sensitive*, Observability*, QualityGate* and trace suites do not regress.

- [ ] **Step 2: Run low-sensitivity static checks**

  Use the repository's available PowerShell search equivalent to scan only changed test/source files for real secrets, webhook values, non-fixture user data, query/body text, token values, complete object keys, raw prompt/response/input/output and `getMessage()`/Throwable logger arguments. Report every hit as field-name reference, fixture sentinel, approved exception type summary, or deployment-only documentation reference.

- [ ] **Step 3: Verify the change boundary**

  Run: `git diff --check` and `git status --short`.

  Expected: no whitespace errors; no roadmap, CI, migration, evaluation, QualityGatePolicy, or unrelated Phase 5 file changes.

- [ ] **Step 4: Stop at deployment-only handoff**

  Do not run deployment commands or start services. Hand off the unresolved real-environment items: two-user JWT penetration, proxy/management-port policy, WebSocket handshake/topic tests, real log collector redaction, and real Milvus/Redis/OSS/model/worker cross-user Trace residue checks.

## Verification Checklist

- [ ] 22 Controller / 158 mapping / 90 write / 68 read contract remains exact.
- [ ] 146/158 required-auth mapping count and 154/158 auth-contract count remain exact.
- [ ] AUTHZ-001 prompt read is denied for non-admin and does not call prompt service.
- [ ] Horizontal, vertical and anonymous cases are fixed-code assertions; candidate signal semantics are not misreported.
- [ ] Trace entity/event field sets remain exact metadata-only sets.
- [ ] Forbidden fixture fields and values are all absent from save projections, logger text, throwable text and task error fields.
- [ ] `application-invariant-only` and `mock-contract-only` labels remain separate.
- [ ] Full test suite and `git diff --check` pass; roadmap remains unchanged.
