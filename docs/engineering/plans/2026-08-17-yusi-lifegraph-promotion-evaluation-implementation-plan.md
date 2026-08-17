# LifeGraph Promotion Evaluation Implementation Plan

> **For inline execution:** This plan is to be executed only after user review and confirmation. The project instructions disable sub-agents and auto-review; implementation stays in this workspace.

**Goal:** Add a deterministic `lifegraph-promotion-v1` H2 replay that locks the LifeGraph promotion input contract and verifies real entity, relation, and provenance persistence without calling an LLM.

**Architecture:** Keep production behavior unchanged. A sanitized typed fixture is first passed directly to `LifeGraphPromotionPolicy` for input/output assertions, then serialized only into a Mockito `LifeGraphExtractor` boundary so the real `LifeGraphBuildServiceImpl.upsertFromDiary` writes the same extraction into H2. The shared `OfflineEvaluationReportWriter` produces the low-sensitivity report consumed by the default Maven test suite and existing CI artifact glob.

**Tech Stack:** Java 21, Spring Boot 3.4, Spring Data JPA, H2 test profile, JUnit 5, Mockito, Jackson, existing `LifeGraphPromotionPolicy`, `LifeGraphBuildServiceImpl`, `EvaluationFixtureRedLineValidator`, and `OfflineEvaluationReportWriter`.

## Global Constraints

- Do not change production Java code, database migrations, application configuration, or start any service in this slice.
- Use only `LifeGraphExtractionResult` and `confirmedImportantPersonKeys` as the fixed promotion inputs; do not call a remote LLM.
- Use the existing H2 `application-test.yml` profile and real LifeGraph repositories; do not replace JPA with an in-memory fake.
- Keep `importance` out of assertions and decision logic; this slice does not modify importance fields or consumers.
- Use only `fixture-*` IDs and `evidence-token-*` values in fixtures; forbid user query, memory body, Prompt, tool arguments/results, secrets, and passwords.
- Write `target/evaluation/lifegraph-promotion-v1-report.json` through `OfflineEvaluationReportWriter`; report summaries contain counts and fixed codes only.
- The new evaluation test is part of default Maven test execution and must pass with `.\mvnw.cmd -q test`.
- Do not start any item from `2026-08-17-yusi-post-release-expansion-backlog.md`.

## File Map

Create:

- `src/test/resources/evaluation/lifegraph-promotion-v1-fixtures.json` — three sanitized promotion scenarios.
- `src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphPromotionEvaluationFixture.java` — typed suite, scenario, expected-result records.
- `src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphPromotionFixtureLoader.java` — shared red-line plus LifeGraph-specific schema validation.
- `src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphPromotionFixtureLoaderTest.java` — loader acceptance and rejection tests.
- `src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphPromotionEvaluationTest.java` — Spring H2 replay, policy assertions, persistence assertions, and report writer entry point.

Modify:

- `src/test/java/com/aseubel/yusi/service/lifegraph/LifeGraphPromotionPolicyTest.java` — add threshold, evidence, confirmed-person, duplicate, and rejected-relation contract tests.

No production file, migration, CI workflow, or existing evaluation report schema needs modification.

---

### Task 1: Lock the promotion policy input contract

**Files:**

- Modify: `src/test/java/com/aseubel/yusi/service/lifegraph/LifeGraphPromotionPolicyTest.java`
- Test target: `src/main/java/com/aseubel/yusi/service/lifegraph/LifeGraphPromotionPolicy.java`

**Interfaces:**

- Consumes: `LifeGraphPromotionPolicy.promote(LifeGraphExtractionResult, Set<String>)`.
- Produces: fixed assertions for `PromotionResult.entities()`, `relations()`, `acceptedEntityKeys()`, and `relationOccurrences()` used by the H2 evaluator.

- [x] **Step 1: Add a failing boundary test for confidence and evidence.**

Build one extraction containing `User`, `Person`, `Item`, and `Event`, then add these relations:

```java
relation("__USER__", "fixture-person-a", "PARTNER_OF", 0.60, "evidence-token-direct-a");
relation("fixture-person-a", "fixture-item-a", "LIKES", 0.59, "evidence-token-low-a");
relation("fixture-person-a", "fixture-event-a", "PARTICIPATED_IN", 0.90, null);
```

Call:

