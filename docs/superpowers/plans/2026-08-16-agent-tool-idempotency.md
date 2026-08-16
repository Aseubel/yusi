# Agent Tool 幂等账本 Implementation Plan

> **For agentic workers:** This plan is executed inline in the current workspace. Do not use subagents. Each task follows TDD and ends with a focused verification checkpoint.

**Goal:** 为声明幂等的写工具建立与 `localToolCallId/tool_call_id` 共用 key 的持久化 claim 账本，阻止并发和重放副作用，并让写工具超时进入 `UNKNOWN` 而不自动重试。

**Architecture:** 选择方案 3，扩展既有 `agent_tool_trace`，不创建第二个逻辑 ID 或独立 ledger key。Controller 生成本地调用 ID 并注册低敏 `AgentToolInvocationContext`；统一 `AgentToolExecutionPolicyExecutor` 在 delegate 前 claim、在 worker 中传播 Context、在结果或异常后收敛账本。READ 工具保留最多一次超时重试，WRITE/UNKNOWN 在访问模式判断处强制禁止 retry。

**Tech Stack:** Java 21, Spring Boot 3.4.5, Spring Data JPA, MySQL migration SQL, LangChain4j 1.18.0, JUnit 5, Mockito。

## Global Constraints

- `tool_call_id`、`localToolCallId` 和 ledger key 必须是同一值；不得新增独立 `idempotency_key`。
- 账本只覆盖能力目录明确声明 `IDEMPOTENT_WRITE` 的写工具；默认 `NONE`，未知工具默认 `UNKNOWN + DENY`。
- `WRITE` 和 `UNKNOWN` 不自动超时重试；只有 `READ` 能力允许最多 1 次 `TIMEOUT_ONCE`。
- 账本 `UNKNOWN` 永不自动重放；claim 并发失败方必须返回 error 标记的标准响应，不能静默成功或调用 delegate。
- `AgentRunTraceService.complete/fail/cancel` 继续收敛 RUNNING 工具；仍为 CLAIMED 的幂等账本必须转为 `UNKNOWN`。
- `agent_run_trace.tool_count` 仍表示完成的逻辑工具调用总数，不按物理尝试、claim 或 ledger 行数增加。
- Context 技术验证先于账本接入；验证失败时才退回显式携带 `ModelRouteContextHolder`，不得依赖隐式 ThreadLocal 继承。
- 账本 claim lease 为 5 分钟，状态保留 30 天；应用启动扫描过期孤儿 `CLAIMED` 为 `UNKNOWN`，不接管执行。
- `updateUserPersona` 是唯一端到端验收对象；不接受只验证空包装或只验证能力目录的实现。
- 不启动服务，不修改 `McpGrpcServiceImpl` 原始 `keyword/query` 日志，不保存参数、query、结果正文、Prompt 或异常 message。

---

### Task 1: Capability and Response Contract

**Files:**
- Create: `src/main/java/com/aseubel/yusi/service/ai/tool/constant/AgentToolIdempotencyMode.java`
- Create: `src/main/java/com/aseubel/yusi/service/ai/tool/constant/AgentToolIdempotencyConstants.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/tool/AgentToolCapability.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/tool/AgentToolCapabilityCatalog.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/tool/constant/AgentToolCapabilityConstants.java`
- Test: `src/test/java/com/aseubel/yusi/service/ai/tool/AgentToolCapabilityCatalogTest.java`

**Interfaces:**
- `AgentToolIdempotencyMode { NONE("none"), IDEMPOTENT_WRITE("idempotent_write") }` exposes `code()`.
- `AgentToolCapability.idempotencyMode()` returns the immutable declaration and defaults to `NONE` in the existing compatibility constructor.
- `AgentToolCapabilityCatalog.METADATA_IDEMPOTENCY_MODE` is exposed in mapped MCP specifications.
- `AgentToolIdempotencyConstants` owns the 5-minute lease, 30-day retention, and stable model responses: `IN_PROGRESS`, `ALREADY_COMPLETED`, `PREVIOUS_FAILURE`, `UNKNOWN`, `CONTEXT_MISSING`.

- [ ] **Step 1: Write the failing tests**

