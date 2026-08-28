# Long-Term Memory Transparency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the existing `MidTermMemory` transparency slice to `UserPersona` and the existing LifeGraph relation data, enforce visibility and matching scopes across runtime reads, and remove the duplicate `/lifegraph` frontend page route.

**Product Requirement:** [Memory transparency and lifecycle](../../prd/2026-08-04-memory-transparency-lifecycle.md)

**Roadmap:** [Yusi Agent product roadmap](../../engineering/plans/2026-08-04-yusi-agent-product-roadmap.md)

**Architecture:** Add row-level lifecycle metadata to `UserPersona` and `LifeGraphEntity`. Keep `LifeGraph` as the existing backend model and use `LifeGraphMention` rows as safe source references. Add authenticated lifecycle services under `/api/memory`, make runtime repositories expose visible and matchable queries, and keep the existing `/community` graph page as the only graph UI route.

**Tech Stack:** Spring Boot, Spring Data JPA, MySQL/Flyway, Mockito/JUnit 5, React 19, TypeScript, React Router, Tailwind CSS, `pnpm`.

## Global Constraints

- Preserve the existing uncommitted `MidTermMemory` slice and work with its current changes.
- New derived Persona and LifeGraph records default to `matchAllowed=false`; migrations for existing rows preserve current matching behavior with `match_allowed=1`.
- Hidden and expired records are excluded from Agent reads, graph-derived insight reads, and matching; `matchAllowed=false` only excludes matching.
- API responses never include original diary content, full chat content, prompt text, tool arguments, tool results, or LifeGraph mention snippets.
- Every lifecycle mutation is scoped by `userId` in the repository lookup.
- Do not add a new frontend route for LifeGraph or an insights hub. Keep `/community`; remove `/lifegraph` without a redirect.
- Keep `/api/lifegraph` as the existing backend API namespace; it is not the duplicate frontend route.
- Write the failing test before each production behavior change and run the focused test before proceeding.
- Use `pnpm`, not `npm`, for all frontend verification.

---

### Task 1: Add Lifecycle Fields, Defaults, and Filtered Repository Contracts

**Files:**
- Modify: `src/main/java/com/aseubel/yusi/pojo/entity/UserPersona.java`
- Modify: `src/main/java/com/aseubel/yusi/pojo/entity/LifeGraphEntity.java`
- Modify: `src/main/java/com/aseubel/yusi/repository/UserPersonaRepository.java`
- Modify: `src/main/java/com/aseubel/yusi/repository/LifeGraphEntityRepository.java`
- Modify: `src/main/resources/db/init.sql`
- Create: `src/main/resources/db/migration/V20260807__add_long_term_memory_lifecycle.sql`
- Test: `src/test/java/com/aseubel/yusi/service/memory/MemoryLifecycleDefaultsTest.java`

**Interfaces:**
- Produces `UserPersona.getSourceType()`, `getSourceId()`, `getConfidence()`, `getMatchAllowed()`, `getHidden()`, and `getValidUntil()`.
- Produces `LifeGraphEntity.getConfidence()`, `getMatchAllowed()`, `getHidden()`, and `getValidUntil()`.
- Produces `UserPersonaRepository.findVisibleByUserId(String, LocalDateTime)` and `findMatchableByUserId(String, LocalDateTime)`.
- Produces explicit LifeGraph visible/matchable queries used by later tasks.

- [ ] **Step 1: Write the failing default contract test.**

```java
@Test
void newDerivedPersonaAndGraphEntityDoNotEnterMatchingWithoutConsent() {
    UserPersona persona = UserPersona.builder().userId("user-1").build();
    LifeGraphEntity entity = LifeGraphEntity.builder()
            .userId("user-1")
            .type(LifeGraphEntity.EntityType.Topic)
            .nameNorm("topic")
            .displayName("Topic")
            .build();

    assertFalse(Boolean.TRUE.equals(persona.getMatchAllowed()));
    assertFalse(Boolean.TRUE.equals(entity.getMatchAllowed()));
    assertFalse(Boolean.TRUE.equals(persona.getHidden()));
    assertFalse(Boolean.TRUE.equals(entity.getHidden()));
}
```

- [ ] **Step 2: Run the focused test and confirm the expected missing-field failure.**

Run: `.\mvnw.cmd -q -Dtest=MemoryLifecycleDefaultsTest test`

Expected: compilation fails because the lifecycle accessors and builder fields do not exist yet.

- [ ] **Step 3: Add entity fields and builder defaults.**