```java
LifeGraphPromotionPolicy.PromotionResult result =
        new LifeGraphPromotionPolicy().promote(extraction, Set.of());
```

Assert that the threshold relation is accepted, while the low-confidence and missing-evidence relations are absent. Use the existing test helper style, but make confidence and evidence explicit parameters so the boundary is visible.

- [x] **Step 2: Run only the policy test and verify the new test result.**

Run:

```powershell
.\mvnw.cmd -q "-Dtest=LifeGraphPromotionPolicyTest" test
```

Expected: the existing implementation passes the threshold/evidence behavior. If this exposes a policy regression, stop the implementation at that failure and review the production change separately; do not weaken the assertion.

- [x] **Step 3: Add the confirmed-person and topology assertions.**

Use an extraction with `fixture-person-b`, `fixture-item-b`, `fixture-event-b`, and `fixture-person-c`, then assert:

```java
LifeGraphPromotionPolicy.PromotionResult result = policy.promote(
        extraction,
        Set.of("fixture-person-b"));

assertTrue(result.acceptedEntityKeys().containsAll(
        Set.of("fixture-person-b", "fixture-item-b", "fixture-event-b")));
assertFalse(result.acceptedEntityKeys().contains("fixture-person-c"));
assertTrue(result.relations().stream().noneMatch(r ->
        "fixture-person-c".equals(r.getSource()) || "fixture-person-c".equals(r.getTarget())));
```

Include `FRIEND_OF` for `fixture-person-b -> fixture-person-c` and assert it is rejected. Add one relation for each `MENTIONED`, `MENTIONED_IN`, `SAID`, and `RELATED_TO` and assert none is present in `result.relations()`.

- [x] **Step 4: Add duplicate occurrence assertions.**

Repeat the same accepted relation twice and assert:

```java
assertEquals(1, result.relations().size());
assertEquals(2, result.relationOccurrences().get("__user__|fixture-person-a|PARTNER_OF"));
```

Use normalized keys in the expected occurrence key, matching the current policy contract.

- [x] **Step 5: Run the policy test again.**

Run the same focused Maven command. Expected: PASS with no production source changes.

- [x] **Step 6: Commit only after the user authorizes implementation.**

The implementation commit will include this test change together with the fixture and evaluator tasks below; do not commit during the current design/plan turn.

### Task 2: Add the typed, sanitized fixture contract

**Files:**

- Create: `src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphPromotionEvaluationFixture.java`
- Create: `src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphPromotionFixtureLoader.java`
- Create: `src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphPromotionFixtureLoaderTest.java`
- Create: `src/test/resources/evaluation/lifegraph-promotion-v1-fixtures.json`

**Interfaces:**

- Consumes: JSON fixture resource and `EvaluationFixtureRedLineValidator`.
- Produces: `Suite`, `EvaluationCase`, `Scenario`, `Expected`, and a typed `LifeGraphExtractionResult` for the evaluator.

- [x] **Step 1: Write loader tests for the public schema.**

Add tests for:

```java
@Test
void loadsTheSanitizedPromotionSuite() {
    LifeGraphPromotionEvaluationFixture.Suite suite = loader.load(validFixture());
    assertEquals("lifegraph-promotion-v1", suite.suiteId());
    assertEquals(3, suite.cases().get(0).scenarios().size());
}

@Test
void rejectsForbiddenFixtureFieldsWithoutEchoingTheirValue() {
    assertInvalid(jsonWithField("rawText", "synthetic-sensitive-value"));
}

@Test
void rejectsNonFixtureIdsAndNonTokenEvidence() {
    assertInvalid(jsonWithField("userId", "real-user-value"));
    assertInvalid(jsonWithField("evidenceSnippet", "not-an-evidence-token"));
}

@Test
void rejectsUnknownFieldsAndInvalidPromotionShape() {
    assertInvalid(jsonWithField("unknownField", "fixture-value"));
    assertInvalid(jsonWithMissingRequiredRelationEndpoint());
}
```

Define the helpers in the same test class so the examples have no implicit test API:

```java
private final ObjectMapper objectMapper = new ObjectMapper();
private final LifeGraphPromotionFixtureLoader loader =
        new LifeGraphPromotionFixtureLoader(objectMapper);

private JsonNode validFixture() throws IOException {
    try (InputStream input = new ClassPathResource(
            LifeGraphPromotionFixtureLoader.DEFAULT_RESOURCE).getInputStream()) {
        return objectMapper.readTree(input);
    }
}

private void assertInvalid(JsonNode root) {
    LifeGraphPromotionFixtureLoader.FixtureValidationException failure =
            assertThrows(LifeGraphPromotionFixtureLoader.FixtureValidationException.class,
                    () -> loader.load(root));
    assertEquals("FIXTURE_INVALID", failure.code());
}

private JsonNode jsonWithField(String field, String value) throws IOException {
    ObjectNode root = (ObjectNode) validFixture().deepCopy();
    switch (field) {
        case "userId" -> ((ObjectNode) root.at(
                "/cases/0/scenarios/0")).put(field, value);
        case "evidenceSnippet" -> ((ObjectNode) root.at(
                "/cases/0/scenarios/0/extraction/relations/0")).put(field, value);
        default -> root.put(field, value);
    }
    return root;
}

private JsonNode jsonWithMissingRequiredRelationEndpoint() throws IOException {
    ObjectNode root = (ObjectNode) validFixture().deepCopy();
    ((ObjectNode) root.at(
            "/cases/0/scenarios/0/extraction/relations/0")).remove("target");
    return root;
}
```

The valid fixture must have `schemaVersion=1`, `suiteId=lifegraph-promotion-v1`, one `EVAL-MEM-003` case, and exactly three scenarios. Invalid input must surface only `FIXTURE_INVALID`; never assert or print the rejected value.

- [x] **Step 2: Run the loader tests before adding the loader implementation.**

Run:

```powershell
.\mvnw.cmd -q "-Dtest=LifeGraphPromotionFixtureLoaderTest" test
```

Expected: FAIL because the typed records and loader do not exist yet.

- [x] **Step 3: Define the typed records.**

Use records equivalent to:

```java
public record Suite(int schemaVersion, String suiteId, List<EvaluationCase> cases) {}

public record EvaluationCase(String caseId, List<Scenario> scenarios) {}

public record Scenario(
        String scenarioId,
        String userId,
        String sourceId,
        List<String> confirmedImportantPersonKeys,
        LifeGraphExtractionResult extraction,
        Expected expected) {}

public record Expected(
        Set<String> acceptedEntityKeys,
        Set<String> acceptedRelationKeys,
        Set<String> rejectedRelationKeys,
        int sourceEntityEvidenceCount,
        int sourceRelationEvidenceCount,
        int sourceMentionCount) {}
```

Keep `expected` values as synthetic keys and counts only. Do not add a field capable of carrying raw source content, Prompt, tool arguments/results, or a secret.

- [x] **Step 4: Implement strict loader validation.**

Construct an `ObjectMapper` copy with `FAIL_ON_UNKNOWN_PROPERTIES=true`, call `EvaluationFixtureRedLineValidator.validateTree(root)`, then validate:

```java
schemaVersion == 1
suiteId.equals("lifegraph-promotion-v1")
caseId.matches("EVAL-MEM-003")
scenarioId.matches("EVAL-MEM-003-[A-C]")
userId.matches("fixture-user-[a-z0-9-]+")
sourceId.matches("fixture-diary-promotion-[a-z0-9-]+")
```

Validate that entity keys, relation endpoints, confirmed-person keys, and expected sets use fixture prefixes or the User sentinel; all entity types and relation types resolve through the existing protocol enums; confidence is between `0.0` and `1.0`; evidence snippets match `evidence-token-[a-z0-9-]+`; `props` is null or empty; and every expected key exists in the extraction. Convert every validation error to a `FixtureValidationException("FIXTURE_INVALID")` without retaining the source exception message.

- [x] **Step 5: Add the three sanitized scenarios.**

Use only synthetic values. The fixture must encode:

- `EVAL-MEM-003-A`: direct User->Person at `0.60`, accepted Person->Item/Event, Person->Person expansion, all four rejected relation codes, missing evidence, and `0.59` rejection.
- `EVAL-MEM-003-B`: `confirmedImportantPersonKeys=["fixture-person-b"]`, current-source Person->Item/Event accepted, Person->Person expansion rejected; source ID and user ID are unique.
- `EVAL-MEM-003-C`: no relation can pass evidence, confidence, confirmed-person, or topology gates; expected source contribution counts are all zero.

Every accepted non-User entity has a mention with an evidence token so the H2 source-evidence count is deterministic. Do not add a `content`, `plainContent`, or natural-language fixture field.

