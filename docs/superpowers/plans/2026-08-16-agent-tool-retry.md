# Agent Tool Timeout Retry Implementation Plan

> **For inline execution:** This plan is executed in the current workspace without subagents. Each task uses a focused test checkpoint and the complete slice is committed independently.

**Goal:** 对明确允许的只读工具增加一次、仅针对超时的自动重试，并以逻辑调用总 deadline、可取消退避和低敏 `attempt_count` 保持运行时可控、可观测。

**Architecture:** 能力目录显式提供 `accessMode` 与 `retryPolicy`，未知能力默认为 `UNKNOWN/DENY`，写操作永不自动重试。重试完全封装在 `AgentToolExecutionPolicyExecutor` 中：同一逻辑工具调用最多再发起一次尝试，所有尝试共享不超过 30 秒的总 deadline；取消令牌可以唤醒退避等待。工具 Trace 保留逻辑调用的一行记录，初始 `attempt_count=1`，每次真正开始的重试由共享 registry 增加一次，不保存参数、结果或异常文本。

**Tech Stack:** Java 21, Spring Boot 3.4, Spring Data JPA, JUnit 5, Mockito, MySQL migration SQL, LangChain4j 1.18.0.

## Global Constraints

- 能力目录没有明确 `READ` 和 `TIMEOUT_ONCE` 契约时，默认 `UNKNOWN/DENY`，不得根据工具来源或结果文本推断可重试。
- 单个逻辑工具调用最多允许 1 次重试；`AgentToolExecutionPolicy` 的总 deadline 最大为 30 秒，远小于聊天 SSE 的 180 秒。
- 重试只允许由 `AgentToolTimeoutException` 触发；`TOOL_FAILED`、`CANCELLED`、`UNKNOWN` 或任何普通运行时异常不得重试。
- 退避期间取消令牌必须唤醒等待并立即结束当前逻辑调用，不得发起下一次尝试。
- 重试逻辑必须位于 `AgentToolExecutionPolicyExecutor` 包装层，不能散落在 Controller、MCP 服务或具体工具中。
- `agent_tool_trace.attempt_count` 是低敏的物理尝试次数：首次执行为 `1`，一次成功重试后为 `2`；不保存参数、结果、Prompt、用户 query 或异常 message。
- 当前专用线程池默认 `8` 个 daemon worker，仍由 `agent.tool.execution.pool-size` 配置；本切片把等待队列容量设为独立的 `agent.tool.execution.queue-capacity`（默认 `16`），满载时使用拒绝策略，不回退到调用线程。同一逻辑调用的重试是串行的，但被放弃且不响应中断的旧尝试可能继续占用一个 worker，因此一次重试窗口最多可能同时占用“旧尝试 + 新尝试”两个槽位。总 deadline 会限制排队等待；本切片不自动把 worker 容量翻倍，也不创建第二个重试线程池。
- 不实现自动重试写操作、用户确认、跨请求恢复、通用幂等写入和 MCP gRPC 原始 `keyword/query` 日志收敛。
- `agent_run_trace.tool_count` 语义保持不变，仍表示完成的逻辑工具调用数量，不按物理重试次数递增。
- 不启动服务，不依赖远程模型、Milvus 或跨域环境；不使用子 agent。

## File Map

