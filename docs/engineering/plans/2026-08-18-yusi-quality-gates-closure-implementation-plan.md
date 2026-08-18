# Yusi 质量门槛收尾实施计划

> **For inline execution:** 本计划只能在对应设计文档经用户评审确认后执行。当前按用户 AGENTS.md 约束仅允许本会话 inline 实施，禁止子 agent 和 auto-review；一次只执行一个质量子切片，独立提交后停止。

**Goal:** 为对话协议、Timeline 来源重建、匹配/连接边界和关键指标建立默认 Maven 可重复的低敏离线回放门槛。

**Architecture:** 三个领域各自拥有独立的 versioned fixture、strict loader、真实业务边界 replay、报告和聚焦测试；测试侧共享 `QualityGatePolicy` 调用现有 `OfflineEvaluationReportWriter`。对话使用真实 H2 上下文与控制器边界加确定性 Assistant/TokenStream 替身，Timeline 使用真实 H2 LifeGraph/Timeline 服务，匹配使用真实 H2 连接事实加 Mockito 的 Milvus/Embedding/ChatModel。不存在跨测试类读取 `target` 报告的顺序依赖。

**Tech Stack:** Java 21、Spring Boot 3.4、JPA、H2 test profile、JUnit 5、Mockito、LangChain4j TokenStream、现有 `OfflineEvaluationReportWriter`、Maven Surefire、GitHub Actions artifact。

## Global Constraints

- 设计评审未通过前不修改生产代码、测试代码、fixture 或 CI，不启动应用服务或外部依赖。
- 本总计划包含四个独立子切片；当前首个执行目标只有 Task 1，对应切片提交后必须停止等待验收，不得顺手执行 Task 2-4。
- 不新增生产表、migration、API、前端行为、生产事件枚举或用户可见功能；若 replay 发现生产缺陷，记录固定 code 并回到单独设计，不在本计划内修生产代码。
- `src/main/java`、`src/main/resources`、CI workflow、`OfflineEvaluationReportWriter` 的生产/共享契约不改；新增内容仅限明确列出的 `src/test`、测试 fixture 和工程文档。
- 所有 fixture、报告、Trace、日志不得持久化或打印用户 query、记忆正文、Prompt、工具参数/结果、密钥、密码、异常正文或堆栈。
- fixture 必须先经 `EvaluationFixtureRedLineValidator`，再经领域 loader；报告 `actualSummary` 只允许数字、布尔值和固定枚举。
- 报告必须写入 `target/evaluation/*.json`，使用 `OfflineEvaluationReportWriter.write(...)`，并由默认 Maven 测试生成；CI 继续使用现有 artifact wildcard。
- 报告中的 `versions.prompt` 必须正向断言精确等于 `{key: "fixture", version: "fixture-v1", locale: "zh-CN"}`；敏感扫描必须先移除该字段，不得用裸 `prompt` 关键词误报。
- 每个 suite 必须锁定 expected case IDs、最小 assertionCount、PASS case count 和关键零违规计数；不能通过删 case 或减少断言降低门槛。
- `MidTermMemory` 的 `calculateDecayedImportance` 和所有既有衰减/近期状态链路不得改动；新匹配回放必须有 MidTermMemory 正向对照。
- Timeline v1、promotion v1、memory lifecycle v1、importance v1、memory relation v1 的 fixture、报告和行为保持回归绿；Timeline 重建使用独立 suite。
- `match.viewed` 当前不存在；不得用 `match.recommended`、SoulMatch 行数或推荐列表查询冒充接受率分母。报告必须输出 `acceptanceRateAvailable=false` 或等价固定状态。
- 不启动 MySQL、Redis、Milvus、OSS、模型服务；`@SpringBootTest` 只加载 test profile 的进程内容器。最终命令为 `.\mvnw.cmd -q test`。
- 通过全量测试和 scope audit 后独立提交；提交后停下等待验收，并在提交前自查 roadmap checkbox 未被错误勾选。post-release backlog 不启动。

## Slice Order and Stop Points

| 子切片 | 本计划任务 | 产物 | 独立提交后动作 |
| --- | --- | --- | --- |
| 对话协议基线 | Task 1 | `chat-quality-v1-report.json` | 停止，等评审 |
| Timeline 来源重建 | Task 2 | `lifegraph-timeline-rebuild-v1-report.json` | 停止，等评审 |
| 匹配与连接评测集 | Task 3 | `match-quality-v1-report.json` | 停止，等评审 |
| 统一门槛策略/收尾 | Task 4 | 共享策略测试与所有报告门槛 | 停止，等评审 |

