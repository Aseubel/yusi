# Offline Evaluation Replay Baseline Implementation Plan

> **For agentic workers:** Execute this plan task-by-task in the current session. Do not use subagents for this plan. Each implementation change follows a red-green-refactor cycle and is committed at a stable boundary.

**Goal:** Build a deterministic, H2-backed LifeGraph/Timeline replay baseline that emits a versioned low-sensitivity JSON report in the default Maven test suite and archives it in CI.

**Architecture:** The replay harness loads a strict, sanitized fixture from test resources, drives the real LifeGraph repositories and services against the existing H2 `test` profile, and evaluates stable aggregate facts rather than raw text. Source-order cases enter the existing `LifeGraphTaskBatchService` revision short-circuit; source replacement and deletion cases exercise the real `LifeGraphBuildService` and `LifeTimelineService`. Test-only fixture/report support stays under `src/test`, while the production change is limited to protecting DELETE processing from stale Diary revisions.

**Tech Stack:** Java 21, Spring Boot test, Spring Data JPA, H2 `MODE=MySQL`, JUnit 5, Mockito only for the fixed extractor/Prompt boundary, Jackson, Maven Surefire, GitHub Actions artifacts.

## Global Constraints

- Replay persistence is the existing H2 embedded test database with `ddl-auto=create-drop`; no in-memory repository fake and no development/production database writes.
- Report schema version is `1` and always reserves `model`, `prompt`, `retrieval`, and `ranking` version slots.
- New samples use the existing `EVAL-<DOMAIN>-<NNN>` contract: `EVAL-MEM-002` and `EVAL-TIMELINE-001`, with `EVAL-MEM-002-A/B/C` scenarios.
- Source tests cover stale revision ordering, same-revision duplicate replacement, and complete source deletion cleanup while preserving other sources.
- Fixtures contain only synthetic IDs, evidence tokens, structured expected values, and fixed extractor output; no real text, prompt, tool data, secret, password, or reversible personal data.
- Machine-readable output is `target/evaluation/lifegraph-timeline-v1-report.json`; the JUnit test is part of default `mvn test`, and CI archives `target/evaluation/*.json`.
- The report never contains fixture body text, evidence snippets, exception messages, model output, or query text; only stable counts, statuses, versions, and violation codes are emitted.

---

### Task 1: Protect DELETE processing from stale Diary revisions

**Files:**
- Modify: `src/main/java/com/aseubel/yusi/service/lifegraph/LifeGraphTaskBatchService.java:54-90,126-157`
- Test: `src/test/java/com/aseubel/yusi/service/lifegraph/LifeGraphTaskBatchServiceTest.java`

**Interfaces:**
- Consumes: `LifeGraphTask.sourceRevision`, the current `Diary.sourceRevision`, and existing `isSuperseded(Long, Long)`.
- Produces: pending-batch DELETE and single-task DELETE skip an older event, complete the task, and never call `deleteByDiary`; same/newer DELETE still calls the build service.

- [ ] **Step 1: Write the failing regression test**

Add a test that creates a DELETE task at revision `1`, returns a current Diary at revision `2`, runs `processPendingTasks()`, and verifies completion without deletion:

```java
@Test
void skipsOlderDeleteRevisionWithoutRemovingCurrentLifeGraph() {
    LifeGraphTask task = LifeGraphTask.createDeleteTask("diary-1", "user-a", "event-old-delete");
    task.setId(11L);
    task.setSourceRevision(1L);
    when(taskClaimService.claimPendingTasks(any(LocalDateTime.class), any(Integer.class)))
            .thenReturn(List.of(task));
    when(diaryRepository.findByDiaryIdAndUserId("diary-1", "user-a"))
            .thenReturn(Diary.builder().diaryId("diary-1").userId("user-a").sourceRevision(2L).build());

    service().processPendingTasks();

    verify(lifeGraphBuildService, never()).deleteByDiary(anyString(), anyString());
    verify(taskRepository).markAsCompleted(eq(11L), any(LocalDateTime.class));
}
```

- [ ] **Step 2: Run the focused test and verify the expected failure**

Run:

```powershell
.\mvnw.cmd -q "-Dtest=LifeGraphTaskBatchServiceTest#skipsOlderDeleteRevisionWithoutRemovingCurrentLifeGraph" test
```

Expected: `FAIL`, because the current DELETE branch calls `deleteByDiary` before checking the current Diary revision.

- [ ] **Step 3: Implement the smallest production guard**

In the pending-task DELETE branch, load the current Diary first. If it exists and `isSuperseded(task.getSourceRevision(), currentDiary.getSourceRevision())` is true, mark the task completed and close the run scope without deletion. Apply the same branch to `processSingleTask` when the loaded task has `TaskType.DELETE`. Keep existing behavior when the source row is absent or the task revision is current.