- Create `src/main/java/com/aseubel/yusi/service/ai/tool/constant/AgentToolAccessMode.java`: `READ`, `WRITE`, `UNKNOWN` 能力语义。
- Create `src/main/java/com/aseubel/yusi/service/ai/tool/AgentToolRetryPolicy.java`: 默认拒绝和一次超时重试策略，验证最大重试次数为 1。
- Modify `src/main/java/com/aseubel/yusi/service/ai/tool/AgentToolCapability.java`: 保存访问模式和重试策略，旧构造方式默认拒绝。
- Modify `src/main/java/com/aseubel/yusi/service/ai/tool/constant/AgentToolCapabilityConstants.java` and `src/main/java/com/aseubel/yusi/service/ai/tool/AgentToolCapabilityCatalog.java`: 为已知工具显式登记语义，并把 MCP metadata 扩展为访问模式和重试策略。
- Modify `src/main/java/com/aseubel/yusi/service/ai/tool/AgentToolExecutionPolicy.java`: 增加 per-attempt timeout 与 logical total deadline。
- Modify `src/main/java/com/aseubel/yusi/service/ai/runtime/AgentToolExecutionPolicyExecutor.java`: 在包装层实现一次超时重试、总 deadline 和可取消退避。
- Create `src/main/java/com/aseubel/yusi/service/ai/runtime/AgentToolExecutionAttemptObserver.java`: executor 与 Trace registry 之间的低敏重试通知接口。
- Modify `src/main/java/com/aseubel/yusi/service/ai/runtime/AgentCancellationToken.java`: 增加可唤醒的限时等待。
- Create `src/main/java/com/aseubel/yusi/service/ai/runtime/AgentToolExecutionAttemptRegistry.java`: 关联 request identity 与本地工具调用 ID，在物理重试开始时更新低敏 Trace，并在终态清理。
- Modify `src/main/java/com/aseubel/yusi/service/ai/tool/AgentToolExecutionPolicyService.java`: Task 3 uses the observer's no-op implementation; Task 5 replaces it with the attempt registry for each local/MCP wrapper.
- Modify `src/main/java/com/aseubel/yusi/config/ai/AgentToolExecutionConfig.java`: 为专用池增加可配置有界等待队列和拒绝策略。
- Modify `src/main/java/com/aseubel/yusi/pojo/entity/AgentToolTrace.java` and `src/main/java/com/aseubel/yusi/service/ai/runtime/AgentToolTraceService.java`: 持久化、初始化和递增 `attempt_count`。
- Create `src/main/resources/db/migration/V20260829__add_tool_attempt_count.sql` and modify `src/main/resources/db/init.sql`: 数据库列和初始化结构。
- Modify `src/main/java/com/aseubel/yusi/controller/AiController.java`: 在工具生命周期回调登记/完成 attempt registry，并在 run 终态清理。
- Test `src/test/java/com/aseubel/yusi/service/ai/tool/AgentToolCapabilityCatalogTest.java`, `src/test/java/com/aseubel/yusi/service/ai/runtime/AgentToolExecutionPolicyExecutorTest.java`, `src/test/java/com/aseubel/yusi/service/ai/runtime/AgentToolTraceServiceTest.java`, `src/test/java/com/aseubel/yusi/controller/AiControllerCancellationTest.java` and create `src/test/java/com/aseubel/yusi/service/ai/runtime/AgentToolExecutionAttemptRegistryTest.java`.
- Modify `docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md` after implementation with the completed boundary and the remaining write-confirmation work.

---

### Task 1: Add Explicit Capability Access and Retry Semantics

**Files:**
- Create: `src/main/java/com/aseubel/yusi/service/ai/tool/constant/AgentToolAccessMode.java`
- Create: `src/main/java/com/aseubel/yusi/service/ai/tool/AgentToolRetryPolicy.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/tool/AgentToolCapability.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/tool/constant/AgentToolCapabilityConstants.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/tool/AgentToolCapabilityCatalog.java`
- Test: `src/test/java/com/aseubel/yusi/service/ai/tool/AgentToolCapabilityCatalogTest.java`

**Interfaces:**
- `AgentToolAccessMode` exposes `READ`, `WRITE`, `UNKNOWN` and a stable lowercase `code()`.
- `AgentToolRetryPolicy` exposes `DENY` and `TIMEOUT_ONCE`, `maxRetries()`, `backoff()`, `code()` and validates `0 <= maxRetries <= 1`.
- `AgentToolCapability` adds `accessMode()` and `retryPolicy()` after `executionPolicy()`; its existing seven-argument constructor delegates to `UNKNOWN/DENY`.
- `AgentToolCapabilityCatalog` maps only `searchMemories`, `searchDiary`, `searchLifeGraph` and `web_search` to `READ/TIMEOUT_ONCE`; `updateUserPersona` maps to `WRITE/DENY`; all other names and MCP tools map to `UNKNOWN/DENY`.

