# Model Gateway Responses and Observability Implementation Plan

**Goal:** Make configured Responses models actually use the Responses API, prevent incompatible request fields and historical reasoning from causing 400 responses, expose actionable low-sensitivity upstream errors, and verify every configured tier's routing behavior.

**Architecture:** Preserve the existing runtime configuration precedence and provider abstraction, but make protocol-specific request construction explicit. Route text and image conversations to capability-correct tiers, enrich the request/failure context at the gateway boundary, merge health state by freshness, and reject invalid weighted/routing configurations before they can affect traffic.

**Tech Stack:** Java 21, Spring Boot, LangChain4j 1.18.0, YAML model gateway configuration, Redis/MySQL runtime configuration, Maven Wrapper, Docker Compose.

## Global Constraints

- Do not add new tests; run and rely on the existing test suite and compile checks.
- Preserve low-sensitivity logging: never log prompts, completions, reasoning text, image URLs, tool arguments, or API keys.
- Preserve runtime configuration precedence: MySQL active configuration, then Redis canonical configuration, then YAML defaults.
- Include all requested changes, including `yusi_prod.env.example`, in the final commit.
- Check the roadmap before committing; do not mark deployment-only acceptance items complete without deployment evidence.

### Task 1: Trace the model request and configuration boundaries

**Files:**
- Inspect `src/main/java/com/aseubel/yusi/service/ai/model/ModelConfigCenter.java`
- Inspect `src/main/java/com/aseubel/yusi/service/ai/model/ModelProxyFactory.java`
- Inspect `src/main/java/com/aseubel/yusi/service/ai/model/ModelRouterService.java`
- Inspect `src/main/java/com/aseubel/yusi/service/ai/model/ModelStateCenter.java`
- Inspect `src/main/java/com/aseubel/yusi/service/ai/model/strategy/*.java`
- Inspect `src/main/java/com/aseubel/yusi/controller/AiController.java`
- Inspect `src/main/resources/application-prod.yml`, `src/main/resources/application-dev.yml`, and `yusi_prod.env.example`

- [x] Record the request path, effective configuration precedence, provider endpoint construction, exception propagation, and each tier's members and strategy before editing.

### Task 2: Align text/image routing and Responses configuration

**Files:**
- Modify `src/main/java/com/aseubel/yusi/controller/AiController.java`
- Modify `src/main/resources/application-prod.yml`
- Modify `src/main/resources/application-dev.yml`
- Modify `yusi_prod.env.example`

- [x] Route image requests through the image-understanding scene and ensure the vision tier contains only models declaring `VLM`.
- [x] Configure DeepSeek's vision model with `RESPONSES`, `deepseek-v4-flash-vision-exp`, and the required endpoint environment variables.
- [x] Keep text-only traffic on the text tier and preserve explicit runtime overrides.

### Task 3: Make protocol-specific request construction compatible

**Files:**
- Modify `src/main/java/com/aseubel/yusi/service/ai/model/ModelProxyFactory.java`
- Inspect the installed LangChain4j Responses request/parameter types before editing.

- [x] Strip historical reasoning/thinking content when constructing Responses input while preserving supported text, image, and tool-call content.
- [x] Pass only Responses-compatible generation parameters and keep tool/response-format options intact.
- [x] Ensure streaming and non-streaming paths use the same protocol-aware behavior.

### Task 4: Improve failure observability and state freshness

**Files:**
- Modify `src/main/java/com/aseubel/yusi/service/ai/model/ModelProxyFactory.java`
- Modify `src/main/java/com/aseubel/yusi/service/ai/model/ModelInvocationErrorClassifier.java`
- Modify `src/main/java/com/aseubel/yusi/service/ai/model/ModelStateCenter.java`
- Inspect related model trace/logging classes for duplicate or unsafe output.

- [x] Extract a bounded, low-sensitivity upstream error summary containing HTTP status and provider error metadata without sensitive request/response content.
- [x] Include protocol, model, endpoint, tier, strategy, and message/image/tool counts in gateway failure and completion logs.
- [x] Merge local and Redis runtime states by `lastUpdatedAt` so stale remote state cannot overwrite a newer local failure/success.

### Task 5: Validate and correct tier load balancing

**Files:**
- Modify `src/main/java/com/aseubel/yusi/service/ai/model/ModelConfigCenter.java`
- Modify `src/main/java/com/aseubel/yusi/service/ai/model/strategy/WeightedRandomSelectionStrategy.java`
- Modify related strategy classes only where the scan proves a behavior defect.

- [x] Reject non-positive weights, invalid priorities/timeouts, duplicate tier members, and capability/tier mismatches at configuration validation time.
- [x] Ensure weighted random never selects zero-weight members and remains deterministic enough for the existing routing contract.
- [x] Verify round-robin, least-latency, failover, and weighted-random behavior against actual configured members and health filtering.

### Task 6: Document, verify, and commit

**Files:**
- Create a bug record under `docs/record/bugs/`
- Create an engineering record under `docs/record/`
- Do not modify `docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md` unless an existing item is genuinely completed by deployment evidence.

- [x] Run existing relevant tests, compile, and `git diff --check`; compile passed and the focused existing suite passed 37/37. The Docker CLI is unavailable in this environment, so Compose parsing and provider/deployment verification remain deployment-only.
- [x] Recheck the roadmap item status before staging; no deployment-only roadmap item was marked complete.
- [x] Commit all intended changes, including the env example, in one commit.
