# AI Chat Interruption Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make stopping the web AI chat immediately cancel the browser stream, the active LangChain4j model stream, the SSE emitter, and the per-user AI lock.

**Architecture:** Add a request-scoped cancellation registry keyed by authenticated user and client-generated request ID. The stream endpoint captures LangChain4j's contextual `StreamingHandle`; explicit cancellation and SSE disconnects share an idempotent terminal cleanup path. The frontend uses a small request controller to isolate concurrent request state and sends a best-effort authenticated cancel request before aborting the reader.

**Tech Stack:** Spring MVC `SseEmitter`, LangChain4j 1.18 `TokenStream`/`StreamingHandle`, Java 21/JUnit 5/Mockito, React 19/TypeScript, Vitest, pnpm.

---

## File Map

- Create `src/main/java/com/aseubel/yusi/service/ai/ChatStreamCancellationRegistry.java`: owns active chat sessions, cancellation state, model handle binding, emitter termination, and idempotent cleanup.
- Create `src/main/java/com/aseubel/yusi/pojo/dto/chat/ChatCancelRequest.java`: request body for the authenticated cancel endpoint.
- Modify `src/main/java/com/aseubel/yusi/pojo/dto/chat/ChatRequest.java`: carry the optional client request ID.
- Modify `src/main/java/com/aseubel/yusi/controller/AiController.java`: register stream sessions, capture contextual handles, suppress post-cancel output, expose the cancel endpoint, and unify terminal cleanup.
- Modify `src/main/java/com/aseubel/yusi/service/ai/model/ModelProxyFactory.java`: preserve response, thinking, and tool-call streaming contexts through the masking wrapper.
- Create `src/test/java/com/aseubel/yusi/service/ai/ChatStreamCancellationRegistryTest.java`: focused registry lifecycle and race tests.
- Create `src/test/java/com/aseubel/yusi/service/ai/model/ModelProxyFactoryTest.java`: verify masked streaming callbacks preserve cancellation context.
- Create `src/test/java/com/aseubel/yusi/controller/AiControllerCancellationTest.java`: verify the authenticated cancel method delegates only the current user's request ID.
- Create `frontend/src/lib/chatStream.ts`: request ID creation, active request ownership, stale-finally protection, and cancel request transport.
- Create `frontend/src/lib/chatStream.test.ts`: frontend cancellation and request-state unit tests.
- Modify `frontend/src/components/ChatWidget.tsx`: send request IDs, call the backend cancel endpoint, abort the reader, and guard async state by request ID.
- Modify `frontend/package.json` and `frontend/pnpm-lock.yaml`: add the Vitest test script and locked dev dependency.

### Task 1: Add the backend cancellation session and registry

**Files:**
- Create: `src/main/java/com/aseubel/yusi/service/ai/ChatStreamCancellationRegistry.java`
- Test: `src/test/java/com/aseubel/yusi/service/ai/ChatStreamCancellationRegistryTest.java`

- [ ] **Step 1: Write failing lifecycle tests**

  Test that a registered session can be cancelled before a model handle is
  bound, that a handle bound after cancellation is cancelled immediately, and
  that a bound handle is cancelled exactly once. Add tests for repeated
  cancellation, normal completion not becoming cancellation, owner/request ID
  isolation, emitter completion, and cleanup callback invocation once.

- [ ] **Step 2: Run the focused test to verify the expected failure**

  Run: `./mvnw -Dtest=ChatStreamCancellationRegistryTest test`

  Expected: compilation failure because the registry/session API does not yet
  exist.

- [ ] **Step 3: Implement the minimal registry/session API**

  Use a `ConcurrentHashMap` keyed by `(userId, requestId)`. Each session uses
  atomic state and an `AtomicReference<StreamingHandle>`. `cancel()` must
  transition only from active, cancel the current handle if present, complete
  the emitter, run cleanup, and remove itself from the registry. `bind()` must
  cancel a handle immediately when the session was already cancelled, closing
  the bind/cancel race. `complete()` and `fail()` must be terminal and
  idempotent without invoking model cancellation for a normal completion.