- [x] **Step 1: Write the failing tests**

Extend the catalog tests with these assertions:

```java
assertEquals(AgentToolAccessMode.READ, memoryCapability.accessMode());
assertEquals(AgentToolRetryPolicy.TIMEOUT_ONCE, memoryCapability.retryPolicy());
assertEquals(AgentToolAccessMode.WRITE, personaCapability.accessMode());
assertEquals(AgentToolRetryPolicy.DENY, personaCapability.retryPolicy());
assertEquals(AgentToolAccessMode.UNKNOWN, unknownCapability.accessMode());
assertEquals(AgentToolRetryPolicy.DENY, unknownCapability.retryPolicy());
assertEquals("read", mapped.metadata().get(AgentToolCapabilityCatalog.METADATA_ACCESS_MODE));
assertEquals("timeout_once", mapped.metadata().get(AgentToolCapabilityCatalog.METADATA_RETRY_POLICY));
```

- [x] **Step 2: Run the focused test to verify it fails**

Run:

```powershell
.\mvnw.cmd -q "-Dtest=AgentToolCapabilityCatalogTest" test
```

Expected: compilation failure because the access mode, retry policy and metadata fields do not exist.

- [x] **Step 3: Implement the minimal capability contract**

Use these concrete policy definitions:

```java
public record AgentToolRetryPolicy(int maxRetries, Duration backoff) {
    public static final AgentToolRetryPolicy DENY = new AgentToolRetryPolicy(0, Duration.ZERO);
    public static final AgentToolRetryPolicy TIMEOUT_ONCE =
            new AgentToolRetryPolicy(1, Duration.ofMillis(100));

    public AgentToolRetryPolicy {
        if (maxRetries < 0 || maxRetries > 1) {
            throw new IllegalArgumentException("Agent tool retries must be between 0 and 1");
        }
        if (backoff == null || backoff.isNegative()) {
            throw new IllegalArgumentException("Agent tool retry backoff must not be negative");
        }
    }

    public String code() {
        return maxRetries == 0 ? "deny" : "timeout_once";
    }

    public boolean allowsRetry(int retryCount) {
        return retryCount >= 0 && retryCount < maxRetries;
    }
}
```

The catalog must return `UNKNOWN/DENY` for an unrecognized tool instead of treating every MCP tool as a network-readable retry candidate. Add `METADATA_ACCESS_MODE` and `METADATA_RETRY_POLICY` constants and attach their code values to mapped MCP specifications.

- [x] **Step 4: Run the focused test to verify it passes**

Run the same Maven command. Expected: all catalog tests pass and no unknown tool receives a retry policy.

---

### Task 2: Add Per-Attempt and Logical-Call Deadlines

**Files:**
- Modify: `src/main/java/com/aseubel/yusi/service/ai/tool/AgentToolExecutionPolicy.java`
- Test: `src/test/java/com/aseubel/yusi/service/ai/tool/AgentToolCapabilityCatalogTest.java`

**Interfaces:**
- `AgentToolExecutionPolicy` becomes `record AgentToolExecutionPolicy(Duration timeout, Duration totalDeadline)`.
- The existing one-argument constructor remains and uses the same duration for both fields.
- `MAX_TOTAL_DEADLINE` is `Duration.ofSeconds(30)`; policies reject a total deadline above it or below the per-attempt timeout.
- Policy values are: memory read `15s/30s`, network read `20s/30s`, persona write `10s/10s`, default `10s/10s`.

- [x] **Step 1: Write the failing test**

