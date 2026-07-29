# Phase 4 Completion Implementation Plan

**Goal:** Complete Yusi v4 Phase 4 with usable voice diaries, image cognition, scoped developer API keys, and MCP permission enforcement.

**Architecture:** Preserve the existing flow where the Go MCP server exposes tools and calls Java internal capabilities over gRPC. Add capability checks at the Java gRPC boundary. Treat voice transcription and image understanding as provider adapters feeding the existing diary and cognition event paths; raw media remains in OSS and only derived text enters cognition.

**Tech Stack:** Spring Boot 3.4.5, Java 21, LangChain4j 1.18.0, Spring MVC, MySQL/Flyway, React/TypeScript, Go MCP server, gRPC.

---

### Task 1: Add Phase 4 persistence and API contracts

**Files:**
- Modify: `src/main/java/com/aseubel/yusi/pojo/entity/Diary.java`
- Modify: `src/main/java/com/aseubel/yusi/pojo/entity/DeveloperConfig.java`
- Modify: `src/main/java/com/aseubel/yusi/pojo/dto/diary/WriteDiaryRequest.java`
- Modify: `src/main/java/com/aseubel/yusi/pojo/dto/diary/EditDiaryRequest.java`
- Modify: `src/main/java/com/aseubel/yusi/pojo/dto/developer/DeveloperConfigVO.java`
- Create: `src/main/java/com/aseubel/yusi/pojo/dto/diary/VoiceDiaryResponse.java`
- Create: `src/main/java/com/aseubel/yusi/pojo/dto/developer/DeveloperScopeUpdateRequest.java`
- Create: `src/main/resources/db/migration/V20260729__complete_phase4.sql`

- [x] Add `audioObjectKey` to diaries.
- [x] Add developer scopes and revocation timestamp.
- [x] Add migration SQL and update `init.sql`.

### Task 2: Implement voice diary

**Files:**
- Modify: `src/main/java/com/aseubel/yusi/service/oss/OssService.java`
- Create: `src/main/java/com/aseubel/yusi/config/ai/properties/SpeechRecognitionProperties.java`
- Create: `src/main/java/com/aseubel/yusi/service/diary/VoiceTranscriptionService.java`
- Create: `src/main/java/com/aseubel/yusi/service/diary/impl/OpenAiCompatibleVoiceTranscriptionService.java`
- Modify: `src/main/java/com/aseubel/yusi/controller/DiaryController.java`
- Modify: `src/main/java/com/aseubel/yusi/resources/application*.yml`

- [x] Upload supported audio types to OSS.
- [x] Call configurable OpenAI-compatible transcription endpoint.
- [x] Return transcript and audio object key.
- [x] Preserve existing diary write and cognition flow.

### Task 3: Implement image cognition

**Files:**
- Modify: `src/main/java/com/aseubel/yusi/pojo/dto/cognition/CognitionIngestCommand.java`
- Create: `src/main/java/com/aseubel/yusi/service/cognition/ImageUnderstandingService.java`
- Create: `src/main/java/com/aseubel/yusi/service/cognition/impl/LangChainImageUnderstandingService.java`
- Modify: `src/main/java/com/aseubel/yusi/service/diary/impl/DiaryServiceImpl.java`
- Modify: `src/main/java/com/aseubel/yusi/service/cognition/impl/AgentCognitionOrchestratorImpl.java`

- [x] Carry image object keys only in the post-commit cognition command.
- [x] Describe images with LangChain4j `ImageContent`.
- [x] Append descriptions to masked cognition text without persisting raw media in prompts.
- [x] Fail open to text-only cognition when image understanding is unavailable.

### Task 4: Implement scoped API keys and MCP authorization

**Files:**
- Modify: `src/main/java/com/aseubel/yusi/pojo/entity/DeveloperConfig.java`
- Modify: `src/main/java/com/aseubel/yusi/service/developer/DeveloperConfigService.java`
- Modify: `src/main/java/com/aseubel/yusi/service/developer/impl/DeveloperConfigServiceImpl.java`
- Modify: `src/main/java/com/aseubel/yusi/controller/DeveloperConfigController.java`
- Modify: `src/main/java/com/aseubel/yusi/grpc/McpGrpcServiceImpl.java`
- Modify: `frontend/src/lib/api.ts`
- Modify: `frontend/src/pages/Settings.tsx`

- [x] Define read/write scopes with `MEMORY_READ` as the default.
- [x] Add revoke and scope update endpoints.
- [x] Reject revoked/unauthorized keys at the Java gRPC boundary.
- [x] Keep Go MCP tools as transport adapters only.

### Task 5: Update docs and static verification

- [x] Mark Phase 4 items complete only after all four contracts are present.
- [x] Update architecture record with the MCP boundary decision.
- [x] Run source/search/XML/diff checks; do not run service, Maven build, or tests.