Task 5 只描述最终验证方式，不授权在 Task 1-4 的任一提交中跨刀执行。用户确认后若只批准
Task 1，后续任务保持未执行状态。

## File Map

### Task 1: 对话协议基线

- Create: `src/test/resources/evaluation/chat-quality-v1-fixtures.json`：只含 `inputKind`、虚构 key、认知范围、工具策略和固定期望 code。
- Create: `src/test/java/com/aseubel/yusi/evaluation/chat/ChatQualityEvaluationFixture.java`：typed records。
- Create: `src/test/java/com/aseubel/yusi/evaluation/chat/ChatQualityFixtureLoader.java`：红线校验、suite/case/ID/场景形状校验。
- Create: `src/test/java/com/aseubel/yusi/evaluation/chat/ChatQualityFixtureLoaderTest.java`：loader 的版本、场景完整性和低敏拒绝测试。
- Create: `src/test/java/com/aseubel/yusi/evaluation/chat/ChatQualityEvaluationReport.java`：把领域 summary 映射到通用 writer。
- Create: `src/test/java/com/aseubel/yusi/evaluation/chat/ChatQualityEvaluationTest.java`：H2 context、Controller/TokenStream 回放和报告。
- Create: `src/test/java/com/aseubel/yusi/evaluation/QualityGatePolicy.java`：Task 1 先提供通用 PASS/版本/断言/低敏门槛，Task 2-4 复用。
- Create: `src/test/java/com/aseubel/yusi/evaluation/QualityGatePolicyTest.java`：只测试共享策略的失败条件，不依赖其它 suite 报告文件。

### Task 2: Timeline 来源重建

- Create: `src/test/resources/evaluation/lifegraph-timeline-rebuild-v1-fixtures.json`。
- Create: `src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphTimelineRebuildEvaluationFixture.java`。
- Create: `src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphTimelineRebuildFixtureLoader.java`。
- Create: `src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphTimelineRebuildEvaluationReport.java`。
- Create: `src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphTimelineRebuildEvaluationTest.java`。
- Reuse without modifying: `LifeGraphBuildServiceImpl`、`LifeGraphTaskBatchService`、`LifeTimelineService`、`lifegraph-timeline-v1` fixture/loader/report。

### Task 3: 匹配与连接评测集

- Create: `src/test/resources/evaluation/match-quality-v1-fixtures.json`。
- Create: `src/test/java/com/aseubel/yusi/evaluation/match/MatchQualityEvaluationFixture.java`。
- Create: `src/test/java/com/aseubel/yusi/evaluation/match/MatchQualityFixtureLoader.java`。
- Create: `src/test/java/com/aseubel/yusi/evaluation/match/MatchQualityEvaluationReport.java`。
- Create: `src/test/java/com/aseubel/yusi/evaluation/match/MatchQualityEvaluationTest.java`。
- Reuse: real H2 repositories/services for `SoulMatch`、`SoulConnection`、`SoulConnectionEvent`、`ProductEvent`、`MatchFeedback`；Mockito only for Milvus/Embedding/ChatModel and other external boundaries。

### Task 4: 统一门槛收尾

- Modify: Task 1-3 evaluation tests only to call the already-defined `QualityGatePolicy` domain checks.
- Modify: `docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md` only after all four slices are independently accepted, if the roadmap owner explicitly wants checkbox updates; current implementation must leave Phase 4 unchecked.
- Inspect only: `.github/workflows/deploy_k8s.yml` to confirm existing `target/evaluation/*.json` archive remains sufficient。

### Task 5: Final verification

- Generated only: `target/evaluation/*.json`；不提交。
- Inspect only: changed test/fixture files, roadmap checkbox state, post-release backlog state and `git diff --check`。

---

## Task 1: 对话协议基线

**Purpose:** 在默认 Maven 内建立可重复的记忆上下文、克制策略、冲突显式化和工具低敏生命周期基线；不把 Assistant 替身的自由文本当作真实模型质量分。

**Interfaces:**