Add these `UserPersona` fields:

```java
@Column(name = "source_type", nullable = false, length = 32)
@Builder.Default
private String sourceType = "UNKNOWN";

@Column(name = "source_id", length = 128)
private String sourceId;

@Column(name = "confidence", nullable = false)
@Builder.Default
private Double confidence = 0.5;

@Column(name = "match_allowed", nullable = false)
@Builder.Default
private Boolean matchAllowed = false;

@Column(name = "hidden", nullable = false)
@Builder.Default
private Boolean hidden = false;

@Column(name = "valid_until")
private LocalDateTime validUntil;
```

Add the same lifecycle fields to `LifeGraphEntity` except `sourceType` and `sourceId`; its source evidence remains in `LifeGraphMention`.

- [ ] **Step 4: Add the Flyway migration and update the bootstrap schema.**

Create `V20260807__add_long_term_memory_lifecycle.sql` with `ALTER TABLE` statements for `user_persona` and `life_graph_entity`. Set existing rows to `match_allowed=1`, `hidden=0`, and clamp existing confidence values to `0..1` where a source confidence is unavailable. Add user/lifecycle indexes. Mirror the final columns and defaults in `src/main/resources/db/init.sql`.

- [ ] **Step 5: Add explicit repository filters.**

Use JPQL predicates with `hidden = false` and `(validUntil IS NULL OR validUntil > :now)`. Add matchable variants that also require `matchAllowed = true`. Keep raw `findByUserId...` and name lookups for ownership checks and cognition ingestion.

```java
@Query("SELECT p FROM UserPersona p WHERE p.userId = :userId "
        + "AND p.hidden = false "
        + "AND (p.validUntil IS NULL OR p.validUntil > :now)")
Optional<UserPersona> findVisibleByUserId(@Param("userId") String userId,
                                          @Param("now") LocalDateTime now);

@Query("SELECT p FROM UserPersona p WHERE p.userId = :userId "
        + "AND p.hidden = false "
        + "AND p.matchAllowed = true "
        + "AND (p.validUntil IS NULL OR p.validUntil > :now)")
Optional<UserPersona> findMatchableByUserId(@Param("userId") String userId,
                                            @Param("now") LocalDateTime now);
```

Add corresponding LifeGraph methods for visible pages, visible type/search reads, matchable top entities, and `findByIdAndUserId` ownership lookups.

- [ ] **Step 6: Run the focused test and compile.**

Run: `.\mvnw.cmd -q -Dtest=MemoryLifecycleDefaultsTest test`

Expected: the test passes and the compiler reports no lifecycle entity errors.

- [ ] **Step 7: Commit only this task's files.**

```text
git add src/main/java/com/aseubel/yusi/pojo/entity/UserPersona.java src/main/java/com/aseubel/yusi/pojo/entity/LifeGraphEntity.java src/main/java/com/aseubel/yusi/repository/UserPersonaRepository.java src/main/java/com/aseubel/yusi/repository/LifeGraphEntityRepository.java src/main/resources/db/init.sql src/main/resources/db/migration/V20260807__add_long_term_memory_lifecycle.sql src/test/java/com/aseubel/yusi/service/memory/MemoryLifecycleDefaultsTest.java
git commit -m "feat: add long-term memory lifecycle fields"
```

### Task 2: Implement Persona Transparency and Lifecycle Controls

**Files:**
- Create: `src/main/java/com/aseubel/yusi/pojo/dto/memory/PersonaMemoryItem.java`
- Create: `src/main/java/com/aseubel/yusi/pojo/dto/memory/UpdatePersonaMemoryRequest.java`
- Create: `src/main/java/com/aseubel/yusi/service/memory/UserPersonaLifecycleService.java`
- Modify: `src/main/java/com/aseubel/yusi/service/user/UserPersonaService.java`
- Modify: `src/main/java/com/aseubel/yusi/service/user/impl/UserPersonaServiceImpl.java`
- Modify: `src/main/java/com/aseubel/yusi/service/persona/UserPersonaUpdateService.java`
- Modify: `src/main/java/com/aseubel/yusi/service/persona/impl/UserPersonaUpdateServiceImpl.java`
- Modify: `src/main/java/com/aseubel/yusi/service/cognition/impl/AgentCognitionOrchestratorImpl.java`
- Modify: `src/main/java/com/aseubel/yusi/controller/MemoryCenterController.java`
- Test: `src/test/java/com/aseubel/yusi/service/memory/UserPersonaLifecycleServiceTest.java`
- Test: `src/test/java/com/aseubel/yusi/service/user/UserPersonaServiceVisibilityTest.java`

