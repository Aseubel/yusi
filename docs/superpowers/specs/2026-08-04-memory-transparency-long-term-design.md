# Long-Term Memory Transparency and Lifecycle Design

**Status:** Approved for implementation
**Date:** 2026-08-04

## Goal

Extend the existing memory transparency slice from `MidTermMemory` to the stable user persona and existing LifeGraph relation data, so users can see where derived understanding came from and control whether it remains available to the Agent or matching system.

This slice also removes the duplicate technical LifeGraph route. It does not add a second "life graph" product page or introduce the future unified insights hub.

## Product Boundaries

The product-facing terms are:

- `Memory Center`: privacy and lifecycle controls for derived understanding.
- `Relationship Graph`: the existing graph visualization at `/community`.
- `Timeline`, `Soul Report`, and `Agent Growth`: existing insight views that remain separate for this slice.

`LifeGraph` is an internal data model behind the existing Relationship Graph, timeline, matching, and insight services. The implementation must not create a new `/lifegraph` page or a second graph model.

The existing `/community` route remains the only relationship graph route. The `/lifegraph` route is removed without a redirect or compatibility alias. Existing links are updated to use `/community` where necessary.

## Data Model

### UserPersona

Add row-level transparency and lifecycle metadata to `user_persona`:

- `source_type`: latest source category such as `DIARY`, `CHAT_SUMMARY`, `PLAZA`, `USER_EDIT`, or `UNKNOWN`.
- `source_id`: source business ID when available.
- `confidence`: normalized AI confidence in the current derived persona, from `0` to `1`.
- `match_allowed`: whether the persona may contribute to matching.
- `hidden`: whether the Agent must stop using the persona.
- `valid_until`: nullable expiry; `null` means no expiry.

Persona remains a single stable structure in this slice. Field-level provenance and field-level lifecycle are explicitly out of scope. Existing user-edit and cognition merge flows preserve lifecycle controls and update source metadata appropriately.

### LifeGraphEntity

Add lifecycle metadata to `life_graph_entity`:

- `confidence`: normalized confidence for the derived entity.
- `match_allowed`: whether the entity may contribute to matching.
- `hidden`: whether the entity is unavailable to Agent reads and graph-derived insights.
- `valid_until`: nullable expiry.

Existing `life_graph_mention` rows are the source evidence. The transparency API returns source type, diary ID, date, and aggregate counts only; it never returns mention snippets or original diary content.

Deleting an entity removes its derived aliases, relations, mentions, and merge records in one user-scoped transaction. Original diaries and chat messages are not deleted.

## Lifecycle Semantics

- Active and unexpired data can be used according to its usage scope.
- Hidden or expired Persona data is excluded from chat context, cognition conflict checks, reports, growth metrics, and matching.
- Hidden or expired LifeGraph entities are excluded from graph visualization, timeline/insight queries, chat context, and matching.
- `match_allowed=false` only excludes a record from matching; it may still be used by other visible Agent features.
- User deletion removes the current derived record. Later source processing may create a new derived record from new input; the original source remains untouched.
- Every update and delete operation verifies `id + userId` ownership.
- Lifecycle changes that affect matching trigger a best-effort match-profile refresh. Refresh failure must not roll back the user's lifecycle choice.

Runtime repositories expose explicit visible and matchable query methods. Ingestion code may use raw ownership/name lookups to update cognition, but user-facing and Agent-consuming paths must use the filtered methods.

## API Surface

Extend the existing authenticated `/api/memory` boundary:

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/memory/persona` | Return the current user's safe persona summary and lifecycle metadata. |
| `PATCH` | `/api/memory/persona` | Update allowed persona fields and lifecycle controls. |
| `DELETE` | `/api/memory/persona` | Delete the derived persona row for the current user. |
| `GET` | `/api/memory/life-graph?limit=50` | Return safe entity summaries and source references. |
| `PATCH` | `/api/memory/life-graph/{id}` | Update lifecycle controls for one owned entity. |
| `DELETE` | `/api/memory/life-graph/{id}` | Delete one owned derived entity and dependent graph rows. |

Responses expose summaries, labels, confidence, timestamps, lifecycle state, matching scope, and safe source references. They do not expose raw diary text, full chat content, prompts, tool arguments, or tool results.

## Frontend Information Architecture

This implementation adds no new frontend route.

- `MemoryCenter` remains the control surface for mid-term memory and gains stable persona and LifeGraph sections or tabs.
- `LifeGraph2D` remains the visualization surface at `/community`; its data is filtered by lifecycle state.
- `/lifegraph` is removed and is not redirected.
- The future unified insight entry is deferred. When implemented, it should compose timeline, relationship graph, reports, and growth through deliberate in-page navigation rather than adding more top-level routes.

## Implementation Units

- Entity fields and migrations for Persona and LifeGraph lifecycle metadata.
- Repository queries for visible, matchable, and owned data.
- Persona and LifeGraph lifecycle services with DTOs and controller endpoints.
- Cognition, chat, reports, timeline, community, growth, and matching read-path updates.
- Memory Center API types and UI controls; existing relationship graph route cleanup.
- Focused service tests for ownership, filtering, lifecycle updates, deletion cleanup, and match refresh behavior.

## Verification

Backend:

- `./mvnw -q -DskipTests compile`
- focused lifecycle tests
- full `./mvnw -q test`

Frontend:

- `pnpm build`
- `pnpm test`
- `pnpm lint`

The implementation is complete only when the route audit confirms that `/community` is the sole relationship graph entry and no `/lifegraph` compatibility route remains.

## Non-Goals

- A new LifeGraph or "人生图谱" page.
- A generic fact table or field-level Persona provenance.
- Independent lifecycle controls for relation edges.
- Deleting original diaries or chat messages.
- The unified insights hub and its visual redesign.
- Redirects or compatibility aliases for removed routes.
