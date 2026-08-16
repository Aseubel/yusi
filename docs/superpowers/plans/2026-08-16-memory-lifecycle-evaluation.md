# 中期记忆生命周期与消费边界回放实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用真实 H2 和生产记忆消费服务完成 `EVAL-MEM-001` 生命周期回放，证明隐藏、过期、合并、未授权匹配和删除后的记忆不会泄漏到检索或匹配画像。

**Architecture:** 抽取测试侧通用报告 writer 和 fixture 红线校验器，保留现有 LifeGraph 报告的 JSON 兼容输出。新增独立 `memory-lifecycle-v1` fixture/loader/replay suite；repository、生命周期服务、搜索服务和匹配画像服务使用真实 H2，只有 Milvus、Embedding 和向量删除副作用使用 mock。

**Tech Stack:** Java 21, Spring Boot Test, Spring Data JPA, H2 `MODE=MySQL`, JUnit 5, Mockito, Jackson, LangChain4j 1.18.0, Milvus SDK 2.6.16。

## Global Constraints

- 使用 `src/test/resources/application-test.yml` 的 H2 内存库，不使用 repository fake，不写生产数据库或新的 migration。
- 每个场景必须先验证可用记忆正向检索，再验证受限记忆缺席；正向对照失败时负向断言不得通过。
- EVAL-MEM-001-C 必须让 `MidTermMemoryVectorService.delete` 抛异常，并继续验证数据库删除和向量残留零泄漏。
- EVAL-MEM-001-B 以真实 `MatchProfile.midMemorySummary` 为验收层级，不以 repository mock 的调用次数为验收条件。
- 场景 B 不 mock `MidTermMemoryRepository`、`MatchProfileRepository`、`UserService` 或 `UserPersonaService`；只 mock Embedding/Milvus 外部边界。
- 场景 C 分别断言 `OTHER_USER_RESIDUAL_NOT_LEAKED` 和 `DELETE_DOES_NOT_AFFECT_OTHER_USER`。
- 不把 Milvus `HybridSearchReq.filter` / `expr` 的服务端执行列为已覆盖能力，并在最终说明中明确排除。
- `profileLeakCount` 每个 case 和整套报告的固定验收阈值为 `0`。
- fixture 禁止真实正文、Prompt、工具参数、工具结果、密钥、密码和真实用户 ID。
- 默认 `mvn test` 必须执行新回放，不新增可默认跳过的 profile；不启动 Web、gRPC、Redis、Milvus 或模型服务。
- 不启用子 agent，不运行 auto-review。

---

## 文件边界

**共享评测基础：**

- Create: `src/test/java/com/aseubel/yusi/evaluation/OfflineEvaluationReportWriter.java`
- Test: `src/test/java/com/aseubel/yusi/evaluation/OfflineEvaluationReportWriterTest.java`
- Create: `src/test/java/com/aseubel/yusi/evaluation/EvaluationFixtureRedLineValidator.java`
- Test: `src/test/java/com/aseubel/yusi/evaluation/EvaluationFixtureRedLineValidatorTest.java`
- Modify: `src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphTimelineEvaluationReport.java`
- Modify: `src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphTimelineFixtureLoader.java`
- Modify: `src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphTimelineFixtureLoaderTest.java`

**记忆评测套件：**

- Create: `src/test/resources/evaluation/memory-lifecycle-v1-fixtures.json`
- Create: `src/test/java/com/aseubel/yusi/evaluation/memory/MemoryLifecycleEvaluationFixture.java`
- Create: `src/test/java/com/aseubel/yusi/evaluation/memory/MemoryLifecycleFixtureLoader.java`
- Test: `src/test/java/com/aseubel/yusi/evaluation/memory/MemoryLifecycleFixtureLoaderTest.java`
- Create: `src/test/java/com/aseubel/yusi/evaluation/memory/MemoryLifecycleEvaluationTest.java`

**质量门槛：**

- Modify: `.github/workflows/deploy_k8s.yml:24-33`
- Modify: `docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md:502-520`

## Task 1: Add the shared report envelope

**Interfaces:**

- `OfflineEvaluationReportWriter.write(Path path, String suiteId, List<CaseResult> cases)` creates parent directories and writes schema version `1`, runner version `v1`, generated time, sorted cases, four version slots and aggregate summary.
- `OfflineEvaluationReportWriter.CaseResult.actualSummary` is `Map<String, Object>`, allowing LifeGraph and memory suites to use different low-sensitivity counters.
- Violation codes are sorted before serialization; summary status is `PASS` only when every case passes and the list is non-empty.