**Interfaces:**
- `UserPersonaLifecycleService.get(String userId): PersonaMemoryItem`
- `UserPersonaLifecycleService.update(String userId, UpdatePersonaMemoryRequest request): PersonaMemoryItem`
- `UserPersonaLifecycleService.delete(String userId): void`
- `UserPersonaService.getUserPersona(String userId)` returns only visible/active data.
- `UserPersonaService.getMatchableUserPersona(String userId)` returns only visible, unexpired, match-authorized data.
- `UserPersonaUpdateService.mergeFromRouting(String, CognitionRoutingResult, String sourceType, String sourceId)` records the cognition source while preserving hidden, expiry, and match controls.

- [ ] **Step 1: Write failing tests for ownership, visibility, and match scope.**

```java
@Test
void hiddenPersonaIsNotReturnedToAgent() {
    UserPersona hidden = persona("user-1");
    hidden.setHidden(true);
    when(repository.findVisibleByUserId(eq("user-1"), any())).thenReturn(Optional.empty());

    UserPersona result = service().getUserPersona("user-1");

    assertEquals("user-1", result.getUserId());
    assertNull(result.getPreferredName());
}

@Test
void updateChangesLifecycleAndRefreshesMatchProfile() {
    UserPersona persona = persona("user-1");
    when(repository.findByUserId("user-1")).thenReturn(Optional.of(persona));
    when(repository.save(any(UserPersona.class))).thenAnswer(invocation -> invocation.getArgument(0));

    UpdatePersonaMemoryRequest request = new UpdatePersonaMemoryRequest();
    request.setHidden(true);
    request.setMatchAllowed(false);

    PersonaMemoryItem result = lifecycle().update("user-1", request);

    assertEquals("HIDDEN", result.getLifecycleStatus());
    verify(matchProfileAssembler).refreshProfile("user-1");
}
```

Add a test that `delete("user-1")` does not delete a row belonging to another user and a test that an expired Persona is absent from both visible and matchable service reads.

- [ ] **Step 2: Run the tests and confirm they fail for the missing lifecycle service/fields.**

Run: `.\mvnw.cmd -q -Dtest=UserPersonaLifecycleServiceTest,UserPersonaServiceVisibilityTest test`

Expected: compilation fails because the DTO, service, and matchable read method are not implemented.

- [ ] **Step 3: Implement the DTO and lifecycle service.**

`PersonaMemoryItem` exposes the five Persona values, source metadata, confidence, timestamps, `validUntil`, `matchAllowed`, `hidden`, and `lifecycleStatus`. `UpdatePersonaMemoryRequest` accepts optional Persona values, `confidence`, `matchAllowed`, `hidden`, `validUntil`, `clearValidUntil`, and a list of field names to clear. Reject unknown clear-field names and confidence outside `0..1`.

The service must use `findByUserId(userId)` for mutation, set `sourceType=USER_EDIT` and `confidence=1.0` when a user changes Persona values, set `updatedAt`, save once, and refresh the match profile when content, match authorization, hidden state, or validity changes. A missing Persona returns a safe empty response for `GET`; deleting a missing row is a no-op.

- [ ] **Step 4: Filter existing Persona consumers.**

Implement `getUserPersona` with `findVisibleByUserId` and an empty builder fallback. Implement `getMatchableUserPersona` with `findMatchableByUserId`. Keep lifecycle fields out of `BeanUtil` input from user-controlled request objects by copying allowed properties explicitly in the lifecycle service.

- [ ] **Step 5: Propagate cognition source metadata.**

Keep the existing two-argument `mergeFromRouting` as a delegating overload and add the source-aware method. Update `AgentCognitionOrchestratorImpl` to pass `command.getSourceType()` and `command.getSourceId()`. New AI-derived Persona records keep `matchAllowed=false`; existing user opt-in is preserved.

- [ ] **Step 6: Add controller endpoints and verify the focused tests.**

Add to `MemoryCenterController`:

```java
@GetMapping("/persona")
public Response<PersonaMemoryItem> getPersona() {
    return Response.success(personaLifecycleService.get(UserContext.getUserId()));
}

@PatchMapping("/persona")
public Response<PersonaMemoryItem> updatePersona(
        @Valid @RequestBody UpdatePersonaMemoryRequest request) {
    return Response.success(personaLifecycleService.update(UserContext.getUserId(), request));
}

@DeleteMapping("/persona")
public Response<Void> deletePersona() {
    personaLifecycleService.delete(UserContext.getUserId());
    return Response.success();
}
```