Add assertions that the known retryable policies have a total deadline of 30 seconds and that constructing a policy above the 30-second cap throws `IllegalArgumentException`.

- [x] **Step 2: Run the focused test to verify it fails**

Run:

```powershell
.\mvnw.cmd -q "-Dtest=AgentToolCapabilityCatalogTest" test
```

Expected: compilation failure because `totalDeadline()` is not present.

- [x] **Step 3: Implement the policy deadline**

Add the two-duration record, validation, compatibility constructor and the four constants exactly as specified by the interface above. Do not read the 180-second SSE timeout into this policy; the 30-second cap is an independent tool logical-call limit.

- [x] **Step 4: Run the focused test to verify it passes**

Run the same Maven command. Expected: all policy and catalog tests pass.

---

### Task 3: Implement Timeout-Only Retry in the Executor Wrapper

**Files:**
- Modify: `src/main/java/com/aseubel/yusi/service/ai/runtime/AgentToolExecutionPolicyExecutor.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/runtime/AgentCancellationToken.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/tool/AgentToolExecutionPolicyService.java`
- Modify: `src/main/java/com/aseubel/yusi/config/ai/AgentToolExecutionConfig.java`
- Test: `src/test/java/com/aseubel/yusi/service/ai/runtime/AgentToolExecutionPolicyExecutorTest.java`
- Test: `src/test/java/com/aseubel/yusi/service/ai/tool/AgentToolExecutionPolicyServiceTest.java`

**Interfaces:**
- `AgentToolExecutionAttemptObserver` is a functional interface with `void onRetry(ToolExecutionRequest request)` and `NOOP = request -> {}`; it receives only the request object identity and never persists request content.
- The wrapper constructor consumes `AgentToolExecutionPolicy`, `AgentToolRetryPolicy`, `ExecutorService`, and `AgentToolExecutionAttemptObserver`; the initial service wiring uses the observer's no-op implementation, and Task 5 replaces it with the Trace registry.
- Legacy constructors that accept only `Duration` remain retry-disabled for direct callers.
- `AgentCancellationToken.awaitCancellation(long, TimeUnit)` returns `true` when cancellation wakes the wait and `false` on timeout.
- The executor catches only `AgentToolTimeoutException` for retry; it immediately propagates `AgentToolCancelledException`, `IllegalStateException`, `Error` and every other exception.

- [x] **Step 1: Write the failing tests**

Add these focused behaviors with a one-worker or two-worker test executor and the existing `request()` helper:

```java
@Test
void retriesOneTimeoutAndThenReturnsTheSuccessfulResult() {
    AtomicInteger calls = new AtomicInteger();
    ToolExecutor delegate = (request, memoryId) ->
            calls.incrementAndGet() == 1 ? throwTimeout() : "ok";
    AgentToolExecutionPolicyExecutor executor = new AgentToolExecutionPolicyExecutor(
            delegate,
            new AgentToolExecutionPolicy(Duration.ofMillis(50), Duration.ofMillis(250)),
            AgentToolRetryPolicy.TIMEOUT_ONCE,
            toolExecutor,
            AgentToolExecutionAttemptObserver.NOOP,
            "testTool");

    assertEquals("ok", executor.execute(request(), "user-1"));
    assertEquals(2, calls.get());
}

@Test
void doesNotRetryToolFailureOrCancellation() {
    AtomicInteger calls = new AtomicInteger();
    ToolExecutor delegate = (request, memoryId) -> {
        calls.incrementAndGet();
        throw new IllegalStateException("tool failed");
    };
    AgentToolExecutionPolicyExecutor executor = new AgentToolExecutionPolicyExecutor(
            delegate,
            new AgentToolExecutionPolicy(Duration.ofMillis(50), Duration.ofMillis(250)),
            AgentToolRetryPolicy.TIMEOUT_ONCE,
            toolExecutor,
            AgentToolExecutionAttemptObserver.NOOP,
            "testTool");

    assertThrows(IllegalStateException.class, () -> executor.execute(request(), "user-1"));
    assertEquals(1, calls.get());
}

@Test
void cancellationDuringBackoffPreventsTheSecondAttempt() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    AgentCancellationToken token = new AgentCancellationToken();
    ExecutorService caller = Executors.newSingleThreadExecutor();
    ScheduledExecutorService canceller = Executors.newSingleThreadScheduledExecutor();
    ToolExecutor delegate = (request, memoryId) -> {
        calls.incrementAndGet();
        throw new AgentToolTimeoutException("testTool");
    };
    AgentToolExecutionPolicyExecutor executor = new AgentToolExecutionPolicyExecutor(
            delegate,
            new AgentToolExecutionPolicy(Duration.ofMillis(50), Duration.ofSeconds(2)),
            new AgentToolRetryPolicy(1, Duration.ofSeconds(1)),
            toolExecutor,
            AgentToolExecutionAttemptObserver.NOOP,
            "testTool");
    Future<String> result = caller.submit(() -> {
        ModelRouteContextHolder.set(ModelRouteContext.builder().cancellationToken(token).build());
        try {
            return executor.execute(request(), "user-1");
        } finally {
            ModelRouteContextHolder.clear();
        }
    });

    canceller.schedule(token::cancel, 50, TimeUnit.MILLISECONDS);
    assertInstanceOf(AgentToolCancelledException.class,
            assertThrows(ExecutionException.class, () -> result.get(1, TimeUnit.SECONDS)).getCause());
    assertEquals(1, calls.get());
    canceller.shutdownNow();
    caller.shutdownNow();
}

@Test
void logicalDeadlineBoundsBothAttempts() {
    AtomicInteger calls = new AtomicInteger();
    ToolExecutor delegate = (request, memoryId) -> {
        calls.incrementAndGet();
        Thread.sleep(100);
        return "late";
    };
    AgentToolExecutionPolicyExecutor executor = new AgentToolExecutionPolicyExecutor(
            delegate,
            new AgentToolExecutionPolicy(Duration.ofMillis(40), Duration.ofMillis(100)),
            AgentToolRetryPolicy.TIMEOUT_ONCE,
            toolExecutor,
            AgentToolExecutionAttemptObserver.NOOP,
            "testTool");

    assertThrows(AgentToolTimeoutException.class, () -> executor.execute(request(), "user-1"));
    assertTrue(calls.get() <= 2);
}

private String throwTimeout() {
    throw new AgentToolTimeoutException("testTool");
}
```

The test-only backoff uses `new AgentToolRetryPolicy(1, Duration.ofSeconds(1))`; production catalog policies continue to use the 100ms constant.

- [x] **Step 2: Run the focused test to verify it fails**

Run:

```powershell
.\mvnw.cmd -q "-Dtest=AgentToolExecutionPolicyExecutorTest" test
```

Expected: compilation failure for the new constructor and cancellation wait method, or assertion failure because no retry occurs.

- [x] **Step 3: Implement the bounded retry loop**

The wrapper must follow this sequence:

```java
long logicalDeadline = System.nanoTime() + policy.totalDeadline().toNanos();
int retryCount = 0;
while (true) {
    checkCancelled(token);
    try {
        return awaitOneAttempt(operation, routeContext, request,
                min(policy.timeout(), remaining(logicalDeadline)), retryCount);
    } catch (AgentToolTimeoutException timeout) {
        if (!retryPolicy.allowsRetry(retryCount) || isCancelled(token)) {
            throw timeout;
        }
        awaitRetryBackoff(retryPolicy.backoff(), token, logicalDeadline);
        retryCount++;
    }
}
```