- [ ] **Step 1: Write the failing generic writer test**

Create `OfflineEvaluationReportWriterTest` with one synthetic case and assert:

```java
var result = new OfflineEvaluationReportWriter.CaseResult(
        "EVAL-MEM-001", "EVAL-MEM-001-A", "PASS", "fixture-v1", "expectation-v1",
        OfflineEvaluationReportWriter.Versions.fixtureBaseline(),
        2, 2, List.of(), Map.of("profileLeakCount", 0));
Path output = tempDir.resolve("memory-report.json");
OfflineEvaluationReportWriter.write(output, "memory-lifecycle-v1", List.of(result));
JsonNode root = objectMapper.readTree(output.toFile());
assertEquals(1, root.path("schemaVersion").asInt());
assertEquals("memory-lifecycle-v1", root.path("suiteId").asText());
assertTrue(root.at("/cases/0/versions/model").isObject());
assertTrue(root.at("/cases/0/versions/prompt").isObject());
assertTrue(root.at("/cases/0/versions/retrieval").isObject());
assertTrue(root.at("/cases/0/versions/ranking").isObject());
assertEquals(0, root.at("/cases/0/actualSummary/profileLeakCount").asInt());
assertEquals("PASS", root.at("/summary/status").asText());
```

Also assert that a failed case makes summary status `FAIL`, generated time exists, and no
fixture value outside the supplied low-sensitivity summary map is generated.

- [ ] **Step 2: Run the writer test red**

Run:

```powershell
.\mvnw.cmd -q "-Dtest=OfflineEvaluationReportWriterTest" test
```

Expected: compilation failure because the shared writer does not exist.

- [ ] **Step 3: Implement the shared writer**

Implement `OfflineEvaluationReportWriter` with records for model, prompt, retrieval/ranking strategy,
versions, case result, report and summary. Use a `LinkedHashMap` copy for `actualSummary`,
sort cases by `caseId` then `scenarioId`, create the parent directory, and serialize with
Jackson modules and ISO-8601 `generatedAt`. Do not add raw input, exception messages, Prompt text or
vector result text.

- [ ] **Step 4: Route the existing LifeGraph writer through the shared envelope**

Keep `LifeGraphTimelineEvaluationReport.write(Path, List<CaseResult>)` as a compatibility facade.
Convert its typed `ActualSummary` to an insertion-ordered map with the existing six keys and convert
its typed version records to `OfflineEvaluationReportWriter.Versions`. Existing report path and JSON
field names must remain compatible.

Run:

```powershell
.\mvnw.cmd -q "-Dtest=OfflineEvaluationReportWriterTest,LifeGraphTimelineEvaluationReportTest" test
```

Expected: both report tests pass and the LifeGraph report still contains four version slots and six
`actualSummary` counters.

- [ ] **Step 5: Commit**

```powershell
git add src/test/java/com/aseubel/yusi/evaluation/OfflineEvaluationReportWriter.java src/test/java/com/aseubel/yusi/evaluation/OfflineEvaluationReportWriterTest.java src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphTimelineEvaluationReport.java
git commit -m "test: extract offline evaluation report envelope"
```

## Task 2: Share fixture red-line validation and add memory fixtures

**Interfaces:**

- `EvaluationFixtureRedLineValidator.validateTree(JsonNode root)` rejects forbidden field names,
  strings longer than 256 characters and non-token evidence fields with stable code `FIXTURE_INVALID`.
- `MemoryLifecycleFixtureLoader.load()` returns typed `MemoryLifecycleEvaluationFixture.Suite`;
  invalid JSON throws its nested validation exception with code `FIXTURE_INVALID`.
- Memory fixture IDs use `fixture-user-*` and `fixture-memory-*`; summary values use
  `memory-summary-*`; lifecycle values are only `ACTIVE`, `HIDDEN`, `EXPIRED`, `MERGED`.

- [ ] **Step 1: Write validator and loader red-line tests**

Add tests for a valid three-scenario fixture and invalid trees containing `rawText`, a non-token
summary, and a non-fixture memory ID. Compare only the stable error code and never print invalid values.
The valid fixture test must load successfully, verify three scenarios, and verify every scenario has
a positive retrieval key and a restricted key.

