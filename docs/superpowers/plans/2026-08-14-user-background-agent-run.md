# User Background AgentRun Implementation Plan

> **For inline execution:** This plan is executed in the current workspace. Each task follows TDD and ends with a focused verification checkpoint.

**Goal:** 将认知摄取、LifeGraph、周报和主动问候统一关联到用户级 `AgentRun`、`TaskExecution` 和 `ModelCallTrace`，并保持低敏、幂等和可恢复。

**Architecture:** 复用现有 `agent_run_trace`、`task_execution` 和 `model_call_trace`。为 `ModelRouteContextHolder` 增加可嵌套 scope，使后台运行的 `runId` 在异步线程内被每次模型调用继承。每个用户级工作流使用稳定任务幂等键创建或复用 `runId`；跨用户匹配批处理不接入本切片。

**Tech Stack:** Java 21, Spring Boot 3.4, Spring Data JPA, JUnit 5, Mockito, MySQL migration SQL.

## Global Constraints

- 不启动服务，不依赖远程模型、Milvus 或跨域环境。
- 遵循 TDD：每个行为先写失败测试并确认失败，再写生产代码。
- 不保存原始文本、Prompt 正文、模型输出、思考内容、工具参数或密钥。
- 不启用子 agent，不把匹配批处理强行建模为用户级 AgentRun。
- 重试复用同一个逻辑 `runId`；稳定幂等键不得包含随机扫描批次 ID。
- 所有异步线程显式打开和清理运行上下文。

---

### Task 1: Scoped Run Context and Task Contracts

**Files:**
- Modify: `src/main/java/com/aseubel/yusi/service/ai/runtime/AgentRunTraceService.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/ModelRouteContextHolder.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/ModelProxyFactory.java`
- Modify: `src/main/java/com/aseubel/yusi/config/ai/PersistentChatMemoryStore.java`
- Modify: `src/main/java/com/aseubel/yusi/service/task/TaskExecutionService.java`
- Modify: `src/main/java/com/aseubel/yusi/pojo/constant/TaskExecutionType.java`
- Modify: `src/main/java/com/aseubel/yusi/pojo/constant/TaskExecutionSourceType.java`
- Modify: `src/main/java/com/aseubel/yusi/pojo/constant/TaskExecutionKeys.java`
- Create: `src/test/java/com/aseubel/yusi/service/ai/model/ModelRouteContextHolderTest.java`
- Modify: `src/test/java/com/aseubel/yusi/service/ai/runtime/AgentRunTraceServiceTest.java`
- Modify: `src/test/java/com/aseubel/yusi/service/task/TaskExecutionServiceTest.java`

**Interfaces:**
- `AgentRunTraceService.open(String userId, String runId, String scene)` returns `RunScope` with `runId()`, `complete()`, `fail(String)`, `retryWait()` and `close()`.
- `ModelRouteContextHolder.open(ModelRouteContext)` pushes/restores a frame; `set`/`clear` remain compatible; `getEffective()` merges nearest correlation fields.
- `TaskExecutionService.findByTaskId(String)` returns `Optional<TaskExecution>`.
- Add `COGNITION_INGEST` and `PROACTIVE_GREETING` task types, the proactive source type, and `TaskExecutionKeys.daily(...)`.

- [x] **Step 1: Write failing tests.**

The first test must prove nested restore and inherited correlation:

```java
try (ModelRouteContextHolder.Scope ignored = ModelRouteContextHolder.open(
        ModelRouteContext.builder().runId("run-outer").userId("user-1").build())) {
    ModelRouteContextHolder.set(ModelRouteContext.builder().scene("image").build());
    assertEquals("image", ModelRouteContextHolder.get().getScene());
    assertEquals("run-outer", ModelRouteContextHolder.getEffective().getRunId());
    ModelRouteContextHolder.clear();
    assertEquals("run-outer", ModelRouteContextHolder.get().getRunId());
}
assertNull(ModelRouteContextHolder.get());
```

Extend `AgentRunTraceServiceTest` to assert `open(...).complete()` persists a completed trace and clears the context. Extend `TaskExecutionServiceTest` to assert repository lookup and stable daily keys.

- [x] **Step 2: Verify RED.**

