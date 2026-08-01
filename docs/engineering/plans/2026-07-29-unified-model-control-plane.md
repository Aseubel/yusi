# Unified Model Control Plane Implementation Plan

> **For agentic workers:** This plan is executed inline because repository instructions prohibit sub-agents.

**Goal:** Move speech-to-text endpoint selection under the existing runtime model control center while keeping protocol-specific clients separate.

**Architecture:** Extend model endpoint definitions with explicit capabilities and add a speech-to-text registry that consumes the same runtime configuration and update events. Chat remains backed by LangChain4j chat models; ASR remains a multipart HTTP adapter. The control plane owns endpoint identity, routing membership, credentials, timeouts, enablement, and hot reload.

**Tech Stack:** Spring Boot, Java 21, Redis/Redisson runtime config, Spring RestTemplate, LangChain4j chat model registry.

---

### Task 1: Extend the model control-plane contract

**Files:**
- Modify: src/main/java/com/aseubel/yusi/config/ai/properties/ModelRoutingProperties.java
- Create: src/main/java/com/aseubel/yusi/service/ai/model/ModelCapability.java
- Modify: src/main/java/com/aseubel/yusi/service/ai/model/ModelConfigCenter.java
- Modify: src/main/java/com/aseubel/yusi/service/ai/model/ModelInstanceRegistry.java

- [x] Add CHAT, STREAMING_CHAT, EMBEDDING, and SPEECH_TO_TEXT capabilities.
- [x] Give legacy model definitions chat capabilities when capabilities is absent.
- [x] Make the chat registry skip endpoints without chat capabilities.
- [x] Extend runtime config display, secret merging, and capability-group validation.

### Task 2: Add the speech capability adapter

**Files:**
- Create: src/main/java/com/aseubel/yusi/service/diary/SpeechToTextClient.java
- Create: src/main/java/com/aseubel/yusi/service/diary/TranscriptionResult.java
- Create: src/main/java/com/aseubel/yusi/service/diary/impl/OpenAiCompatibleSpeechToTextClient.java
- Create: src/main/java/com/aseubel/yusi/service/ai/asr/SpeechModelRegistry.java
- Create: src/main/java/com/aseubel/yusi/service/diary/impl/ModelRoutedVoiceTranscriptionService.java
- Modify: src/main/java/com/aseubel/yusi/config/ai/ChatModelConfig.java

- [x] Build clients from ModelConfigCenter endpoint definitions with SPEECH_TO_TEXT.
- [x] Reload clients when ModelConfigUpdatedEvent is published.
- [x] Preserve multipart OpenAI-compatible request/response handling.
- [x] Fail clearly when no enabled ASR endpoint exists.

### Task 3: Migrate bootstrap configuration and record the boundary

**Files:**
- Modify: src/main/resources/application-dev.yml
- Modify: src/main/resources/application-prod.yml
- Modify: docs/record/langchain4j-1.18-architecture-evolution.md
- Modify: docs/design/backend-design.md

- [x] Move ASR endpoint/model/timeout into model.routing.models with an ASR group.
- [x] Keep model.speech.asr out of the runtime source of truth.
- [x] Document that unified management does not imply one client implementation.

### Task 4: Static verification

- [x] Run git diff --check.
- [x] Search for remaining ASR reads from SpeechRecognitionProperties.
- [x] Search for capability and ASR route wiring.
- [x] Do not run Maven/Go tests or builds and do not start the application.

---

Implementation note: runtime verification is limited to static checks because repository instructions prohibit compilation and tests.