Run: `.\mvnw.cmd -q -Dtest=UserPersonaLifecycleServiceTest,UserPersonaServiceVisibilityTest test`

Expected: all focused Persona tests pass.

- [ ] **Step 7: Commit the Persona slice.**

```text
git add src/main/java/com/aseubel/yusi/pojo/dto/memory/PersonaMemoryItem.java src/main/java/com/aseubel/yusi/pojo/dto/memory/UpdatePersonaMemoryRequest.java src/main/java/com/aseubel/yusi/service/memory/UserPersonaLifecycleService.java src/main/java/com/aseubel/yusi/service/user/UserPersonaService.java src/main/java/com/aseubel/yusi/service/user/impl/UserPersonaServiceImpl.java src/main/java/com/aseubel/yusi/service/persona/UserPersonaUpdateService.java src/main/java/com/aseubel/yusi/service/persona/impl/UserPersonaUpdateServiceImpl.java src/main/java/com/aseubel/yusi/service/cognition/impl/AgentCognitionOrchestratorImpl.java src/main/java/com/aseubel/yusi/controller/MemoryCenterController.java src/test/java/com/aseubel/yusi/service/memory/UserPersonaLifecycleServiceTest.java src/test/java/com/aseubel/yusi/service/user/UserPersonaServiceVisibilityTest.java
git commit -m "feat: add persona transparency controls"
```

### Task 3: Implement LifeGraph Transparency, Sources, and Deletion Cleanup

**Files:**
- Create: `src/main/java/com/aseubel/yusi/pojo/dto/memory/LifeGraphMemoryItem.java`
- Create: `src/main/java/com/aseubel/yusi/pojo/dto/memory/LifeGraphSourceItem.java`
- Create: `src/main/java/com/aseubel/yusi/pojo/dto/memory/LifeGraphMemoryResponse.java`
- Create: `src/main/java/com/aseubel/yusi/pojo/dto/memory/UpdateLifeGraphMemoryRequest.java`
- Create: `src/main/java/com/aseubel/yusi/service/memory/LifeGraphLifecycleService.java`
- Modify: `src/main/java/com/aseubel/yusi/repository/LifeGraphEntityAliasRepository.java`
- Modify: `src/main/java/com/aseubel/yusi/repository/LifeGraphMentionRepository.java`
- Modify: `src/main/java/com/aseubel/yusi/repository/LifeGraphRelationRepository.java`
- Modify: `src/main/java/com/aseubel/yusi/repository/LifeGraphMergeJudgmentRepository.java`
- Modify: `src/main/java/com/aseubel/yusi/controller/MemoryCenterController.java`
- Test: `src/test/java/com/aseubel/yusi/service/memory/LifeGraphLifecycleServiceTest.java`

**Interfaces:**
- `LifeGraphLifecycleService.list(String userId, int limit): LifeGraphMemoryResponse`
- `LifeGraphLifecycleService.update(String userId, Long entityId, UpdateLifeGraphMemoryRequest request): LifeGraphMemoryItem`
- `LifeGraphLifecycleService.delete(String userId, Long entityId): void`

- [ ] **Step 1: Write failing tests for safe sources, ownership, lifecycle updates, and cleanup.**

```java
@Test
void listReturnsDiaryReferencesWithoutMentionSnippets() {
    LifeGraphEntity entity = entity(11L, "user-1");
    LifeGraphMention mention = LifeGraphMention.builder()
            .userId("user-1")
            .entityId(11L)
            .diaryId("diary-7")
            .snippet("private original diary text")
            .entryDate(LocalDate.of(2026, 8, 1))
            .build();
    when(entityRepository.findByUserId(eq("user-1"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(entity)));
    when(mentionRepository.findTop200ByUserIdAndEntityIdOrderByCreatedAtDesc("user-1", 11L))
            .thenReturn(List.of(mention));

    LifeGraphMemoryItem result = service().list("user-1", 50).getEntities().get(0);

    assertEquals("diary-7", result.getSources().get(0).getSourceId());
    assertNull(result.getSources().get(0).getSnippet());
}
```

Add tests for cross-user mutation rejection, expired entity status, match-profile refresh after lifecycle changes, and deletion of relation/alias/mention/merge rows before the entity.

- [ ] **Step 2: Run the focused test and confirm the missing service failure.**

Run: `.\mvnw.cmd -q -Dtest=LifeGraphLifecycleServiceTest test`