- [ ] **Step 2: Extract the shared tree validator without changing LifeGraph behavior**

Move recursive forbidden-field, maximum-length and token-prefix checks from
`LifeGraphTimelineFixtureLoader` into `EvaluationFixtureRedLineValidator`. Keep LifeGraph typed
suite checks and its nested exception facade unchanged. Run existing LifeGraph loader tests to prove
`FIXTURE_INVALID` behavior remains unchanged.

- [ ] **Step 3: Implement typed memory fixture records and loader**

Use records with this shape:

```java
public record Suite(int schemaVersion, String suiteId, List<EvaluationCase> cases) {}
public record EvaluationCase(String caseId, List<Scenario> scenarios) {}
public record Scenario(String scenarioId, String userId, List<MemoryRecord> memories,
                       List<VectorCandidate> vectorCandidates, String positiveMemoryKey,
                       String deleteMemoryKey, boolean vectorDeleteFails,
                       Expected expected) {}
public record MemoryRecord(String memoryKey, String ownerUserId, String summaryToken,
                           String lifecycle, boolean matchAllowed, String mergedIntoKey) {}
public record VectorCandidate(String memoryKey, String ownerUserId, String summaryToken) {}
public record Expected(Set<String> availableKeys, Set<String> matchableKeys,
                       Set<String> retrievedKeys, Set<String> restrictedKeys,
                       String retainedUserMemoryKey, String otherUserId,
                       String otherUserMemoryKey) {}
```

Validate suite ID `memory-lifecycle-v1`, case/scenario IDs `EVAL-MEM-001` and
`EVAL-MEM-001-[A-C]`, fixture prefixes, summary token prefixes, non-empty expected sets,
and scenario-specific requirements:

- A has active/chat-only plus hidden/expired/merged records;
- B has a matchable positive and at least one non-matchable or lifecycle-restricted record;
- C has a delete target, retained deleting-user memory, other-user memory and `vectorDeleteFails=true`.

Reject unknown lifecycle, merged record without `mergedIntoKey`, delete target absent from `memories`,
and expected keys absent from the scenario.

- [ ] **Step 4: Add the sanitized fixture resource**

Create `memory-lifecycle-v1-fixtures.json` with:

- A: `fixture-memory-active-a`, `fixture-memory-chat-only-a`, hidden, expired and merged records;
- B: `fixture-memory-matchable-b`, `fixture-memory-chat-only-b`, hidden, expired and merged records;
- C: deleting user `fixture-user-c` owns delete/retained records, `fixture-user-other` owns
  the other record, and vector deletion failure is true.

Use only synthetic tokens such as `memory-summary-active-a`; do not include prose or actual memory content.

- [ ] **Step 5: Run focused loader tests and commit**

```powershell
.\mvnw.cmd -q "-Dtest=EvaluationFixtureRedLineValidatorTest,LifeGraphTimelineFixtureLoaderTest,MemoryLifecycleFixtureLoaderTest" test
```

Expected: valid fixtures load, forbidden fields and bad prefixes fail with `FIXTURE_INVALID`, and
no invalid fixture value is printed. Commit all shared validator, LifeGraph loader, memory fixture and loader files:

```powershell
git add src/test/resources/evaluation/memory-lifecycle-v1-fixtures.json src/test/java/com/aseubel/yusi/evaluation/EvaluationFixtureRedLineValidator.java src/test/java/com/aseubel/yusi/evaluation/EvaluationFixtureRedLineValidatorTest.java src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphTimelineFixtureLoader.java src/test/java/com/aseubel/yusi/evaluation/memory
git commit -m "test: add sanitized memory lifecycle fixtures"
```

## Task 3: Implement the real H2 memory lifecycle replay

**Interfaces:**

- Test entry point: `MemoryLifecycleEvaluationTest.writesTheMemoryLifecycleEvaluationReport()`.
- Inputs: loader, real H2 `MidTermMemoryRepository`, `MidTermMemoryLifecycleService`,
  `MidTermMemorySearchService`, `MatchProfileAssembler`, `UserRepository` and
  `MatchProfileRepository`.
- External doubles: `EmbeddingModel` returns a fixed one-dimensional vector; `MilvusClientV2`
  returns deterministic `SearchResp`; `MidTermMemoryVectorService.delete` throws only in C.