Extend `AgentToolCapabilityCatalogTest` with these assertions:

```java
@Test
void updatePersonaDeclaresIdempotentWriteAndMcpMetadataExposesTheMode() {
    AgentToolCapabilityCatalog catalog = new AgentToolCapabilityCatalog(new ObjectMapper());
    catalog.registerLocal(new PersonaTool());

    AgentToolCapability capability = catalog.findByName(AgentToolConstants.UPDATE_USER_PERSONA)
            .orElseThrow();
    assertEquals(AgentToolAccessMode.WRITE, capability.accessMode());
    assertEquals(AgentToolIdempotencyMode.IDEMPOTENT_WRITE, capability.idempotencyMode());
    assertEquals(AgentToolRetryPolicy.DENY, capability.retryPolicy());
}

@Test
void unknownAndLegacyCapabilitiesDefaultToNoIdempotency() {
    AgentToolCapability capability = new AgentToolCapability(
            "legacy", AgentToolConstants.SOURCE_LOCAL, "v1", "test", "{}", Set.of(),
            new AgentToolExecutionPolicy(Duration.ofSeconds(1)));
    assertEquals(AgentToolIdempotencyMode.NONE, capability.idempotencyMode());
}
```

`PersonaTool` must contain an actual `@Tool(name = AgentToolConstants.UPDATE_USER_PERSONA, value = "Update persona")` method so the test exercises schema registration rather than a name-only mock.

- [ ] **Step 2: Run the focused test to verify it fails**

Run:

```powershell
.\mvnw.cmd -q "-Dtest=AgentToolCapabilityCatalogTest" test
```

Expected: compilation failure for the missing idempotency mode and missing record field.

- [ ] **Step 3: Implement the contract**

Add the enum and constants. Append `AgentToolIdempotencyMode idempotencyMode` to `AgentToolCapability`; preserve the existing 7-argument and 9-argument constructors by delegating to `NONE`. In `AgentToolCapabilityCatalog`, map only `updateUserPersona` to `IDEMPOTENT_WRITE`; all other tools return `NONE`. Add the mode to `ToolSpecification.metadata()` using the existing capability metadata pattern. Do not infer idempotency from a tool description or parameter schema.

- [ ] **Step 4: Run the focused test to verify it passes**

Run the same Maven command. Expected: PASS, including the pre-existing read/MCP retry assertions.

- [ ] **Step 5: Commit the contract slice**

```powershell
git add src/main/java/com/aseubel/yusi/service/ai/tool src/test/java/com/aseubel/yusi/service/ai/tool/AgentToolCapabilityCatalogTest.java
git commit -m "feat: declare agent tool idempotency capability"
```

### Task 2: Invocation Context Propagation Verification

**Files:**
- Create: `src/main/java/com/aseubel/yusi/service/ai/runtime/AgentToolInvocationContext.java`
- Create: `src/main/java/com/aseubel/yusi/service/ai/runtime/AgentToolInvocationContextProvider.java`
- Create: `src/main/java/com/aseubel/yusi/service/ai/runtime/AgentToolInvocationContextHolder.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/runtime/AgentToolExecutionAttemptRegistry.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/runtime/AgentToolExecutionPolicyExecutor.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/tool/AgentToolExecutionPolicyService.java`
- Create: `src/test/java/com/aseubel/yusi/service/ai/runtime/AgentToolInvocationContextPropagationTest.java`
- Modify: `src/test/java/com/aseubel/yusi/service/ai/runtime/AgentToolExecutionAttemptRegistryTest.java`

**Interfaces:**
- `AgentToolInvocationContext` is an immutable record containing `userId`, `runId`, `localToolCallId`, `toolName`, `toolSource`, `AgentToolAccessMode`, `AgentToolIdempotencyMode`, and `capabilityVersion`.
- `AgentToolInvocationContextProvider.find(Object requestIdentity)` returns an `Optional<AgentToolInvocationContext>`; the provider has a `NOOP` implementation.
- `AgentToolInvocationContextHolder.current()` returns the worker-local Context; `open(context)` returns an idempotent `Scope` that clears it in `close()`.
- `AgentToolExecutionAttemptRegistry` stores the Context under request object identity while preserving its existing retry observer and cleanup methods.
- `AgentToolExecutionPolicyExecutor` captures the provider result before submitting to the dedicated pool and opens the holder only around delegate execution; the `finally` block always clears it.