Run `./mvnw -q -Dtest=ModelRouteContextHolderTest,AgentRunTraceServiceTest,TaskExecutionServiceTest test`.

Expected: compilation/test failure because the new scope, lookup, enum values and key helper are absent.

- [x] **Step 3: Implement minimally.**

Use `ThreadLocal<Deque<ModelRouteContext>>`; `set` pushes, `clear` pops one frame, and an idempotent `Scope` restores its frame. `getEffective` fills request/run/user/scene/Prompt/routing fields from newest to oldest. Make `ModelProxyFactory.resolveContext` and `PersistentChatMemoryStore.currentRunId` use it. `RunScope` delegates terminal methods to `AgentRunTraceService`; blank run IDs use `IdUtil.fastSimpleUUID()`. Add the lookup, enums and daily key without adding a table.

- [x] **Step 4: Verify GREEN.**

Run `./mvnw -q -Dtest=ModelRouteContextHolderTest,AgentRunTraceServiceTest,TaskExecutionServiceTest,ModelProxyFactoryTest,PersistentChatMemoryStoreCorrelationTest test`.

- [x] **Step 5: Commit.**

```bash
git add src/main/java/com/aseubel/yusi/service/ai/runtime/AgentRunTraceService.java src/main/java/com/aseubel/yusi/service/ai/model/ModelRouteContextHolder.java src/main/java/com/aseubel/yusi/service/ai/model/ModelProxyFactory.java src/main/java/com/aseubel/yusi/config/ai/PersistentChatMemoryStore.java src/main/java/com/aseubel/yusi/service/task/TaskExecutionService.java src/main/java/com/aseubel/yusi/pojo/constant/TaskExecutionType.java src/main/java/com/aseubel/yusi/pojo/constant/TaskExecutionSourceType.java src/main/java/com/aseubel/yusi/pojo/constant/TaskExecutionKeys.java src/test/java/com/aseubel/yusi/service/ai/model/ModelRouteContextHolderTest.java src/test/java/com/aseubel/yusi/service/ai/runtime/AgentRunTraceServiceTest.java src/test/java/com/aseubel/yusi/service/task/TaskExecutionServiceTest.java
git commit -m "feat: add scoped background agent run context"
```

### Task 2: Correlate Cognition Ingestion

**Files:**
- Modify: `src/main/java/com/aseubel/yusi/pojo/dto/cognition/CognitionIngestCommand.java`
- Modify: `src/main/java/com/aseubel/yusi/service/cognition/impl/AgentCognitionOrchestratorImpl.java`
- Modify: `src/test/java/com/aseubel/yusi/service/cognition/AgentCognitionOrchestratorTest.java`

**Interfaces:** `CognitionIngestCommand.runId` is optional at event creation. The orchestrator creates `COGNITION_INGEST` with `TaskExecutionKeys.fromSourceRevision(...)`, claims it as `cognition-ingest`, opens `AgentRunTrace(scene=cognition_ingest)`, then finishes both ledgers.

- [x] **Step 1: Write failing tests.**

Capture `TaskExecutionCommand` and assert a generated run ID, exact reuse of a supplied run ID, and `TaskExecutionService.succeed` on normal/empty Diary ingestion. Keep the existing assertion that empty Diary removes old memory and does not call cognition.

- [x] **Step 2: Verify RED.**

Run `./mvnw -q -Dtest=AgentCognitionOrchestratorTest test`; expected failure because the command and orchestrator have no run/task boundary.

- [x] **Step 3: Implement.**

Create/reuse the task by source revision, skip already successful executions, claim new work, open the scope, preserve Diary source removal, and complete or fail both ledgers. Do not store text in checkpoints.

- [x] **Step 4: Verify GREEN.**

Run `./mvnw -q -Dtest=AgentCognitionOrchestratorTest,CognitionRoutingServiceImplTest test`.

- [x] **Step 5: Commit.**

```bash
git add src/main/java/com/aseubel/yusi/pojo/dto/cognition/CognitionIngestCommand.java src/main/java/com/aseubel/yusi/service/cognition/impl/AgentCognitionOrchestratorImpl.java src/test/java/com/aseubel/yusi/service/cognition/AgentCognitionOrchestratorTest.java
git commit -m "feat: correlate cognition ingestion runs"
```

