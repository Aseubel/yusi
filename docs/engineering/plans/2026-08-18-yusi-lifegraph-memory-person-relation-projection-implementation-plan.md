# LifeGraph 记忆中心人物关系投影实施计划

> **For inline execution:** 本计划只在设计文档经过用户评审确认后执行。项目纪律禁止启用子 agent 和 auto-review，所有实现留在当前工作区按任务逐项完成。

**Goal:** 从当前 `LifeGraphRelation` 事实表为记忆中心 Person 实体提供低敏的 `relationToUser`、`relationOrigin` 投影，并在同一面补齐只读 `importance` 展示，用真实 H2 回放证明投影不漂移。

**Architecture:** `LifeGraphLifecycleService` 在读取当前用户的 LifeGraph 实体时，以当前用户 User 实体的语义端点和 `LifeGraphRelationRepository.findByUserId` 的关系事实构造 Person 投影；不向 `LifeGraphEntity` 写入缓存字段。现有 `/api/memory/life-graph` DTO 继续承载只读字段，前端只在现有 Person 卡片显示安全关系元数据和 importance。

**Tech Stack:** Java 21、Spring Boot 3.4、Spring Data JPA、H2、JUnit 5、Mockito、Jackson、现有 `OfflineEvaluationReportWriter`、React 19、TypeScript、i18next、Vitest、pnpm 11.9。

## Global Constraints

- 只修改记忆中心 `LifeGraphMemoryItem` / `LifeGraphLifecycleService` / `LifeGraphMemoryPanel` 的透明度消费链；社区图谱、匹配画像、LifeGraphTool 和其他排序入口不改。
- 重要人物事实只来自当前用户的 `LifeGraphRelation`：语义端点必须是当前 User 与当前 Person，关系 type 仅接受 `LifeGraphRelationType.isPersonRelation()`。
- `relationToUser` 和 `relationOrigin` 都是只读字段；没有直接 User-Person 关系时两个字段同时为 `null`。
- 多条关系选择顺序固定为 `MANUAL` 优先、`updatedAt DESC`、`type ASC`、`id ASC`；不得依赖数据库默认返回顺序。
- 不增加 Person 实体缓存字段、数据库 migration、关系编辑 API、关系证据或全部关系数组。
- 后端当前 Java DTO 已有 `importance` 和映射；只验证并保持其兼容默认值，补齐前端 API 类型与展示，不重复增加字段。
- 不修改 `LifeGraphPromotionPolicy`、importance 排序、`MatchProfileAssemblerImpl`、中期记忆衰减、社区图谱、名称搜索、Top50、`LifeGraphBuildServiceImpl.buildKnownEntities`、社区洞察、合并候选、情绪触发或 Prompt 已知实体排序。
- 任何 Trace、报告、fixture、日志不得持久化或打印用户 query、记忆正文、Prompt、工具参数/结果、证据 snippet、关系正文、密钥、异常正文或堆栈。
- 回放使用脱敏的 `lifegraph-promotion-v1-fixtures.json` 的原生 `EVAL-MEM-003-B` 身份 `fixture-user-promotion-b` / `fixture-diary-promotion-b`；不重写 scenario 的 user/source id，只额外 seed 投影所需实体和关系行，并用精确实体 id 定位断言。
- 报告使用 `OfflineEvaluationReportWriter`、schema version 1、`target/evaluation/lifegraph-memory-relation-v1-report.json`；正向断言 `versions.prompt` 恰好为 `{key: "fixture", version: "fixture-v1", locale: "zh-CN"}`，低敏扫描先移除该字段再执行。
- 每个实现任务按 TDD 顺序推进；切片结束运行 `.\mvnw.cmd -q test`、`frontend` 测试和构建；不启动服务，提交后停下来等待验收。
- 不启动 `docs/engineering/plans/2026-08-17-yusi-post-release-expansion-backlog.md` 中的任何条目。

## File Map

修改：

