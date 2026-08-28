# Model Management Control Plane Implementation Plan

> **For agentic workers:** This plan is executed inline because the repository instructions prohibit sub-agents and auto-review.

**Goal:** Rebuild the model governance control plane so model priority/weight, tier strategies, risk-aware routes, draft previews, runtime health, and administrator resets are all represented by one clear and operationally accurate contract.

**Architecture:** Keep schema v2 as the only persisted governance format. Introduce a pure route planning projection that accepts an explicit configuration snapshot and runtime state snapshot, use the same planner for production routing and draft previews, and keep Redis as the short-lived runtime state source synchronized through the existing hash and topic. Expose a complete admin snapshot and reset endpoints, then make the console UI consume those projections as separate Overview, Models/Tiers, Routes, and Activity workflows.

**Tech Stack:** Java 21, Spring Boot, Redisson, MySQL/JPA, React 19, TypeScript, Vite, Tailwind CSS, lucide-react, existing admin/auth and i18n components.

## Global Constraints

- Do not retain the old console semantics or compatibility branches; schema v2 is the only governance payload.
- Do not add new test files. Run existing focused Maven tests, backend compilation, frontend TypeScript/lint/build, and static SQL checks.
- Never persist or expose API keys, prompts, responses, thinking/reasoning text, image URLs, tool arguments, or complete upstream response bodies.
- `route.priority` is descending route-match priority; `model.priority` is ascending `FAIL_OVER` priority; `model.weight` is used only by `WEIGHTED_RANDOM` and may be zero.
- `riskLevel` participates in route matching with `LOW`, `MEDIUM`, `HIGH`, and `*` only.
- Runtime reset changes only short-lived Redis health state and records a low-sensitivity administrator audit event.
- Before each commit, inspect `docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md`; do not tick deployment-only items without deployment evidence.
- Docker output location is out of scope; no Docker runtime changes are required.

---

### Task 1: Establish the shared route-planning contract

**Files:**
- Create: `src/main/java/com/aseubel/yusi/service/ai/model/ModelRoutePlanner.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/ModelRouteCandidate.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/ModelRouteDecision.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/ModelRoutePolicyMatcher.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/ModelRouterService.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/constant/ModelRouteExclusionReason.java`

**Interfaces:**
- `ModelRoutePlanner.plan(ModelRoutingProperties properties, ModelRouteContext context, Map<String, ModelRuntimeState> states)` returns a complete `ModelRouteDecision` without creating provider clients or reading secrets outside the supplied properties.
- `ModelRouteCandidate` adds `rank`, `fallback`, `strategy`, `priority`, `weight`, `avgLatencyMs`, `phase`, and a human-readable `exclusionExplanation` while preserving the existing production accessor methods used by `ModelProxyFactory`.
- `ModelRouteDecision` adds a structured `RouteReason` value containing matched route ID, scene match level, risk match level, route priority, primary tier, fallback tier order, and strategy order; `routeReason()` remains the low-sensitivity serialized trace value.

- [ ] **Step 1: Define the new candidate and route-reason records.**

  Add immutable fields with null-safe defaults. Keep `primaryEligible()` true only when `available` is true and `excludedReason` is null; fallback candidates remain marked with `fallback-tier` for the existing attempt-chain behavior.

- [ ] **Step 2: Move route matching into the planner boundary.**

  Update `ModelRoutePolicyMatcher.match(...)` to score scene specificity first, then risk specificity (`exact` before `*`; a missing request risk does not filter a route), then descending route priority, then normalized route ID. Reject invalid risk values before planning.

- [ ] **Step 3: Centralize tier ordering and exclusion explanations.**

  For every primary and fallback tier, calculate strategy ordering from the supplied members and state map. Filter by tier enabled, capability, scene declaration, token/context budget, runtime phase, and zero weight for weighted random. Add explicit `MODEL_DISABLED`, `MODEL_UNKNOWN`, `TIER_DISABLED`, `ZERO_WEIGHT`, and existing budget reasons where needed. Sort excluded members after attemptable members, preserving deterministic model ID order for ties.

- [ ] **Step 4: Make production routing call the planner.**

  Reduce `ModelRouterService.plan(...)` to normalize context, snapshot current states, and delegate to `ModelRoutePlanner`. Ensure the planner receives the effective config and that both primary and fallback tiers use their own strategy. Do not change `ModelProxyFactory` fallback eligibility rules in this task.

- [ ] **Step 5: Run focused existing routing checks.**

  Run: `./mvnw.cmd -Dtest=ModelRoutePolicyMatcherTest,ModelRouterServiceTest,RoundRobinSelectionStrategyTest,FailOverSelectionStrategyTest test`

  Expected: the existing tests either pass or fail only where old assertions encode the intentionally replaced route semantics; adjust those existing assertions in a later task only after the new planner is compiled.