- `ChatQualityFixtureLoader.load()` returns `ChatQualityEvaluationFixture.Suite`。
- `ChatQualityEvaluationTest` consumes `Suite`, real H2 repositories and existing `AiController`/`ContextBuilderService` beans。
- `QualityGatePolicy.requirePass(...)` consumes the loader's actual suite ID, a list of `OfflineEvaluationReportWriter.CaseResult`, and an immutable `SuiteContract`; it compares the actual suite/case identity with the contract and throws only fixed-code assertion failures。
- Produces `target/evaluation/chat-quality-v1-report.json` with numeric/boolean actual summary only。

- [ ] **Step 1: Lock the pre-change scope and existing report baseline.**

Run:

```powershell
git status --short --branch
git diff --name-only
    rg -n "建立对话评测集|建立 Timeline 评测集|建立匹配评测集|关键指标|^- \[ \].*对话|^- \[ \].*Timeline" docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md
```

Expected: no unrelated user edits are reverted; roadmap quality checkboxes remain unchecked; no
post-release entry is selected. Do not copy report contents or fixture values into terminal output.

- [ ] **Step 2: Write the failing fixture loader contract test.**

Create `ChatQualityFixtureLoaderTest` beside the loader and start with these typed contract cases:

```java
@Test
void loadsTheVersionedSanitizedFixture() {
    Suite suite = new ChatQualityFixtureLoader(objectMapper).load();
    assertEquals(1, suite.schemaVersion());
    assertEquals("chat-quality-v1", suite.suiteId());
    assertEquals(Set.of("EVAL-CHAT-001", "EVAL-CHAT-002", "EVAL-CHAT-003", "EVAL-TOOL-001"),
            suite.cases().stream().map(EvaluationCase::caseId).collect(Collectors.toSet()));
}

@Test
void rejectsQueryAndUnknownFixtureFieldsWithoutEchoingValues() throws Exception {
    ObjectNode invalid = minimalFixture();
    invalid.withArray("cases").get(0).withArray("scenarios").get(0)
            .put("query", "synthetic-forbidden-input");
    ChatQualityFixtureLoader.FixtureValidationException failure = assertThrows(
            ChatQualityFixtureLoader.FixtureValidationException.class,
            () -> loader.load(invalid));
    assertEquals("FIXTURE_INVALID", failure.code());
    assertFalse(failure.getMessage().contains("synthetic-forbidden-input"));
}

private ObjectNode minimalFixture() {
    ObjectNode root = objectMapper.createObjectNode();
    root.put("schemaVersion", 1);
    root.put("suiteId", "chat-quality-v1");
    ObjectNode evaluationCase = objectMapper.createObjectNode();
    evaluationCase.put("caseId", "EVAL-CHAT-001");
    ObjectNode scenario = objectMapper.createObjectNode();
    scenario.put("scenarioId", "EVAL-CHAT-001-A");
    scenario.put("userId", "fixture-user-chat-test");
    scenario.put("inputKind", "NO_HISTORY");
    scenario.set("availableMemoryKeys", objectMapper.createArrayNode());
    scenario.set("allowedTools", objectMapper.createArrayNode());
    scenario.set("expectedPolicyCodes", objectMapper.createArrayNode().add("NO_STABLE_CLAIM_WITHOUT_CONTEXT"));
    scenario.set("expected", objectMapper.createObjectNode().put("semanticModelScoreAvailable", false));
    evaluationCase.set("scenarios", objectMapper.createArrayNode().add(scenario));
    root.set("cases", objectMapper.createArrayNode().add(evaluationCase));
    return root;
}
```

Expected before fixture/loader implementation: compilation or test fails because the typed suite and
resource do not exist. Do not satisfy the test by weakening the forbidden-field rule.

- [ ] **Step 3: Add the strict fixture schema and loader.**

Use records with no raw text fields:

```java
public record Suite(int schemaVersion, String suiteId, List<EvaluationCase> cases) {}
public record EvaluationCase(String caseId, List<Scenario> scenarios) {}
public record Scenario(String scenarioId, String userId, String inputKind,
                       Set<String> availableMemoryKeys, Set<String> allowedTools,
                       Set<String> expectedPolicyCodes, JsonNode expected) {}
```

The loader must call `EvaluationFixtureRedLineValidator.validateTree`, configure
`FAIL_ON_UNKNOWN_PROPERTIES=true`, require suite `chat-quality-v1`, require exactly the four case
IDs above, validate `fixture-user-*`/`fixture-memory-*` patterns, reject `query`, `prompt`,
`response`, `summary`, `content`, tool argument/result fields and duplicate scenario IDs, and require
`expected.semanticModelScoreAvailable=false` for the three chat cases. Use only fixed exception code
`FIXTURE_INVALID`; never include input values in the exception.

