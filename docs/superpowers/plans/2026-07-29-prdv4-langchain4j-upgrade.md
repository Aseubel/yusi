# PRD v4 and LangChain4j Upgrade Implementation Plan

**Goal:** Align the project with LangChain4j 1.18, finish the remaining Phase 3 integration work, and update v4 documentation while retaining Phase 4 and Phase 5.

**Architecture:** Keep the existing Spring service boundaries and MCP integration. Use the approved situation scenarios as a repository-backed recommendation source, with deterministic keyword scoring and the current fallback behavior. Treat LangChain4j 1.18 agentic and multimodal APIs as documented follow-up architecture, not as an unrelated rewrite.

**Tech Stack:** Java 21, Spring Boot 3.4.5, Spring Data JPA, LangChain4j 1.18.0, LangChain4j community 1.18.0-beta28, Maven.

---

### Task 1: Upgrade LangChain4j dependency line

**Files:**
- Modify: `pom.xml`
- Inspect: `src/main/java/com/aseubel/yusi/config/ai`, `src/main/java/com/aseubel/yusi/service/ai`

- [x] Set core and OpenAI modules to `1.18.0`.
- [x] Set community, Spring Boot, Easy RAG, DashScope, and MCP modules to `1.18.0-beta28`.
- [x] Keep existing public interfaces unless 1.18 source inspection proves an API adjustment is required.
- [x] Record request/response embeddings, multimodal AI Services, and agentic Human-in-the-Loop/BDI as follow-up architecture recommendations.

### Task 2: Finish Phase 3 scenario recommendation

**Files:**
- Modify: `src/main/java/com/aseubel/yusi/repository/SituationScenarioRepository.java`
- Modify: `src/main/java/com/aseubel/yusi/service/match/ConnectionGuideService.java`
- Test: `src/test/java/com/aseubel/yusi/service/match/ConnectionGuideServiceTest.java`

- [x] Add a focused unit test for selecting an approved scenario matching both profiles.
- [x] Add a focused unit test for returning null when profiles or approved scenarios are insufficient.
- [x] Load only approved scenarios and score title/description token overlap against both profiles.
- [x] Preserve the current non-null fallback recommendation when both profiles have usable mid-memory but no scenario matches.

### Task 3: Finish Phase 3 feedback TODO without inventing unavailable data

**Files:**
- Modify: `src/main/java/com/aseubel/yusi/service/match/MatchFeedbackService.java`
- Modify: `src/main/java/com/aseubel/yusi/service/match/impl/MatchServiceImpl.java`

- [x] Add explicit `INTERACT` and `REPORT` recording methods with input validation.
- [x] Keep recommendation action handling backward compatible for ACCEPT/SKIP.
- [x] Add interaction-depth weighting to the preference context and preserve strong-negative reporting behavior.
- [x] Remove the stale TODO that claims reason extraction is possible without reason data in `MatchFeedback`.

### Task 4: Update product and architecture documentation

**Files:**
- Modify: `docs/prd/prd_v4.md`
- Modify: `docs/design/backend-design.md`

- [x] Mark Phase 3 F9.3 complete and describe the repository-backed matching behavior.
- [x] Keep Phase 4 and Phase 5 unchanged as future roadmap items.
- [x] Update the dependency version statement.
- [x] Add concise 1.18 architecture recommendations with adoption boundaries.

### Task 5: Static verification

- [x] Search for stale LangChain4j versions and Phase 3 TODO markers.
- [x] Inspect the final diff and source signatures.
- [x] Do not start the application, run Maven build/test, or claim compilation success.