### Task 2: Tighten schema validation and model identity rules

**Files:**
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/ModelConfigCenter.java`
- Modify: `src/main/java/com/aseubel/yusi/config/ai/properties/ModelRoutingProperties.java`
- Modify: `src/main/java/com/aseubel/yusi/config/ai/properties/RoutePolicyDefinition.java`
- Modify: `src/main/java/com/aseubel/yusi/config/ai/properties/ModelTierDefinition.java`
- Modify: `frontend/src/lib/modelRouting.ts`

**Interfaces:**
- `ModelConfigCenter.validate(...)` remains the final server-side validator and reports stable object paths such as `models[deepseek].weight` and `routes[chat].riskLevel` in `BusinessException` messages.
- `ModelConfigCenter.updateCanonical(...)` rejects a submitted model ID change by comparing every existing model ID against the current version.
- `validateGovernanceDraft(...)` returns object-scoped errors for duplicate IDs, invalid risk values, negative priority/weight, tier member references, and weighted tiers without a positive eligible member.

- [ ] **Step 1: Normalize and validate model scheduling fields.**

  Accept `weight >= 0`, `priority >= 0`, positive timeout/context limits, and unique trimmed IDs. Preserve server-side secret merge. Reject provider/protocol combinations that cannot be built by the existing adapter registry.

- [ ] **Step 2: Validate tier strategy invariants.**

  Require every tier member to exist exactly once and match tier capabilities. For `WEIGHTED_RANDOM`, require at least one enabled, scene-capable member with `weight > 0`; do not reject a zero-weight model globally.

- [ ] **Step 3: Validate route risk and tier availability.**

  Normalize risk values to uppercase and allow only `LOW`, `MEDIUM`, `HIGH`, `*`. Validate both primary and fallback tiers for enabled state and at least one enabled, scene-capable member. Require route IDs and model IDs to be stable; ordinary update requests cannot rename existing IDs.

- [ ] **Step 4: Align the browser draft validator and model editor assumptions.**

  Make the draft validator use the same risk, zero-weight, strategy, and identity rules. Preserve `MASKED_SECRET` handling and make `toUpdateRequest` send only schema v2 fields.

- [ ] **Step 5: Run existing configuration checks.**

  Run: `./mvnw.cmd -Dtest=ModelConfigCenterTest,ModelManagementServiceTest test`

  Expected: existing tests pass after only necessary assertion updates for zero-weight and stable IDs.

### Task 3: Implement runtime state reset and multi-instance convergence

**Files:**
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/ModelStateCenter.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/ModelStateEvent.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/ModelRuntimeState.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/constant/ModelStateAction.java`
- Modify: `src/main/java/com/aseubel/yusi/redis/common/RedisKey.java`
- Modify: `src/main/java/com/aseubel/yusi/pojo/constant/SecurityAuditAction.java`
- Modify: `src/main/java/com/aseubel/yusi/pojo/constant/SecurityAuditDetailKeys.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/ModelManagementService.java`
- Modify: `src/main/java/com/aseubel/yusi/controller/ModelManagementController.java`

**Interfaces:**
- `ModelStateCenter.reset(String instanceId)` resets one known state and returns the resulting `ModelRuntimeState`.
- `ModelStateCenter.resetAll(Collection<String> instanceIds)` resets the union of known Redis, local, and configured runtime IDs and returns the reset count.
- `ModelStateCenter.listStates()` reports only recorded states; the management projection is responsible for adding `UNKNOWN` rows for configured models with no state.
- `POST /api/model/states/{modelId}/reset` returns `{modelId, status, state}`.
- `POST /api/model/states/reset` returns `{scope: "all", count, status}`.

- [ ] **Step 1: Add reset state construction.**

  Reset phase to `UP`, availability true, counters/latency/error rate/QPS/consecutive counts to zero, `lastError` null, and `nextProbeAt` zero. Preserve instance ID and model name. Stamp `lastUpdatedAt` with `max(now, previous + 1)`.

- [ ] **Step 2: Persist reset before publishing.**

  Write the reset state to the Redis map first. Publish `ModelStateEvent(action=RESET)` afterward. Return a typed runtime exception for map write failure; if publish fails after the map write, log `operation=model_state_reset_publish` and return a successful “stored, convergence pending” result.

- [ ] **Step 3: Merge reset events into every local window.**

  Listener handling must use the event state freshness and apply reset fields even when the local window is currently `DOWN`. A stale event cannot overwrite a newer success/failure. Add an explicit state action code and avoid logging model secrets or error text.

- [ ] **Step 4: Add administrator service and controller operations.**

  Check the model ID against effective config for single reset, audit success/failure with `MODEL_RUNTIME_STATE_RESET`, `MODEL_GOVERNANCE`, resource ID model or `all`, and allow-listed details `operation`, `scope`, and `count`. Do not clear traces, Micrometer counters, configuration versions, or unrelated Redis keys.