- [ ] **Step 4: Run the focused class and commit**

```powershell
.\mvnw.cmd -q "-Dtest=LifeGraphTaskBatchServiceTest" test
git add src/main/java/com/aseubel/yusi/service/lifegraph/LifeGraphTaskBatchService.java src/test/java/com/aseubel/yusi/service/lifegraph/LifeGraphTaskBatchServiceTest.java
git commit -m "fix: ignore stale life graph delete revisions"
```

Expected: all tests in the class pass before the commit.

### Task 2: Add the sanitized fixture contract and loader

**Files:**
- Create: `src/test/resources/evaluation/lifegraph-timeline-v1-fixtures.json`
- Create: `src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphTimelineEvaluationFixture.java`
- Create: `src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphTimelineFixtureLoader.java`
- Test: `src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphTimelineFixtureLoaderTest.java`

**Interfaces:**
- Consumes: classpath resource `/evaluation/lifegraph-timeline-v1-fixtures.json`.
- Produces: typed suite/case/scenario/event records containing case/scenario IDs, source metadata, revision, operation, extraction JSON, and structured expectations; invalid fixtures throw `FixtureValidationException` with stable code `FIXTURE_INVALID`.

- [ ] **Step 1: Write loader tests before the loader**

Cover a valid fixture and a temporary invalid JSON tree containing `rawText` and a non-fixture user ID. Assert only the stable error code; never print the invalid value.

- [ ] **Step 2: Run the loader test red**

```powershell
.\mvnw.cmd -q "-Dtest=LifeGraphTimelineFixtureLoaderTest" test
```

Expected: `FAIL` because the typed fixture/loader classes do not exist.

- [ ] **Step 3: Implement typed records and strict validation**

Use Jackson to parse the known schema. Recursively reject `plainContent`, `rawText`, `prompt`, `toolArguments`, `toolResult`, `secret`, `password`, and `content`; require `fixture-user-*`, `fixture-diary-*`, `fixture-plaza-*`, and `evidence-token-*` prefixes where applicable; cap fixture strings at 256 characters; require `EVAL-*` case/scenario IDs; and allow only `DIARY`/ `PLAZA` source types.

The fixture contains `EVAL-MEM-002-A` (revision 3 then stale revision 1), `EVAL-MEM-002-B` (same revision 5 repeated), `EVAL-MEM-002-C` (two sources then each source removed), and `EVAL-TIMELINE-001-A` (dated Event plus Person/Topic non-events). Extraction data contains only synthetic entity keys and evidence tokens.

- [ ] **Step 4: Run loader tests and commit**

```powershell
.\mvnw.cmd -q "-Dtest=LifeGraphTimelineFixtureLoaderTest" test
git add src/test/resources/evaluation/lifegraph-timeline-v1-fixtures.json src/test/java/com/aseubel/yusi/evaluation/lifegraph
git commit -m "test: add sanitized life graph replay fixtures"
```

Expected: valid fixture loads; forbidden fields, invalid prefixes, and overlong strings fail with `FIXTURE_INVALID`.

### Task 3: Add the versioned machine-readable report

**Files:**
- Create: `src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphTimelineEvaluationReport.java`
- Test: `src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphTimelineEvaluationReportTest.java`

**Interfaces:**
- Consumes: stable case/scenario outcomes and assertion counts.
- Produces: `LifeGraphTimelineEvaluationReport.write(Path, List<CaseResult>)`, which creates parent directories and serializes schema version `1`, suite ID, runner version, `generatedAt`, four version slots, summaries, and violation codes in insertion order.

- [ ] **Step 1: Write the failing schema serialization test**

Write a passing case result to a temporary path, parse it as `JsonNode`, and assert `/schemaVersion`, `/cases/0/versions/model`, `/cases/0/versions/prompt`, `/cases/0/versions/retrieval`, `/cases/0/versions/ranking`, and `/summary/status`. Assert `generatedAt` exists but is excluded by the report equality helper, and no evidence token is emitted.

- [ ] **Step 2: Run report tests red, implement, then run green**

```powershell
.\mvnw.cmd -q "-Dtest=LifeGraphTimelineEvaluationReportTest" test
```

Expected first: `FAIL` because the report model/writer is missing. Implement explicit `VersionSlot model/prompt/retrieval/ranking` record fields, fixed baseline values (`fixture/none/fixture-v1`), stable violation codes, and a Jackson pretty-print writer. Then rerun until PASS.

- [ ] **Step 3: Commit the report contract**

```powershell
git add src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphTimelineEvaluationReport.java src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphTimelineEvaluationReportTest.java
git commit -m "test: add life graph evaluation report schema"
```

### Task 4: Execute the real H2 LifeGraph/Timeline replay

**Files:**
- Create: `src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphTimelineEvaluationTest.java`

