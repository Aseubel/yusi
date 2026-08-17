
# LifeGraph importance 决策消费实施计划

> **For inline execution:** 本计划只在设计文档经过用户评审确认后执行。项目指令禁止启用子 agent 和 auto-review，因此实现留在当前工作区逐任务完成。

**Goal:** 将 LifeGraphEntity.importance 接入 matchable LifeGraph 候选窗口和匹配画像摘要排序，并用已有 promotion H2 回放基线验证真实决策消费。

**Architecture:** 保留现有 matchAllowed、隐藏、过期和类型优先级边界。Repository 候选窗口使用 importance DESC -> mentionCount DESC，Assembler 在现有类型优先级内使用相同的字典序并增加稳定兜底键；测试复用 lifegraph-promotion-v1 的场景 B，经过真实 H2 promotion 后再执行匹配画像。

**Tech Stack:** Java 21、Spring Boot 3.4、Spring Data JPA、H2 test profile、JUnit 5、Mockito、Jackson、现有 LifeGraphEntityRepository、MatchProfileAssemblerImpl、LifeGraphBuildServiceImpl、OfflineEvaluationReportWriter。

## Global Constraints

- 只修改 findMatchableTopByUserId 和 MatchProfileAssemblerImpl 的匹配画像决策链；全图、搜索、Top50、社区洞察、合并候选、情绪触发和 Prompt 已知实体排序不改。
- 不修改 LifeGraphEntity.importance 字段、人物字段、promotion 规则、数据库 schema、migration 或人物关系推导。
- 不修改 MidTermMemory 的 repository、buildMidMemorySummary 或 calculateDecayedImportance；必须添加正向衰减对照断言。
- 使用已有 lifegraph-promotion-v1-fixtures.json 的脱敏 EVAL-MEM-003-B，不调用 LLM、Embedding、Milvus、Redis、OSS 或真实用户数据。
- 测试报告写入 target/evaluation/lifegraph-importance-v1-report.json，沿用 OfflineEvaluationReportWriter，由默认 Maven 测试生成并由现有 CI artifact 归档。
- 报告必须正向断言 versions.prompt 恰好为 {key: "fixture", version: "fixture-v1", locale: "zh-CN"}；敏感词自检不得把 prompt 字段名作为禁词。
- COALESCE 排序 JPQL 除 H2 全量测试外，必须加入上线前目标 MySQL 版本回归清单；本切片不启动 MySQL 服务。
- Trace、报告、fixture、日志不得持久化或打印用户 query、记忆正文、Prompt、工具参数/结果、密钥、异常正文或实体摘要。
- 每个任务按测试先行；最终运行 .\mvnw.cmd -q test，不启动服务；实现完成后提交并停下来等验收。
- 不启动 2026-08-17-yusi-post-release-expansion-backlog.md 中的任何条目。

---

## 文件地图

修改：

- src/main/java/com/aseubel/yusi/repository/LifeGraphEntityRepository.java — 仅修改 matchable 候选查询的 JPQL 排序。
- src/main/java/com/aseubel/yusi/service/match/impl/MatchProfileAssemblerImpl.java — 仅修改 LifeGraph 摘要比较器，保持中期记忆衰减链不变。
- src/test/java/com/aseubel/yusi/service/match/MatchProfileAssemblerVisibilityTest.java — 增加 importance/mentionCount 边界、稳定兜底和 MidTermMemory 正向对照。

创建：

- src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphImportanceConsumptionEvaluationTest.java — 复用 promotion fixture 场景 B，真实 H2 回放 matchable 查询和 MatchProfile，输出低敏报告。

不修改：

- src/test/resources/evaluation/lifegraph-promotion-v1-fixtures.json 及其 loader；它是本切片的既有输入基线。
- 全图、名称搜索、Top50、社区洞察、合并候选、情绪触发、Prompt 已知实体路径。
- MidTermMemory 生产代码和任何数据库迁移。

---

### Task 1: 写出排序和中期记忆的失败测试

Files:

- Modify: src/test/java/com/aseubel/yusi/service/match/MatchProfileAssemblerVisibilityTest.java
- Test target: src/main/java/com/aseubel/yusi/service/match/impl/MatchProfileAssemblerImpl.java

Interfaces:

- Consumes: MatchProfileAssemblerImpl.refreshProfile(String) and the mocked LifeGraphEntityRepository.findMatchableTopByUserId(...).
- Produces: deterministic assertions for the persisted MatchProfile.lifeGraphSummary and midMemorySummary.

- [ ] Step 1: Add the failing importance-over-mention assertion.

Extend the existing Mockito test setup with two same-type entities returned in the opposite order:

~~~~java
LifeGraphEntity mentionHeavy = entity(1L, "fixture-mention-heavy");
mentionHeavy.setImportance(0.4);
mentionHeavy.setMentionCount(9);

LifeGraphEntity importanceHeavy = entity(2L, "fixture-importance-heavy");
importanceHeavy.setImportance(0.8);
importanceHeavy.setMentionCount(1);

when(lifeGraphEntityRepository.findMatchableTopByUserId(anyString(), any(), any()))
        .thenReturn(List.of(mentionHeavy, importanceHeavy));
~~~~

Call service().refreshProfile("user-1") with the existing user/persona/memory and embedding stubs,
then assert both names occur in profile.getLifeGraphSummary() and the importance-heavy name has the
smaller index. The test must fail before the production comparator changes because the current code
uses mentionCount after type priority.

- [ ] Step 2: Add the failing mentionCount tie-break assertion.

Return two same-type entities with equal importance=0.6 and different mention counts:

~~~~java
LifeGraphEntity lowMention = entity(3L, "fixture-mention-low");
lowMention.setImportance(0.6);
lowMention.setMentionCount(1);
LifeGraphEntity highMention = entity(4L, "fixture-mention-high");
highMention.setImportance(0.6);
highMention.setMentionCount(4);
~~~~

Assert fixture-mention-high precedes fixture-mention-low in the LifeGraph summary. Keep both
entities in the same EntityType so type priority cannot satisfy the assertion accidentally.

- [ ] Step 3: Add the stable equal-score assertion.

Create two same-type entities with equal importance and mentionCount, set distinct updatedAt, and
assert the newer entity is first. Add a second pair with equal timestamps and IDs 11L and 12L,
then assert ID 11L is first. This makes the final fallback explicit instead of relying on Mockito
input order.

- [ ] Step 4: Add the MidTermMemory positive control.

Return two matchable MidTermMemory objects from midTermMemoryRepository: a recent synthetic
summary with importance=0.7 and an old synthetic summary with importance=1.0 created 365 days ago.
Keep the repository query stub unchanged and assert the recent summary precedes the old summary in
profile.getMidMemorySummary(). This exercises the existing 14-day decay behavior while the
LifeGraph assertions fail independently.

- [ ] Step 5: Run the focused tests and record the baseline failure.

Run:

~~~~powershell
.\mvnw.cmd -q "-Dtest=MatchProfileAssemblerVisibilityTest" test
~~~~

Expected before implementation: the new importance ordering assertions fail; the MidTermMemory
positive control and existing visibility test pass. Do not weaken the expected ordering to match the
current implementation.

---

### Task 2: Make the matchable repository window importance-aware

Files:

- Modify: src/main/java/com/aseubel/yusi/repository/LifeGraphEntityRepository.java in findMatchableTopByUserId.
- Test: src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphImportanceConsumptionEvaluationTest.java in Task 4.

Interfaces:

- Consumes: existing findMatchableTopByUserId(String userId, LocalDateTime now, Pageable pageable) signature.
- Produces: a matchable candidate window ordered by importance, then mentionCount, then stable persisted fields.

- [ ] Step 1: Replace only the query ORDER BY clause.

Keep every existing visibility, expiration, and matchAllowed predicate unchanged. Replace the
current mention-only order with:

~~~~java
ORDER BY COALESCE(e.importance, 0.5) DESC,
         COALESCE(e.mentionCount, 0) DESC,
         e.updatedAt DESC,
         e.id ASC
~~~~

Do not change findVisibleByUserId, the name-search query, findTop50ByUserIdOrderByMentionCountDesc,
or any query used by community, merge, emotion, Timeline, or known-entity paths.

- [ ] Step 2: Run compilation and the existing visibility test.

Run:

~~~~powershell
.\mvnw.cmd -q "-Dtest=MatchProfileAssemblerVisibilityTest" test
~~~~