### Task 3: Correlate Diary and Plaza LifeGraph Tasks

**Files:**
- Modify: `src/main/java/com/aseubel/yusi/service/lifegraph/LifeGraphTaskCreator.java`
- Modify: `src/main/java/com/aseubel/yusi/service/lifegraph/LifeGraphTaskBatchService.java`
- Modify: `src/main/java/com/aseubel/yusi/service/lifegraph/PlazaLifeGraphListener.java`
- Create: `src/test/java/com/aseubel/yusi/service/lifegraph/LifeGraphTaskCreatorTest.java`
- Modify: `src/test/java/com/aseubel/yusi/service/lifegraph/LifeGraphTaskBatchServiceTest.java`
- Modify: `src/test/java/com/aseubel/yusi/service/lifegraph/PlazaLifeGraphListenerTest.java`

**Interfaces:** New LifeGraph task commands always carry a generated run ID; existing idempotency rows win. The worker loads the execution by `taskExecutionId`, opens the scope around `LifeGraphBuildService`, and keeps the AgentRun running during retry wait.

- [x] **Step 1: Write failing tests.**

Capture the creator/listener command and assert a non-empty run ID. In the batch test return an execution with `runId="life-run-1"`, throw from extraction, and assert retry wait does not complete the AgentRun; add a success case for completion and context cleanup.

- [x] **Step 2: Verify RED.**

Run `./mvnw -q -Dtest=LifeGraphTaskCreatorTest,LifeGraphTaskBatchServiceTest,PlazaLifeGraphListenerTest test`; expected failure because creation and workers do not propagate runs.

- [x] **Step 3: Implement.**

Generate the run before `createOrGet`, then use the returned execution run ID and start the trace. In workers, claim/load the execution, open/close the scope explicitly, complete delete/blank/missing/superseded paths, and only fail the run when task retry reaches a terminal failure.

- [x] **Step 4: Verify GREEN.**

Run `./mvnw -q -Dtest=LifeGraphTaskCreatorTest,LifeGraphTaskBatchServiceTest,PlazaLifeGraphListenerTest,LifeGraphCognitionBridgeServiceTest test`.

- [x] **Step 5: Commit.**

```bash
git add src/main/java/com/aseubel/yusi/service/lifegraph/LifeGraphTaskCreator.java src/main/java/com/aseubel/yusi/service/lifegraph/LifeGraphTaskBatchService.java src/main/java/com/aseubel/yusi/service/lifegraph/PlazaLifeGraphListener.java src/test/java/com/aseubel/yusi/service/lifegraph/LifeGraphTaskCreatorTest.java src/test/java/com/aseubel/yusi/service/lifegraph/LifeGraphTaskBatchServiceTest.java src/test/java/com/aseubel/yusi/service/lifegraph/PlazaLifeGraphListenerTest.java
git commit -m "feat: correlate lifegraph task runs"
```

### Task 4: Correlate Weekly Reports and Proactive Greetings

**Files:**
- Modify: `src/main/java/com/aseubel/yusi/service/report/SoulReportGenerator.java`
- Modify: `src/main/java/com/aseubel/yusi/service/agent/impl/AgentProactiveServiceImpl.java`
- Create: `src/test/java/com/aseubel/yusi/service/report/SoulReportGeneratorTest.java`
- Create: `src/test/java/com/aseubel/yusi/service/agent/AgentProactiveServiceImplTest.java`

**Interfaces:** Reports use a per-user run and stable `userId + periodStart` idempotency. Eligible greetings use `PROACTIVE_GREETING` with a daily stable key; the scan batch remains log-only.

- [x] **Step 1: Write failing tests.**

Mock one active report user and a successful model response; assert the task command has a stable key without a batch UUID and the saved report uses the task run ID. For proactive greeting, assert one eligible user creates one task/notification, model failure with template fallback completes, and an existing completed task does not call model/notification.

- [x] **Step 2: Verify RED.**

Run `./mvnw -q -Dtest=SoulReportGeneratorTest,AgentProactiveServiceImplTest test`; expected failure because neither workflow creates a task/run boundary and reports share a scan-level ID.