- `src/main/java/com/aseubel/yusi/pojo/dto/memory/LifeGraphMemoryItem.java`：增加关系投影输出字段；保留已有 `importance`。
- `src/main/java/com/aseubel/yusi/service/memory/LifeGraphLifecycleService.java`：读取当前用户关系事实、解析语义方向、按稳定规则构造投影，并在 `list` 与 `update` 返回路径保持一致。
- `src/test/java/com/aseubel/yusi/service/memory/LifeGraphLifecycleServiceTest.java`：覆盖关系事实投影、隔离、确定性和敏感字段边界。
- `frontend/src/lib/api.ts`：补齐 `LifeGraphMemoryItem.importance`、`relationToUser`、`relationOrigin` 类型。
- `frontend/src/components/memory/LifeGraphMemoryPanel.tsx`：在现有卡片展示 importance 和 Person 关系元数据。
- `frontend/src/i18n/locales/zh.json`、`frontend/src/i18n/locales/en.json`：新增关系类型、确认来源和 importance 展示文案。
- `frontend/src/lib/memoryCenter.test.ts`：增加前端安全展示辅助逻辑的纯函数断言；不引入新的 React 测试依赖。

创建：

- `src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphMemoryRelationProjectionEvaluationTest.java`：真实 H2 回放、删除后重读、跨用户隔离和报告生成。

复用但不修改：

- `src/test/resources/evaluation/lifegraph-promotion-v1-fixtures.json`：现有脱敏固定 extraction 输入。
- `src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphPromotionFixtureLoader.java`：现有 fixture loader。
- `src/test/java/com/aseubel/yusi/evaluation/OfflineEvaluationReportWriter.java`：既有低敏报告 envelope。

不修改：

- `LifeGraphRelation` 实体、`LifeGraphEntity` 实体、repository schema/migration、promotion 与抽取代码。
- `LifeGraphDataService`、`GraphSnapshotDTO`、`LifeGraphQueryService`、`LifeGraphTool`、`MatchProfileAssemblerImpl`、`MidTermMemory` 和前一切片排序测试。

---

### Task 1: 固化 DTO 合同与单元测试失败路径

**Files:**

- Modify: `src/main/java/com/aseubel/yusi/pojo/dto/memory/LifeGraphMemoryItem.java`
- Test: `src/test/java/com/aseubel/yusi/service/memory/LifeGraphLifecycleServiceTest.java`

**Interfaces:**

- Consumes: `LifeGraphMemoryItem.builder()` and `LifeGraphLifecycleService.list(String, int)`.
- Produces: nullable `relationToUser` / `relationOrigin` output fields and the service test contract that `importance` remains read-only and visible.

- [ ] **Step 1: Add failing service assertions for the output contract.**

Extend the existing Mockito fixture with a current-user entity, a Person entity, and a relation whose semantic endpoints point from User to Person. Stub `relationRepository.findByUserId("user-1")`, `entityRepository.findByUserIdAndTypeAndNameNorm("user-1", LifeGraphEntity.EntityType.User, LifeGraphConstants.USER_ENTITY_NORM)`, and the existing source repositories. Assert:

```java
LifeGraphMemoryItem item = service().list("user-1", 50).getEntities().get(0);

assertEquals("PARTNER_OF", item.getRelationToUser());
assertEquals("MANUAL", item.getRelationOrigin());
assertEquals(0.8, item.getImportance());
```

Before production mapping exists, this test must fail to compile or fail the assertions because the relation fields are absent.

- [ ] **Step 2: Add the no-relation and non-Person assertions.**

Use one Person with no matching relation and one Topic with a relation to another non-User endpoint. Assert both fields are `null` for both items; assert the Topic is never treated as a Person projection target. Also assert `UpdateLifeGraphMemoryRequest` still has no `importance`, `relationToUser`, or `relationOrigin` declared field.

- [ ] **Step 3: Run the focused test to verify the failure.**

Run:

```powershell
.\mvnw.cmd -q -Dtest=LifeGraphLifecycleServiceTest test
```

Expected: FAIL because the new DTO fields and projection have not been implemented. Do not weaken the assertions to make the pre-implementation test pass.

- [ ] **Step 4: Add the DTO fields with the API-safe types.**

Add the following fields to `LifeGraphMemoryItem`, leaving the existing `importance` field in place:

```java
private String relationToUser;
private String relationOrigin;
```

Keep Lombok builder/getter behavior and do not add these names to `UpdateLifeGraphMemoryRequest`.

- [ ] **Step 5: Run the focused test again.**

Run the same Maven command. Expected: the test still fails only on missing service projection values, demonstrating that the DTO contract is now present and the remaining work is mapping.

- [ ] **Step 6: Commit the contract test and DTO.**

