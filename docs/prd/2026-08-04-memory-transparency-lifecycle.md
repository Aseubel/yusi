# Yusi 记忆透明度与生命周期产品需求

**Status:** Implemented for Phase 1 slice
**Date:** 2026-08-04
**Product phase:** Phase 1, Memory Trust and Lifecycle

## Product Goal

让用户能够理解 Yusi 对自己的长期理解，并控制这些派生认知是否继续被 Agent、洞察和匹配使用。

用户至少应该能够回答三件事：

1. Agent 记住了什么。
2. 这份理解来自哪里。
3. 它是否会影响匹配，以及如何停止使用、调整有效期或删除。

## Scope

### Existing Slice: Mid-Term Memory

The first vertical slice covers `MidTermMemory` in the Memory Center. It supports safe summaries, source metadata, expiry, confidence, hide/restore, deletion, and independent matching authorization. Original diary and chat content remains outside the Memory Center.

### This Slice: Stable Persona and Relationship Graph

Extend the same trust boundary to:

- `UserPersona`: the stable, high-level understanding of the user's preferences and interaction needs.
- Existing LifeGraph relation data: entities, their safe diary evidence references, and the existing relationship graph visualization.

The Persona is controlled as one stable structure in this phase. Per-field provenance and per-relation lifecycle controls are deferred until the product demonstrates that users need that level of detail.

The implementation record is [记忆透明度与生命周期](../engineering/records/2026-08-04-yusi-memory-transparency-lifecycle.md). The roadmap item is complete for this slice; the unified insights hub remains a later product direction.

## User Capabilities

- View safe Persona values, source category, source ID, confidence, update time, expiry, and matching scope.
- Hide or restore the Persona.
- Change Persona expiry, matching authorization, and delete the current derived Persona.
- View LifeGraph entity names, types, summaries, mention counts, confidence, lifecycle state, and safe source references.
- Hide or restore one relationship-graph entity.
- Change an entity's expiry or matching authorization.
- Delete one derived relationship-graph entity and its derived aliases, relations, mentions, and merge records without deleting the original diary or chat.

## Privacy and Usage Rules

- Hidden or expired Persona data is not used in chat context, cognition conflict checks, reports, growth metrics, or matching.
- Hidden or expired relationship-graph entities are not used in graph visualization, timeline/insight queries, chat context, or matching.
- Matching authorization is independent: disabling it removes the item from matching while leaving other visible Agent uses available.
- Memory Center source references may show diary IDs, dates, and counts, but never original diary text or LifeGraph mention snippets.
- New derived Persona and LifeGraph records are not matchable until the user explicitly authorizes them.
- Existing records keep their current matching behavior during migration.
- Deleting derived understanding does not delete its source content. Later source processing may produce a new derived record from new input.

## Information Architecture

- `Memory Center` remains the control surface for lifecycle and privacy actions.
- `/community` remains the only frontend relationship graph route.
- `/lifegraph` is removed as a duplicate technical route and is not redirected.
- `/api/lifegraph` remains the existing backend API namespace and is not a product navigation entry.
- Timeline, Relationship Graph, Soul Report, and Agent Growth remain separate views in this implementation. A future unified insights entry may compose them with in-page navigation, but it is not part of this slice.

## Acceptance Criteria

- A user can inspect Persona and relationship-graph records without receiving raw diary, chat, prompt, or tool content.
- A user cannot update or delete another user's Persona or graph entity by changing an ID.
- Hiding or expiring a record prevents it from reaching the specified Agent and matching read paths.
- Disabling matching removes the record from the match profile without hiding it from other allowed visible uses.
- Deleting a graph entity removes its derived graph dependencies while preserving source diaries and chats.
- The frontend exposes one relationship graph entry, `/community`, and contains no `/lifegraph` page alias.
- The existing mid-term memory controls continue to work.

## Non-Goals

- A new "LifeGraph" or "人生图谱" product page.
- A generic fact table or field-level Persona provenance.
- Independent lifecycle controls for relation edges.
- Deleting source diaries or chat messages.
- Redesigning all insight pages into a unified hub in this implementation.

## Related Documents

- [Agent product roadmap](../engineering/plans/2026-08-04-yusi-agent-product-roadmap.md)
- [Engineering design](../superpowers/specs/2026-08-04-memory-transparency-long-term-design.md)
- [Implementation plan](../superpowers/plans/2026-08-04-long-term-memory-transparency.md)
- [Implementation record](../engineering/records/2026-08-04-yusi-memory-transparency-lifecycle.md)