- [x] **Step 3: Implement report correlation.**

Use a stable period key, create the per-user execution, open a scoped model context with its returned run ID, save it to `SoulReport.generationRunId`, and complete/fail task and run. Keep no-activity and notification fallback behavior.

- [x] **Step 4: Implement proactive correlation.**

After existing eligibility checks pass, create the daily task and skip completed/running duplicates. Open a per-user scope for model generation. Template fallback plus successful notification completes; notification failure fails. Do not change current quiet-hour/cooldown rules.

- [x] **Step 5: Verify GREEN and commit.**

Run `./mvnw -q -Dtest=SoulReportGeneratorTest,AgentProactiveServiceImplTest test`, then:

```bash
git add src/main/java/com/aseubel/yusi/service/report/SoulReportGenerator.java src/main/java/com/aseubel/yusi/service/agent/impl/AgentProactiveServiceImpl.java src/test/java/com/aseubel/yusi/service/report/SoulReportGeneratorTest.java src/test/java/com/aseubel/yusi/service/agent/AgentProactiveServiceImplTest.java
git commit -m "feat: trace report and proactive agent runs"
```

### Task 5: Run-Scoped Model Trace Query and Migration

**Files:**
- Modify: `src/main/java/com/aseubel/yusi/pojo/entity/ModelCallTrace.java`
- Modify: `src/main/java/com/aseubel/yusi/pojo/dto/model/ModelCallTraceQuery.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/ModelManagementService.java`
- Create: `src/main/resources/db/migration/V20260825__add_model_trace_run_scope_index.sql`
- Modify: `src/main/resources/db/init.sql`
- Modify: `src/test/java/com/aseubel/yusi/service/ai/runtime/ModelCallTraceServiceTest.java`
- Create: `src/test/java/com/aseubel/yusi/service/ai/model/ModelManagementServiceTest.java`

**Interfaces:** Add `ModelCallTraceQuery.runId` and an entity index on `(user_id, run_id, created_at)`; the filter remains inside existing admin/user scope.

- [x] **Step 1: Write failing tests.**

Assert model event persistence includes `runId`/`userId`. Capture the management specification and assert a query with both fields contributes both predicates.

- [x] **Step 2: Verify RED.**

Run `./mvnw -q -Dtest=ModelCallTraceServiceTest,ModelManagementServiceTest test`; expected failure because the DTO and specification ignore `runId`.

- [x] **Step 3: Implement and verify.**

Add the field, equality predicate, entity index, incremental migration and matching `init.sql` index. Run `./mvnw -q -Dtest=ModelCallTraceServiceTest,ModelManagementControllerTest test`.

- [x] **Step 4: Commit.**

```bash
git add src/main/java/com/aseubel/yusi/pojo/entity/ModelCallTrace.java src/main/java/com/aseubel/yusi/pojo/dto/model/ModelCallTraceQuery.java src/main/java/com/aseubel/yusi/service/ai/model/ModelManagementService.java src/main/resources/db/migration/V20260825__add_model_trace_run_scope_index.sql src/main/resources/db/init.sql src/test/java/com/aseubel/yusi/service/ai/runtime/ModelCallTraceServiceTest.java src/test/java/com/aseubel/yusi/service/ai/model/ModelManagementServiceTest.java
git commit -m "feat: query model traces by agent run"
```

### Task 6: Roadmap and Full Verification

**Files:**
- Modify: `docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md`
- Modify: `docs/superpowers/plans/2026-08-14-user-background-agent-run.md`

- [x] **Step 1: Update records.**

Record the four completed workflow associations, per-user report semantics, proactive ledger and matching-batch exclusion; leave memory retrieval, tool governance and cancellation as later Phase 3 work. Mark this plan’s completed tasks.

- [x] **Step 2: Run final verification.**

Run:

```bash
git diff --check
./mvnw -q test
git status --short --branch
git log --oneline -8
```

Expected: all commands exit 0, only planned files changed, and no service process is started.

- [x] **Step 3: Commit documentation.**

```bash
git add docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md docs/superpowers/plans/2026-08-14-user-background-agent-run.md
git commit -m "docs: record background agent run progress"
```