```powershell
git add src/main/java/com/aseubel/yusi/pojo/dto/memory/LifeGraphMemoryItem.java src/test/java/com/aseubel/yusi/service/memory/LifeGraphLifecycleServiceTest.java
git commit -m "test: define lifegraph memory relation projection contract"
```

---

### Task 2: Implement relation-fact projection in the lifecycle service

**Files:**

- Modify: `src/main/java/com/aseubel/yusi/service/memory/LifeGraphLifecycleService.java`
- Test: `src/test/java/com/aseubel/yusi/service/memory/LifeGraphLifecycleServiceTest.java`

**Interfaces:**

- Consumes: `LifeGraphEntityRepository.findByUserIdAndTypeAndNameNorm`, `LifeGraphRelationRepository.findByUserId`, `LifeGraphRelation.semanticSourceId/semanticTargetId`, `LifeGraphRelation.Origin`, and `LifeGraphRelationType.isPersonRelation()`.
- Produces: `LifeGraphMemoryItem` with relation metadata derived from the current relation row on every service read.

- [ ] **Step 1: Add failing semantic-direction, cross-user, and multi-relation tests.**

Add test cases that:

```java
// Reverse semantic direction is still a direct User-Person fact.
relation.setSemanticSourceId(person.getId());
relation.setSemanticTargetId(userEntity.getId());
relation.setType("FAMILY_OF");
relation.setOrigin(LifeGraphRelation.Origin.AUTO);

// Two direct facts for the same Person.
manual.setOrigin(LifeGraphRelation.Origin.MANUAL);
automatic.setOrigin(LifeGraphRelation.Origin.AUTO);
```

Assert the reverse fact returns `FAMILY_OF` / `AUTO`, while the pair with both facts returns the MANUAL row. Add a same-origin tie with equal timestamps and assert type ascending, then equal type/timestamp and assert id ascending. Add a relation whose `userId` is `user-1` but whose semantic User endpoint is a different user's User entity; assert the Person item has null projection fields. These tests must fail before the helper exists.

- [ ] **Step 2: Run the focused tests and record the expected failure.**

Run:

```powershell
.\mvnw.cmd -q -Dtest=LifeGraphLifecycleServiceTest test
```

Expected: FAIL on relation metadata and selection assertions; no production relation fields are changed by the test setup.

- [ ] **Step 3: Add a private projection value and candidate comparator.**

Implement a private immutable helper in `LifeGraphLifecycleService` (a nested record is sufficient) carrying only `relationToUser` and `relationOrigin`. Add helpers equivalent to:

```java
private Long semanticSourceId(LifeGraphRelation relation) {
    return relation.getSemanticSourceId() == null ? relation.getSourceId() : relation.getSemanticSourceId();
}

private Long semanticTargetId(LifeGraphRelation relation) {
    return relation.getSemanticTargetId() == null ? relation.getTargetId() : relation.getSemanticTargetId();
}

private boolean isDirectUserPersonRelation(String userId, Long userEntityId,
        LifeGraphEntity entity, LifeGraphRelation relation) {
    if (!Objects.equals(userId, relation.getUserId())
            || entity.getType() != LifeGraphEntity.EntityType.Person) {
        return false;
    }
    LifeGraphRelationType relationType = LifeGraphRelationType.fromCode(relation.getType());
    if (relationType == null || !relationType.isPersonRelation()) {
        return false;
    }
    Long sourceId = semanticSourceId(relation);
    Long targetId = semanticTargetId(relation);
    return (Objects.equals(sourceId, userEntityId) && Objects.equals(targetId, entity.getId()))
            || (Objects.equals(targetId, userEntityId) && Objects.equals(sourceId, entity.getId()));
}

private LifeGraphRelation chooseRepresentativeRelation(List<LifeGraphRelation> candidates) {
    return candidates.stream()
            .sorted(Comparator.comparingInt(this::originPriority).reversed()
                    .thenComparing(LifeGraphRelation::getUpdatedAt,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(relation -> normalizeRelationType(relation.getType()))
                    .thenComparing(LifeGraphRelation::getId,
                            Comparator.nullsLast(Comparator.naturalOrder())))
            .findFirst()
            .orElse(null);
}
```