- [ ] **Step 1: Write the failing propagation test**

Create a real worker propagation test:

```java
@Test
void contextIsCapturedBeforeSubmitAndVisibleOnlyDuringDelegate() {
    ExecutorService workers = Executors.newFixedThreadPool(1);
    ToolExecutionRequest request = ToolExecutionRequest.builder().name("testTool").build();
    AgentToolInvocationContext context = new AgentToolInvocationContext(
            "user-1", "run-1", "local-1", "testTool", "local",
            AgentToolAccessMode.READ, AgentToolIdempotencyMode.NONE, "v1");
    AgentToolInvocationContextProvider provider = identity ->
            identity == request ? Optional.of(context) : Optional.empty();
    AtomicReference<AgentToolInvocationContext> seen = new AtomicReference<>();

    ToolExecutor delegate = (ignored, memoryId) -> {
        seen.set(AgentToolInvocationContextHolder.current());
        return "ok";
    };
    AgentToolExecutionPolicyExecutor executor = new AgentToolExecutionPolicyExecutor(
            delegate, new AgentToolExecutionPolicy(Duration.ofSeconds(1)),
            AgentToolRetryPolicy.DENY, AgentToolAccessMode.READ,
            AgentToolIdempotencyMode.NONE, workers,
            AgentToolExecutionAttemptObserver.NOOP, provider, null, "testTool");

    assertEquals("ok", executor.execute(request, "user-1"));
    assertEquals(context, seen.get());
    assertNull(AgentToolInvocationContextHolder.current());
    workers.shutdownNow();
}
```

Add a registry assertion that `registry.find(request)` returns the same `localToolCallId` as the retry trace entry and becomes empty after `complete` or `clearRun`.

- [ ] **Step 2: Run the focused test to verify it fails**

Run:

```powershell
.\mvnw.cmd -q "-Dtest=AgentToolInvocationContextPropagationTest,AgentToolExecutionAttemptRegistryTest" test
```

Expected: compilation failure because Context, provider and the extended executor constructor do not exist.

- [ ] **Step 3: Implement explicit worker propagation**

Add the record, provider, and holder with no arguments/results stored. Extend the registry `register` path to create one Context from the Controller-supplied capability fields and expose it through `find`. Modify the executor to keep backward-compatible constructors that pass `UNKNOWN`, `NONE`, `NOOP` provider and no ledger. In the new constructor, resolve Context on the caller thread, capture it in the submitted lambda, and use:

```java
try (AgentToolInvocationContextHolder.Scope ignored =
        AgentToolInvocationContextHolder.open(invocationContext)) {
    return operation.call();
}
```

When the propagation test passes, record that the preferred mechanism is technically viable; do not add the `ModelRouteContext` fallback fields. If the test cannot observe Context in the worker, stop this task, add the same fields to `ModelRouteContext`, pass the enriched context explicitly into the worker, and rerun the same test before moving to Task 3. In either implementation, the local tool call ID remains the sole key.

- [ ] **Step 4: Run the focused tests to verify they pass**

Run the same Maven command plus the existing executor tests:

```powershell
.\mvnw.cmd -q "-Dtest=AgentToolInvocationContextPropagationTest,AgentToolExecutionAttemptRegistryTest,AgentToolExecutionPolicyExecutorTest" test
```

Expected: PASS and no holder value remains on the caller or worker thread.

- [ ] **Step 5: Commit the propagation slice**

```powershell
git add src/main/java/com/aseubel/yusi/service/ai/runtime src/main/java/com/aseubel/yusi/service/ai/tool/AgentToolExecutionPolicyService.java src/test/java/com/aseubel/yusi/service/ai/runtime
git commit -m "feat: propagate agent tool invocation context"
```

### Task 3: Persisted Ledger and Recovery Boundaries