**Interfaces:**
- Consumes: the fixture suite, real Spring beans `LifeGraphBuildService`, `LifeGraphTaskBatchService`, `LifeTimelineService`, Diary/Plaza and LifeGraph repositories, plus mocked `LifeGraphExtractor` and `PromptManager` as the fixed local boundary.
- Produces: `target/evaluation/lifegraph-timeline-v1-report.json`, one result per executable scenario, and a JUnit failure whenever stable violation codes are present.

- [ ] **Step 1: Write the evaluation skeleton and run it red**

Create a `@SpringBootTest`, `@ActiveProfiles("test")`, `@Import(TestInfrastructureConfig.class)` class with one suite test, real repositories/services, and `@MockBean` extractor/PromptManager. Load the fixture and call a not-yet-implemented replay helper.

```powershell
.\mvnw.cmd -q "-Dtest=LifeGraphTimelineEvaluationTest" test
```

Expected: `FAIL` because the replay helper/report integration is missing.

- [ ] **Step 2: Implement deterministic H2 setup and replay**

Use the existing `src/test/resources/application-test.yml`; never start Web, gRPC, Redis, Milvus, or a model client. Configure a fixed `PromptSnapshot` and return fixture extraction JSON from the mocked extractor. Persist only synthetic Diary/Card command data and use distinct fixture users.

For A, persist current Diary revision 3, process revision 3 through real `processSingleTask`, then submit revision 1 against the same current Diary; assert the old extractor is not called and revision-3 evidence remains.

For B, process the same revision-5 extraction twice through the real replacement path; assert one entity evidence row, one relation evidence row, one Diary mention, and unchanged aggregate counts.

For C, apply one Event from two sources (Diary and Plaza), delete the Plaza source, assert only its evidence/contribution disappears while the Diary contribution remains, then delete the Diary source and assert all entity evidence, relation evidence, mention, auto aggregates, and Timeline nodes are gone.

For the Timeline scenario, assert only dated Event nodes appear; Person and Topic are excluded. Include a direct User-to-Person relation and Person-to-attribute relation, plus a Person-to-Person expansion candidate; assert the second-hop Person and low-value `MENTIONED`/`SAID` relations are absent.

- [ ] **Step 3: Write the report before failing JUnit**

Collect only counts, statuses, and stable codes such as `FIXTURE_INVALID`, `STALE_REVISION_APPLIED`, `DUPLICATE_CONTRIBUTION`, `SOURCE_RESIDUAL`, `PROMOTION_BOUNDARY`, `TIMELINE_RESIDUAL`, and `USER_SCOPE_LEAK`. Write the report in a `finally` block, then assert suite status `PASS`; never include exception messages or source text.

- [ ] **Step 4: Run focused and full tests, then commit**

```powershell
.\mvnw.cmd -q "-Dtest=LifeGraphTimelineEvaluationTest" test
.\mvnw.cmd -q test
git add src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphTimelineEvaluationTest.java
git commit -m "test: add h2 life graph timeline replay baseline"
```

Expected: both test commands exit 0 and the report contains `summary.status == "PASS"`.

### Task 5: Wire CI artifact collection and roadmap status

**Files:**
- Modify: `.github/workflows/deploy_k8s.yml:24-40`
- Modify: `docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md:502-530`

**Interfaces:**
- Consumes: the default backend test command and `target/evaluation/*.json`.
- Produces: a CI artifact named `lifegraph-timeline-evaluation` even when tests fail, plus roadmap checkmarks only for the implemented LifeGraph/Timeline baseline.

- [ ] **Step 1: Add artifact collection after backend tests**

```yaml
    - name: Archive LifeGraph evaluation report
      if: always()
      uses: actions/upload-artifact@v4
      with:
        name: lifegraph-timeline-evaluation
        path: target/evaluation/*.json
        if-no-files-found: warn
```

- [ ] **Step 2: Update only the implemented roadmap items**

Mark fixed LifeGraph/Timeline replay, report versioning, and the default regression gate complete. Leave conversation, matching, proactive, and model-comparison evaluation items unchecked.

- [ ] **Step 3: Verify and commit**

```powershell
git diff --check
git status --short
git add .github/workflows/deploy_k8s.yml docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md
git commit -m "ci: archive life graph evaluation baseline"
```

Inspect the workflow diff to ensure artifact collection is in `verify`, not deployment, and no service startup or external endpoint was added.

## Final Verification

Run a fresh full verification before reporting completion:

```powershell
.\mvnw.cmd -q test
git diff --check
git status --short
git log -5 --oneline
```

Confirm the JSON report has schema version `1`, all four version slots, the existing `EVAL-*` IDs, the three source lifecycle boundaries, `summary.status` `PASS`, and no raw fixture text or forbidden field names. Do not start any application service.