- [ ] **Step 4: Run the focused test to verify it passes**

  Run: `./mvnw -Dtest=ChatStreamCancellationRegistryTest test`

  Expected: all registry tests pass.

### Task 2: Preserve LangChain4j streaming cancellation context

**Files:**
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/ModelProxyFactory.java`
- Test: `src/test/java/com/aseubel/yusi/service/ai/model/ModelProxyFactoryTest.java`

- [ ] **Step 1: Write the failing proxy-context test**

  Build a mocked selected streaming model and a masked `MaskResult`. Invoke the
  proxy with a contextual partial response containing a fake
  `StreamingHandle`. Assert that the downstream handler receives the unmasked
  text and the exact original `PartialResponseContext`/handle. Add equivalent
  assertions for thinking and partial tool-call contexts where the proxy
  currently relies on interface defaults.

- [ ] **Step 2: Run the focused test to verify it fails for the right reason**

  Run: `./mvnw -Dtest=ModelProxyFactoryTest test`

  Expected: failure because the masking wrapper currently downgrades the
  contextual callback and the downstream handle is replaced or lost.

- [ ] **Step 3: Implement contextual forwarding**

  Override the contextual response callback in the wrapper, create a new
  `PartialResponse` only for unmasked text, and pass the original context
  unchanged. Forward thinking and partial tool-call contextual callbacks
  unchanged so cancellation can be captured before the first text token.

- [ ] **Step 4: Run the focused test and the existing model tests**

  Run: `./mvnw -Dtest=ModelProxyFactoryTest,FailOverSelectionStrategyTest,RoundRobinSelectionStrategyTest test`

  Expected: all selected tests pass.

### Task 3: Wire request IDs, controller lifecycle, and cancel endpoint

**Files:**
- Modify: `src/main/java/com/aseubel/yusi/pojo/dto/chat/ChatRequest.java`
- Create: `src/main/java/com/aseubel/yusi/pojo/dto/chat/ChatCancelRequest.java`
- Modify: `src/main/java/com/aseubel/yusi/controller/AiController.java`
- Test: `src/test/java/com/aseubel/yusi/controller/AiControllerCancellationTest.java`

- [ ] **Step 1: Write the failing controller cancellation test**

  Instantiate the controller with mocked constructor dependencies, inject a
  registry containing sessions for two users, set `UserContext` to one user,
  and call the cancel method with both request IDs. Assert that only the
  current user's handle is cancelled and that repeated/unknown cancellation is
  successful and harmless.

- [ ] **Step 2: Run the focused test to verify it fails**

  Run: `./mvnw -Dtest=AiControllerCancellationTest test`

  Expected: compilation failure because the cancel request type and controller
  endpoint do not yet exist.

- [ ] **Step 3: Add request DTOs and controller registration**

  Add nullable `requestId` to `ChatRequest` and a non-sensitive request ID to
  `ChatCancelRequest`. Generate a UUID server-side when a legacy stream request
  omits it. Acquire the existing AI lock, create the emitter, create/register a
  session before executor dispatch, and attach emitter completion, timeout, and
  error callbacks to the session.

- [ ] **Step 4: Connect the contextual token callbacks**

  Use `onPartialResponseWithContext`, `onPartialThinkingWithContext`, and
  `onPartialToolCallWithContext` to bind the latest handle. Check session
  activity before invoking `emitter.send`. If cancellation won before model
  invocation, return without calling the assistant. On normal completion call
  `complete`; on cancellation or cancellation-shaped model errors avoid error
  output and use the same cleanup path.

- [ ] **Step 5: Add and expose the authenticated cancel endpoint**

  Add `POST /api/ai/chat/cancel` with `@Auth`, read the current user from
  `UserContext`, cancel only that user's request ID, and return the repository's
  normal `Response.success()` shape. Keep cancellation idempotent and do not
  reveal whether another user's request exists.

- [ ] **Step 6: Run backend cancellation tests**

  Run: `./mvnw -Dtest=ChatStreamCancellationRegistryTest,ModelProxyFactoryTest,AiControllerCancellationTest test`

  Expected: all focused backend cancellation tests pass.

### Task 4: Add frontend request ownership and cancel transport

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/pnpm-lock.yaml`
- Create: `frontend/src/lib/chatStream.ts`
- Test: `frontend/src/lib/chatStream.test.ts`

