# AI Chat Interruption Design

## Goal

Make cancellation of the AI chat stream immediate and end-to-end. A user
clicking Stop must stop the browser stream, cancel the active model stream on
the server, release the per-user AI lock, and allow the next message to start
without waiting for the original model response or the SSE timeout.

## Scope

This change applies to the main AI chat flow:

- Backend `POST /api/ai/chat/stream`.
- Backend authenticated cancellation endpoint.
- Frontend `ChatWidget`.
- The LangChain4j streaming-model proxy used by the chat assistant.

The existing chat history format and unrelated streaming agents are unchanged.
An interrupted assistant response remains visible in the current frontend
conversation, but it is not treated as a completed assistant response by the
backend. The current response is not persisted as a completed AI message.

## Architecture

Each chat request has a client-generated UUID carried in `ChatRequest`. The
backend keeps an in-memory active-request registry keyed by user ID and
request ID. An active request owns:

- an atomic cancelled flag;
- the `SseEmitter`;
- the latest LangChain4j `StreamingHandle`;
- idempotent cleanup state.

The stream endpoint registers the request before dispatching work to the
executor. The token stream uses `onPartialResponseWithContext` so the active
model handle is captured as soon as the provider starts streaming. The
registry cancellation operation marks the request cancelled, calls
`StreamingHandle.cancel()`, completes the SSE emitter, and releases the AI
lock. Every callback checks the cancelled flag before sending output or
running normal completion logic.

The SSE emitter's completion, timeout, and error callbacks all use the same
registry cancellation path. This means an explicit cancel request, a browser
abort, a network disconnect, and an SSE timeout have one cleanup implementation
and cannot leave the model or per-user lock running.

The model proxy must preserve `PartialResponseContext` when it wraps a
streaming response for sensitive-data unmasking. The current wrapper only
overrides the string callback, which discards the provider's real cancellation
handle. The contextual callback will unmask the partial text while forwarding
the original streaming context unchanged.

## API Contract

`ChatRequest` gains an optional `requestId` field for compatibility with older
clients. The frontend always sends a UUID. If a legacy request has no ID, the
server generates one before registration and still supports disconnect-based
cancellation.

Add an authenticated endpoint:

```text
POST /api/ai/chat/cancel
Content-Type: application/json
Authorization: Bearer eyJhbGciOi...

{"requestId":"9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6f"}
```

The endpoint cancels only an active request owned by the authenticated user.
Cancelling an already completed, already cancelled, or unknown request is
idempotent and returns success; it must not affect another user's request.

## Backend Lifecycle

1. Validate the message and image count, acquire the existing AI lock, create
   the emitter, and register the request.
2. Before model invocation, check cancellation. Abort without invoking the
   assistant if cancellation won the startup race.
3. Capture the current `StreamingHandle` from each contextual partial callback.
   The latest handle is used because tool calls can create another model round.
4. Send partial output only while the request is active.
5. On normal completion, run existing cleanup and remove the registry entry.
6. On cancellation, skip normal completion/persistence behavior, complete the
   emitter, release the lock, and remove the registry entry.
7. Make all cleanup idempotent so callbacks racing with an explicit cancel do
   not double-release or emit errors after cancellation.

If a provider supplies a cancellation handle that cannot cancel its underlying
transport, the server still marks the request cancelled and suppresses all
further output; the provider-specific handle is invoked whenever supported.
The configured OpenAI-compatible streaming model and LangChain4j 1.18
contextual streaming path are expected to close the active stream.

## Frontend Behavior

The frontend creates one request ID and one `AbortController` per send. Stop
immediately updates the UI, sends the authenticated cancellation request with
`keepalive`, then aborts the SSE reader. The cancel request uses its own
transport and must not reuse the stream abort signal.

The active request ID guards every asynchronous state update and `finally`
handler. An old request therefore cannot clear a newer request's controller or
streaming state. Partial output already displayed remains in the current
conversation, while the stopped request no longer accepts tokens.

## Error Handling

- A cancellation is not shown as a connection error.
- Cancellation races with model startup are handled by the active-request
  cancelled flag and a pre-invocation check.
- Cancellation races with normal completion resolve once through idempotent
  cleanup; at most one terminal path releases the lock and completes the SSE.
- Unknown request IDs do not disclose another user's active requests.
- A failed best-effort cancel HTTP request is still covered by abort-driven SSE
  completion on the server; the frontend does not wait for it before updating
  the UI.

## Testing

Backend unit tests cover:

- registering and finding a request by owner and ID;
- cancellation before a model handle is bound;
- cancellation after a handle is bound and exactly one handle cancellation;
- repeated cancellation and terminal cleanup being idempotent;
- owner isolation;
- contextual model-proxy callback forwarding without losing the streaming
  handle.

Frontend unit tests cover:

- sending a request ID in the stream body;
- issuing a cancel request with the same ID and auth token;
- preserving the partial response after Stop;
- preventing an older request's asynchronous cleanup from mutating a newer
  request's state.

Verification runs the focused backend tests, focused frontend tests, backend
compilation, and frontend TypeScript/Vite build. No development or production
service is started.

## Non-Goals

- Persisting an interrupted partial assistant response as a completed history
  message.
- Replacing SSE with WebSocket.
- Adding a distributed cancellation bus for requests whose cancel HTTP call is
  routed to a different backend instance.