`awaitRetryBackoff` must use the token's signal wait rather than an uninterruptible sleep. It checks the deadline before and after waiting. The retry task checks cancellation before invoking the delegate and calls `observer.onRetry(request)` immediately before the delegate starts when `retryCount > 0`. The first timeout cancels its future with `cancel(true)`; an uncooperative delegate may still occupy its old worker, but the new attempt is submitted to the same fixed worker pool and remains subject to the shared logical deadline. `AgentToolExecutionConfig` uses `ThreadPoolExecutor` with `ArrayBlockingQueue(queueCapacity)` and `AbortPolicy`; it must never run an overflow tool on the caller thread.

- [x] **Step 4: Run the focused tests to verify they pass**

Run:

```powershell
.\mvnw.cmd -q "-Dtest=AgentToolExecutionPolicyExecutorTest,AgentToolExecutionPolicyServiceTest" test
```

Expected: all timeout, cancellation, deadline and local/MCP wrapper tests pass.

---

### Task 4: Persist Low-Sensitivity Attempt Counts

**Files:**
- Create: `src/main/java/com/aseubel/yusi/service/ai/runtime/AgentToolExecutionAttemptRegistry.java`
- Modify: `src/main/java/com/aseubel/yusi/pojo/entity/AgentToolTrace.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/runtime/AgentToolTraceService.java`
- Create: `src/main/resources/db/migration/V20260829__add_tool_attempt_count.sql`
- Modify: `src/main/resources/db/init.sql`
- Test: `src/test/java/com/aseubel/yusi/service/ai/runtime/AgentToolExecutionAttemptRegistryTest.java`
- Test: `src/test/java/com/aseubel/yusi/service/ai/runtime/AgentToolTraceServiceTest.java`

**Interfaces:**
- `AgentToolTrace.attemptCount` is a non-null integer with default `1`.
- `AgentToolTraceService.incrementAttemptCount(userId, runId, localToolCallId)` increments only a `RUNNING` row and caps the value at `2` for this slice.
- `AgentToolExecutionAttemptRegistry.register(userId, runId, requestIdentity, upstreamId, toolName, localToolCallId)` stores only low-sensitivity identifiers.
- `AgentToolExecutionAttemptRegistry` implements `AgentToolExecutionAttemptObserver.onRetry(requestIdentity)` and calls `AgentToolTraceService.incrementAttemptCount(...)` once for the registered request.
- `AgentToolExecutionAttemptRegistry.recordRetry(requestIdentity)` increments the registered logical trace once; it does nothing for an unknown request or an already-recorded retry.
- `complete(requestIdentity)` and `clearRun(userId, runId)` remove in-memory state; cancellation/terminal cleanup cannot retain request or argument data.

- [x] **Step 1: Write the failing tests**

Assert `AgentToolTraceService.start(...)` creates `attemptCount=1`, `incrementAttemptCount(...)` changes it to `2`, a second increment leaves it at `2`, completed rows do not change, and registry cleanup removes pending request identity state. Verify the registry calls the trace service only once for a retry.

- [x] **Step 2: Run the focused test to verify it fails**

Run:

```powershell
.\mvnw.cmd -q "-Dtest=AgentToolTraceServiceTest,AgentToolExecutionAttemptRegistryTest" test
```

Expected: compilation failure because `attemptCount` and the registry do not exist.

- [x] **Step 3: Implement persistence and the identity registry**

Add this migration:

```sql
ALTER TABLE `agent_tool_trace`
    ADD COLUMN `attempt_count` INT NOT NULL DEFAULT 1 COMMENT '物理工具尝试次数，仅记录数量' AFTER `capability_version`;
```

Set `attemptCount(1)` on trace creation. The registry uses request object identity for retry accounting; upstream ID and tool name are not used to guess a different concurrent request. The Controller's existing run cleanup calls `clearRun` when a tool/run terminal callback completes.

- [x] **Step 4: Run the focused tests to verify they pass**

Run the same Maven command. Expected: persistence and registry tests pass.

---

### Task 5: Connect the Registry to Chat Tool Lifecycle and Record the Slice