**Files:**
- Modify: `src/main/java/com/aseubel/yusi/pojo/entity/AgentToolTrace.java`
- Modify: `src/main/java/com/aseubel/yusi/repository/AgentToolTraceRepository.java`
- Create: `src/main/java/com/aseubel/yusi/service/ai/runtime/AgentToolIdempotencyLedgerService.java`
- Create: `src/main/java/com/aseubel/yusi/service/ai/runtime/AgentToolIdempotencyMaintenance.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/runtime/AgentToolTraceService.java`
- Create: `src/main/resources/db/migration/V20260830__add_tool_idempotency_ledger.sql`
- Modify: `src/main/resources/db/init.sql`
- Create: `src/test/java/com/aseubel/yusi/service/ai/runtime/AgentToolIdempotencyLedgerServiceTest.java`
- Modify: `src/test/java/com/aseubel/yusi/service/ai/runtime/AgentToolTraceServiceTest.java`

**Interfaces:**
- `AgentToolTrace.IdempotencyMode`: `NONE`, `IDEMPOTENT_WRITE`.
- `AgentToolTrace.IdempotencyStatus`: `CLAIMED`, `COMPLETED`, `FAILED`, `UNKNOWN`.
- `AgentToolIdempotencyLedgerService.claim(AgentToolInvocationContext context)` returns a `ClaimDecision` with `CLAIMED`, `IN_PROGRESS`, `ALREADY_COMPLETED`, `PREVIOUS_FAILURE`, `UNKNOWN`, or `CONTEXT_MISSING`.
- `resolveSuccess(context)`, `resolveFailure(context)`, `resolveUnknown(context)`, `recoverOrphanedClaims(now)`, and `clearExpiredStates(now)` are idempotent and never parse result text.
- Existing `AgentToolTraceService.start(...)` overloads remain source-compatible; the new overload accepts `AgentToolTrace.IdempotencyMode` and stores `NONE` for old callers.

- [ ] **Step 1: Write failing ledger tests**

Add tests that create a running `AgentToolTrace` with the same `toolCallId` as the Context and assert:

```java
when(repository.claimIdempotency(anyString(), anyString(), anyString(), any(), any(), eq(now), eq(expiry)))
        .thenReturn(1);
assertEquals(ClaimDecision.CLAIMED, ledger.claim(context, now));
verify(repository).claimIdempotency("user-1", "run-1", "local-1", now, expiry);

when(repository.claimIdempotency(anyString(), anyString(), anyString(), any(), any(), any(), any()))
        .thenReturn(0);
when(repository.findByUserIdAndRunIdAndToolCallId("user-1", "run-1", "local-1"))
        .thenReturn(Optional.of(traceWith(AgentToolTrace.IdempotencyStatus.COMPLETED)));
assertEquals(ClaimDecision.ALREADY_COMPLETED, ledger.claim(context, now));
```

Also assert that `resolveUnknown` changes only a `CLAIMED` row, `recoverOrphanedClaims` updates claims older than five minutes, `clearExpiredStates` clears only ledger columns, and `NONE` traces are never claimed.

- [ ] **Step 2: Run the focused test to verify it fails**

```powershell
.\mvnw.cmd -q "-Dtest=AgentToolIdempotencyLedgerServiceTest,AgentToolTraceServiceTest" test
```

Expected: compilation failure for the ledger fields, repository update methods, and service.

- [ ] **Step 3: Implement schema and atomic state transitions**

Add the five columns from the design document. Keep `tool_call_id` as the only ledger key. Add JPA enum fields and timestamps. Add repository methods using conditional `@Modifying` updates:

```java
@Modifying
@Query("""
    update AgentToolTrace t set
        t.idempotencyStatus = :claimed,
        t.idempotencyClaimedAt = :now,
        t.idempotencyResolvedAt = null,
        t.idempotencyExpiresAt = :expiresAt,
        t.updatedAt = :now
    where t.userId = :userId and t.runId = :runId and t.toolCallId = :toolCallId
      and t.idempotencyMode = :mode
      and (t.idempotencyStatus is null or t.idempotencyExpiresAt <= :now)
    """)
int claimIdempotency(String userId, String runId, String toolCallId,
        AgentToolTrace.IdempotencyMode mode, AgentToolTrace.IdempotencyStatus claimed,
        LocalDateTime now, LocalDateTime expiresAt);
```

