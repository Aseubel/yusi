# AgentToolTrace Implementation Plan

> **For agentic workers:** This plan is executed inline in the current workspace. Each task follows TDD and ends with a focused verification checkpoint.

**Goal:** 为聊天 AgentRun 持久化低敏工具调用明细，支持 null 上游 tool id 的本地关联、终态收敛和安全失败分类。

**Architecture:** 新增独立的 `agent_tool_trace` 子表，不把 `agent_run_trace` 变成万能明细容器，也不引入通用事件溯源平台。`AiController` 在实际的 L342-L372 工具回调处创建和完成工具 Trace；`AgentRunTraceService.complete/fail/cancel` 统一收敛孤儿工具记录。工具服务本身不保存查询和结果正文。

**Tech Stack:** Java 21, Spring Boot 3.4, Spring Data JPA, JUnit 5, Mockito, MySQL migration SQL, React/TypeScript SSE contract unchanged.

## Global Constraints

- 不启动服务，不依赖远程模型、Milvus 或跨域环境。
- 不使用子 agent。
- `AgentToolTrace.toolCallId` 必须由后端在 `beforeToolExecution` 生成；`ToolExecutionRequest.id()` 只能写入可空的 `upstreamToolCallId`。
- `agent_run_trace.tool_count` 语义保持不变，仍表示已完成工具调用总数。
- AgentRun `complete/fail/cancel` 都必须收敛同一用户运行下仍为 `RUNNING` 的工具 Trace。
- `failureCategory` 只使用固定枚举分类，不从工具结果文本或异常 message 推导并落库。
- 不保存 query、工具参数、召回内容、工具结果、Prompt、模型输出、思考内容或密钥。
- `McpGrpcServiceImpl` 的原始 keyword/query 日志本次显式排除，另立日志安全切片处理。

---

### Task 1: Tool Trace Contract and Persistence Model

**Files:**
- Create: `src/main/java/com/aseubel/yusi/pojo/constant/AgentToolConstants.java`
- Create: `src/main/java/com/aseubel/yusi/pojo/entity/AgentToolTrace.java`
- Create: `src/main/java/com/aseubel/yusi/repository/AgentToolTraceRepository.java`
- Create: `src/main/java/com/aseubel/yusi/service/ai/runtime/AgentToolTraceService.java`
- Create: `src/main/resources/db/migration/V20260826__create_agent_tool_trace.sql`
- Modify: `src/main/resources/db/init.sql`
- Create: `src/test/java/com/aseubel/yusi/service/ai/runtime/AgentToolTraceServiceTest.java`

**Interfaces:**
- `AgentToolTraceService.start(String userId, String runId, String localToolCallId, String upstreamToolCallId, String toolName, String toolSource)` creates or returns a `RUNNING` trace idempotently.
- `AgentToolTraceService.complete(String userId, String runId, String localToolCallId, long durationMs, boolean failed)` transitions a running trace to `COMPLETED` or `FAILED`.
- `AgentToolTraceService.closeRunning(String userId, String runId, AgentToolTrace.Status status, AgentToolTrace.FailureCategory failureCategory)` transitions all remaining `RUNNING` rows for one user/run to the supplied terminal state.

- [ ] **Step 1: Write the failing tests**

Add tests that prove:

```java
String localId = service.start("user-1", "run-1", "local-1", null,
        AgentToolConstants.SEARCH_MEMORIES, AgentToolConstants.SOURCE_LOCAL);
service.complete("user-1", "run-1", localId, 120L, false);

assertEquals(AgentToolTrace.Status.COMPLETED, saved.getStatus());
assertNull(saved.getFailureCategory());
assertEquals("local-1", saved.getToolCallId());
```

Also assert that `complete(..., true)` stores only `TOOL_FAILED`, duplicate completion does not alter the terminal row, and `closeRunning(..., CANCELLED, CANCELLED)` closes every running row without touching completed rows.

- [ ] **Step 2: Run the focused test to verify it fails**

Run:

```powershell
.\mvnw.cmd -q -Dtest=AgentToolTraceServiceTest test
```

Expected: compilation failure because the entity, service and repository do not exist.

- [ ] **Step 3: Implement the persistence model**

Create `AgentToolTrace` with `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED` statuses and fixed failure categories `TOOL_FAILED`, `AGENT_ERROR`, `TIMEOUT`, `CANCELLED`, `UNKNOWN`. Add `upstream_tool_call_id` as nullable reference-only data. Add the unique constraint `(user_id, run_id, tool_call_id)`, the run history index `(user_id, run_id, created_at)`, and the recovery index `(status, updated_at)`.

