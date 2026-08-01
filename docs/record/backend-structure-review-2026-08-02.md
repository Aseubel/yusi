# Backend Structure Review (2026-08-02)

## Scope

This review covers the Java backend package tree under
`src/main/java/com/aseubel/yusi`:

- `common`
- `config`
- `controller`
- `grpc`
- `monitor`
- `pojo`
- `redis`
- `repository`
- `service`

The review is structural only. It does not change runtime behavior, API
contracts, persistence mappings, or the MCP boundary.

## Current Facts

The backend currently contains 137 service classes. Most business packages
already follow a domain-oriented layout:

```text
service/<domain>/
  public service contracts and domain services
  impl/                 interface implementations
```

The following packages are already coherent and should remain stable:

- `service/diary`
- `service/lifegraph`
- `service/match`
- `service/plaza`
- `service/room`
- `service/user`
- `service/agent`
- `service/developer`
- `service/geo`
- `service/key`
- `service/location`
- `service/stats`
- `service/suggestion`

The main structural problem is `service/ai`. It currently contains several
different ownership areas:

| Area | Current examples | Intended ownership |
| --- | --- | --- |
| Model control plane | `model/**` | `service/ai/model` |
| Speech adapters | `asr/**` | `service/ai/asr` |
| AI runtime state | `runtime/**` | `service/ai/runtime` |
| Agent tools | `tool/**` | `service/ai/tool` |
| Prompt access | `PromptManager`, `PromptService` | `service/ai/prompt` |
| Diary RAG and embeddings | `DiaryChunker`, `Embedding*`, retrieval assembler | `service/ai/rag` and `service/ai/embedding` |
| Memory cognition | `MemoryCompression*`, `MidTermMemorySearchService` | `service/memory` |
| Chat context | `ContextBuilderService` | `service/ai/chat` |
| Event-driven analysis | `EmotionAnalysisService` | `service/cognition` |

## Structural Findings

### 1. Technical and domain layers are mixed

`service/ai` is both a technical platform package and a business workflow
package. This makes controllers and domain services depend directly on model
implementation concepts such as `ModelRouteContext`.

### 2. Memory ownership is split

Memory behavior is currently distributed across:

- `service/ai/MemoryCompressionService`
- `service/ai/MidTermMemorySearchService`
- `service/memory/MidMemoryUpdateService`
- `service/cognition/MidMemoryFusionService`
- `service/cognition/CognitiveConflictDetector`

These classes should not be moved mechanically. The target boundary must
distinguish memory domain operations from AI adapters and cognition workflows.

### 3. Prompt ownership leaks into domain services

Multiple domain services import `service.ai.prompt.PromptManager` directly. Prompt
loading is an AI platform concern; domain services should depend on a stable
prompt port or an application-level cognition service rather than on the
concrete prompt manager.

### 4. Implementation layout is inconsistent inside `service/ai`

Most domains use `impl`, while `service/ai` historically placed concrete
services at its root. The `runtime`, `tool`, `prompt`, `rag`, `embedding`, and
`chat` moves establish the subpackage convention. Implementation classes now
live beside the capability they implement rather than in a shared AI
`impl` directory.

### 5. Non-service packages are comparatively stable

- `controller` is a transport/API layer.
- `grpc` contains the Java-side MCP boundary implementation.
- `repository` is persistence access and is already isolated.
- `pojo/dto` and `pojo/entity` are separated; `pojo/constant` now has the
  corrected spelling.
- `config/ai`, `config/oss`, `config/security`, and `config/jpa` are coherent
  configuration slices.
- `redis` is an infrastructure slice and should not be merged into
  `common`.

## Target Structure

The target is a domain-oriented service tree with an explicit AI platform
inside it:

```text
service/
  ai/
    asr/
    chat/
    embedding/          # embedding gateway and model-facing operations
    mask/
    model/
    prompt/
    rag/                # diary chunking and retrieval assembly
    runtime/
    tool/
  cognition/            # routing, conflict, fusion, multimodal analysis
  memory/               # memory lifecycle and domain-facing operations
  persona/
  diary/
  lifegraph/
  agent/
  user/
  ...
```

The exact split between `ai/rag`, `ai/embedding`, `memory`, and `cognition`
must follow dependency direction, not file names:

```text
controller / grpc
        |
application and domain services
        |
AI capability ports (prompt, embedding, chat, speech)
        |
provider adapters and model control plane
```

The MCP boundary remains unchanged:

```text
MCP/HTTP client -> Go MCP server -> gRPC -> Java internal capability
```

The Go MCP server is not a Java backend-only service.

## Migration Rules

1. Move one ownership slice at a time and update production/test package
   paths together.
2. A package move must remove all old imports and stale documentation paths.
3. Do not move entities, repositories, or controllers into service packages.
4. Keep interface contracts stable where they are consumed by controllers,
   gRPC, or configuration.
5. Introduce a port only when it removes a real dependency on provider/model
   implementation details.
6. Compile after each slice; do not batch unrelated behavior changes with
   package moves.
7. Keep the complete structure migration in one final commit after review.

## Migration Progress

The first ownership migration slice is now complete:

- Prompt contracts and implementation moved to `service/ai/prompt`.
- Diary chunking and retrieval assembly moved to `service/ai/rag`.
- Embedding gateway and embedding task services moved to
  `service/ai/embedding`.
- Memory compression, memory search, and the compression assistant moved to
  `service/memory`.
- Production and test package paths were updated together.

## Deferred Items

No remaining concrete class is currently stranded at the root of
`service/ai`. Further changes should focus on dependency direction and
capability ports rather than additional directory movement.

## Verification Baseline

At the time of this record:

- Java compile passes with `./mvnw -DskipTests compile`.
- Frontend build passes with `pnpm run build`.
- No application server was started.