Add the required `Objects`, `Comparator`, and `LifeGraphRelationType` imports with the helper. The actual endpoint helpers must use `Long`, fall back from semantic to physical IDs, verify the current User entity id, verify the item type is `Person`, and accept only `LifeGraphRelationType.fromCode(type).isPersonRelation()`. `originPriority` must return `1` for `MANUAL` and `0` for `AUTO`; `normalizeRelationType` must trim and uppercase a non-null type. A missing or unrecognized type yields no candidate, and a null origin yields no candidate rather than an invented source.

- [ ] **Step 4: Map the projection from `toItem`.**

Read current-user relations through the already available `relationRepository.findByUserId(userId)`. Resolve the current User entity with the existing `LifeGraphEntityRepository.findByUserIdAndTypeAndNameNorm` contract, build a per-list candidate map keyed only by current-user Person id, and pass the selected projection into `toItem`. For the `update` path, use the same projection helper against the saved entity so PATCH responses cannot return a stale or divergent shape. Do not add a repository cache or entity fields.

- [ ] **Step 5: Add the low-sensitivity assertions.**

Assert with reflection or JSON serialization that `LifeGraphMemoryItem` exposes `relationToUser`, `relationOrigin`, and `importance`, but not `evidenceSnippet`, `snippet`, `props`, `evidenceDiaryId`, or relation正文字段. Assert that relation origin is only `AUTO` or `MANUAL` and that missing type/origin produces a null pair rather than an arbitrary string.

- [ ] **Step 6: Run the focused backend test to verify it passes.**

```powershell
.\mvnw.cmd -q -Dtest=LifeGraphLifecycleServiceTest test
```

Expected: PASS, including existing diary source, lifecycle, deletion, and cross-user mutation tests.

- [ ] **Step 7: Commit the backend projection.**

```powershell
git add src/main/java/com/aseubel/yusi/service/memory/LifeGraphLifecycleService.java src/test/java/com/aseubel/yusi/service/memory/LifeGraphLifecycleServiceTest.java
git commit -m "feat: project person relation facts in memory center"
```

---

### Task 3: Add real H2 replay and the low-sensitivity report

**Files:**

- Create: `src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphMemoryRelationProjectionEvaluationTest.java`
- Reuse: `src/test/resources/evaluation/lifegraph-promotion-v1-fixtures.json`
- Reuse: `src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphPromotionFixtureLoader.java`
- Reuse: `src/test/java/com/aseubel/yusi/evaluation/OfflineEvaluationReportWriter.java`
- Generated: `target/evaluation/lifegraph-memory-relation-v1-report.json`

**Interfaces:**

- Consumes: the existing fixture loader, Spring Boot `test` profile, real JPA repositories, `LifeGraphLifecycleService`, and the H2 database.
- Produces: PASS/FAIL `CaseResult` values with counts and fixed violation codes only; a report under `target/evaluation`.

- [ ] **Step 1: Add the evaluation test skeleton and report contract.**

Use `@SpringBootTest`, `@ActiveProfiles("test")`, and `@Import(TestInfrastructureConfig.class)` in the same style as the existing LifeGraph evaluation suites. Set:

```java
private static final String USER_ID = "fixture-user-promotion-b";
private static final String SOURCE_ID = "fixture-diary-promotion-b";
private static final Path REPORT_PATH = Path.of(
        "target", "evaluation", "lifegraph-memory-relation-v1-report.json");
```

Call `LifeGraphPromotionFixtureLoader.load()` on the complete fixture suite so its required three-case and source-id validation runs, then select exactly the case `EVAL-MEM-003` and scenario `EVAL-MEM-003-B`. Preserve the scenario's native `userId` and `sourceId`; do not construct an isolated/remapped scenario. Do not print or place the fixture extraction, evidence token, entity summary, relation endpoints, or exception message in the report.

- [ ] **Step 2: Seed isolated H2 entities and the direct relation.**

Within the test database, use the native `USER_ID` / `SOURCE_ID` from the selected scenario. Seed only the User entity, the Person projection target, a separate deletion-only Person, and the minimal H2 rows required by the memory-center service; do not replace the scenario identity. Save a MANUAL relation using the current user entity id and Person id as semantic endpoints, physical endpoints in min/max order, a safe relation code such as `FAMILY_OF`, and fixed timestamps. Add an AUTO relation in the reverse semantic direction for the multi-relation assertion, and save exactly one MANUAL relation for the deletion-only Person. If the prior promotion replay has already inserted rows for the native B identity in the shared test context, remove only those exact user/source/endpoint rows through the existing repositories before seeding; do not delete other users' data.