The service must reject blank user/run/local IDs, never accept an arbitrary failure string, update only `RUNNING` rows, and catch no business exceptions from the caller. The controller wrapper will make Trace writes non-blocking.

- [ ] **Step 4: Run the focused test to verify it passes**

Run:

```powershell
.\mvnw.cmd -q -Dtest=AgentToolTraceServiceTest test
```

Expected: PASS.

- [ ] **Step 5: Commit the persistence slice**

```powershell
git add src/main/java/com/aseubel/yusi/pojo/constant/AgentToolConstants.java src/main/java/com/aseubel/yusi/pojo/entity/AgentToolTrace.java src/main/java/com/aseubel/yusi/repository/AgentToolTraceRepository.java src/main/java/com/aseubel/yusi/service/ai/runtime/AgentToolTraceService.java src/main/resources/db/migration/V20260826__create_agent_tool_trace.sql src/main/resources/db/init.sql src/test/java/com/aseubel/yusi/service/ai/runtime/AgentToolTraceServiceTest.java
git commit -m "feat: add low-sensitivity agent tool trace"
```

### Task 2: Null-Safe Tool Correlation and Controller Lifecycle

**Files:**
- Modify: `src/main/java/com/aseubel/yusi/controller/AiController.java:342-372`
- Create: `src/main/java/com/aseubel/yusi/service/ai/runtime/AgentToolTraceCorrelation.java`
- Modify: `src/test/java/com/aseubel/yusi/controller/AiControllerCancellationTest.java`
- Create: `src/test/java/com/aseubel/yusi/service/ai/runtime/AgentToolTraceCorrelationTest.java`

**Interfaces:**
- `AgentToolTraceCorrelation.register(Object requestIdentity, String upstreamToolCallId, String toolName, String localToolCallId)` registers one started tool.
- `AgentToolTraceCorrelation.resolve(Object requestIdentity, String upstreamToolCallId, String toolName)` returns the local id using identity, upstream id, then per-tool FIFO fallback.
- `AgentToolTraceCorrelation.clear()` removes all pending handles for the chat request.

- [ ] **Step 1: Write the failing tests**

Test the correlation object with an upstream id, a null upstream id, and two null-id calls to the same tool. The first two must resolve to their local IDs; the fallback must consume FIFO entries and return empty when no pending handle exists. Test `clear()` removes all pending entries.

Extend `AiControllerCancellationTest` to inject a mocked `AgentToolTraceService`, capture the callbacks, and assert that a request with `id(null)`:

- creates a nonblank local tool id;
- sends the same local id in `tool.started` and `tool.completed`;
- stores the null upstream id without using it as the database key;
- records `TOOL_FAILED` only when `hasFailed()` is true;
- does not expose the private tool result text in any event.

- [ ] **Step 2: Run the focused tests to verify they fail**

Run:

```powershell
.\mvnw.cmd -q -Dtest=AgentToolTraceCorrelationTest,AiControllerCancellationTest test
```

Expected: compilation or assertion failure because the controller has no Trace dependency or local-id correlation.

- [ ] **Step 3: Implement the callback integration**

Inject `AgentToolTraceService` into `AiController`. In the actual `beforeToolExecution` callback at L342-L354:

1. Generate a local id with `IdUtil.fastSimpleUUID()`.
2. Read the upstream request id only as nullable reference data.
3. Start a Trace and register the request identity/upstream id/tool name mapping.
4. Send `tool.started` with the local id.

In the actual `onToolExecuted` callback at L355-L372, resolve the local id, complete exactly that row, classify `hasFailed()` as `TOOL_FAILED`, and send `tool.completed` with the local id. Keep `traceRunToolCompleted` exactly once per callback so `agent_run_trace.tool_count` remains a completed-call aggregate.

All Trace operations go through the existing non-blocking `runTrace`-style guard. A missing or unmatched callback must not complete a different tool row. The per-chat correlation object is cleared after run completion, failure, cancellation, and synchronous setup failure.

- [ ] **Step 4: Run the focused tests to verify they pass**

Run:

```powershell
.\mvnw.cmd -q -Dtest=AgentToolTraceCorrelationTest,AiControllerCancellationTest test
```

Expected: PASS.

- [ ] **Step 5: Commit the controller slice**