- [ ] **Step 5: Update the existing authz contract baseline only if required.**

  Search `src/test/java/com/aseubel/yusi/security/AuthzCoverageContractTest.java` and `AuthzBoundaryMockMvcTest.java`. Add only the two new endpoint mappings to existing expected admin mappings if their contract is source-enumeration based; do not create a new test class.

### Task 4: Build a complete management projection and draft-aware preview API

**Files:**
- Create: `src/main/java/com/aseubel/yusi/pojo/dto/model/ModelRuntimeResetResponse.java`
- Create: `src/main/java/com/aseubel/yusi/pojo/dto/model/ModelRouteReason.java`
- Modify: `src/main/java/com/aseubel/yusi/pojo/dto/model/ModelGovernanceSnapshot.java`
- Modify: `src/main/java/com/aseubel/yusi/pojo/dto/model/ModelRoutePreviewRequest.java`
- Modify: `src/main/java/com/aseubel/yusi/pojo/dto/model/ModelRoutePreviewResponse.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/ModelManagementService.java`
- Modify: `src/main/java/com/aseubel/yusi/controller/ModelManagementController.java`
- Modify: `frontend/src/lib/api.ts`
- Modify: `frontend/src/lib/modelRouting.ts`

**Interfaces:**
- `GET /api/model/console` returns every configured model, including `UNKNOWN` runtime status, tier usage, route usage, priority/weight, recent low-sensitivity error, and a summary of UP/UNKNOWN/HALF_OPEN/DOWN.
- `POST /api/model/routes/preview` accepts `{scene, riskLevel, estimatedInputTokens, reservedOutputTokens, draft}` where `draft` is the schema v2 model/tier/route projection without secrets; it returns ranked candidates, strategy fields, exclusion code/explanation, route reason object, and warnings.
- The service uses a `ModelRoutingProperties` built from the draft and the pure planner; it never mutates `ModelConfigCenter`, creates provider clients, or accepts API keys in the preview payload.

- [ ] **Step 1: Extend the snapshot DTO.**

  Add model status/phase, consecutive failures, latency, last error, tier IDs, route IDs, and last updated time. Add tier strategy description, member counts by UNKNOWN/UP/HALF_OPEN/DOWN, and route projections containing route priority, risk, primary/fallback tier strategy and aggregate availability.

- [ ] **Step 2: Project missing runtime states as UNKNOWN.**

  Build `stateById` from the state center, but do not fabricate an `UP` `ModelRuntimeState`. Use explicit status fields in the DTO so unknown models are visible while the production planner still treats unrecorded state as attemptable cold start.

- [ ] **Step 3: Add a draft-to-properties conversion with secret rejection.**

  Reuse the schema v2 update DTO shape without `apikey`. Reject any preview JSON containing secret fields rather than silently accepting them. Merge no server secrets because preview only needs provider/model metadata and scheduling fields.

- [ ] **Step 4: Return structured route preview data.**

  Include rank, fallback flag, strategy, priority, weight, average latency, phase, available/attemptable, exclusion code, explanation, and route reason fields. Keep `routeReason` as a low-sensitivity string for existing trace consumers until the frontend switches to the structured field.

- [ ] **Step 5: Update the frontend API types and request path.**

  Add reset methods and new snapshot/preview fields to `frontend/src/lib/api.ts`. `ModelManagement.tsx` must send the current draft projection when previewing and mark preview stale whenever any relevant model, tier, route, or default route field changes.

### Task 5: Add the required database migration and engineering record

**Files:**
- Create: `src/main/resources/db/migration/V20260831__add_model_management_read_indexes.sql`
- Modify: `src/main/resources/db/init.sql`
- Create: `docs/record/2026-08-23-model-management-control-plane.md`
- Create: `docs/record/bugs/2026-08-23-model-management-control-plane.md`

**Interfaces:**
- The migration is additive and safe for the existing `model_call_trace` table:

  ```sql
  ALTER TABLE `model_call_trace`
      ADD KEY `idx_model_call_trace_model_created` (`model_id`, `created_at`),
      ADD KEY `idx_model_call_trace_scene_model_created` (`scene`, `model_id`, `created_at`);
  ```

- `init.sql` contains the same two keys in the `model_call_trace` definition.

- [ ] **Step 1: Add the migration script.**

  Use the exact additive SQL above and preserve the repository's existing migration naming convention. Do not add a runtime-state table.

- [ ] **Step 2: Align initialization SQL.**

  Add the same keys after the existing model trace keys in `init.sql`, without renaming or removing prior indexes.