Expected: compilation fails because the DTOs and lifecycle service do not exist.

- [ ] **Step 3: Implement safe DTO mapping and list behavior.**

Map `LifeGraphEntity` to type, display name, summary, counts, confidence, timestamps, lifecycle state, and controls. For each entity, map at most 20 `LifeGraphMention` records to `sourceId=diaryId`, `sourceType=DIARY`, `entryDate`, and `createdAt`; do not copy `snippet`.

`LifeGraphMemoryResponse` contains `entities`, `activeCount`, `hiddenCount`, `expiredCount`, and `matchableCount`. Clamp `limit` to `1..100` at the controller boundary.

- [ ] **Step 4: Implement lifecycle mutation with ownership and best-effort profile refresh.**

Use `findByIdAndUserId` for every update/delete. Update only `confidence`, `matchAllowed`, `hidden`, and `validUntil`/`clearValidUntil`; set `updatedAt` through JPA and refresh matching after scope-affecting changes. A vector operation is not involved in LifeGraph lifecycle changes.

- [ ] **Step 5: Implement user-scoped derived-row cleanup.**

Add repository delete methods for aliases, mentions, and merge judgments by `userId + entityId`. Load and delete both source and target relations for the owned entity, then delete dependent rows and the entity within one `@Transactional` method. Do not call diary or chat repositories.

- [ ] **Step 6: Add controller endpoints and run focused tests.**

Add to `MemoryCenterController`:

```java
@GetMapping("/life-graph")
public Response<LifeGraphMemoryResponse> getLifeGraph(
        @RequestParam(defaultValue = "50") int limit) {
    return Response.success(lifeGraphLifecycleService.list(UserContext.getUserId(), limit));
}

@PatchMapping("/life-graph/{id}")
public Response<LifeGraphMemoryItem> updateLifeGraph(
        @PathVariable Long id,
        @Valid @RequestBody UpdateLifeGraphMemoryRequest request) {
    return Response.success(lifeGraphLifecycleService.update(UserContext.getUserId(), id, request));
}

@DeleteMapping("/life-graph/{id}")
public Response<Void> deleteLifeGraph(@PathVariable Long id) {
    lifeGraphLifecycleService.delete(UserContext.getUserId(), id);
    return Response.success();
}
```

Run: `.\mvnw.cmd -q -Dtest=LifeGraphLifecycleServiceTest test`

Expected: all focused LifeGraph lifecycle tests pass.

- [ ] **Step 7: Commit the LifeGraph transparency slice.**

```text
git add src/main/java/com/aseubel/yusi/pojo/dto/memory/LifeGraphMemoryItem.java src/main/java/com/aseubel/yusi/pojo/dto/memory/LifeGraphSourceItem.java src/main/java/com/aseubel/yusi/pojo/dto/memory/LifeGraphMemoryResponse.java src/main/java/com/aseubel/yusi/pojo/dto/memory/UpdateLifeGraphMemoryRequest.java src/main/java/com/aseubel/yusi/service/memory/LifeGraphLifecycleService.java src/main/java/com/aseubel/yusi/repository/LifeGraphEntityAliasRepository.java src/main/java/com/aseubel/yusi/repository/LifeGraphMentionRepository.java src/main/java/com/aseubel/yusi/repository/LifeGraphRelationRepository.java src/main/java/com/aseubel/yusi/repository/LifeGraphMergeJudgmentRepository.java src/main/java/com/aseubel/yusi/controller/MemoryCenterController.java src/test/java/com/aseubel/yusi/service/memory/LifeGraphLifecycleServiceTest.java
git commit -m "feat: add relationship graph lifecycle controls"
```

### Task 4: Enforce Visibility Across Agent, Insight, Graph, and Match Reads