- [ ] **Step 1: Add the frontend test command and dependency**

  Add `"test": "vitest run"` and a compatible Vitest dev dependency, then run
  `pnpm install --lockfile-only` from `frontend` to update the lockfile without
  starting Vite or any application service.

- [ ] **Step 2: Write failing pure unit tests**

  Test that a request controller starts an active request, `stop()` returns and
  clears only that request, `finish(oldRequestId)` cannot clear a newer request,
  and `cancelChatStream` sends `POST /api/ai/chat/cancel` with the request ID,
  bearer token, JSON content type, and `keepalive: true`. Mock `fetch` only at
  the transport boundary.

- [ ] **Step 3: Run the frontend tests to verify they fail**

  Run: `pnpm test -- src/lib/chatStream.test.ts`

  Expected: module/export failures because the request controller and cancel
  helper do not yet exist.

- [ ] **Step 4: Implement the request helper**

  Add a small controller that owns the active `{requestId, AbortController}`.
  Make `stop()` detach the active request immediately, and make `finish()`
  return false for stale request IDs. Add the best-effort cancel fetch helper
  with its own transport and swallowed network failure.

- [ ] **Step 5: Run the frontend unit tests**

  Run: `pnpm test -- src/lib/chatStream.test.ts`

  Expected: all focused frontend cancellation tests pass.

### Task 5: Integrate the frontend helper into `ChatWidget`

**Files:**
- Modify: `frontend/src/components/ChatWidget.tsx`
- Test: `frontend/src/lib/chatStream.test.ts`

- [ ] **Step 1: Extend tests for stream request payload and stop semantics**

  Add helper-level assertions for the request payload containing the same
  request ID used by cancellation and for retaining non-empty partial content
  after a stopped request. Keep the React component change limited to wiring
  the tested helper and request guards.

- [ ] **Step 2: Add request IDs and stale-request guards**

  Generate one ID per `handleSend`, create one abort controller, include the ID
  in the `/ai/chat/stream` body, and update the active request controller. Guard
  token updates, error updates, and `finally` cleanup by the request ID so an
  old stream cannot mutate a newer stream's global state.

- [ ] **Step 3: Replace the current stop handler**

  Detach the active request, set the UI to non-streaming immediately, invoke
  the authenticated cancel helper with `void`, and abort the SSE reader. Keep
  the current partial assistant message when it has content and suppress the
  normal error toast for `AbortError` caused by an intentional stop.

- [ ] **Step 4: Run the frontend focused tests and TypeScript check**

  Run: `pnpm test -- src/lib/chatStream.test.ts` and `pnpm exec tsc -b`

  Expected: tests pass and TypeScript reports no errors.

### Task 6: Full verification without starting services

**Files:**
- No new source files; inspect only the targeted diffs and test outputs.

- [ ] **Step 1: Run the focused and related backend unit tests**

  Run: `./mvnw -Dtest=ChatStreamCancellationRegistryTest,ModelProxyFactoryTest,AiControllerCancellationTest,FailOverSelectionStrategyTest,RoundRobinSelectionStrategyTest test`

  Expected: all selected tests pass.

- [ ] **Step 2: Compile the backend without tests**

  Run: `./mvnw -DskipTests compile`

  Expected: Maven compilation succeeds without launching Spring Boot.

- [ ] **Step 3: Run the complete frontend unit suite and production build**

  Run from `frontend`: `pnpm test` and `pnpm build`

  Expected: Vitest passes and `tsc -b && vite build` succeeds. Do not run
  `pnpm dev`, `pnpm preview`, Docker Compose, or the backend application.

- [ ] **Step 4: Review the final diff and workspace scope**

  Run: `git diff --check`, `git status --short`, and `git -C frontend diff --check`.

  Confirm that only the approved design/implementation files were changed by
  this task and that pre-existing unrelated worktree modifications remain
  untouched.