- [ ] **Step 3: Record the behavior change and root causes.**

  Document the old UI/route semantics, the new reset contract, the migration execution order, and the low-sensitivity boundary. The bug record must mention risk matching, unknown-state projection, zero-weight validation, published-only preview, and missing reset convergence as fixed causes.

### Task 6: Rebuild the admin console information architecture

**Files:**
- Modify: `frontend/src/pages/admin/ModelManagement.tsx`
- Modify: `frontend/src/pages/admin/model-management/types.ts`
- Modify: `frontend/src/pages/admin/model-management/ModelGovernanceOverview.tsx`
- Modify: `frontend/src/pages/admin/model-management/RuntimeHealthPanel.tsx`
- Modify: `frontend/src/pages/admin/model-management/ModelRegistryPanel.tsx`
- Modify: `frontend/src/pages/admin/model-management/RouteList.tsx`
- Modify: `frontend/src/pages/admin/model-management/RoutePolicyEditor.tsx`
- Modify: `frontend/src/pages/admin/model-management/RoutePreview.tsx`
- Modify: `frontend/src/pages/admin/model-management/ModelCallActivity.tsx`
- Modify: `frontend/src/i18n/locales/zh.json`
- Modify: `frontend/src/i18n/locales/en.json`

**Interfaces:**
- Overview shows the configured model master table, not only runtime-created rows, with status, priority, weight, tier usage, route usage, latest error, and per-model/all reset controls.
- Models view separates model registration from tier management; model ID is disabled during edit, scheduling fields show their strategy scope, and tier members show actual priority/weight/latency.
- Routes view is scene-first and displays `scene + risk -> route priority -> primary tier/strategy -> ordered fallback tiers/strategies`; the route editor edits only route fields and never silently changes tier strategy.
- Route preview renders the draft candidate chain with rank and explicit exclusion explanations.

- [ ] **Step 1: Add reset state and confirmation flows.**

  Add a per-row reset icon button with tooltip and a single all-reset confirmation dialog. After reset, update local state immediately from the response and refresh the console snapshot; show separate messages for Redis write failure and publish convergence pending.

- [ ] **Step 2: Redesign the overview/health projection.**

  Include UNKNOWN in counts and rows, show recent error and last update, and keep refresh controls. Avoid nested decorative cards; use full-width table bands and compact repeated rows consistent with the existing admin design system.

- [ ] **Step 3: Make the model registry and tier editor explicit.**

  Add priority and weight columns, strategy scope helper text, model tier/route usage, stable-ID editing, zero-weight input support, and a tier management section for members and strategy. Prevent a new model form from mutating tier drafts before the model is saved.

- [ ] **Step 4: Simplify route editing.**

  Show risk wildcard as an explicit option, route priority with descending-match help, all fallback tiers with each tier's strategy, and links/actions to edit tiers in the tier view. Remove the primary-tier strategy mutation callback from the route editor.

- [ ] **Step 5: Render structured draft preview and activity context.**

  Replace semicolon-only route reason display with route/risk/priority/strategy labels while retaining a collapsible low-sensitivity raw reason if needed. Activity filters add model ID and status/error category where the backend already supports them.

- [ ] **Step 6: Update Chinese and English copy.**

  Add labels for UNKNOWN, reset semantics, priority/weight scope, strategy behavior, risk matching, candidate rank, exclusion explanations, and pending convergence. Do not add visible instructional paragraphs unrelated to the workflow.

- [ ] **Step 7: Run the existing frontend checks.**

  Run from `frontend`: `pnpm exec tsc -b`, `pnpm lint`, and `pnpm build`.

### Task 7: Verify, review, and commit implementation slices

**Files:**
- Inspect all changed files and generated diffs.
- Modify existing tests only when a contract assertion is invalidated by the approved semantic replacement.

- [ ] **Step 1: Run backend verification.**

  Run focused tests for model routing/config/controller and then the repository backend compile command available in `pom.xml`. Capture exit codes and failure counts; do not claim success from a partial log.

- [ ] **Step 2: Run frontend and static verification.**

  Run the frontend commands from Task 6, `git diff --check`, and a SQL text check confirming both new index names exist in the migration and `init.sql`.

- [ ] **Step 3: Inspect the final behavior against the acceptance list.**

  Confirm the console has visible priority/weight semantics, all configured models including UNKNOWN, scene/risk route chains, current-draft preview, per-model/all reset, and no secret/prompt/thinking data in DTOs or logs. Confirm reset does not modify config or trace data.

- [ ] **Step 4: Check roadmap before each implementation commit.**

  Re-read the model/observability and Phase 5 entries in `docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md`. Leave deployment-only checkboxes unchanged unless a matching deployment command and evidence exists.

- [ ] **Step 5: Commit the implementation.**

  Stage only the intended backend, frontend, migration, documentation, and necessary existing contract files. Commit with `feat: rebuild model management control plane` after fresh verification evidence is available.