The service reads the row only after a zero-row claim to map the blocking status. Use `AgentToolIdempotencyConstants.CLAIM_LEASE` and `.LEDGER_RETENTION` instead of inline durations. Every resolve update includes `idempotencyStatus = CLAIMED` in its predicate.

`AgentToolIdempotencyMaintenance` listens for `ApplicationReadyEvent` and invokes `recoverOrphanedClaims(Clock.systemUTC())`; a daily scheduled method invokes `clearExpiredStates`. The startup scan marks stale claims `UNKNOWN`, never calls a tool, and logs only the affected row count.

Update `AgentToolTraceService.closeRunning` so idempotent rows that remain claimed become `UNKNOWN` before/with ordinary tool trace convergence. Existing constructors and non-ledger trace tests must remain valid.

- [ ] **Step 4: Run focused persistence tests**

```powershell
.\mvnw.cmd -q "-Dtest=AgentToolIdempotencyLedgerServiceTest,AgentToolTraceServiceTest,AgentRunTraceServiceTest" test
```

Expected: PASS; terminal convergence still leaves completed traces unchanged and does not alter `tool_count`.

- [ ] **Step 5: Commit the persistence slice**

```powershell
git add src/main/java/com/aseubel/yusi/pojo/entity/AgentToolTrace.java src/main/java/com/aseubel/yusi/repository/AgentToolTraceRepository.java src/main/java/com/aseubel/yusi/service/ai/runtime/AgentToolIdempotencyLedgerService.java src/main/java/com/aseubel/yusi/service/ai/runtime/AgentToolIdempotencyMaintenance.java src/main/java/com/aseubel/yusi/service/ai/runtime/AgentToolTraceService.java src/main/resources/db/migration/V20260830__add_tool_idempotency_ledger.sql src/main/resources/db/init.sql src/test/java/com/aseubel/yusi/service/ai/runtime/AgentToolIdempotencyLedgerServiceTest.java src/test/java/com/aseubel/yusi/service/ai/runtime/AgentToolTraceServiceTest.java
git commit -m "feat: add agent tool idempotency ledger"
```

### Task 4: Executor Decision Matrix and Standard Errors

**Files:**
- Create: `src/main/java/com/aseubel/yusi/service/ai/runtime/AgentToolIdempotencyBlockedException.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/runtime/AgentToolExecutionPolicyExecutor.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/tool/AgentToolExecutionPolicyService.java`
- Modify: `src/test/java/com/aseubel/yusi/service/ai/runtime/AgentToolExecutionPolicyExecutorTest.java`
- Modify: `src/test/java/com/aseubel/yusi/service/ai/tool/AgentToolExecutionPolicyServiceTest.java`

**Interfaces:**
- The executor receives `AgentToolAccessMode`, `AgentToolIdempotencyMode`, `AgentToolInvocationContextProvider`, and `AgentToolIdempotencyLedgerService` in its full constructor; existing constructors delegate to `UNKNOWN/NONE/NOOP` defaults.
- `AgentToolIdempotencyBlockedException` carries only a stable `AgentToolIdempotencyConstants` response code, never a request or result.
- `execute(...)` returns the stable response string for a blocked decision. `executeWithContext(...)` returns `ToolExecutionResult.builder().isError(true).resultText(response).build()`.
- `allowsTimeoutRetry(...)` is true only when `accessMode == READ`, `idempotencyMode == NONE`, and the declared retry policy allows the current retry count.

- [ ] **Step 1: Write the failing matrix tests**

Add these focused tests to `AgentToolExecutionPolicyExecutorTest`:

```java
@Test
void writeTimeoutNeverStartsASecondAttemptAndMarksClaimUnknown() {
    AtomicInteger calls = new AtomicInteger();
    AgentToolIdempotencyLedgerService ledger = mock(AgentToolIdempotencyLedgerService.class);
    when(ledger.claim(any())).thenReturn(ClaimDecision.CLAIMED);
    ToolExecutor delegate = (request, memoryId) -> {
        calls.incrementAndGet();
        throw new AgentToolTimeoutException("updateUserPersona");
    };
    AgentToolExecutionPolicyExecutor executor = executorFor(
            delegate, AgentToolAccessMode.WRITE, AgentToolIdempotencyMode.IDEMPOTENT_WRITE,
            AgentToolRetryPolicy.TIMEOUT_ONCE, ledger);

    assertThrows(AgentToolTimeoutException.class, () -> executor.execute(request(), "user-1"));
    assertEquals(1, calls.get());
    verify(ledger).resolveUnknown(any());
}

@Test
void competingClaimReturnsErrorAndNeverInvokesDelegate() {
    AgentToolIdempotencyLedgerService ledger = mock(AgentToolIdempotencyLedgerService.class);
    when(ledger.claim(any())).thenReturn(ClaimDecision.IN_PROGRESS);
    AtomicInteger calls = new AtomicInteger();
    ToolExecutor delegate = (request, memoryId) -> { calls.incrementAndGet(); return "bad"; };
    AgentToolExecutionPolicyExecutor executor = executorFor(
            delegate, AgentToolAccessMode.WRITE, AgentToolIdempotencyMode.IDEMPOTENT_WRITE,
            AgentToolRetryPolicy.DENY, ledger);

    ToolExecutionResult result = executor.executeWithContext(request(), null);

    assertTrue(result.isError());
    assertTrue(result.text().contains("IDEMPOTENCY_IN_PROGRESS"));
    assertEquals(0, calls.get());
}
```

Add equivalent assertions for `COMPLETED`, `FAILED`, `UNKNOWN`, and missing Context. Preserve existing READ timeout retry, ordinary failure, cancellation, backoff cancellation, deadline and attempt observer tests.

- [ ] **Step 2: Run the focused tests to verify they fail**

```powershell
.\mvnw.cmd -q "-Dtest=AgentToolExecutionPolicyExecutorTest,AgentToolExecutionPolicyServiceTest" test
```

Expected: compilation failure for the ledger-aware constructor and failure because a WRITE with `TIMEOUT_ONCE` currently retries.

- [ ] **Step 3: Implement the wrapper-layer decision order**

In both execute methods, resolve Context before claim. For `IDEMPOTENT_WRITE`, return `CONTEXT_MISSING` if no Context exists; otherwise call `ledger.claim(context)` and return an error response for every non-`CLAIMED` decision. Claim must happen before `executor.submit`.

Wrap the existing `await` loop with ledger resolution:

```java
try {
    T result = await(operation, routeContext, request, invocationContext);
    if (isToolError(result)) {
        ledger.resolveFailure(invocationContext);
    } else {
        ledger.resolveSuccess(invocationContext);
    }
    return result;
} catch (AgentToolTimeoutException | AgentToolCancelledException exception) {
    if (isIdempotentWrite()) {
        ledger.resolveUnknown(invocationContext);
    }
    throw exception;
} catch (RuntimeException exception) {
    if (isIdempotentWrite()) {
        ledger.resolveFailure(invocationContext);
    }
    throw exception;
}
```

`isToolError` may inspect `ToolExecutionResult.isError()` only. It must return false for a plain String, regardless of its text. Move the current retry branch behind `allowsTimeoutRetry`; for WRITE and UNKNOWN, rethrow timeout after ledger resolution. Keep cancellation checks and the 30-second logical deadline. If `executor.submit` rejects after claim, resolve `UNKNOWN` before rethrowing.

Wire the full constructor from `AgentToolExecutionPolicyService`, while the two existing test constructors inject no-op provider/ledger dependencies. Pass the catalog's access and idempotency declarations into every local and MCP wrapper.

- [ ] **Step 4: Run the focused matrix**

```powershell
.\mvnw.cmd -q "-Dtest=AgentToolExecutionPolicyExecutorTest,AgentToolExecutionPolicyServiceTest,AgentToolCapabilityCatalogTest" test
```

Expected: PASS; READ still retries once, WRITE never retries, blocked results are `isError == true`, and no retry observer is called for writes.

- [ ] **Step 5: Commit the executor slice**