- [ ] **Step 4: Add the sanitized fixture and verify only its shape.**

Create four scenarios with `inputKind` values `NO_HISTORY`, `SUPPORTED_AND_UNSUPPORTED_MEMORY`,
`UNRESOLVED_CONFLICT`, and `TOOL_FAILURE`. Use synthetic IDs such as `fixture-user-chat-a` and
`fixture-memory-visible-a`; use fixed policy codes such as `NO_STABLE_CLAIM_WITHOUT_CONTEXT`,
`VISIBLE_MEMORY_ONLY`, `CONFLICT_REQUIRES_ATTENTION`, and `TOOL_EVENT_LOW_SENSITIVITY`.
Do not place a query, message, memory prose, Prompt, or tool data in JSON. Run:

```powershell
.\mvnw.cmd -q "-Dtest=ChatQualityFixtureLoaderTest" test
```

Expected: loader tests PASS; no target report is written by this step.

- [ ] **Step 5: Add the real Spring/H2 context replay boundary.**

Create the test with the existing profile and infrastructure:

```java
@SpringBootTest
@ActiveProfiles("test")
@Import(TestInfrastructureConfig.class)
class ChatQualityEvaluationTest {
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private MidTermMemoryRepository midTermMemoryRepository;
    @Autowired private ChatMemoryMessageRepository chatMemoryMessageRepository;
    @Autowired private ContextBuilderService contextBuilderService;

    @MockBean private PromptManager promptManager;
    @MockBean private CognitiveConflictDetector conflictDetector;
    @MockBean(name = "diaryAssistant")
    private Assistant diaryAssistant;
}
```

Seed only synthetic `User`, `UserPersona`, `MidTermMemory`, `CognitiveConflict` and chat-turn rows.
Use a unique fixture user per scenario and delete/flush those rows before each replay. For
`NO_HISTORY`, assert the context contains the relationship-stage no-invention instruction and no
mid-memory section. For `SUPPORTED_AND_UNSUPPORTED_MEMORY`, assert the visible positive control is
present and hidden/expired/other-user rows are absent. For `UNRESOLVED_CONFLICT`, mock only the
detector result with a fixed policy token and assert the conflict section exists; do not write its
text to the report. These are in-memory assertions over production context assembly, not snapshots
of Prompt text.

- [ ] **Step 6: Add the controller/tool lifecycle replay.**

Use the same controller boundary pattern as `AiControllerCancellationTest`: mock
`Assistant.chatWithMessage(...)` with a `TokenStream` test double, execute the captured executor task,
invoke the captured tool callbacks with fixed tool name/source/status and no real parameters, then
collect only `AgentStreamEvent.type`, `toolName`, `toolSource`, `success`, `durationMs`, and whether
the run reaches a terminal event. Assert:

```java
assertTrue(events.stream()
        .filter(event -> event.type().startsWith("tool.") || event.type().startsWith("run."))
        .allMatch(event -> event.text() == null));
assertEquals(0, events.stream()
        .filter(event -> "response.delta".equals(event.type()))
        .count());
assertTrue(events.stream().filter(event -> "tool.started".equals(event.type())).count() == 1);
assertTrue(events.stream().filter(event -> "tool.completed".equals(event.type())).count() == 1);
assertTrue(events.stream().anyMatch(event -> "run.failed".equals(event.type())));
```

Never create a `ToolExecutionRequest` containing a real-looking query; if the LangChain4j callback
requires arguments, use a short synthetic token in memory and assert it is absent from every captured
event, report, and test log.

- [ ] **Step 7: Write the report and apply the shared gate.**

Build one generic `CaseResult` per scenario. `actualSummary` may contain only fields such as:

```java
Map.of(
        "memoryReferencePolicyPassCount", memoryReferencePolicyPassCount,
        "tonePolicyPassCount", tonePolicyPassCount,
        "noUnsupportedClaimPolicyPassCount", noUnsupportedClaimPolicyPassCount,
        "contextPositiveControlPassCount", contextPositiveControlPassCount,
        "restrictedContextLeakCount", restrictedContextLeakCount,
        "privacyBoundaryViolationCount", privacyBoundaryViolationCount,
        "conflictPolicyPassCount", conflictPolicyPassCount,
        "toolParameterResultExposureCount", toolParameterResultExposureCount,
        "semanticModelScoreAvailable", false)
```