**Files:**
- Modify: `src/main/java/com/aseubel/yusi/service/lifegraph/LifeGraphDataService.java`
- Modify: `src/main/java/com/aseubel/yusi/service/lifegraph/LifeGraphQueryService.java`
- Modify: `src/main/java/com/aseubel/yusi/service/lifegraph/impl/LifeGraphBuildServiceImpl.java`
- Modify: `src/main/java/com/aseubel/yusi/service/lifegraph/impl/LifeGraphCognitionBridgeServiceImpl.java`
- Modify: `src/main/java/com/aseubel/yusi/service/lifegraph/LifeTimelineService.java`
- Modify: `src/main/java/com/aseubel/yusi/service/lifegraph/impl/CommunityInsightServiceImpl.java`
- Modify: `src/main/java/com/aseubel/yusi/service/lifegraph/impl/EmotionTimelineServiceImpl.java`
- Modify: `src/main/java/com/aseubel/yusi/service/lifegraph/LifeGraphMergeSuggestionService.java`
- Modify: `src/main/java/com/aseubel/yusi/service/agent/AgentGrowthService.java`
- Modify: `src/main/java/com/aseubel/yusi/service/match/impl/MatchProfileAssemblerImpl.java`
- Test: `src/test/java/com/aseubel/yusi/service/lifegraph/LifeGraphDataServiceVisibilityTest.java`
- Test: `src/test/java/com/aseubel/yusi/service/match/MatchProfileAssemblerVisibilityTest.java`

**Interfaces:**
- Graph snapshots, timelines, communities, emotion reads, merge suggestions, growth counts, and match summaries consume visible or matchable repository methods as appropriate.
- Ingestion name/alias lookups remain raw so cognition can update its derived state without accidentally exposing it to a consumer.

- [ ] **Step 1: Write failing read-path tests.**

```java
@Test
void fullGraphDoesNotReturnHiddenOrExpiredNodes() {
    when(entityRepository.findVisibleByUserId(eq("user-1"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(visibleEntity(1L))));
    when(entityRepository.countVisibleByUserId("user-1")).thenReturn(1L);

    GraphSnapshotDTO snapshot = service().getFullGraph("user-1", 0, 200);

    assertEquals(List.of(1L), snapshot.getNodes().stream().map(GraphSnapshotDTO.NodeDTO::getId).toList());
}
```

Add a match assembler test that only `matchAllowed=true` entities and Persona data reach the profile summary.

- [ ] **Step 2: Run the tests and confirm the old repository calls are still used.**

Run: `.\mvnw.cmd -q -Dtest=LifeGraphDataServiceVisibilityTest,MatchProfileAssemblerVisibilityTest test`

Expected: test setup fails or assertions show hidden data can still flow through the old raw repository methods.

- [ ] **Step 3: Update graph visualization and query paths.**

`LifeGraphDataService.getFullGraph` uses visible pages and `countVisibleByUserId`. `getGraphBfs` validates that the center and every expanded node are visible and only includes relations whose endpoints are in the visible node set. `LifeGraphQueryService.localSearch` filters aliases, seed entities, related entities, and mentions through visible entity IDs before composing Agent context.

- [ ] **Step 4: Update insight and growth paths.**

Replace raw entity reads in `LifeTimelineService`, `CommunityInsightServiceImpl`, `EmotionTimelineServiceImpl`, and `LifeGraphMergeSuggestionService` with visible queries. `AgentGrowthService` reports visible entity counts and breakdowns. Existing Persona consumers become filtered through `UserPersonaService.getUserPersona`.

- [ ] **Step 5: Update matching and cognition ingestion defaults.**

`MatchProfileAssemblerImpl` uses matchable Persona and matchable LifeGraph queries. `LifeGraphBuildServiceImpl` uses visible known entities for extraction context while retaining raw identity resolution for ingestion. `LifeGraphCognitionBridgeServiceImpl` sets `sourceType`, `sourceId` where available through mention records, `confidence=0.5`, `matchAllowed=false`, and `hidden=false` on new derived entities.

- [ ] **Step 6: Run focused tests and the backend compile.**

Run: `.\mvnw.cmd -q -Dtest=LifeGraphDataServiceVisibilityTest,MatchProfileAssemblerVisibilityTest test`

Expected: focused visibility tests pass.

Run: `.\mvnw.cmd -q -DskipTests compile`

Expected: exit code `0`.

- [ ] **Step 7: Commit the read-path changes.**

```text
git add src/main/java/com/aseubel/yusi/service/lifegraph/LifeGraphDataService.java src/main/java/com/aseubel/yusi/service/lifegraph/LifeGraphQueryService.java src/main/java/com/aseubel/yusi/service/lifegraph/impl/LifeGraphBuildServiceImpl.java src/main/java/com/aseubel/yusi/service/lifegraph/impl/LifeGraphCognitionBridgeServiceImpl.java src/main/java/com/aseubel/yusi/service/lifegraph/LifeTimelineService.java src/main/java/com/aseubel/yusi/service/lifegraph/impl/CommunityInsightServiceImpl.java src/main/java/com/aseubel/yusi/service/lifegraph/impl/EmotionTimelineServiceImpl.java src/main/java/com/aseubel/yusi/service/lifegraph/LifeGraphMergeSuggestionService.java src/main/java/com/aseubel/yusi/service/agent/AgentGrowthService.java src/main/java/com/aseubel/yusi/service/match/impl/MatchProfileAssemblerImpl.java src/test/java/com/aseubel/yusi/service/lifegraph/LifeGraphDataServiceVisibilityTest.java src/test/java/com/aseubel/yusi/service/match/MatchProfileAssemblerVisibilityTest.java
git commit -m "fix: enforce long-term memory visibility"
```