```powershell
git add src/main/java/com/aseubel/yusi/service/ai/runtime/AgentToolIdempotencyBlockedException.java src/main/java/com/aseubel/yusi/service/ai/runtime/AgentToolExecutionPolicyExecutor.java src/main/java/com/aseubel/yusi/service/ai/tool/AgentToolExecutionPolicyService.java src/test/java/com/aseubel/yusi/service/ai/runtime/AgentToolExecutionPolicyExecutorTest.java src/test/java/com/aseubel/yusi/service/ai/tool/AgentToolExecutionPolicyServiceTest.java
git commit -m "feat: enforce idempotent tool execution policy"
```

### Task 5: Controller Wiring and `updateUserPersona` End-to-End Protection

**Files:**
- Modify: `src/main/java/com/aseubel/yusi/controller/AiController.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/tool/UserPersonaTool.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/runtime/AgentRunTraceService.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/runtime/AgentToolTraceService.java`
- Modify: `src/test/java/com/aseubel/yusi/controller/AiControllerCancellationTest.java`
- Create: `src/test/java/com/aseubel/yusi/service/ai/tool/UserPersonaToolIdempotencyTest.java`

**Interfaces:**
- At the existing `AiController` callback location L342-L372, `beforeToolExecution` generates one local ID, creates `AgentToolInvocationContext`, registers it under the request identity, and starts Trace with the same idempotency mode.
- `onToolExecuted` resolves and completes the same local ID; it never creates a second ID and calls `toolCompleted` once per logical callback.
- `traceRunCompleted`, `traceRunFailed`, `traceRunCancelled`, and synchronous setup failure clear the context registry after invoking the existing terminal Trace path.
- `UserPersonaTool` uses `AgentToolInvocationContextHolder.current().userId()` when present; it falls back to the LangChain memory ID only for direct non-Agent tests. The context's `localToolCallId` is consumed by the wrapper ledger, not regenerated by the tool.

- [ ] **Step 1: Write the failing controller and E2E tests**

Extend the existing controller callback test to capture the registry Context and verify:

```java
verify(agentToolExecutionAttemptRegistry).register(
        eq("user-1"), eq("request-tool"), same(request), eq("tool-call-1"),
        eq("web_search"), eq("mcp"), anyString(),
        eq(AgentToolAccessMode.READ), eq(AgentToolIdempotencyMode.NONE), anyString());
```

Create `UserPersonaToolIdempotencyTest` with a mocked `UserPersonaService`, an actual `UserPersonaTool`, an actual `AgentToolCapabilityCatalog`, a real single-worker executor, an `AgentToolIdempotencyLedgerService` backed by a Mockito repository, and the unified policy service. Assert:

1. one first call reaches `updateUserPersona` using the Context user ID;
2. replaying the same local ID returns `IDEMPOTENCY_ALREADY_COMPLETED` with `isError == true` and the service invocation count remains one;
3. two concurrent claims allow exactly one delegate and the other returns `IDEMPOTENCY_IN_PROGRESS` as an error;
4. a timeout produces one delegate call, `resolveUnknown`, and a later replay returns `IDEMPOTENCY_UNKNOWN`.

- [ ] **Step 2: Run the focused tests to verify they fail**

```powershell
.\mvnw.cmd -q "-Dtest=AiControllerCancellationTest,UserPersonaToolIdempotencyTest" test
```

Expected: compilation or verification failure because the Controller has no idempotency Context registration and `UserPersonaTool` does not read the holder.

- [ ] **Step 3: Implement lifecycle wiring and real tool consumption**

Use the catalog result already resolved by the Controller to populate every Context field. Add a `traceToolStarted` overload that passes `AgentToolTrace.IdempotencyMode` to `AgentToolTraceService.start`; the old trace method remains unchanged for existing callers. Register the Context before the tool can execute, then emit the existing safe SSE event with the same local ID.

In `UserPersonaTool`, select the effective user ID with the holder Context first and reject a blank value. Do not read or write the tool call ID from arguments. Keep result text handling unchanged; the executor, not the tool, owns ledger resolution.

In `AgentToolTraceService.closeRunning`, invoke `resolveUnknown` for rows whose idempotency status is `CLAIMED` before finishing the ordinary trace. Keep the existing `AgentRunTraceService` complete/fail/cancel calls and `tool_count` updates unchanged.

- [ ] **Step 4: Run the E2E and lifecycle tests**