```powershell
git add src/main/java/com/aseubel/yusi/controller/AiController.java src/main/java/com/aseubel/yusi/service/ai/runtime/AgentToolTraceCorrelation.java src/test/java/com/aseubel/yusi/controller/AiControllerCancellationTest.java src/test/java/com/aseubel/yusi/service/ai/runtime/AgentToolTraceCorrelationTest.java
git commit -m "feat: correlate chat tool calls with local ids"
```

### Task 3: AgentRun Terminal Convergence

**Files:**
- Modify: `src/main/java/com/aseubel/yusi/service/ai/runtime/AgentRunTraceService.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/runtime/AgentToolTraceService.java`
- Modify: `src/test/java/com/aseubel/yusi/service/ai/runtime/AgentRunTraceServiceTest.java`
- Modify: `src/test/java/com/aseubel/yusi/service/ai/runtime/AgentToolTraceServiceTest.java`

**Interfaces:**
- `AgentRunTraceService` receives `AgentToolTraceService` and calls `closeRunning` before persisting each of `complete`, `fail`, and `cancel`.
- `complete` uses `COMPLETED`; `fail` uses `FAILED + AGENT_ERROR`; `cancel` uses `CANCELLED + CANCELLED`.

- [ ] **Step 1: Write the failing tests**

Add tests for all three terminal paths with one running tool row and one already completed row. Assert that the running row reaches the corresponding terminal status, the completed row is unchanged, and repeated terminal calls do not create or reopen traces. Add a cancellation test where no `onToolExecuted` callback is invoked.

- [ ] **Step 2: Run the focused tests to verify they fail**

Run:

```powershell
.\mvnw.cmd -q -Dtest=AgentRunTraceServiceTest,AgentToolTraceServiceTest test
```

Expected: failure because AgentRun terminal methods do not yet close tool traces.

- [ ] **Step 3: Implement terminal convergence**

Call the tool service from the three terminal methods before or together with the existing AgentRun update. Preserve AgentRun Trace write-failure tolerance and make tool convergence idempotent. Do not derive categories from any exception message or tool result. Keep the existing `tool_count` increment logic unchanged.

- [ ] **Step 4: Run the focused tests to verify they pass**

Run:

```powershell
.\mvnw.cmd -q -Dtest=AgentRunTraceServiceTest,AgentToolTraceServiceTest,AiControllerCancellationTest test
```

Expected: PASS.

- [ ] **Step 5: Commit the terminal convergence slice**

```powershell
git add src/main/java/com/aseubel/yusi/service/ai/runtime/AgentRunTraceService.java src/main/java/com/aseubel/yusi/service/ai/runtime/AgentToolTraceService.java src/test/java/com/aseubel/yusi/service/ai/runtime/AgentRunTraceServiceTest.java src/test/java/com/aseubel/yusi/service/ai/runtime/AgentToolTraceServiceTest.java
git commit -m "feat: converge agent tool traces on run termination"
```

### Task 4: Scope Verification and Roadmap Record

**Files:**
- Modify: `docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md`
- Modify: `docs/superpowers/specs/2026-08-16-agent-tool-trace-design.md`
- Modify: `docs/superpowers/plans/2026-08-16-agent-tool-trace.md`

- [ ] **Step 1: Self-review the persisted data boundary**

Search the changed Java files and schema for `query`, `keyword`, `arguments`, `resultText`, Prompt content, exception message persistence, and secret fields. The only allowed upstream request field is nullable `upstreamToolCallId`; no raw content may be stored or sent in SSE.

- [ ] **Step 2: Run focused and full verification**

Run:

```powershell
git diff --check
.\mvnw.cmd -q -Dtest=AgentToolTraceServiceTest,AgentToolTraceCorrelationTest,AgentRunTraceServiceTest,AiControllerCancellationTest test
.\mvnw.cmd -q test
git status --short --branch
```

Expected: all tests and `git diff --check` pass, no service process is started, and `McpGrpcServiceImpl.java` is unchanged.

- [ ] **Step 3: Update records**

Record that chat tool calls now have low-sensitivity persistent children of AgentRun, local tool IDs handle null LangChain/MCP IDs, all three AgentRun terminal paths converge orphan rows, `agent_run_trace.tool_count` remains an aggregate completed-call count, and MCP gRPC raw query logs are explicitly deferred.

- [ ] **Step 4: Commit the documentation record**

```powershell
git add docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md docs/superpowers/specs/2026-08-16-agent-tool-trace-design.md docs/superpowers/plans/2026-08-16-agent-tool-trace.md
git commit -m "docs: record agent tool trace boundaries"
```