- [x] **Step 6: Run loader tests and the fixture red-line test.**

Run:

```powershell
.\mvnw.cmd -q "-Dtest=LifeGraphPromotionFixtureLoaderTest,EvaluationFixtureRedLineValidatorTest" test
```

Expected: PASS. The test must not start the application or access a remote dependency.

### Task 3: Implement the real H2 promotion replay and report

**Files:**

- Create: `src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphPromotionEvaluationTest.java`
- Reuse: `src/test/java/com/aseubel/yusi/evaluation/OfflineEvaluationReportWriter.java`
- Reuse: `src/test/java/com/aseubel/yusi/TestInfrastructureConfig.java`
- Reuse: `src/test/resources/application-test.yml`

**Interfaces:**

- Consumes: typed fixture `Scenario`, `LifeGraphPromotionPolicy`, real LifeGraph repositories, and `LifeGraphBuildService.upsertFromDiary`.
- Produces: `target/evaluation/lifegraph-promotion-v1-report.json` and JUnit failure when any policy/H2/red-line assertion fails.

- [x] **Step 1: Add the Spring test shell and report test method.**

Use the existing evaluation annotations and mocks:

```java
@SpringBootTest
@ActiveProfiles("test")
@Import(TestInfrastructureConfig.class)
class LifeGraphPromotionEvaluationTest {
    private static final Path REPORT_PATH = Path.of(
            "target", "evaluation", "lifegraph-promotion-v1-report.json");

    @Autowired private ObjectMapper objectMapper;
    @Autowired private LifeGraphBuildService lifeGraphBuildService;
    @Autowired private LifeGraphEntityRepository entityRepository;
    @Autowired private LifeGraphRelationRepository relationRepository;
    @Autowired private LifeGraphEntityEvidenceRepository entityEvidenceRepository;
    @Autowired private LifeGraphRelationEvidenceRepository relationEvidenceRepository;
    @Autowired private LifeGraphMentionRepository mentionRepository;

    @MockBean private PromptManager promptManager;
    @MockBean private LifeGraphExtractor extractor;
}
```

In `@BeforeEach`, reset the two mocks and return `new PromptSnapshot("graphrag-extract", "fixture-v1", "zh-CN", "fixture")`. The extractor mock is the only extraction boundary; it must never be replaced by a real model.

- [x] **Step 2: Add the policy-first replay assertion.**

For each scenario, execute:

```java
LifeGraphPromotionPolicy.PromotionResult policyResult =
        new LifeGraphPromotionPolicy().promote(
                scenario.extraction(),
                Set.copyOf(scenario.confirmedImportantPersonKeys()));
```

Compare normalized accepted entity keys, normalized accepted relation keys, rejected relation expectations, and source occurrence counts to the fixture expectation. Record only fixed violation codes such as `POLICY_BOUNDARY`; do not include a key or relation string in the report.

- [x] **Step 3: Seed the confirmed person for scenario B.**

Before invoking BuildService for `EVAL-MEM-003-B`, save a synthetic User and Person in H2, then save a manual direct relation with semantic direction User->Person:

```java
LifeGraphEntity person = LifeGraphEntity.builder()
        .userId(scenario.userId())
        .type(LifeGraphEntity.EntityType.Person)
        .nameNorm("fixture-person-b")
        .displayName("fixture-person-b")
        .mentionCount(0)
        .relationCount(0)
        .confidence(0.9)
        .importance(0.8)
        .matchAllowed(false)
        .hidden(false)
        .origin(LifeGraphEntity.Origin.MANUAL)
        .build();
person = entityRepository.save(person);

LifeGraphRelation.builder()
        .userId(scenario.userId())
        .sourceId(Math.min(user.getId(), person.getId()))
        .targetId(Math.max(user.getId(), person.getId()))
        .semanticSourceId(user.getId())
        .semanticTargetId(person.getId())
        .type("PARTNER_OF")
        .confidence(BigDecimal.valueOf(0.9))
        .weight(1)
        .manualWeight(1)
        .origin(LifeGraphRelation.Origin.MANUAL)
        .build()
```

Set the pre-seeded User entity fields as well, and keep the Person `nameNorm` exactly
`fixture-person-b`; `findConfirmedImportantPersons` returns this field rather than `displayName`.
After saving the manual relation, derive the confirmed set from H2 using the same user-scoped entity
lookups and `policy.personRelations()` relation-type filter used by production, then assert before the
BuildService call:

```java
Set<String> confirmedFromH2 = findConfirmedImportantPersonKeysFromH2(scenario.userId());
checks.check("CONFIRMED_PERSON_POSITIVE_CONTROL",
        !confirmedFromH2.isEmpty()
                && confirmedFromH2.contains("fixture-person-b"));
```

The evaluator helper must use semantic endpoints with the legacy physical-endpoint fallback:

```java
private Set<String> findConfirmedImportantPersonKeysFromH2(String userId) {
    Set<String> result = new LinkedHashSet<>();
    for (LifeGraphRelation relation : relationRepository.findByUserId(userId)) {
        String type = relation.getType() == null
                ? "" : relation.getType().trim().toUpperCase(Locale.ROOT);
        if (!policy.personRelations().contains(type)) {
            continue;
        }
        Long sourceId = relation.getSemanticSourceId() == null
                ? relation.getSourceId() : relation.getSemanticSourceId();
        Long targetId = relation.getSemanticTargetId() == null
                ? relation.getTargetId() : relation.getSemanticTargetId();
        LifeGraphEntity source = entityRepository.findByIdAndUserId(sourceId, userId).orElse(null);
        LifeGraphEntity target = entityRepository.findByIdAndUserId(targetId, userId).orElse(null);
        if (source != null && target != null
                && source.getType() == LifeGraphEntity.EntityType.User
                && target.getType() == LifeGraphEntity.EntityType.Person) {
            result.add(target.getNameNorm());
        } else if (source != null && target != null
                && target.getType() == LifeGraphEntity.EntityType.User
                && source.getType() == LifeGraphEntity.EntityType.Person) {
            result.add(source.getNameNorm());
        }
    }
    return result;
}
```

Flush the two repositories before calling this helper so the positive control reads the just-saved
H2 rows. The helper is test-only and must not be added to production code.

Do not proceed with the scenario when this positive control fails. Do not create source evidence for
the manual confirmation; it must remain distinguishable from the current fixture source.

- [x] **Step 4: Feed the fixed extraction through the real BuildService.**

Configure the mock from the typed extraction:

```java
when(extractor.extract(anyString(), anyString(), anyString(), anyString(),
        anyString(), anyString(), anyString(), anyString()))
        .thenReturn(objectMapper.writeValueAsString(scenario.extraction()));
```

Invoke the real production entry point with only synthetic source data:

```java
lifeGraphBuildService.upsertFromDiary(
        Diary.builder()
                .diaryId(scenario.sourceId())
                .userId(scenario.userId())
                .title("fixture-title")
                .entryDate(LocalDate.of(2026, 8, 17))
                .build(),
        "evidence-token-diary-input");
```

Do not persist the synthetic input string into the report. The call must use H2-backed repositories and the real policy invocation inside `LifeGraphBuildServiceImpl`.

- [x] **Step 5: Assert H2 results by source contribution.**

Query only the scenario user and current `DIARY + sourceId` contribution. Assert:

```java
sourceEntityEvidenceCount == scenario.expected().sourceEntityEvidenceCount()
sourceRelationEvidenceCount == scenario.expected().sourceRelationEvidenceCount()
sourceMentionCount == scenario.expected().sourceMentionCount()
```

For A, assert the threshold relation and its two accepted value/event relations exist, while low-confidence, missing-evidence, rejected-code, and Person->Person relations do not. For B, assert the manual confirmation remains and only the two current-source auto relations are added. For C, assert only the automatically ensured User entity remains and all current-source contribution counts are zero. Use fixed codes `H2_ENTITY_BOUNDARY`, `H2_RELATION_BOUNDARY`, and `EVIDENCE_BOUNDARY`.

- [x] **Step 6: Build a low-sensitivity actual summary and write the report.**

Populate `OfflineEvaluationReportWriter.CaseResult` with:

```java
new OfflineEvaluationReportWriter.CaseResult(
        caseId,
        scenario.scenarioId(),
        checks.hasFailures() ? "FAIL" : "PASS",
        "fixture-v1",
        "expectation-v1",
        OfflineEvaluationReportWriter.Versions.fixtureBaseline(),
        checks.assertionCount(),
        checks.passedAssertionCount(),
        checks.violationCodes(),
        Map.ofEntries(
                Map.entry("entityCount", entityCount),
                Map.entry("userEntityCount", userEntityCount),
                Map.entry("personEntityCount", personEntityCount),
                Map.entry("autoEntityCount", autoEntityCount),
                Map.entry("relationCount", relationCount),
                Map.entry("autoRelationCount", autoRelationCount),
                Map.entry("entityEvidenceCount", entityEvidenceCount),
                Map.entry("relationEvidenceCount", relationEvidenceCount),
                Map.entry("mentionCount", mentionCount),
                Map.entry("sourceEntityEvidenceCount", sourceEntityEvidenceCount),
                Map.entry("sourceRelationEvidenceCount", sourceRelationEvidenceCount),
                Map.entry("sourceMentionCount", sourceMentionCount)));
```

Wrap fixture/serialization/replay failures into `FIXTURE_INVALID` or `REPLAY_EXECUTION` only. Always
call `OfflineEvaluationReportWriter.write(REPORT_PATH, "lifegraph-promotion-v1", results)` in
`finally`. Assert the results are non-empty, every case is `PASS`, and the report exists. Parse the
report JSON and assert that each case's `/versions/prompt` node is exactly:

```json
{"key":"fixture","version":"fixture-v1","locale":"zh-CN"}
```

The negative report scan must exclude the structural field name `prompt` and must reject only
`evidence-token-`, `rawText`, `plainContent`, `toolArguments`, `toolResult`, `secret`, and
`password`. It must not include fixture values, entity keys, Prompt text, tool arguments/results, or
exception messages in the report.

- [x] **Step 7: Run the focused evaluation test.**

Run:

```powershell
.\mvnw.cmd -q "-Dtest=LifeGraphPromotionEvaluationTest" test
```

Expected: PASS and a report at `target/evaluation/lifegraph-promotion-v1-report.json` whose summary status is `PASS`. No application service or external client may be started.

### Task 4: Verify the slice and commit after approval

**Files:**

- Verify: all files in Tasks 1-3.
- Output: `target/evaluation/lifegraph-promotion-v1-report.json` (ignored/generated; do not commit it unless the repository already tracks generated reports).

- [x] **Step 1: Run the focused policy, loader, and replay tests together.**

Run:

```powershell
.\mvnw.cmd -q "-Dtest=LifeGraphPromotionPolicyTest,LifeGraphPromotionFixtureLoaderTest,LifeGraphPromotionEvaluationTest" test
```

Expected: PASS; the report remains low sensitivity.

- [x] **Step 2: Check the generated report for forbidden values.**

Run:

```powershell
rg -n "evidence-token-|rawText|plainContent|toolArguments|toolResult|secret|password" target/evaluation/lifegraph-promotion-v1-report.json
```

Expected: no matches. The exact `versions.prompt` object is checked by
`LifeGraphPromotionEvaluationTest`, not by this negative scan. This command must not be replaced by
printing fixture or database contents.

- [x] **Step 3: Run the required full Maven suite.**

Run:

```powershell
.\mvnw.cmd -q test
```

Expected: exit code `0`; all existing evaluation reports and the new promotion report are generated under `target/evaluation`.

- [x] **Step 4: Run repository hygiene checks.**

Run:

```powershell
git diff --check
git status --short
```

Expected: no whitespace errors; only the intended test fixture/loader/evaluator/unit-test files and documentation are changed. Preserve the user-owned roadmap and backlog changes.

- [x] **Step 5: Commit the completed slice after the user confirms the design and plan.**

Run:

```powershell
git add docs/engineering/specs/2026-08-17-yusi-lifegraph-promotion-evaluation-design.md docs/engineering/plans/2026-08-17-yusi-lifegraph-promotion-evaluation-implementation-plan.md src/test/resources/evaluation/lifegraph-promotion-v1-fixtures.json src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphPromotionEvaluationFixture.java src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphPromotionFixtureLoader.java src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphPromotionFixtureLoaderTest.java src/test/java/com/aseubel/yusi/evaluation/lifegraph/LifeGraphPromotionEvaluationTest.java src/test/java/com/aseubel/yusi/service/lifegraph/LifeGraphPromotionPolicyTest.java
git commit -m "test: add lifegraph promotion h2 evaluation"
```

The current turn stops before this commit because production/test implementation is gated on user confirmation. After the commit and full test pass, stop and wait for the next review instruction.