- [ ] **Step 3: Replay through the real service and compare against the relation row.**

Call `lifeGraphLifecycleService.list(USER_ID, 50)` after repository flush. Locate the Person item by its persisted entity id, never by display name or nameNorm. From `relationRepository.findByUserId(USER_ID)`, filter the exact User-Person candidates for that same entity id using the same semantic endpoint fallback and 8-code person-relation whitelist as production. Select the expected comparison row with the same representative ordering as production: `MANUAL` before `AUTO`, `updatedAt DESC` with null last, normalized type ASC, and id ASC with null last. Assert:

```java
assertEquals(selectedRelation.getType(), personItem.getRelationToUser());
assertEquals(selectedRelation.getOrigin().name(), personItem.getRelationOrigin());
assertEquals(personEntity.getImportance(), personItem.getImportance());
```

Also assert a Topic or Event item has null relation fields and that the same-name Person owned by the other user is absent from the current user response. The report records only fixed assertion counts and codes, not the ids or names used for the in-memory comparison.

- [ ] **Step 4: Add the delete-and-reread regression.**

Use a separate deletion-only Person with exactly one valid User-Person relation, delete that relation through the real H2 repository, call `flush()`/`clear()` on the test `EntityManager`, then call `lifeGraphLifecycleService.list(USER_ID, 50)` again. Assert both projection fields are null. This keeps the deletion assertion independent from the multi-relation representative-selection case and proves that no Person field, cache, or stale DTO may preserve the deleted relation.

- [ ] **Step 5: Add cross-user and deterministic selection checks.**

Persist a relation row with the current tenant id but semantic endpoints belonging to another user's User/Person pair and assert it is ignored. For two valid rows on the same Person, assert MANUAL wins over AUTO; for equal origin/timestamps assert normalized type ASC; for equal type/timestamp assert smaller id ASC. Report only fixed pass counts and codes such as `RELATION_FACT_MATCH`, `RELATION_DELETE_SYNC`, `CROSS_USER_RELATION_FILTER`, `RELATION_SELECTION_STABLE`, and `MEMORY_LOW_SENSITIVITY`.

- [ ] **Step 6: Write and validate the report envelope.**

Create `CaseResult` with `OfflineEvaluationReportWriter.Versions.fixtureBaseline()` and `actualSummary` containing only integer counts and boolean pass indicators. Write the report in a `finally` block. After writing, assert the JSON node at `/cases/0/versions/prompt` equals:

```json
{"key":"fixture","version":"fixture-v1","locale":"zh-CN"}
```

Deep-copy the report, remove each `versions.prompt` field, and scan the remaining serialized values for `evidence-token-`, `rawtext`, `plaincontent`, `toolarguments`, `toolresult`, `secret`, and `password`. Do not scan the raw report for the field name `prompt`.

- [ ] **Step 7: Run the focused evaluation.**

```powershell
.\mvnw.cmd -q -Dtest=LifeGraphMemoryRelationProjectionEvaluationTest test
```

Expected: PASS and `target/evaluation/lifegraph-memory-relation-v1-report.json` exists with all cases PASS and no sensitive values.

- [ ] **Step 8: Commit the H2 replay.**

```powershell
git add src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphMemoryRelationProjectionEvaluationTest.java
git commit -m "test: replay memory relation projection on h2"
```

---

### Task 4: Wire the existing memory-center UI contract

**Files:**

- Modify: `frontend/src/lib/api.ts`
- Modify: `frontend/src/components/memory/LifeGraphMemoryPanel.tsx`
- Modify: `frontend/src/i18n/locales/zh.json`
- Modify: `frontend/src/i18n/locales/en.json`
- Modify: `frontend/src/lib/memoryCenter.ts`
- Test: `frontend/src/lib/memoryCenter.test.ts`

**Interfaces:**

- Consumes: backend `LifeGraphMemoryItem` JSON fields `importance`, `relationToUser`, `relationOrigin`.
- Produces: existing memory center cards that expose only safe, read-only importance and Person relation metadata.

- [ ] **Step 1: Add the failing TypeScript contract assertions.**

Import `type LifeGraphMemoryItem` from `./api` in `frontend/src/lib/memoryCenter.test.ts` and add a fixture with all required fields. Use it in a test assertion so `noUnusedLocals` remains satisfied:

```ts
const personMemory: LifeGraphMemoryItem = {
  id: 1,
  type: 'Person',
  displayName: 'fixture-person',
  summary: null,
  mentionCount: 1,
  relationCount: 1,
  confidence: 0.9,
  importance: 0.8,
  relationToUser: 'FAMILY_OF',
  relationOrigin: 'MANUAL',
  createdAt: null,
  updatedAt: null,
  validUntil: null,
  matchAllowed: true,
  hidden: false,
  lifecycleStatus: 'ACTIVE',
  sources: [],
}

it('accepts the read-only lifegraph relation contract', () => {
  expect(personMemory.relationOrigin).toBe('MANUAL')
  expect(personMemory.importance).toBe(0.8)
})
```

Run `pnpm exec tsc -b` from `frontend`; it must fail before `api.ts` is updated because the current interface lacks these properties. Do not add a runtime API call or a new dependency.

- [ ] **Step 2: Add the typed read-only fields.**

In `frontend/src/lib/api.ts`, add:

```ts
importance: number;
relationToUser?: string | null;
relationOrigin?: 'AUTO' | 'MANUAL' | null;
```

Keep `UpdateLifeGraphMemoryRequest` unchanged so TypeScript prevents writes to these fields.

- [ ] **Step 3: Render importance in the existing metadata grid.**

Use the existing `formatPercent(entity.importance)` and `memoryCenter.importance` translation in the read-only metadata area. Do not add an editable control or a new section/page. Keep the layout stable for both Person and non-Person cards.

- [ ] **Step 4: Render relation metadata only for Person items with a complete pair.**

In `LifeGraphMemoryPanel`, render a compact read-only block only when `isDisplayablePersonRelation(...)` returns true. Translate the relation code through the existing `memoryCenter.relationshipGraph` namespace with fixed keys for all person relation codes (`PARTNER_OF`, `FAMILY_OF`, `FRIEND_OF`, `COLLEAGUE_OF`, `MENTOR_OF`, `SIBLING_OF`, `PARENT_OF`, `CHILD_OF`). Translate origin as automatic extraction or manual confirmation. If the pair is incomplete or the code is not in that eight-code whitelist, render nothing; never render raw evidence, relation props, relation id, or endpoint names.

- [ ] **Step 5: Add Chinese and English translations.**

Add only safe UI labels under `memoryCenter.relationshipGraph`, for example:

```json
"importance": "重要程度",
"relationToUser": "与我的关系",
"relationOrigin": "确认来源",
"relationOrigins": { "AUTO": "自动抽取", "MANUAL": "人工确认" },
"relationTypes": { "FAMILY_OF": "家人", "FRIEND_OF": "朋友" }
```

Add equivalent English labels and all eight supported person relation codes. Keep existing top-level `memoryCenter.importance` compatible; do not display relation evidence or raw relation text.

- [ ] **Step 6: Add a pure frontend display helper test.**

Export `isDisplayablePersonRelation` from the existing `frontend/src/lib/memoryCenter.ts` utility module and use it in the panel. It must accept a presentation-only object with `type`, `relationToUser`, and `relationOrigin`, and test these cases in `frontend/src/lib/memoryCenter.test.ts`:

```ts
expect(isDisplayablePersonRelation({ type: 'Person', relationToUser: 'FAMILY_OF', relationOrigin: 'MANUAL' })).toBe(true)
expect(isDisplayablePersonRelation({ type: 'Topic', relationToUser: 'FAMILY_OF', relationOrigin: 'MANUAL' })).toBe(false)
expect(isDisplayablePersonRelation({ type: 'Person', relationToUser: null, relationOrigin: null })).toBe(false)
```

The predicate must be exactly presentation-only: `type === 'Person'`, `relationToUser` is one of the eight known person relation codes, and origin is in `['AUTO', 'MANUAL']`; it must not duplicate backend ownership or fact selection logic. Use `personMemory` from Step 1 in one positive assertion so the new API fields are exercised by Vitest as well as TypeScript. Add a negative assertion for `relationToUser: 'FUTURE_RELATION'` returning false.

- [ ] **Step 7: Run frontend tests and type/build checks.**

From `frontend` run:

```powershell
pnpm test
pnpm run build
```

Expected: all existing Vitest tests pass, the new predicate cases pass, and TypeScript/Vite build succeeds.

- [ ] **Step 8: Commit the frontend contract.**