### Task 5: Remove the Duplicate Frontend Relationship Graph Route

**Files:**
- Modify: `frontend/src/App.tsx`
- Verify: `frontend/src/components/Diary.tsx`
- Verify: `frontend/src/pages/LifeGraph2D.tsx`

**Interfaces:**
- `/community` is the only frontend route rendering `LifeGraph2D`.
- `/api/lifegraph` remains unchanged for the graph page's data operations.

- [ ] **Step 1: Write the failing route audit.**

Run this read-only assertion before editing:

```powershell
$routeCount = (rg -n "path: '/lifegraph'" frontend/src/App.tsx | Measure-Object).Count
if ($routeCount -ne 1) { throw "Expected the current duplicate route to exist exactly once" }
```

- [ ] **Step 2: Remove only the duplicate route.**

Delete `{ path: '/lifegraph', element: <LifeGraph2D /> },` from `frontend/src/App.tsx`. Leave all internal `/lifegraph/...` API paths and `src/lib/lifegraph.ts` names unchanged. Keep the Diary shortcut pointing to `/community`.

- [ ] **Step 3: Run the route audit and TypeScript build.**

Run:

```powershell
$routeCount = (rg -n "path: '/lifegraph'" frontend/src/App.tsx | Measure-Object).Count
if ($routeCount -ne 0) { throw "The duplicate frontend route still exists" }
if ((rg -n "path: '/community'" frontend/src/App.tsx | Measure-Object).Count -ne 1) { throw "The canonical graph route is missing" }
```

Run: `pnpm build`

Expected: the route audit passes and the production build exits `0`.

- [ ] **Step 4: Commit the route cleanup.**

```text
git add frontend/src/App.tsx
git commit -m "refactor: remove duplicate relationship graph route"
```

### Task 6: Add Persona and Relationship Graph Controls to Memory Center

**Files:**
- Modify: `frontend/src/lib/api.ts`
- Create: `frontend/src/components/memory/PersonaMemoryPanel.tsx`
- Create: `frontend/src/components/memory/LifeGraphMemoryPanel.tsx`
- Modify: `frontend/src/pages/MemoryCenter.tsx`
- Modify: `frontend/src/i18n/locales/zh.json`
- Modify: `frontend/src/i18n/locales/en.json`
- Create: `frontend/src/lib/memoryCenter.ts`
- Test: `frontend/src/lib/memoryCenter.test.ts`

**Interfaces:**
- `personaMemoryApi.get/update/remove` maps to `/memory/persona`.
- `lifeGraphMemoryApi.get/update/remove` maps to `/memory/life-graph`.
- `MemoryCenter` sections are `'MID_TERM' | 'PERSONA' | 'RELATIONSHIP_GRAPH'`; no new route is introduced.

- [ ] **Step 1: Write failing frontend helper tests.**

Create pure helpers in `frontend/src/lib/memoryCenter.ts` and test them without rendering:

```ts
export type MemoryCenterSection = 'MID_TERM' | 'PERSONA' | 'RELATIONSHIP_GRAPH'

export const isLifecycleActive = (status: string) => status === 'ACTIVE'

export const canUseForMatching = (status: string, matchAllowed: boolean) =>
  isLifecycleActive(status) && matchAllowed
```

Test that hidden/expired/merged status is never matchable and that `ACTIVE + matchAllowed=true` is matchable.

- [ ] **Step 2: Run the frontend test and confirm the missing-helper failure.**

Run: `pnpm test src/lib/memoryCenter.test.ts`

Expected: the test fails because `memoryCenter.ts` and its exported helpers do not exist.

- [ ] **Step 3: Add API types and helpers.**

Add TypeScript interfaces mirroring the backend DTOs, including safe `sources` with `sourceId`, `sourceType`, `entryDate`, and `createdAt`, but no `snippet`. Add API methods with typed `ApiResponse<T>` wrappers and the pure lifecycle helpers.

- [ ] **Step 4: Implement the Persona panel.**