Call `QualityGatePolicy.requirePass(
        suite.suiteId(),
        results,
        new QualityGatePolicy.SuiteContract(
                "chat-quality-v1",
                Set.of("EVAL-CHAT-001", "EVAL-CHAT-002", "EVAL-CHAT-003", "EVAL-TOOL-001"),
                minimumAssertionCount))
    before `OfflineEvaluationReportWriter.write(...)`. Assert report JSON has a `versions.prompt` node exactly
equal to `{key:"fixture",version:"fixture-v1",locale:"zh-CN"}`. Deep-copy the JSON, remove only
`versions.prompt`, and scan for `evidence-token-`, `rawText`, `plainContent`, `toolArguments`,
`toolResult`, `secret`, `password`, query markers and response text markers. Never scan the original
JSON for a bare `prompt` token.

- [ ] **Step 8: Run the focused conversation suite.**

```powershell
.\mvnw.cmd -q "-Dtest=ChatQualityFixtureLoaderTest,ChatQualityEvaluationTest,QualityGatePolicyTest" test
```

Expected: all four case results PASS, report exists at
`target/evaluation/chat-quality-v1-report.json`, `summary.status=PASS`, semantic score availability
is explicitly false, and no low-sensitivity scan violation is printed.

- [ ] **Step 9: Finish the first slice, run full tests, audit and commit.**

Run:

```powershell
.\mvnw.cmd -q test
git diff --check
git diff --name-only
rg -n "Phase 4|建立对话评测集|post-release|上线后扩展" docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md docs/engineering/plans/2026-08-17-yusi-post-release-expansion-backlog.md
```

Expected: full Maven suite exits `0`; existing reports remain PASS and the new chat report is
generated; diff contains only Task 1 test/fixture/report-policy files and approved docs; roadmap
checkboxes and backlog remain unchanged. Commit only this slice:

```powershell
git add src/test/java/com/aseubel/yusi/evaluation/chat src/test/java/com/aseubel/yusi/evaluation/QualityGatePolicy.java src/test/java/com/aseubel/yusi/evaluation/QualityGatePolicyTest.java src/test/resources/evaluation/chat-quality-v1-fixtures.json docs/engineering/specs/2026-08-18-yusi-quality-gates-closure-design.md docs/engineering/plans/2026-08-18-yusi-quality-gates-closure-implementation-plan.md
git commit -m "test: add deterministic chat quality baseline"
```

Stop after the commit and wait for user acceptance. Do not begin Task 2 in the same turn.

---

## Task 2: Timeline 来源修改后的真实 H2 重建

**Purpose:** 在不改变既有 `lifegraph-timeline-v1` baseline 的前提下证明同一 Diary 来源 revision 更新后旧贡献撤销、新贡献生效、删除后 Timeline 同步为空。

**Interfaces:**

- `LifeGraphTimelineRebuildFixtureLoader.load()` returns a strict typed suite with one scenario。
- Replay uses `LifeGraphTaskBatchService.processSingleTask(Long, Diary, String)` and the existing
  8-argument `LifeGraphExtractor.extract(...)` Mockito boundary。
- The report uses `LifeGraphTimelineRebuildEvaluationReport` and shared `QualityGatePolicy`。

- [ ] **Step 1: Write loader/report contract tests before the fixture.**

Require suite `lifegraph-timeline-rebuild-v1`, case `EVAL-TIMELINE-002`, scenario
`EVAL-TIMELINE-002-A`, one DIARY source and exactly three operations `UPSERT(revision=1)`,
`UPSERT(revision=2)`, `DELETE(revision=2)`. Reject unknown fields, non-fixture IDs, no-date Event
expectations and any raw/evidence prose. Expect the focused loader test to fail before files exist.

- [ ] **Step 2: Add the sanitized revision fixture.**

Use `fixture-user-timeline-rebuild` and `fixture-diary-timeline-rebuild`; revision 1 extracts only
`fixture-rebuild-event-old` on `2026-07-01`, revision 2 extracts only `fixture-rebuild-event-new`
on `2026-08-11`. Evidence fields, if required by the existing extractor schema, use only
`evidence-token-rebuild-old`/`evidence-token-rebuild-new`. Expected data contains counts and fixed
tokens only, never event text.

- [ ] **Step 3: Implement the real H2 replay in TDD order.**

Seed the Diary and user, create a task per event, configure the extractor with the fixture extraction,
and call the production task batch service. After revision 1, call `getLifeChapters` and assert one
old node. After revision 2, assert old node count `0`, new node count `1`, current source entity/
relation/evidence counts match the revision 2 expectation, and no old source residual remains. After
DELETE, assert Timeline node count `0` and source residual count `0`. Use H2 repository queries as
the factual checks; do not implement a second Timeline clusterer in test code.

- [ ] **Step 4: Write the report and enforce rebuild-specific thresholds.**

Use actual summary fields `beforeRevisionNodeCount`, `afterRevisionOldResidualCount`,
`afterRevisionNewNodeCount`, `afterDeleteTimelineNodeCount`, and `sourceResidualCount`. Required
thresholds are `1`, `0`, `1`, `0`, and `0`. Positive `versions.prompt` assertion and post-removal
low-sensitivity scan follow Task 1 exactly.

- [ ] **Step 5: Run focused, then full tests and commit only this slice.**

```powershell
.\mvnw.cmd -q "-Dtest=LifeGraphTimelineRebuildFixtureLoaderTest,LifeGraphTimelineRebuildEvaluationTest" test
.\mvnw.cmd -q test
git diff --check
git add src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphTimelineRebuild* src/test/resources/evaluation/lifegraph-timeline-rebuild-v1-fixtures.json
git commit -m "test: add timeline rebuild replay gate"
```

Expected: old `lifegraph-timeline-v1` report remains its original suite/case contract; new report is
PASS; full test exits `0`. Stop and wait for acceptance.

---

## Task 3: 匹配召回、理由与连接质量评测集

**Purpose:** 用真实 H2 证明连接事实和反馈闭环，用 Mockito 固定外部召回/模型结果，覆盖召回覆盖、理由覆盖、双向共鸣、持续互动和强负面排除；明确记录接受率分母缺失。

**Interfaces:**

- `MatchQualityFixtureLoader.load()` returns three typed scenarios with profile keys, expected recall
  counts, fixed reason count and lifecycle actions; no profile/letter/reason prose。
- `MatchQualityEvaluationTest` consumes real `SoulMatchRepository`, `SoulConnectionRepository`,
  `SoulConnectionEventRepository`, `ProductEventRepository`, `MatchFeedbackRepository` and real
  lifecycle/feedback services。
- Milvus `hybridSearch`, Embedding, ChatModel, PromptManager and asynchronous letter calls are
  deterministic Mockito boundaries; no external service is started。

- [ ] **Step 1: Write the fixture loader failure-first tests.**

Require suite `match-quality-v1`, case `EVAL-MATCH-001`, scenarios `EVAL-MATCH-001-A/B/C`, fixture
user/profile ID patterns, fixed `recallCandidates`, `expectedReasonCount`, lifecycle action enums,
and `acceptanceRateAvailable=false`. Reject `profileText`, `reason`, `letter`, `query`, `prompt`,
tool and password fields. Expect loader test failure before fixture/loader implementation.

- [ ] **Step 2: Add the sanitized matching fixture.**

Scenario A defines two recalled candidate keys, one target profile and a required reason count of 3;
scenario B defines the two participant keys and ordered actions `ACCEPT`, `ACCEPT`,
`DEEP_INTERACTION`, `DEEP_INTERACTION`; scenario C defines an old pair, `REPORT` feedback and a
required subsequent recommendation count of 0. Use only fixture IDs, counts, enum codes and boolean
expectations. Do not store profile summaries, LLM JSON, reason text or letters.

- [ ] **Step 3: Establish the H2/mocked dependency boundary.**

Use `@SpringBootTest`, `@ActiveProfiles("test")`, `@Import(TestInfrastructureConfig.class)`. Mock
only `MilvusClientV2`, `EmbeddingModel`, `ChatModel`, `PromptManager` and the asynchronous executor
if needed to make `generateLetter` deterministic. Because `@MockBean PromptManager` replaces the
global bean, configure fixed token defaults before replay so unrelated beans never receive Mockito
nulls:

```java
when(promptManager.getPrompt(any(PromptKey.class))).thenReturn("fixture-token-prompt");
when(promptManager.getPrompt(anyString())).thenReturn("fixture-token-prompt");
when(promptManager.getSnapshot(any(PromptKey.class))).thenReturn(
        new PromptSnapshot("fixture", "fixture-v1", "zh-CN", "fixture-token-prompt"));