Expected: compilation succeeds; importance-focused assertions may still fail because the Assembler
comparator has not changed yet. Hidden/expired/matchable repository boundaries must remain untouched.

---

### Task 3: Consume importance in the MatchProfile LifeGraph comparator

Files:

- Modify: src/main/java/com/aseubel/yusi/service/match/impl/MatchProfileAssemblerImpl.java in buildLifeGraphSummary and nearby private helpers.
- Test: src/test/java/com/aseubel/yusi/service/match/MatchProfileAssemblerVisibilityTest.java.

Interfaces:

- Consumes: the existing List<LifeGraphEntity> returned by findMatchableTopByUserId.
- Produces: the existing MatchProfile.lifeGraphSummary text with unchanged type labels and six-entity limit.

- [ ] Step 1: Add null-safe effective sort helpers.

Implement the following semantics without changing persisted values:

~~~~java
private double effectiveImportance(LifeGraphEntity entity) {
    Double value = entity.getImportance();
    return value == null || !Double.isFinite(value) ? 0.5 : value;
}

private int effectiveMentionCount(LifeGraphEntity entity) {
    return entity.getMentionCount() == null ? 0 : entity.getMentionCount();
}
~~~~

The write path already clamps valid importance to [0, 1]; the read comparator only supplies the
legacy null default and must not introduce person-specific derivation.

- [ ] Step 2: Replace only the LifeGraph stream comparator.

Keep the User filter, limit(6), summary fallback, and type label mapping unchanged. Use this
comparator key order:

~~~~java
Comparator
        .comparingInt((LifeGraphEntity entity) -> lifeGraphPriority(entity.getType())).reversed()
        .thenComparing(Comparator.comparingDouble(this::effectiveImportance).reversed())
        .thenComparing(Comparator.comparingInt(this::effectiveMentionCount).reversed())
        .thenComparing(LifeGraphEntity::getUpdatedAt,
                Comparator.nullsLast(Comparator.reverseOrder()))
        .thenComparing(LifeGraphEntity::getId,
                Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(LifeGraphEntity::getNameNorm,
                Comparator.nullsLast(Comparator.naturalOrder()))
~~~~

The final name fallback makes transient test objects deterministic when an ID is absent; persisted
rows use updatedAt and id as the primary stable keys. Do not move importance ahead of typePriority
in this slice.

- [ ] Step 3: Preserve the MidTermMemory implementation byte-for-byte in behavior.

Do not edit buildMidMemorySummary or calculateDecayedImportance. The Task 1 positive control must
continue to pass after the LifeGraph comparator is changed.

- [ ] Step 4: Run the focused unit suite.

Run:

~~~~powershell
.\mvnw.cmd -q "-Dtest=MatchProfileAssemblerVisibilityTest" test
~~~~

Expected: all existing and new unit assertions pass, including the high-importance/low-mention
case, equal-importance mention tie-break, stable fallback, visibility boundary, and MidTermMemory
decay control.

---

### Task 4: Add the real H2 promotion-to-MatchProfile replay

Files:

- Create: src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphImportanceConsumptionEvaluationTest.java.
- Reuse: src/test/resources/evaluation/lifegraph-promotion-v1-fixtures.json and
  LifeGraphPromotionFixtureLoader without changing their contract.
- Reuse: LifeGraphPromotionEvaluationFixture, LifeGraphBuildServiceImpl,
  OfflineEvaluationReportWriter, and existing H2 test configuration.

Interfaces:

- Consumes: EVAL-MEM-003-B fixed LifeGraphExtractionResult plus its H2-confirmed person setup.
- Produces: target/evaluation/lifegraph-importance-v1-report.json, JUnit failure on any fixed
  violation code, and no sensitive output.

- [ ] Step 1: Create the Spring H2 test boundary.

Use the existing evaluation annotations and inject real repositories/services:

~~~~java
@SpringBootTest
@ActiveProfiles("test")
@Import(TestInfrastructureConfig.class)
class LifeGraphImportanceConsumptionEvaluationTest {
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private LifeGraphBuildService lifeGraphBuildService;
    @Autowired private LifeGraphEntityRepository entityRepository;
    @Autowired private LifeGraphRelationRepository relationRepository;
    @Autowired private MatchProfileAssembler matchProfileAssembler;
    @Autowired private MatchProfileRepository matchProfileRepository;
    @Autowired private MidTermMemoryRepository midTermMemoryRepository;

    @MockBean private PromptManager promptManager;
    @MockBean private LifeGraphExtractor extractor;
    @MockBean(name = "embeddingModel") private EmbeddingModel embeddingModel;
    @MockBean(name = "milvusClientV2") private MilvusClientV2 milvusClientV2;
}
~~~~

Configure the fixture PromptSnapshot and deterministic embedding response in @BeforeEach; no
external service may be started.

- [ ] Step 2: Load the baseline extraction with an isolated replay identity.

Use LifeGraphPromotionFixtureLoader(objectMapper).load(), select case EVAL-MEM-003 and scenario
EVAL-MEM-003-B, and never copy fixture text into the report. Reuse only its typed extraction and
expectations, but construct the importance replay context with these isolated identities:

~~~~text
userId = "fixture-user-importance-b"
sourceId = "fixture-diary-importance-b"
~~~~

Never use fixture-user-promotion-b or fixture-diary-promotion-b in this test. Reuse the established
H2 seed exactly under the isolated user:

~~~~text
Person.nameNorm = "fixture-person-b"
Person.importance = 0.8
Person.origin = MANUAL
Person.matchAllowed = false
User -> Person relation.origin = MANUAL
~~~~

The H2-derived confirmed set must be checked before replay and must be non-empty and contain
fixture-person-b; otherwise emit only CONFIRMED_PERSON_POSITIVE_CONTROL and fail the case.

- [ ] Step 3: Replay the fixed extraction through production BuildService.

Configure the mocked LifeGraphExtractor using the existing eight-argument signature and return
the serialized typed extraction. Call lifeGraphBuildService.upsertFromDiary(...) with the isolated
fixture-diary-importance-b source and the isolated fixture-user-importance-b user, then flush the
real JPA repositories. Assert the existing promotion H2 boundary with fixed counts/codes before
testing ranking, so a promotion regression cannot be mistaken for a ranking result.

- [ ] Step 4: Prepare the authorized H2 ranking projection.

Insert a synthetic User row for fixture-user-importance-b and set matchAllowed=true only on the
already persisted fixture-person-b, fixture-event-b, and fixture-item-b rows belonging to that
isolated user. Keep the existing fields
otherwise unchanged, then set deterministic ranking values for the comparison:

~~~~text
fixture-person-b: importance 0.8, mentionCount 1
fixture-event-b:  importance 0.5, mentionCount 9
fixture-item-b:   importance 0.5, mentionCount 2
~~~~

This setup represents explicit matching authorization after promotion; it does not change the
production default of matchAllowed=false for automatic entities.

- [ ] Step 5: Assert the real repository candidate window.

Call:

~~~~java
List<LifeGraphEntity> candidates = entityRepository.findMatchableTopByUserId(
        scenario.userId(), LocalDateTime.now(), PageRequest.of(0, 1));
~~~~

Assert that the first row is the high-importance person despite its lower mentionCount. Count this
as MATCHABLE_IMPORTANCE_ORDER; do not put the entity key into actualSummary.

- [ ] Step 6: Assert the real MatchProfile output and MidTermMemory control.

Persist two matchable synthetic MidTermMemory rows: a recent importance=0.7 row and an old
importance=1.0 row from 365 days ago. Call matchProfileAssembler.refreshProfile(scenario.userId()).

Assert all of the following in memory:

~~~~java
assertTrue(profile.getLifeGraphSummary().indexOf("fixture-person-b")
        < profile.getLifeGraphSummary().indexOf("fixture-event-b"));
assertTrue(profile.getMidMemorySummary().indexOf("fixture-mid-recent")
        < profile.getMidMemorySummary().indexOf("fixture-mid-old"));
assertTrue(matchProfileRepository.findByUserId(scenario.userId()).isPresent());
~~~~

The first assertion proves importance changes the matching decision; the second is the positive
control that MidTermMemory decay remains active and unchanged.

- [ ] Step 7: Write the low-sensitivity report.

Write one CaseResult through OfflineEvaluationReportWriter with suite id
lifegraph-importance-v1. Use a custom Versions object whose ranking slot is
new StrategyVersion("lifegraph-importance-lexicographic", "v1"); keep model/prompt/retrieval on
the fixture baseline. actualSummary may contain only numeric counts and fixed pass counters:

~~~~java
Map.of(
        "promotionH2BoundaryPassCount", 1,
        "matchableCandidateImportancePassCount", 1,
        "matchProfileImportancePassCount", 1,
        "midMemoryDecayControlPassCount", 1)
~~~~

Positive-assert /versions/prompt equals the exact fixture object. When scanning the report for
forbidden content, remove that field first and scan for evidence-token-, rawtext, plaincontent,
toolarguments, toolresult, secret, and password; do not scan for the literal field name prompt.

- [ ] Step 8: Run the focused H2 evaluation.

Run:

~~~~powershell
.\mvnw.cmd -q "-Dtest=LifeGraphImportanceConsumptionEvaluationTest" test
~~~~

Expected: one PASS case, report at target/evaluation/lifegraph-importance-v1-report.json,
report summary status PASS, exact versions.prompt, and no forbidden low-sensitivity token.

---

### Task 5: Verify scope isolation and the complete test suite

Files:

- Inspect only: LifeGraphEntityRepository.java, MatchProfileAssemblerImpl.java, and the test/report files above.
- Generated: target/evaluation/lifegraph-importance-v1-report.json.

- [ ] Step 1: Search for accidental out-of-scope ordering changes.

Run:

~~~~powershell
rg -n "findVisibleByUserId\\(|findVisibleByUserIdAndDisplayNameContainingOrderByMentionCountDesc|findTop50ByUserIdOrderByMentionCountDesc|findAllVisibleByUserIdAndType|Sort\\.by\\(Sort\\.Direction\\.DESC, \\"mentionCount\\"\\)" src/main/java/com/aseubel/yusi/service/lifegraph src/main/java/com/aseubel/yusi/repository/LifeGraphEntityRepository.java
~~~~

Expected: all remaining display/operation paths listed in the design document retain their existing
mentionCount ordering; only findMatchableTopByUserId has the new importance-aware order.

- [ ] Step 2: Run the full Maven test suite.

Run:

~~~~powershell
.\mvnw.cmd -q test
~~~~

Expected: all tests pass with zero failures, zero errors, and zero skipped tests; all existing
evaluation reports plus lifegraph-importance-v1-report.json exist below target/evaluation.

- [ ] Step 3: Scan the new report and changed test output boundary.

Run:

~~~~powershell
rg -n -i "evidence-token-|rawtext|plaincontent|toolarguments|toolresult|secret|password" target/evaluation/lifegraph-importance-v1-report.json
~~~~

Expected: no matches and no command/test output containing fixture evidence, profile text, memory
summary, Prompt text, or tool data.

- [ ] Step 4: Review the final diff and commit the slice.

Run:

~~~~powershell
git diff --check
git status --short
git diff -- src/main/java/com/aseubel/yusi/repository/LifeGraphEntityRepository.java src/main/java/com/aseubel/yusi/service/match/impl/MatchProfileAssemblerImpl.java src/test/java/com/aseubel/yusi/service/match/MatchProfileAssemblerVisibilityTest.java src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphImportanceConsumptionEvaluationTest.java
~~~~

Confirm no remaining sorting entry, person field, MidTermMemory decay method, report secret, or
production external dependency was changed. Then commit only the implementation and its tests:

~~~~powershell
git add src/main/java/com/aseubel/yusi/repository/LifeGraphEntityRepository.java src/main/java/com/aseubel/yusi/service/match/impl/MatchProfileAssemblerImpl.java src/test/java/com/aseubel/yusi/service/match/MatchProfileAssemblerVisibilityTest.java src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphImportanceConsumptionEvaluationTest.java
git commit -m "feat: consume lifegraph importance in match profile"
~~~~

Stop after the commit and wait for slice acceptance. Do not begin the separate evaluation of Person
field remodeling in the same turn.

The post-implementation launch checklist must separately execute the matchable COALESCE query on
the target MySQL version and compare null/default, mixed-direction, updatedAt, and id tie-break
behavior with the H2 result. That check is recorded as a release prerequisite and is not claimed by
the local Maven run.