```powershell
git add frontend/src/lib/api.ts frontend/src/components/memory/LifeGraphMemoryPanel.tsx frontend/src/i18n/locales/zh.json frontend/src/i18n/locales/en.json frontend/src/lib/memoryCenter.ts frontend/src/lib/memoryCenter.test.ts
git commit -m "feat: show lifegraph relation metadata in memory center"
```

---

### Task 5: Run scope, privacy, and full verification checks

**Files:**

- Verify: all files changed by Tasks 1-4
- Verify: `target/evaluation/lifegraph-memory-relation-v1-report.json`

**Interfaces:**

- Consumes: completed backend DTO/service tests, H2 report, frontend tests/build, and git diff.
- Produces: a clean, reviewed commit set with full test evidence; no production service startup.

- [ ] **Step 1: Run the low-sensitivity scan on the generated report.**

Remove the `versions.prompt` field before scanning, then run a PowerShell JSON-aware check or equivalent text check over the remaining report values. The scan must find none of:

```text
evidence-token-
rawtext
plaincontent
toolarguments
toolresult
secret
password
```

The report must contain only fixed case ids, assertion counts, fixed relation codes if used as contract labels, and violation codes. No query, memory content, relation evidence, endpoint name, or exception message may appear.

- [ ] **Step 2: Verify the changed-file boundary.**

Run:

```powershell
git diff --name-only HEAD~4..HEAD
rg -n "findTop50ByUserIdOrderByMentionCountDesc|findVisibleByUserIdAndDisplayNameContainingOrderByMentionCountDesc|CommunityInsightServiceImpl|LifeGraphMergeSuggestionService|EmotionTimelineServiceImpl|class LifeGraphTool|calculateDecayedImportance" src/main/java frontend/src
```

Expected: only the documented memory-center files and evaluation files are changed; the listed non-target entry points show no diff. If commit count differs because the implementation is squashed, inspect `git diff --name-only <base>..HEAD` against the pre-task revision instead of altering unrelated files.

- [ ] **Step 3: Run the backend full suite and confirm report generation.**

```powershell
.\mvnw.cmd -q test
```

Expected: exit code `0`; existing `memory-lifecycle-v1`, `lifegraph-timeline-v1`, `lifegraph-promotion-v1`, importance, and the new relation projection report are generated under `target/evaluation` as applicable.

- [ ] **Step 4: Run frontend verification once more after backend completion.**

```powershell
Set-Location frontend
pnpm test
pnpm run build
Set-Location ..
```

Expected: exit code `0` for both commands.

- [ ] **Step 5: Record the MySQL follow-up without running a service.**

Add the exact query compatibility check to the pre-release regression list: target MySQL must verify that the relation projection query strategy, nullable endpoint fallback, and `AUTO/MANUAL` string mapping behave like H2. This task does not start MySQL or any application service.

- [ ] **Step 6: Inspect the final diff and commit the slice.**

```powershell
git diff --check
git status --short
git log -5 --oneline
```

Review that no fixture, report, log, or code path contains user content, prompt content, tool arguments/results, secrets, evidence snippets, or relation正文. Commit any remaining implementation changes with:

```powershell
git add src frontend docs/engineering/specs/2026-08-18-yusi-lifegraph-memory-person-relation-projection-design.md docs/engineering/plans/2026-08-18-yusi-lifegraph-memory-person-relation-projection-implementation-plan.md
git commit -m "feat: complete lifegraph memory relation transparency slice"
```

After the commit and successful verification, stop and wait for user acceptance. Do not begin Person entity field changes or any post-release backlog item in the same slice.

## Self-Review Checklist

- [x] Design covers all four consumer surfaces and explicitly limits changes to the memory center.
- [x] Design treats importance as an existing backend field with a frontend exposure gap and keeps it read-only.
- [x] Semantic relation direction, current-user ownership, `AUTO/MANUAL` origin, multi-relation ordering, and cross-user isolation are explicit.
- [x] Evidence snippets, relation正文, prompt field false-positive handling, and report directory/version requirements are explicit.
- [x] H2 replay includes fact equality, relation deletion followed by reread, and a cross-user negative case.
- [x] Plan has concrete files, signatures, TDD steps, commands, expected results, frontend verification, full Maven verification, and commit boundaries.
- [x] Non-target ranking and consumer entry points are named so implementation can be checked with `rg` and diff inspection.