```

Use `@MockBean` rather than `@SpyBean` deliberately: the match replay must not read or depend on
database-backed prompt contents, and every prompt result must remain a fixed in-memory token. Use
real repositories/services for business rows.
The ChatModel stub returns an in-memory JSON object with `resonance=true`, a score above the current
threshold, and three synthetic reason fields; do not write this JSON to a report or log. The Milvus
stub returns only metadata maps for fixture user IDs; assert query-side user exclusion from the
captured request, but never print that request.

- [ ] **Step 4: Replay recall and recommendation reason coverage.**

Run the production matching method for scenario A with fixture users and profiles. Assert the expected
recall count is reached, one recommendation is persisted, its `match.recommended` ProductEvent has
only allowed low-sensitivity payload keys, and `reasonCount` equals the fixed expected count. Report
only numeric counts. Do not assert or expose the reason/letter content in the report.

- [ ] **Step 5: Replay acceptance, continuous interaction and mutual resonance.**

Persist one `SoulMatch`, execute the lifecycle service's `accept` twice for the two participants, then
record both `DEEP_INTERACTION` feedback rows through the real feedback service. Before the second
deep signal assert the connection is `STARTED` and `allowsChat()`; after both signals call the
production mutual-resonance transition and assert `MUTUAL_RESONANCE`, two feedback rows, and ordered
connection/product events. Count `connection.accepted` as accepted transition facts, not as viewed.

- [ ] **Step 6: Replay strong negative exclusion.**

Persist an old pair outside the recent exposure window, add a real `REPORT` feedback row, flush H2,
and run the production weekly matching path with the same synthetic candidate set. Assert the pair's
strong negative query is true and no new `SoulMatch` row for the pair is created. A recent exposure
or skip cooldown alone is not a valid proof; the setup must be old enough that the strong-negative
branch is the decisive exclusion.

- [ ] **Step 7: Write the report and apply metric thresholds.**

Use actual summary fields `recallExpectedCount`, `recallMatchedCount`, `recommendationCount`,
`reasonCoveragePassCount`, `startedInteractionPassCount`, `mutualResonancePassCount`,
`strongNegativeExcludedCount`, `recommendedCount`, `acceptedCount`, `viewedCount`, and
`acceptanceRateAvailable`. Require exact/zero checks: recall matched equals expected, reason/
interaction/resonance/negative counts each equal their one-case denominator, `viewedCount=0`, and
`acceptanceRateAvailable=false`. Do not calculate a rate. Use the same versions.prompt positive
assertion and redline scan.

- [ ] **Step 8: Run focused, then full tests and commit only this slice.**

```powershell
.\mvnw.cmd -q "-Dtest=MatchQualityFixtureLoaderTest,MatchQualityEvaluationTest" test
.\mvnw.cmd -q test
git diff --check
git add src/test/java/com/aseubel/yusi/evaluation/match src/test/resources/evaluation/match-quality-v1-fixtures.json
git commit -m "test: add matching quality replay gate"
```

Expected: match report PASS, acceptance rate explicitly unavailable, all existing evaluation suites
PASS, full Maven exits `0`. Stop and wait for acceptance.

---

## Task 4: 统一 QualityGatePolicy 与收尾门槛

**Purpose:** 将四类质量门槛的通用规则固定为 test-only executable policy，避免某个新 suite
通过减少 case/断言或隐藏敏感字段来“变绿”。此任务必须在 Task 1-3 分别验收后另起切片。

**Interfaces:**

```java
public final class QualityGatePolicy {
    public static final class GateViolation extends AssertionError {
        public GateViolation(String code) { super(code); }
    }