`PersonaMemoryPanel` fetches `/memory/persona`, displays the five safe Persona fields, source metadata, confidence, timestamps, expiry, and lifecycle state, and exposes icon-labeled controls for hide/restore, matching authorization, expiry, and delete confirmation. Save updates optimistically only after the API response returns the typed item.

- [ ] **Step 5: Implement the Relationship Graph panel.**

`LifeGraphMemoryPanel` fetches `/memory/life-graph`, displays entity type/name/summary, counts, confidence, safe source references, expiry and lifecycle status. It must not render mention snippets. Hide/restore, match authorization, expiry, and deletion use the typed API methods and preserve the current loading/error/toast patterns.

- [ ] **Step 6: Add section navigation to `MemoryCenter`.**

Keep the existing mid-term memory controls intact. Add a compact segmented control in the page body for `MID_TERM`, `PERSONA`, and `RELATIONSHIP_GRAPH`; render the corresponding panel without changing the URL. Keep the existing Diary link to `/memory-center`. Use the existing `Button`, `Checkbox`, `Badge`, `ConfirmDialog`, and icon conventions.

- [ ] **Step 7: Add Chinese and English translations.**

Add labels and messages for the two new panels, lifecycle states, source types, empty/loading/error states, matching scope, expiry, delete confirmation, and safe-source note in both locale files. Keep product copy as “关系图谱”; do not add “人生图谱” as a new product label.

- [ ] **Step 8: Run focused frontend tests and build.**

Run: `pnpm test src/lib/memoryCenter.test.ts`

Expected: helper tests pass.

Run: `pnpm build`

Expected: TypeScript compilation and Vite production build both exit `0`.

- [ ] **Step 9: Commit the Memory Center UI.**

```text
git add frontend/src/lib/api.ts frontend/src/components/memory/PersonaMemoryPanel.tsx frontend/src/components/memory/LifeGraphMemoryPanel.tsx frontend/src/pages/MemoryCenter.tsx frontend/src/i18n/locales/zh.json frontend/src/i18n/locales/en.json frontend/src/lib/memoryCenter.ts frontend/src/lib/memoryCenter.test.ts
git commit -m "feat: expose long-term memory controls"
```

### Task 7: Update Product Roadmap and Run Full Verification

**Files:**
- Modify: `docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md`
- Modify: `docs/engineering/records/2026-08-04-yusi-memory-transparency-lifecycle.md`

- [ ] **Step 1: Update the roadmap only after the implementation tests pass.**

Mark the Persona/LifeGraph transparency item complete, record that `/community` is the sole graph entry, and leave the unified insights hub as a later item. Do not mark unrelated Phase 2-6 items complete.

- [ ] **Step 2: Record the final lifecycle boundary.**

Extend the engineering record with Persona and existing Relationship Graph coverage, safe source references, filtering guarantees, deletion cleanup, and the explicit non-goal that original diaries/chats remain.

- [ ] **Step 3: Run the full backend test suite.**

Run: `.\mvnw.cmd -q test`

Expected: exit code `0` with no test failures.

- [ ] **Step 4: Run the full frontend suite.**

Run: `pnpm test`

Expected: exit code `0` with all tests passing.

Run: `pnpm build`

Expected: exit code `0`.

Run: `pnpm lint`

Expected: exit code `0` with no lint errors.

- [ ] **Step 5: Perform the route and secret-surface audits.**

Run:

```powershell
if ((rg -n "path: '/lifegraph'" frontend/src/App.tsx | Measure-Object).Count -ne 0) { throw "Duplicate frontend graph route remains" }
if ((rg -n "path: '/community'" frontend/src/App.tsx | Measure-Object).Count -ne 1) { throw "Canonical relationship graph route is missing" }
if (rg -n "snippet" src/main/java/com/aseubel/yusi/controller/MemoryCenterController.java src/main/java/com/aseubel/yusi/pojo/dto/memory) { throw "Raw LifeGraph snippets reached the memory-center DTO surface" }
```

Expected: all three checks pass. Internal `/api/lifegraph` calls are allowed and are not route compatibility aliases.

- [ ] **Step 6: Review the final diff and commit documentation.**

Run: `git diff --check`

Run: `git status --short`

Review every changed file against the roadmap and preserve unrelated user changes. Stage only the roadmap and engineering record for this final documentation commit.

```text
git add docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md docs/engineering/records/2026-08-04-yusi-memory-transparency-lifecycle.md
git commit -m "docs: record long-term memory transparency completion"
```