- Output: `target/evaluation/memory-lifecycle-v1-report.json` with one result per scenario and
  `summary.status` equal to `PASS` only when all checks pass.

- [ ] **Step 1: Write the failing Spring test skeleton**

Use `@SpringBootTest`, `@ActiveProfiles("test")` and
`@Import(TestInfrastructureConfig.class)`. Autowire real repositories/services; use
`@MockBean` only for Embedding, Milvus and vector cleanup. Write the report in a `finally` block,
then assert it exists and all results pass.

Run:

```powershell
.\mvnw.cmd -q "-Dtest=MemoryLifecycleEvaluationTest" test
```

Expected: FAIL because replay/setup/assertion methods are not implemented.

- [ ] **Step 2: Persist deterministic H2 records and configure vector candidates**

Create each fixture user in real `UserRepository`, then save `MidTermMemory` rows with fixed
timestamps relative to `FIXED_NOW = LocalDateTime.of(2026, 8, 16, 12, 0)`. Map lifecycle values:

```java
ACTIVE  -> hidden=false, validUntil=null, mergedIntoId=null
HIDDEN  -> hidden=true, validUntil=null, mergedIntoId=null
EXPIRED -> hidden=false, validUntil=FIXED_NOW.minusMinutes(1), mergedIntoId=null
MERGED  -> hidden=false, validUntil=null, mergedIntoId=<survivor id>
```

Set created/updated timestamps from `FIXED_NOW` and maintain a local `Map<String, Long>` from
fixture key to database ID; never put IDs or summary values in the fixture.

Return a `SearchResp` containing hits with `text=<summaryToken>` and metadata
`memoryId=<database id>`, including restricted, deleted and other-user candidates. This tests
the application second filter. Do not assert server-side `HybridSearchReq.filter`; record that
Milvus expr execution is out of scope.

- [ ] **Step 3: Implement positive-first retrieval assertions for A and B**

Use `MidTermMemorySearchService.searchMidTermMemory(userId, "fixture-query", 10)` and
`getRecentMemories(userId, 10)`. Add a helper whose semantics are:

```java
private void checkRetrieval(Checks checks, String positiveToken,
                            List<String> restrictedTokens, List<String> retrieved) {
    boolean positive = retrieved.contains(positiveToken);
    checks.check("RETRIEVAL_POSITIVE_CONTROL", positive);
    checks.check("RESTRICTED_MEMORY_ABSENT",
            positive && restrictedTokens.stream().noneMatch(retrieved::contains));
}
```

For A, retrieval-restricted tokens are hidden/expired/merged. For B, retrieval-restricted tokens
remain hidden/expired/merged because an active `matchAllowed=false` memory is valid for ordinary
Agent recall. Keep a separate profile-restricted set containing chat-only, hidden, expired and
merged values for the later MatchProfile assertion. Assert recent-memory output has its positive
token and no lifecycle-restricted token.

- [ ] **Step 4: Implement B at the MatchProfile output layer**

Call real `MatchProfileAssembler.refreshProfile("fixture-user-b")`. Do not stub
`MidTermMemoryRepository.findMatchableByUserId`; real H2 is under test. Assert:

```java
String profileSummary = profile.getMidMemorySummary();
boolean profilePositive = profileSummary.contains("memory-summary-matchable-b");
int profileLeakCount = List.of(
        "memory-summary-chat-only-b", "memory-summary-hidden-b",
        "memory-summary-expired-b", "memory-summary-merged-b")
        .stream().mapToInt(token -> countOccurrences(profileSummary, token)).sum();
checks.check("PROFILE_POSITIVE_CONTROL", profilePositive);
checks.check("PROFILE_RESTRICTED_MEMORY_ABSENT", profilePositive && profileLeakCount == 0);
checks.check("PROFILE_LEAK_THRESHOLD", profileLeakCount == 0);
```

Mock only `EmbeddingModel.embed(anyString())` and Milvus used by profile sync. Profile row and
`midMemorySummary` must come from real H2 `MatchProfileRepository` and real assembler.

- [ ] **Step 5: Implement C deletion failure and two cross-user checks**

Before deletion, return hits for user A delete target and retained memory and require the target as a
positive result. Configure:

```java
doThrow(new RuntimeException("fixture-vector-delete-failure"))
        .when(midTermMemoryVectorService).delete(deleteTargetId);
assertDoesNotThrow(() -> lifecycleService.delete("fixture-user-c", deleteTargetId));
verify(midTermMemoryVectorService).delete(deleteTargetId);
assertTrue(memoryRepository.findById(deleteTargetId).isEmpty());
```

After deletion, return deleted target, retained user-A memory and user-B memory from the same mock
response. For user A, require retained positive control, then assert deleted target absent and
other-user candidate absent with `OTHER_USER_RESIDUAL_NOT_LEAKED`. Query user B separately and
assert own positive memory remains with `DELETE_DOES_NOT_AFFECT_OTHER_USER`.

Call real `MatchProfileAssembler.refreshProfile("fixture-user-c")` after deletion and
record post-delete `profileLeakCount` from that output. Missing retained positive memory must
fail even when restricted candidates are absent.

- [ ] **Step 6: Write low-sensitivity results and run focused replay**

Each result contains only stable assertion counts, violation codes, version slots and numeric counters:

```java
Map<String, Object> summary = new LinkedHashMap<>();
summary.put("availableCount", availableCount);
summary.put("matchableCount", matchableCount);
summary.put("retrievedCount", retrievedCount);
summary.put("profileLeakCount", profileLeakCount);
summary.put("remainingRowCount", remainingRowCount);
summary.put("crossUserLeakCount", crossUserLeakCount);
```

Never include exception messages, summary tokens, query text, profile text, Milvus entities or database IDs.
Write in `finally`, assert all cases pass, and separately assert total `profileLeakCount` is
exactly zero.

Run:

```powershell
.\mvnw.cmd -q "-Dtest=MemoryLifecycleEvaluationTest" test
```

Expected: exit code 0, three PASS scenarios and `summary.status` `PASS`.

- [ ] **Step 7: Commit the replay**

```powershell
git add src/test/java/com/aseubel/yusi/evaluation/memory/MemoryLifecycleEvaluationTest.java
git commit -m "test: add h2 memory lifecycle replay"
```

## Task 4: Wire CI and roadmap status

- [ ] **Step 1: Use a neutral evaluation artifact name**

Modify the existing `verify` artifact step while keeping `if: always()` and
`target/evaluation/*.json`:

```yaml
- name: Archive offline evaluation reports
  if: always()
  uses: actions/upload-artifact@v4
  with:
    name: offline-evaluation-reports
    path: target/evaluation/*.json
    if-no-files-found: warn
```

Do not move it into deployment, add service startup, or change the Maven test command.

- [ ] **Step 2: Record only the implemented roadmap sub-scope**

Under Phase 4, add a checked sub-entry for `EVAL-MEM-001-A/B/C` covering lifecycle filtering,
MatchProfile authorization, delete/vector-failure residuals and cross-user isolation. Keep the broad
memory evaluation item unchecked because extraction, conflict recognition and model quality remain out
of scope.

- [ ] **Step 3: Verify documentation and commit**

```powershell
git diff --check
rg -n "EVAL-MEM-001|profileLeakCount|Milvus.*expr|offline-evaluation-reports" .github/workflows/deploy_k8s.yml docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md
git add .github/workflows/deploy_k8s.yml docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md
git commit -m "docs: record memory lifecycle evaluation baseline"
```

Expected: artifact remains in `verify`, roadmap records only this sub-scope, and no production
service or endpoint is added.

## Final Verification

Run focused suites then complete backend tests:

```powershell
.\mvnw.cmd -q "-Dtest=MemoryLifecycleFixtureLoaderTest,MemoryLifecycleEvaluationTest,LifeGraphTimelineEvaluationTest" test
.\mvnw.cmd -q test
git diff --check
git status --short
```

Parse both reports:

```powershell
$memory = Get-Content 'target/evaluation/memory-lifecycle-v1-report.json' -Raw | ConvertFrom-Json
$lifegraph = Get-Content 'target/evaluation/lifegraph-timeline-v1-report.json' -Raw | ConvertFrom-Json
$memory.summary.status
$memory.summary.caseCount
$memory.summary.passedCaseCount
$memory.cases.actualSummary.profileLeakCount
$lifegraph.summary.status
```

Required results: memory status `PASS`, three memory cases passed, every
`profileLeakCount` is `0`, existing LifeGraph report remains `PASS`, `git diff --check` is empty,
and `git status --short` is empty. Do not start an application service or make any remote request.