```powershell
.\mvnw.cmd -q "-Dtest=AiControllerCancellationTest,UserPersonaToolIdempotencyTest,AgentRunTraceServiceTest,AgentToolTraceServiceTest" test
```

Expected: PASS; null upstream IDs still use one generated local ID, cancel closes orphan traces, and the persona service is never called by a blocked replay.

- [ ] **Step 5: Commit the end-to-end slice**

```powershell
git add src/main/java/com/aseubel/yusi/controller/AiController.java src/main/java/com/aseubel/yusi/service/ai/tool/UserPersonaTool.java src/main/java/com/aseubel/yusi/service/ai/runtime/AgentRunTraceService.java src/main/java/com/aseubel/yusi/service/ai/runtime/AgentToolTraceService.java src/test/java/com/aseubel/yusi/controller/AiControllerCancellationTest.java src/test/java/com/aseubel/yusi/service/ai/tool/UserPersonaToolIdempotencyTest.java
git commit -m "feat: protect persona writes from duplicate execution"
```

### Task 6: Roadmap Record and Full Verification

**Files:**
- Modify: `docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md`
- Modify: `docs/superpowers/specs/2026-08-16-agent-tool-idempotency-design.md`
- Modify: `docs/superpowers/plans/2026-08-16-agent-tool-idempotency.md`

- [ ] **Step 1: Write the roadmap/documentation assertions**

Update the Phase 3 entry to state that tool idempotency now covers only explicitly declared write tools, shares `localToolCallId/tool_call_id`, blocks `UNKNOWN`, scans stale `CLAIMED`, and leaves write confirmation/pause-resume out of scope. Add a completion entry with the exact `updateUserPersona` E2E object and unchanged `agent_run_trace.tool_count` semantics. Ensure the design doc's six requested points and the implementation plan's task names match the code.

- [ ] **Step 2: Run the persisted-boundary checks**

```powershell
rg -n -i "query|keyword|arguments|resultText|exception\.getMessage|prompt|secret|password" src/main/java/com/aseubel/yusi/service/ai/runtime/AgentTool* src/main/java/com/aseubel/yusi/service/ai/tool/AgentTool* src/main/java/com/aseubel/yusi/pojo/entity/AgentToolTrace.java
```

Expected: no new persistence or model-response path stores raw query/arguments/result/Prompt/secret data; normal tool schema descriptions may still contain parameter names in the capability catalog.

- [ ] **Step 3: Run focused regression tests**

```powershell
.\mvnw.cmd -q "-Dtest=AgentToolCapabilityCatalogTest,AgentToolInvocationContextPropagationTest,AgentToolIdempotencyLedgerServiceTest,AgentToolExecutionPolicyExecutorTest,AgentToolExecutionPolicyServiceTest,UserPersonaToolIdempotencyTest,AgentToolTraceServiceTest,AgentRunTraceServiceTest,AiControllerCancellationTest" test
```

Expected: exit code `0`; no service process is started.

- [ ] **Step 4: Run complete verification**

```powershell
git diff --check
.\mvnw.cmd -q test
git status --short --branch
```

Expected: all tests exit `0`, `git diff --check` is empty, and only the intentional commits/files remain.

- [ ] **Step 5: Commit the roadmap/documentation slice**

```powershell
git add docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md docs/superpowers/specs/2026-08-16-agent-tool-idempotency-design.md docs/superpowers/plans/2026-08-16-agent-tool-idempotency.md
git commit -m "docs: record agent tool idempotency rollout"
```

## Plan Self-Review

- Capability declaration, access-mode gate, retry budget, logical deadline, cancellation during backoff, and worker-pool overlap are covered by Tasks 1, 2, and 4.
- Claim/replay matrix, standard error responses, shared key path, retention, orphan recovery, Context fallback rule, and `tool_count` semantics are explicit in the global constraints and Tasks 3-5.
- `updateUserPersona` is a real E2E consumer in Task 5 rather than an unused abstraction.
- Every production change is preceded by a focused failing test and every task ends with a runnable Maven command and commit boundary.
- No task introduces an independent ledger ID, raw content persistence, MCP raw-log changes, user-facing confirmation, or service startup.