    public record SuiteContract(String expectedSuiteId,
                                Set<String> expectedCaseIds,
                                int minimumAssertionCount) {
        public SuiteContract {
            expectedCaseIds = Set.copyOf(expectedCaseIds);
        }
    }

    public static void requirePass(
            String actualSuiteId,
            List<OfflineEvaluationReportWriter.CaseResult> cases,
            SuiteContract contract) {
        // Validate actualSuiteId, exact case IDs/count, PASS status, assertion totals,
        // fixture versions, and low-sensitivity summary values in that order.
        // Throw GateViolation with a fixed code at the first failed invariant.
    }

    public static void requireMetricAtLeast(
            Map<String, Object> summary, String metric, int expected, String violationCode) {
        // Read a finite, non-negative integer metric and require value >= expected.
    }

    public static void requireMetricEquals(
            Map<String, Object> summary, String metric, int expected, String violationCode) {
        // Read a finite, non-negative integer metric and require value == expected.
    }

    public static int intMetric(Map<String, Object> summary, String metric) {
        // Return only an Integer-valued metric; reject missing, fractional, negative, or non-finite values.
    }

    public static boolean booleanMetric(Map<String, Object> summary, String metric) {
        // Return only a Boolean-valued metric; reject missing or non-Boolean values.
    }
}
```

- [ ] **Step 1: Write policy unit tests for all failure modes.**

Cover missing case, failed case, assertion count reduction, suite mismatch, missing versions.prompt,
wrong prompt identity, negative/NaN-like metric values, sensitive summary value and metric mismatch.
Every failure must expose only the fixed violation code. Do not use target reports as fixtures for this
unit test.

- [ ] **Step 2: Implement the policy and wire all new suites.**

Use immutable expected sets and numeric checks. Keep domain-specific metric names in each report test;
the shared policy only validates types, PASS status, minimum assertions, version identity and safe
summary values. Existing reports continue to be validated by their own existing test classes; do not
refactor them into a cross-test file reader with Surefire ordering dependency.

- [ ] **Step 3: Add the missing-denominator contract assertion.**

In `MatchQualityEvaluationTest`, assert exactly:

```java
assertEquals(0, intMetric(summary, "viewedCount"));
assertFalse(booleanMetric(summary, "acceptanceRateAvailable"));
```

If a future implementation adds `match.viewed`, this assertion should be changed only in a separate
event-instrumentation design, not silently loosened here.

- [ ] **Step 4: Run all new suites and inspect report semantics.**

```powershell
.\mvnw.cmd -q "-Dtest=ChatQualityEvaluationTest,LifeGraphTimelineRebuildEvaluationTest,MatchQualityEvaluationTest,QualityGatePolicyTest" test
```

Expected: all new reports exist, all summary statuses are PASS, `versions.prompt` has the exact
fixture identity, and only fixed counts/statuses appear in `actualSummary`.

- [ ] **Step 5: Run full Maven and commit only the policy wiring.**

```powershell
.\mvnw.cmd -q test
git diff --check
git status --short
git add src/test/java/com/aseubel/yusi/evaluation/QualityGatePolicy.java src/test/java/com/aseubel/yusi/evaluation/QualityGatePolicyTest.java src/test/java/com/aseubel/yusi/evaluation/chat/ChatQualityEvaluationTest.java src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphTimelineRebuildEvaluationTest.java src/test/java/com/aseubel/yusi/evaluation/match/MatchQualityEvaluationTest.java
git commit -m "test: enforce quality gate thresholds"
```

Expected: full suite green and all reports archived by the existing workflow. Stop and wait for
acceptance; do not update roadmap checkboxes in the same implementation commit unless explicitly
requested after evidence review.

---

## Task 5: Final verification checklist (performed once per slice)

- [ ] Run `.\mvnw.cmd -q test` and record exit code `0`; do not claim success without the command output.
- [ ] Confirm all expected reports are below `target/evaluation` and have `summary.status=PASS`.
- [ ] For every report, assert `versions.prompt` exact fixture object after parsing JSON; remove only that node before sensitive scan.
- [ ] Confirm `actualSummary` contains no IDs, query, memory text, Prompt, tool data, keys, exception message or stack trace.
- [ ] Confirm every fixture passed `EvaluationFixtureRedLineValidator` and its strict domain loader.
- [ ] Confirm no `src/main`/migration/frontend/CI production behavior changed in the slice.
- [ ] Confirm `MidTermMemory` decay code and existing five reports remain untouched and green.
- [ ] Confirm roadmap Phase 4 checkbox updates are not silently made before each slice is accepted; broad memory evaluation, `match.viewed` instrumentation and real-model semantic scoring remain explicitly open.
- [ ] Run `git diff --check`, inspect `git status --short`, make one slice-specific commit, and stop for user验收.