**Files:**
- Modify: `src/main/java/com/aseubel/yusi/controller/AiController.java`
- Modify: `src/test/java/com/aseubel/yusi/controller/AiControllerCancellationTest.java`
- Modify: `docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md`

**Interfaces:**
- `beforeToolExecution` registers `(userId, runId, request identity, upstream id, tool name, local toolCallId)` before starting the Trace.
- `onToolExecuted` completes the registry entry after resolving the local tool ID.
- `traceRunCompleted`, `traceRunFailed` and `traceRunCancelled` clear both the existing correlation and the attempt registry.
- The Controller does not implement retry decisions and does not increment `agent_run_trace.tool_count` per attempt.

- [x] **Step 1: Write the failing lifecycle test**

Add this concrete lifecycle assertion to the existing controller test setup:

```java
AgentToolExecutionAttemptRegistry attemptRegistry = mock(AgentToolExecutionAttemptRegistry.class);
ReflectionTestUtils.setField(controller, "agentToolExecutionAttemptRegistry", attemptRegistry);
UserContext.setUserId("user-1");

controller.cancelChat(new ChatCancelRequest("run-1"));

verify(attemptRegistry).clearRun("user-1", "run-1");
verify(agentToolTraceService, never()).incrementAttemptCount(anyString(), anyString(), anyString());
```

The production cleanup method is private; the test must exercise it through the existing run cancellation callback rather than add a test-only production method. Also assert that a completed tool still calls `agentRunTraceService.toolCompleted(...)` once, regardless of `attempt_count`.

- [x] **Step 2: Run the focused test to verify it fails**

Run:

```powershell
.\mvnw.cmd -q "-Dtest=AiControllerCancellationTest" test
```

Expected: compilation failure for the new registry dependency or a missing cleanup interaction.

- [x] **Step 3: Implement lifecycle wiring and documentation**

Inject the registry into `AiController`, call `register` in the existing `beforeToolExecution` callback, call `complete` in `onToolExecuted`, and call `clearRun` from the existing run cleanup methods. Update the roadmap to mark timeout-only retry complete and leave write confirmation/persistent pause-resume unchecked.

- [x] **Step 4: Run the focused and complete verification**

Run:

```powershell
git diff --check
.\mvnw.cmd -q "-Dtest=AgentToolCapabilityCatalogTest,AgentToolExecutionPolicyExecutorTest,AgentToolExecutionPolicyServiceTest,AgentToolTraceServiceTest,AgentToolExecutionAttemptRegistryTest,AiControllerCancellationTest" test
.\mvnw.cmd -q test
```

Expected: all commands exit `0`; only existing test warnings may appear; no service process is started.

- [x] **Step 5: Commit the independent slice**

```powershell
git add docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md docs/superpowers/plans/2026-08-16-agent-tool-retry.md src/main/java src/main/resources/db/init.sql src/test/java
git commit -m "feat: add bounded timeout retry for agent tools"
```

## Acceptance Checklist

- Known read tools expose `READ/TIMEOUT_ONCE`; persona write exposes `WRITE/DENY`; unknown local/MCP tools expose `UNKNOWN/DENY`.
- A logical call makes at most two physical attempts, and only a `AgentToolTimeoutException` can trigger the second attempt.
- All attempts share a maximum 30-second logical deadline, less than the 180-second SSE lifetime.
- Cancellation during the 100ms production backoff (or any test policy backoff) wakes the wait and prevents the second delegate invocation.
- A timed-out uncooperative delegate is interrupted best-effort; its old worker may remain occupied, and the fixed pool's configurable worker/queue capacity plus this one-retry overlap is documented and tested without creating a second executor or caller-thread fallback.
- `attempt_count` is `1` or `2`, remains low sensitivity, and does not alter `agent_run_trace.tool_count`.
- No automatic retry, result-text parsing, or confirmation is added for write operations.
