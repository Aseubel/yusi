# LLM Gateway 路由治理与可视化控制台实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task-by-task. 本仓库 AGENTS.md 明确禁止启用子 agent；全程 inline execution，并禁用 auto-review。

**Goal:** 将模型治理收敛为 schema v2 的可解释、可回放、可热更新应用内 LLM Gateway，并用可视化路由控制台作为管理员的主操作入口。

**Architecture:** 保留现有 Spring Boot 应用内网关、Redis 热更新、模型实例健康状态和 LangChain4j 边界。链路固定为“物理模型注册表 -> 逻辑模型 tier -> 路由策略 -> 不可变路由决策 -> Provider Adapter -> 调用尝试”；每次请求在首个 Provider 调用前固定候选顺序，Fallback 只创建新的 attempt，并把 `route_reason`、候选链、真实模型、usage、延迟和错误类型写入低敏调用轨迹。管理 API 返回 schema v2 治理快照，React 控制台通过路由矩阵、策略编辑器、模型卡片、候选链预览和运行状态视图完成选择。

**Tech Stack:** Java 21, Spring Boot 3.4.5, LangChain4j 1.18.0, Redisson/Redis, Spring Data JPA/MySQL, React 19, TypeScript 5.9, Vite, Tailwind CSS, Radix UI, Lucide, Vitest。

## Global Constraints

- 第一版只实现固定规则路由、逻辑模型层级、健康过滤、错误分类 Fallback、Token/成本元数据和审计；不引入分类器、语义路由、学习型路由、个性化路由或语义缓存。
- 路由规则只引用内部 tier ID，例如 `fast`、`balanced`、`flagship`，不得把供应商真实模型名写进场景规则；模型升级只修改注册表或 tier 成员。
- Provider Adapter 第一版直接支持 `CHAT_COMPLETIONS`、`RESPONSES` 和 `ANTHROPIC_MESSAGES` 三种协议；provider 与协议组合必须经过适配器校验。
- schema v2 是唯一配置形状；管理面只提供 console、preview、states、attempts 和 metrics 契约。
- 密钥只在服务端保存；API 返回 `apiKeyConfigured` 状态，永远不返回原始密钥。日志、轨迹和前端详情不得保存 Prompt、回答、思维链、工具参数或工具结果。
- `route_reason`、policy version、selected tier、selected model 和 `attempt_id` 必须在每次调用中可关联；流式调用确定 Provider 后不得因运行中健康状态改变而改写该次调用的归属。
- 只允许对网络瞬断、供应商 5xx、可识别的 429 和明确可重放的解析失败做 Fallback；上下文超限、参数错误、安全拒答、用户取消和已经产生副作用的工具调用直接结束当前 attempt。
- 管理写入采用配置版本号和乐观并发控制；保存失败时不得发布 Redis 配置事件，也不得改变本地生效配置。
- 前端的主路径必须是可视化表单和路由预览；导出 JSON 只能表达当前 schema v2 快照，不能导入旧格式或绕过服务端校验。
- 不添加 LiteLLM、Kong、Cloudflare AI Gateway 等外部网关进程；如果后续需要独立网关，使用本计划定义的 Provider 和路由契约作为迁移边界。

## File Map

### Backend

- Create: `src/main/java/com/aseubel/yusi/config/ai/properties/ModelTierDefinition.java` - 逻辑模型层级及成员策略。
- Create: `src/main/java/com/aseubel/yusi/config/ai/properties/RoutePolicyDefinition.java` - 场景、语言、风险、主 tier、Fallback tier 和生成参数。
- Modify: `src/main/java/com/aseubel/yusi/config/ai/properties/ModelRoutingProperties.java` - 定义 schema v2 配置、价格和上下文元数据。
- Create: `src/main/java/com/aseubel/yusi/service/ai/model/ModelRouteDecision.java` - 单次请求固定的路由结果。
- Create: `src/main/java/com/aseubel/yusi/service/ai/model/ModelRouteCandidate.java` - 候选模型、所属 tier、健康状态和排除原因。
- Create: `src/main/java/com/aseubel/yusi/service/ai/model/ModelFailureKind.java` - Provider 错误的统一分类。
- Create: `src/main/java/com/aseubel/yusi/service/ai/model/ModelInvocationException.java` - 带错误分类的调用异常。
- Create: `src/main/java/com/aseubel/yusi/service/ai/model/provider/ChatModelProviderAdapter.java` - Chat/Streaming Chat Provider 扩展接口。
- Create: `src/main/java/com/aseubel/yusi/service/ai/model/provider/OpenAiCompatibleChatModelProvider.java` - 现有 OpenAI-compatible 客户端构造及错误归一化。
- Create: `src/main/java/com/aseubel/yusi/service/ai/model/provider/ChatModelProviderRegistry.java` - Provider 查找、校验和实例创建。
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/ModelConfigCenter.java` - schema v2 校验、版本、密钥合并、保存和审计事件。
- Modify: `src/main/java/com/aseubel/yusi/common/exception/ErrorCode.java` - 增加 409 配置版本冲突错误。
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/ModelInstance.java` - 增加 provider、capabilities、tier metadata 和价格快照。
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/ModelInstanceRegistry.java` - 改为通过 Provider Registry 构造客户端。
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/strategy/ModelSelectionStrategy.java` 及相关 strategy 文件 - 从“只选一个”扩展为“生成固定有序候选链”。
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/ModelRouterService.java` - 规则匹配、tier 候选展开、健康过滤和 route reason。
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/ModelRouteContext.java` - 保留现有 builder 调用，增加可选 request/risk/budget 字段。
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/ModelProxyFactory.java` - 一次决策、多次 attempt、错误分类、流式归属和轨迹发布。
- Create: `src/main/java/com/aseubel/yusi/service/ai/model/ModelUsageSnapshot.java` - 统一 usage、finish reason、成本和价格版本。
- Create: `src/main/java/com/aseubel/yusi/service/ai/model/ModelUsageExtractor.java` - 从 LangChain4j 同步/流式响应提取 usage。
- Create: `src/main/java/com/aseubel/yusi/service/ai/runtime/ModelCallAttemptEvent.java` - 低敏调用事件。
- Create: `src/main/java/com/aseubel/yusi/pojo/entity/ModelCallTrace.java` - 调用 attempt 的持久化元数据。
- Create: `src/main/java/com/aseubel/yusi/repository/ModelCallTraceRepository.java` - 轨迹查询和聚合查询。
- Create: `src/main/java/com/aseubel/yusi/service/ai/runtime/ModelCallTraceService.java` - 异步持久化及管理查询。
- Create: `src/main/java/com/aseubel/yusi/pojo/entity/ModelRuntimeConfig.java` - 使用现有 `model_runtime_config` 表保存版本化快照。
- Create: `src/main/java/com/aseubel/yusi/pojo/entity/ModelConfigChangeLog.java` - 使用现有 `model_config_change_log` 表记录变更。
- Create: `src/main/java/com/aseubel/yusi/repository/ModelRuntimeConfigRepository.java`。
- Create: `src/main/java/com/aseubel/yusi/repository/ModelConfigChangeLogRepository.java`。
- Create: `src/main/java/com/aseubel/yusi/pojo/dto/model/ModelGovernanceSnapshot.java` - 前端读取模型、tier、route、状态和版本。
- Create: `src/main/java/com/aseubel/yusi/pojo/dto/model/ModelGovernanceUpdateRequest.java` - 前端保存请求。
- Create: `src/main/java/com/aseubel/yusi/pojo/dto/model/ModelRoutePreviewRequest.java`。
- Create: `src/main/java/com/aseubel/yusi/pojo/dto/model/ModelRoutePreviewResponse.java`。
- Create: `src/main/java/com/aseubel/yusi/pojo/dto/model/ModelCallTraceQuery.java`。
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/ModelManagementService.java`。
- Modify: `src/main/java/com/aseubel/yusi/controller/ModelManagementController.java`。
- Create: `src/main/resources/db/migration/V20260809__create_model_call_trace.sql`。
- Modify: `src/main/resources/application-dev.yml` and `src/main/resources/application-prod.yml` - 使用 schema v2 路由配置。

### Frontend

- Modify: `frontend/src/lib/api.ts` - v2 治理快照、保存、预览、轨迹查询和状态类型。
- Create: `frontend/src/pages/admin/model-management/types.ts` - UI 草稿、选择项和校验类型。
- Create: `frontend/src/pages/admin/model-management/modelRouting.ts` - 纯函数：快照转草稿、矩阵索引、候选链和前端校验。
- Create: `frontend/src/pages/admin/model-management/ModelGovernanceOverview.tsx` - 总览指标和全局保存状态。
- Create: `frontend/src/pages/admin/model-management/ModelRegistryPanel.tsx` - 物理模型卡片、能力、供应商和密钥状态。
- Create: `frontend/src/pages/admin/model-management/RouteMatrix.tsx` - language/scene 路由矩阵。
- Create: `frontend/src/pages/admin/model-management/RoutePolicyEditor.tsx` - 路由条件、主 tier、Fallback 链、策略和参数编辑器。
- Create: `frontend/src/pages/admin/model-management/RoutePreview.tsx` - 路由决策和候选链可视化。
- Create: `frontend/src/pages/admin/model-management/RuntimeHealthPanel.tsx` - 实例健康与熔断状态。
- Create: `frontend/src/pages/admin/model-management/ModelCallActivity.tsx` - 低敏调用轨迹与 route reason 详情。
- Modify: `frontend/src/pages/admin/ModelManagement.tsx` - 由页面编排器替代当前 JSON 文本框逻辑。
- Modify: `frontend/src/i18n/locales/zh.json` and `frontend/src/i18n/locales/en.json` - 新控制台文案、校验、状态和错误。
- Create: `frontend/src/lib/modelRouting.test.ts` - 纯函数和前端契约测试。

### Existing tests and docs

- Create: `src/test/java/com/aseubel/yusi/service/ai/model/ModelRoutePolicyMatcherTest.java`。
- Create: `src/test/java/com/aseubel/yusi/service/ai/model/ModelRouterServiceTest.java`。
- Create: `src/test/java/com/aseubel/yusi/service/ai/model/ModelInvocationErrorClassifierTest.java`。
- Create: `src/test/java/com/aseubel/yusi/service/ai/model/ModelConfigCenterTest.java`。
- Create: `src/test/java/com/aseubel/yusi/service/ai/model/ChatModelProviderRegistryTest.java`。
- Create: `src/test/java/com/aseubel/yusi/service/ai/model/ModelUsageExtractorTest.java`。
- Modify: `src/test/java/com/aseubel/yusi/service/ai/model/ModelProxyFactoryTest.java` and existing strategy tests。
- Modify: `docs/design/model-management-framework.md` and `docs/design/backend-design.md` - 更新 v2 契约、回滚和观测边界。

## Route Contract

The schema v2 config is the only accepted shape. Runtime JSON, YAML bootstrap data and admin updates all use `models`, `tiers`, `routes` and `defaultRoute` directly.

```yaml
model:
  routing:
    schema-version: 2
    default-language: zh
    default-scene: chat
    default-tier: balanced
    models:
      - id: qwen
        provider: openai-compatible
        protocol: CHAT_COMPLETIONS
        model: qwen-model-id
        capabilities: [CHAT, STREAMING_CHAT]
        context-window-tokens: 131072
        pricing:
          input-per-million: null
          output-per-million: null
          price-version: unknown
    tiers:
      fast:
        display-name: Fast
        members: [qwen]
        strategy: LEAST_LATENCY
      balanced:
        display-name: Balanced
        members: [qwen]
        strategy: FAIL_OVER
    routes:
      - id: chat-zh
        scene: chat
        language: zh
        risk-level: LOW
        primary-tier: balanced
        fallback-tiers: [fast]
        max-output-tokens: 512
        enabled: true
        priority: 100
    default-route:
      id: default
      scene: '*'
      language: '*'
      risk-level: LOW
      primary-tier: balanced
      fallback-tiers: [fast]
      enabled: true
      priority: 0
```

Protocol/provider contract:

- `CHAT_COMPLETIONS`: `openai-compatible`, `openai`, `deepseek` or `dashscope`.
- `RESPONSES`: `openai-compatible`, `openai`, `deepseek` or `dashscope`.
- `ANTHROPIC_MESSAGES`: `anthropic`.

The first two provider families use the OpenAI-compatible adapter; the last uses the Anthropic Messages adapter. A mismatched provider/protocol pair is rejected before the instance registry reloads.

The runtime decision has this shape and is immutable after the first provider attempt begins:

```java
public record ModelRouteDecision(
        String requestId,
        String policyId,
        long policyVersion,
        String language,
        String scene,
        String riskLevel,
        String primaryTier,
        List<ModelRouteCandidate> candidates,
        String routeReason,
        long decidedAt) {
}
```

Each `ModelRouteCandidate` contains `tierId`, `modelId`, `provider`, `modelName`, `priority`, `weight`, `available`, and `excludedReason`. `candidates` is already ordered by strategy and fallback tier; `ModelProxyFactory` never re-runs policy matching inside the attempt loop.

## Implementation Tasks

### Task 1: Establish the v2 model and route contract

**Files:**
- Create: `src/main/java/com/aseubel/yusi/config/ai/properties/ModelTierDefinition.java`
- Create: `src/main/java/com/aseubel/yusi/config/ai/properties/RoutePolicyDefinition.java`
- Modify: `src/main/java/com/aseubel/yusi/config/ai/properties/ModelRoutingProperties.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/ModelConfigCenter.java`
- Create: `src/test/java/com/aseubel/yusi/service/ai/model/ModelConfigCenterTest.java`

**Interfaces:**
- Consumes: schema v2 `models`, `tiers`, `routes`, `defaultRoute`, Redis runtime JSON and masked admin payloads.
- Produces: validated `schemaVersion`, `tiers`, `routes`, `defaultRoute` and `defaultTier` with deterministic serialization.

- [ ] **Step 1: Write failing tests for v2 validation and versioned publication.**

Test these exact cases:

```java
@Test
void acceptsCanonicalV2RouteDefinitions() {
    ModelRoutingProperties config = validV2Config();

    center.validateForAdmin(config);

    assertEquals(2, config.getSchemaVersion());
    assertEquals("balanced", config.getRoutes().getFirst().getPrimaryTier());
}

@Test
void rejectsRouteReferencingUnknownTier() {
    ModelRoutingProperties config = validV2Config();
    config.getRoutes().get(0).setPrimaryTier("missing");

    assertThatThrownBy(() -> center.validateForAdmin(config))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("primary-tier");
}

@Test
void rejectsNonV2RuntimePayload() {
    ModelRoutingProperties config = validV2Config();
    config.setSchemaVersion(1);

    assertThatThrownBy(() -> center.validateForAdmin(config))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("schema-version");
}
```

- [ ] **Step 2: Run the focused test and verify it fails for the missing v2 contract.**

Run: `./mvnw -Dtest=ModelConfigCenterTest test`

Expected: FAIL because the schema v2 properties, validation and versioned update methods do not exist.

- [ ] **Step 3: Add the v2 properties and deterministic normalization.**

Add these fields to `ModelRoutingProperties`:

```java
private int schemaVersion = 2;
private String defaultTier;
private Map<String, ModelTierDefinition> tiers = new LinkedHashMap<>();
private List<RoutePolicyDefinition> routes = new ArrayList<>();
private RoutePolicyDefinition defaultRoute;
```

`ModelTierDefinition` must contain `displayName`, `description`, `members`, `strategy`, `enabled`, and `capabilities`; `RoutePolicyDefinition` must contain `id`, `scene`, `language`, `riskLevel`, `primaryTier`, `fallbackTiers`, `maxInputTokens`, `maxOutputTokens`, `temperature`, `topP`, `enabled`, and `priority`.

The schema v2 contract is direct and deterministic: every route names a `primaryTier`, optional ordered `fallbackTiers`, language, scene, risk level and generation parameters; every tier names model members and one selection strategy; every model declares its provider and wire protocol.

Validation must reject duplicate model IDs, duplicate route IDs, empty tier membership, unknown model IDs, unknown primary/fallback tiers, a disabled tier used as a primary tier, an empty route scene, invalid strategy values, negative token limits, and a fallback tier repeated in the same route. It must also verify that a Chat route can reach at least one enabled model with `CHAT` or `STREAMING_CHAT` capability.

- [ ] **Step 4: Add version-aware secret merging and reject non-v2 payloads.**

Add `CONFIG_VERSION_CONFLICT(40901, 409, "模型治理配置版本已过期")` to `ErrorCode`, then make `ModelConfigCenter.updateCanonical` accept `expectedVersion`, validate schema v2 before publication, merge blank API keys by model ID, and reject a stale version with `BusinessException(ErrorCode.CONFIG_VERSION_CONFLICT, ...)`. The snapshot DTO exposes `apiKeyConfigured`, never a placeholder or raw key.

- [ ] **Step 5: Run the focused test and verify it passes.**

Run: `./mvnw -Dtest=ModelConfigCenterTest test`

Expected: PASS with schema v2 validation, protocol/provider validation, secret preservation and stale-version rejection covered.

- [ ] **Step 6: Commit the contract boundary.**

```bash
git add src/main/java/com/aseubel/yusi/config/ai/properties src/main/java/com/aseubel/yusi/service/ai/model/ModelConfigCenter.java src/test/java/com/aseubel/yusi/service/ai/model/ModelConfigCenterTest.java
git add src/main/java/com/aseubel/yusi/common/exception/ErrorCode.java
git commit -m "feat: define versioned model route contract"
```

### Task 2: Move model construction behind Provider Adapter

**Files:**
- Create: `src/main/java/com/aseubel/yusi/service/ai/model/provider/ChatModelProviderAdapter.java`
- Create: `src/main/java/com/aseubel/yusi/service/ai/model/provider/OpenAiCompatibleChatModelProvider.java`
- Create: `src/main/java/com/aseubel/yusi/service/ai/model/provider/ChatModelProviderRegistry.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/ModelInstance.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/ModelInstanceRegistry.java`
- Modify: `src/main/java/com/aseubel/yusi/config/ai/properties/ModelRoutingProperties.java`
- Create: `src/test/java/com/aseubel/yusi/service/ai/model/ChatModelProviderRegistryTest.java`

**Interfaces:**
- Consumes: normalized `ModelDefinition` entries with `provider`, `baseurl`, credentials, timeout, capabilities and model name.
- Produces: `ProviderClientBundle` containing `ChatModel`, `StreamingChatModel`, provider ID and normalized error mapper; `ModelInstanceRegistry` no longer constructs LangChain4j clients directly.

- [ ] **Step 1: Write failing tests for provider selection and capability filtering.**

```java
@Test
void createsChatCompletionsClientForOpenAiCompatibleProvider() {
    ModelRoutingProperties.ModelDefinition definition = chatDefinition("openai-compatible");

    ProviderClientBundle bundle = registry.create(definition);

    assertEquals("openai-compatible", bundle.provider());
    assertThat(bundle.chatModel()).isNotNull();
    assertThat(bundle.streamingChatModel()).isNotNull();
}

@Test
void rejectsUnsupportedChatProviderBeforeRegistryReload() {
    ModelRoutingProperties.ModelDefinition definition = chatDefinition("anthropic");

    assertThatThrownBy(() -> registry.create(definition))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("provider");
}
```

- [ ] **Step 2: Run the focused test and verify it fails.**

Run: `./mvnw -Dtest=ChatModelProviderRegistryTest test`

Expected: FAIL because the adapter interface and registry do not exist.

- [ ] **Step 3: Implement the adapter boundary.**

Define the minimal interface:

```java
public interface ChatModelProviderAdapter {
    String providerId();
    boolean supports(ModelRoutingProperties.ModelDefinition definition);
    ProviderClientBundle create(ModelRoutingProperties.ModelDefinition definition);
    ModelInvocationException normalize(Throwable error, String modelId);
}
```

`OpenAiCompatibleChatModelProvider` must support explicit providers `openai`, `openai-compatible`, `deepseek`, and `dashscope` for `CHAT_COMPLETIONS` and `RESPONSES`. `AnthropicMessagesChatModelProvider` supports `anthropic` only for `ANTHROPIC_MESSAGES`. Each adapter uses the configured base URL, API key, model name and timeout, and must not log the base URL with credentials or the API key.

- [ ] **Step 4: Refactor `ModelInstanceRegistry` to use the registry.**

Before constructing an instance, skip disabled definitions and definitions without Chat capabilities. Resolve the adapter through `ChatModelProviderRegistry`; if no adapter supports the definition, fail configuration reload atomically and retain the previous instance map. Add `provider`, capability set, `contextWindowTokens`, `inputPricePerMillion`, `outputPricePerMillion`, and `priceVersion` to `ModelInstance`.

- [ ] **Step 5: Run the focused provider and existing registry tests.**

Run: `./mvnw -Dtest=ChatModelProviderRegistryTest,ModelProxyFactoryTest test`

Expected: PASS; existing OpenAI-compatible model behavior remains available through the new adapter.

- [ ] **Step 6: Commit the provider boundary.**

```bash
git add src/main/java/com/aseubel/yusi/service/ai/model/provider src/main/java/com/aseubel/yusi/service/ai/model/ModelInstance.java src/main/java/com/aseubel/yusi/service/ai/model/ModelInstanceRegistry.java src/main/java/com/aseubel/yusi/config/ai/properties/ModelRoutingProperties.java src/test/java/com/aseubel/yusi/service/ai/model/ChatModelProviderRegistryTest.java
git commit -m "refactor: isolate chat model provider adapters"
```

### Task 3: Implement deterministic rule routing and ordered Fallback candidates

**Files:**
- Create: `src/main/java/com/aseubel/yusi/service/ai/model/ModelRouteDecision.java`
- Create: `src/main/java/com/aseubel/yusi/service/ai/model/ModelRouteCandidate.java`
- Create: `src/main/java/com/aseubel/yusi/service/ai/model/ModelFailureKind.java`
- Create: `src/main/java/com/aseubel/yusi/service/ai/model/ModelInvocationException.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/ModelRouteContext.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/strategy/ModelSelectionStrategy.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/strategy/RoundRobinSelectionStrategy.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/strategy/LeastLatencySelectionStrategy.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/strategy/WeightedRandomSelectionStrategy.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/strategy/FailOverSelectionStrategy.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/ModelRouterService.java`
- Create: `src/test/java/com/aseubel/yusi/service/ai/model/ModelRoutePolicyMatcherTest.java`
- Create: `src/test/java/com/aseubel/yusi/service/ai/model/ModelRouterServiceTest.java`
- Create: `src/test/java/com/aseubel/yusi/service/ai/model/ModelInvocationErrorClassifierTest.java`

**Interfaces:**
- Consumes: `ModelRouteContext`, v2 route policies, tier members, `ModelRuntimeState` snapshots and selection strategies.
- Produces: `ModelRouterService.plan(ModelRouteContext)` returning one immutable `ModelRouteDecision` with an ordered candidate chain.

- [ ] **Step 1: Write failing tests for route precedence and candidate order.**

Cover exact language/scene match, language wildcard, default route, route priority, disabled model filtering, tier health filtering, primary tier before fallback tier, and a route reason containing policy ID and strategy:

```java
@Test
void exactLanguageAndSceneRouteWinsOverWildcardAndDefault() {
    ModelRouteDecision decision = router.plan(context("zh", "chat"));

    assertEquals("chat-zh", decision.policyId());
    assertEquals("balanced", decision.primaryTier());
    assertThat(decision.routeReason()).contains("policy=chat-zh", "language=zh", "scene=chat");
}

@Test
void fallbackTierIsAppendedOnlyAfterPrimaryTierCandidates() {
    ModelRouteDecision decision = router.plan(context("zh", "summary"));

    assertThat(decision.candidates()).extracting(ModelRouteCandidate::tierId)
            .containsExactly("fast", "fast", "balanced");
    assertThat(decision.candidates().get(2).excludedReason()).isEqualTo("fallback-tier");
}

@Test
void unavailablePrimaryModelIsRecordedAndHealthyFallbackRemainsSelectable() {
    ModelRouteDecision decision = router.plan(context("zh", "chat"));

    assertThat(decision.candidates()).anyMatch(candidate ->
            candidate.modelId().equals("qwen") && candidate.excludedReason().equals("DOWN"));
    assertThat(decision.candidates()).anyMatch(candidate ->
            candidate.tierId().equals("fast") && candidate.available());
}
```

- [ ] **Step 2: Run routing tests and verify they fail.**

Run: `./mvnw -Dtest=ModelRoutePolicyMatcherTest,ModelRouterServiceTest,ModelInvocationErrorClassifierTest test`

Expected: FAIL because route decisions, v2 matching and ordered candidate APIs do not exist.

- [ ] **Step 3: Add route context and immutable decision types.**

Define the route context explicitly with optional request metadata and no group-specific field:

```java
@Value
@Builder
public class ModelRouteContext {
    String requestId;
    String language;
    String scene;
    String riskLevel;
    Integer estimatedInputTokens;
    Integer reservedOutputTokens;
}
```

`ModelRouteCandidate` must carry the physical model metadata and a nullable exclusion reason. `ModelRouteDecision` must contain a defensive copy of the ordered list, the matched policy version, and a single route reason string built from structured fields in this order: `policy`, `language`, `scene`, `risk`, `primary-tier`, `strategy`, `fallback-tiers`, `health-filter`.

- [ ] **Step 4: Change each strategy to return a deterministic ordered list.**

Use this interface while keeping `select` for existing callers:

```java
public interface ModelSelectionStrategy {
    List<ModelInstance> order(String tierId,
                              List<ModelInstance> candidates,
                              Map<String, ModelRuntimeState> states);

    default Optional<ModelInstance> select(String tierId,
                                           List<ModelInstance> candidates,
                                           Map<String, ModelRuntimeState> states) {
        return order(tierId, candidates, states).stream().findFirst();
    }
}
```

Round robin rotates once per decision; least latency sorts available candidates by average latency and then model ID; weighted random creates one weighted permutation using a request-local random source; failover sorts by priority and model ID. No strategy silently changes the list after `plan` returns.

- [ ] **Step 5: Implement route matching and fallback eligibility.**

`ModelRouterService.plan` must normalize language and scene, select the highest-priority enabled route matching exact scene plus exact language, then wildcard language, then default route. For each primary/fallback tier, get members, filter by Chat capability, enabled status, language/scene support and runtime state, then apply the tier strategy. Preserve excluded candidates in the decision for the preview and trace, but only return candidates eligible for a real attempt unless every candidate is down and the configured half-open probe is allowed.

The route service must not perform quality-based escalation. `fallback-tiers` only supplies an ordered recovery chain; `ModelProxyFactory` decides whether an exception is eligible to advance to the next candidate.

- [ ] **Step 6: Implement normalized error kinds and fallback policy.**

`ModelInvocationException` must expose `ModelFailureKind`, `provider`, `modelId`, `retryAfterMs` and the original cause. The adapter maps known HTTP/SDK errors to `TRANSIENT_NETWORK`, `RATE_LIMITED`, `SERVER_ERROR`, `CONTEXT_LIMIT`, `INVALID_REQUEST`, `SAFETY_REFUSAL`, `STRUCTURED_OUTPUT`, `CANCELLED`, or `UNKNOWN`. Fallback eligibility is true only for `TRANSIENT_NETWORK`, `RATE_LIMITED`, `SERVER_ERROR`, and `STRUCTURED_OUTPUT` when no tool call or stream chunk has been emitted.

- [ ] **Step 7: Run all model selection tests.**

Run: `./mvnw -Dtest=ModelRoutePolicyMatcherTest,ModelRouterServiceTest,ModelInvocationErrorClassifierTest,RoundRobinSelectionStrategyTest,FailOverSelectionStrategyTest,ModelProxyFactoryTest test`

Expected: PASS; old `select` tests and new route decision tests pass together.

- [ ] **Step 8: Commit deterministic routing.**

```bash
git add src/main/java/com/aseubel/yusi/service/ai/model src/test/java/com/aseubel/yusi/service/ai/model
git commit -m "feat: add explainable tier based model routing"
```

### Task 4: Pin decisions per request and record every attempt

**Files:**
- Create: `src/main/java/com/aseubel/yusi/service/ai/model/ModelUsageSnapshot.java`
- Create: `src/main/java/com/aseubel/yusi/service/ai/model/ModelUsageExtractor.java`
- Create: `src/main/java/com/aseubel/yusi/service/ai/runtime/ModelCallAttemptEvent.java`
- Create: `src/main/java/com/aseubel/yusi/pojo/entity/ModelCallTrace.java`
- Create: `src/main/java/com/aseubel/yusi/repository/ModelCallTraceRepository.java`
- Create: `src/main/java/com/aseubel/yusi/service/ai/runtime/ModelCallTraceService.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/ModelProxyFactory.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/runtime/AgentRunTraceService.java` only to pass the existing run ID when available.
- Create: `src/main/resources/db/migration/V20260809__create_model_call_trace.sql`
- Create: `src/test/java/com/aseubel/yusi/service/ai/model/ModelUsageExtractorTest.java`
- Modify: `src/test/java/com/aseubel/yusi/service/ai/model/ModelProxyFactoryTest.java`

**Interfaces:**
- Consumes: one `ModelRouteDecision`, LangChain4j sync/stream responses, normalized `ModelInvocationException`, current request/user/run IDs.
- Produces: one `ModelCallAttemptEvent` per actual Provider attempt and one persisted `ModelCallTrace` record per event; trace persistence failures never fail the user model call.

- [ ] **Step 1: Write failing tests for pinned routing and attempt records.**

```java
@Test
void fallbackUsesTheSameDecisionAndCreatesANewAttempt() {
    when(router.plan(any())).thenReturn(decision("balanced", List.of(primary, backup)));
    when(primary.chat(any())).thenThrow(rateLimited(primary));
    when(backup.chat(any())).thenReturn(successResponse(12, 4));

    ChatResponse response = proxy.chat(request());

    assertThat(response.aiMessage().text()).isEqualTo("ok");
    verify(publisher, times(2)).publishEvent(any(ModelCallAttemptEvent.class));
    assertThat(publishedEvents()).extracting(ModelCallAttemptEvent::policyId)
            .containsOnly("chat-zh");
    assertThat(publishedEvents()).extracting(ModelCallAttemptEvent::fallbackUsed)
            .containsExactly(false, true);
}

@Test
void streamDoesNotSwitchAfterFirstPartialResponse() {
    streamingPrimaryEmits("partial");
    streamingPrimaryFailsAfterPartial();

    assertThatThrownBy(() -> proxy.stream(request(), handler())).isInstanceOf(ModelInvocationException.class);
    verifyNoInteractions(backup);
}
```

- [ ] **Step 2: Run the proxy tests and verify they fail.**

Run: `./mvnw -Dtest=ModelProxyFactoryTest,ModelUsageExtractorTest test`

Expected: FAIL because the proxy still calls `select` inside the loop and does not publish attempt events.

- [ ] **Step 3: Add usage, pricing and trace contracts.**

`ModelUsageSnapshot` must contain nullable `inputTokens`, `outputTokens`, `cachedTokens`, `finishReason`, `cost`, `priceVersion`, and `usageSource`. Cost is calculated only when both the model price snapshot and usage are present:

```text
cost = inputTokens / 1_000_000 * inputPricePerMillion
      + outputTokens / 1_000_000 * outputPricePerMillion
```

`ModelCallTrace` must persist `requestId`, `attemptId`, optional `runId` and `userId`, `scene`, `language`, `policyId`, `policyVersion`, `routeReason`, `primaryTier`, `selectedTier`, `modelId`, `provider`, `modelName`, `inputTokens`, `outputTokens`, `cachedTokens`, `cost`, `priceVersion`, `latencyMs`, `ttftMs`, `retryIndex`, `fallbackUsed`, `status`, `errorCode`, `finishReason`, `createdAt`. It must never contain prompt or response columns.

- [ ] **Step 4: Add the migration and repository indexes.**

Create `V20260809__create_model_call_trace.sql` with a primary key, unique `(request_id, attempt_id)`, indexes on `(created_at, scene)`, `(selected_tier, created_at)`, `(provider, created_at)`, `(status, created_at)`, and `(fallback_used, created_at)`. Use nullable numeric columns for usage and cost because some providers do not return usage on streaming errors.

- [ ] **Step 5: Refactor `ModelProxyFactory` around one decision.**

The invocation flow must be:

```text
resolve context
-> router.plan(context) once
-> for candidate in decision.candidates:
     allowRequest(candidate.modelId)
     reserve attempt metadata
     invoke the already selected provider client
     publish success/failure event with the same decision
     stop on success
     advance only when ModelFailureKind is fallback-eligible
-> throw the last normalized error
```

For sync calls, extract usage from `ChatResponse.metadata()` before publishing. For streaming calls, set the selected candidate and decision on the stream session before subscribing; record TTFT at the first partial response, mark success only after the stream completes, and record cancellation or disconnect as non-success. A stream that already emitted a partial response must not call a second candidate.

- [ ] **Step 6: Make trace persistence non-blocking and low-risk.**

Publish `ModelCallAttemptEvent` after each attempt. The listener saves `ModelCallTrace` and logs only the event ID on failure; it must catch persistence exceptions and increment a warning metric without propagating to the chat call. Reuse the existing `AgentRunTrace` run ID when the controller has one; do not change the existing user-facing AgentRun lifecycle behavior.

- [ ] **Step 7: Run model and persistence tests.**

Run: `./mvnw -Dtest=ModelProxyFactoryTest,ModelUsageExtractorTest,ChatStreamCancellationRegistryTest,AgentRunTraceServiceTest test`

Expected: PASS with fallback attempts, stream pinning, usage extraction and trace failure isolation covered.

- [ ] **Step 8: Commit attempt tracking.**

```bash
git add src/main/java/com/aseubel/yusi/service/ai/model src/main/java/com/aseubel/yusi/service/ai/runtime src/main/java/com/aseubel/yusi/pojo/entity/ModelCallTrace.java src/main/java/com/aseubel/yusi/repository/ModelCallTraceRepository.java src/main/resources/db/migration/V20260809__create_model_call_trace.sql src/test/java/com/aseubel/yusi/service/ai/model
git commit -m "feat: trace model routing decisions and attempts"
```

### Task 5: Add versioned governance persistence and admin APIs

**Files:**
- Create: `src/main/java/com/aseubel/yusi/pojo/entity/ModelRuntimeConfig.java`
- Create: `src/main/java/com/aseubel/yusi/pojo/entity/ModelConfigChangeLog.java`
- Create: `src/main/java/com/aseubel/yusi/repository/ModelRuntimeConfigRepository.java`
- Create: `src/main/java/com/aseubel/yusi/repository/ModelConfigChangeLogRepository.java`
- Create: `src/main/java/com/aseubel/yusi/pojo/dto/model/ModelGovernanceSnapshot.java`
- Create: `src/main/java/com/aseubel/yusi/pojo/dto/model/ModelGovernanceUpdateRequest.java`
- Create: `src/main/java/com/aseubel/yusi/pojo/dto/model/ModelRoutePreviewRequest.java`
- Create: `src/main/java/com/aseubel/yusi/pojo/dto/model/ModelRoutePreviewResponse.java`
- Create: `src/main/java/com/aseubel/yusi/pojo/dto/model/ModelCallTraceQuery.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/ModelManagementService.java`
- Modify: `src/main/java/com/aseubel/yusi/controller/ModelManagementController.java`
- Modify: `src/main/java/com/aseubel/yusi/service/ai/runtime/ModelCallTraceService.java`
- Modify: `src/main/resources/db/migration/update_model_management.sql` only if an existing index or column is required by the entities.
- Create: `src/test/java/com/aseubel/yusi/controller/ModelManagementControllerTest.java`

**Interfaces:**
- Consumes: admin-authenticated UI DTOs, expected config version, route preview context and trace filters.
- Produces: `GET /api/model/console`, `PUT /api/model/console`, `POST /api/model/routes/preview`, `GET /api/model/attempts`, and `GET /api/model/metrics`.

- [ ] **Step 1: Write failing API contract tests.**

Assert these response/request contracts:

```text
GET  /api/model/console
     -> { version, schemaVersion, models[], tiers[], routes[], defaultRoute, runtimeStates[], summary }

PUT  /api/model/console
     <- { expectedVersion, models[], tiers[], routes[], defaultRoute }
     -> { version, status: "updated" }

POST /api/model/routes/preview
     <- { language, scene, riskLevel, estimatedInputTokens, reservedOutputTokens }
     -> { policyId, primaryTier, candidates[], routeReason, warnings[] }

GET  /api/model/attempts?scene=chat&modelTier=balanced&page=0&size=20
     -> { content[], totalElements, page, size }
```

The tests must also assert that non-admin users receive 403, stale `expectedVersion` receives the repository's conflict error, and the console payload contains `apiKeyConfigured` but no `apikey` value.

- [ ] **Step 2: Run the API tests and verify they fail.**

Run: `./mvnw -Dtest=ModelManagementControllerTest test`

Expected: FAIL because the v2 DTOs, console endpoints and versioned persistence are absent.

- [ ] **Step 3: Persist snapshots and change logs transactionally.**

`ModelConfigCenter.updateFromAdmin` must execute this order inside one service operation:

1. Read the active MySQL snapshot and compare `expectedVersion`.
2. Normalize, validate and merge secrets into a cloned config.
3. Save the next version to `model_runtime_config`.
4. Save a `UPDATE_CONFIG` row to `model_config_change_log` with operator ID, before JSON, after JSON and success.
5. Update the Redis bucket and publish the config event only after the database writes succeed.
6. Replace the local `AtomicReference` only after Redis write succeeds; on Redis failure, leave the old local config and write a failed change log.

Route and tier strategy changes are published as one versioned console update; an unknown tier must return 400 before any Redis write.

- [ ] **Step 4: Implement the console projection and route preview.**

Build `ModelGovernanceSnapshot` from the effective config, runtime state map and aggregated metrics. The projection must map each model to `apiKeyConfigured`, never expose `baseurl` credentials beyond the configured endpoint, include provider/capability/context/pricing metadata, and return route policies in UI order. `preview` calls `ModelRouterService.plan` without invoking a Provider and returns all candidate exclusion reasons.

- [ ] **Step 5: Add trace queries and metric aggregation.**

Implement filters for time range, scene, language, tier, provider, model, fallback flag and status. Metrics must return route count, fallback rate, success rate, average latency, P95 latency when enough samples exist, 429/error counts, usage totals and unknown-cost count. Do not aggregate prompt content.

- [ ] **Step 6: Run controller and service tests.**

Run: `./mvnw -Dtest=ModelManagementControllerTest,ModelConfigCenterTest,ModelRouterServiceTest test`

Expected: PASS with admin enforcement, stale-write rejection, secret non-disclosure, preview output and audit ordering covered.

- [ ] **Step 7: Commit the governance API.**

```bash
git add src/main/java/com/aseubel/yusi/controller/ModelManagementController.java src/main/java/com/aseubel/yusi/service/ai/model/ModelManagementService.java src/main/java/com/aseubel/yusi/pojo/dto/model src/main/java/com/aseubel/yusi/pojo/entity/ModelRuntimeConfig.java src/main/java/com/aseubel/yusi/pojo/entity/ModelConfigChangeLog.java src/main/java/com/aseubel/yusi/repository src/main/java/com/aseubel/yusi/service/ai/runtime/ModelCallTraceService.java src/test/java/com/aseubel/yusi/controller/ModelManagementControllerTest.java
git commit -m "feat: expose versioned model governance APIs"
```

### Task 6: Replace the JSON-first admin page with a visual governance console

**Files:**
- Modify: `frontend/src/lib/api.ts`
- Create: `frontend/src/pages/admin/model-management/types.ts`
- Create: `frontend/src/pages/admin/model-management/modelRouting.ts`
- Create: `frontend/src/pages/admin/model-management/ModelGovernanceOverview.tsx`
- Create: `frontend/src/pages/admin/model-management/ModelRegistryPanel.tsx`
- Create: `frontend/src/pages/admin/model-management/RouteMatrix.tsx`
- Create: `frontend/src/pages/admin/model-management/RoutePolicyEditor.tsx`
- Create: `frontend/src/pages/admin/model-management/RoutePreview.tsx`
- Create: `frontend/src/pages/admin/model-management/RuntimeHealthPanel.tsx`
- Create: `frontend/src/pages/admin/model-management/ModelCallActivity.tsx`
- Modify: `frontend/src/pages/admin/ModelManagement.tsx`
- Modify: `frontend/src/i18n/locales/zh.json`
- Modify: `frontend/src/i18n/locales/en.json`
- Create: `frontend/src/lib/modelRouting.test.ts`

**Interfaces:**
- Consumes: `modelApi.getConsole`, `modelApi.updateConsole`, `modelApi.previewRoute`, `modelApi.getMetrics`, `modelApi.getAttempts` and the typed DTOs from Task 5.
- Produces: a responsive `/admin/models` console with no raw JSON required for normal model or route changes, stable dirty/save/conflict states, keyboard-accessible controls and a collapsed schema v2 JSON export panel.

- [ ] **Step 1: Write failing pure-function tests for the UI draft model.**

```ts
it('indexes routes by language and scene for the matrix', () => {
  const index = indexRoutes(snapshot.routes)
  expect(index.get('zh::chat')?.primaryTier).toBe('balanced')
})

it('preserves fallback order and rejects duplicate tiers', () => {
  const draft = createRouteDraft({ primaryTier: 'balanced', fallbackTiers: ['fast', 'fast'] })
  expect(validateRouteDraft(draft)).toContain('duplicateFallbackTier')
})

it('does not place a secret placeholder into a new model request', () => {
  const request = toUpdateRequest(draftWithMaskedKey())
  expect(request.models[0]).not.toHaveProperty('apikey')
  expect(request.models[0].apiKey).toBeUndefined()
})
```

- [ ] **Step 2: Run the frontend unit test and verify it fails.**

Run: `pnpm --dir frontend test --run src/lib/modelRouting.test.ts`

Expected: FAIL because the typed v2 API and pure route helpers do not exist.

- [ ] **Step 3: Add typed API contracts and pure draft helpers.**

Update `frontend/src/lib/api.ts` with `ModelGovernanceSnapshot`, `ModelGovernanceUpdateRequest`, `ModelRoutePolicy`, `ModelTier`, `ModelGovernanceModel`, `ModelRoutePreview`, `ModelCallTraceItem`, `ModelMetricSummary` and these methods:

```ts
getConsole: () => api.get<ApiResponse<ModelGovernanceSnapshot>>('/model/console')
updateConsole: (data: ModelGovernanceUpdateRequest) => api.put<ApiResponse<{ version: number; status: 'updated' }>>('/model/console', data)
previewRoute: (data: ModelRoutePreviewRequest) => api.post<ApiResponse<ModelRoutePreview>>('/model/routes/preview', data)
getMetrics: (params?: ModelMetricQuery) => api.get<ApiResponse<ModelMetricSummary>>('/model/metrics', { params })
getAttempts: (params?: ModelAttemptQuery) => api.get<ApiResponse<Page<ModelCallTraceItem>>>('/model/attempts', { params })
```

`modelRouting.ts` must contain only pure functions for matrix indexing, draft creation, payload conversion, dirty comparison, model/tier membership changes, fallback reorder, and validation. Validation must produce stable keys used by both locale files.

- [ ] **Step 4: Build the page shell and overview.**

Replace the current `ModelManagement.tsx` state that parses `rawConfig` with one `snapshot`, one editable `draft`, `selectedRouteId`, `activeTab`, `isDirty`, `isSaving`, `saveError` and `previewContext`. Load console data once, refresh runtime panels independently, and show an explicit conflict action that reloads the server snapshot while preserving the unsaved draft in memory until the administrator chooses to discard it.

The overview must show active model count, healthy/degraded/down count, current fallback rate, average/P95 latency, unknown-cost count and config version. Use compact stats and status indicators; do not turn the page into nested decorative cards.

- [ ] **Step 5: Build the physical model registry UI.**

`ModelRegistryPanel` must provide:

1. Search and status/capability filters.
2. One model row/card showing friendly name, provider, real model ID, endpoint host, enabled state, capability chips, context window, price-known state and runtime health.
3. An edit sheet with provider, endpoint, model ID, timeout, context limit, price snapshot, capabilities and enabled toggle.
4. A secret field with only “已配置/未配置” state; leaving it unchanged sends no API key, and entering a new key sends it once.
5. Tier membership controls that show the model in every tier, with checkboxes for inclusion and a visible weight/priority field.

The main model screen must never present a raw JSON editor or ask the administrator to edit group IDs manually.

- [ ] **Step 6: Build the route matrix and policy editor.**

`RouteMatrix` displays languages as rows and scenes as columns. Each cell shows the selected primary tier, a small fallback count, risk badge and health color derived from the latest snapshot. Selecting a cell opens `RoutePolicyEditor`.

`RoutePolicyEditor` must provide visual controls for:

- language and scene selectors with an explicit wildcard option;
- risk level segmented control;
- primary tier cards showing member count and health summary;
- ordered fallback tier list with add/remove and move-up/move-down icon buttons;
- strategy selector with descriptions in the option label, not an opaque enum;
- max input/output token steppers and temperature/top-P inputs;
- enabled toggle, priority input and validation messages next to the invalid field;
- a “Preview route” action that calls the preview API and renders the exact candidate chain, unavailable reasons, route reason and warnings.

The preview must make the decision legible as `request -> policy -> primary tier -> candidate model -> fallback tier -> candidate model`, using stable layout dimensions and no overlapping labels on mobile.

- [ ] **Step 7: Build runtime health and call activity panels.**

`RuntimeHealthPanel` preserves the current health data but adds provider, tier membership, last error, phase transition time, latency and a filter by tier/provider. `ModelCallActivity` shows only trace metadata: time, scene, route reason, selected tier/model, attempt number, fallback status, latency, usage, cost and error code. Clicking a row opens a detail sheet; it must never show prompt or response text.

- [ ] **Step 8: Add save, reload, schema export and localization states.**

The header actions are `Refresh`, `Export`, `Save` and an icon-only advanced toggle with a tooltip. Save is disabled when the draft is clean or invalid. On success, replace the version and snapshot; on conflict, show reload/keep-draft choices; on validation errors, focus the first invalid visual control. Place the current schema v2 copy/export JSON behavior under the advanced accordion and label it as a snapshot export in both locales.

Add all new Chinese and English keys, including empty, loading, disabled, conflict, stale preview, missing price, down model, masked secret, fallback warning, no route, unsupported provider and mobile sheet labels.

- [ ] **Step 9: Run frontend unit tests and type/build checks.**

Run:

```bash
pnpm --dir frontend test --run
pnpm --dir frontend lint
pnpm --dir frontend build
```

Expected: all Vitest tests pass, ESLint exits 0, TypeScript/Vite build exits 0, and no component imports the old `Textarea` config editor for the normal page flow.

- [ ] **Step 10: Commit the visual console.**

```bash
git add frontend/src/lib/api.ts frontend/src/lib/modelRouting.test.ts frontend/src/pages/admin/ModelManagement.tsx frontend/src/pages/admin/model-management frontend/src/i18n/locales/zh.json frontend/src/i18n/locales/en.json
git commit -m "feat: replace model JSON config with visual route console"
```

### Task 7: Migrate bootstrap configuration and documentation

**Files:**
- Modify: `src/main/resources/application-dev.yml`
- Modify: `src/main/resources/application-prod.yml`
- Modify: `src/main/resources/db/init.sql` only when the new trace table must be included in fresh installs.
- Modify: `docs/design/model-management-framework.md`
- Modify: `docs/design/backend-design.md`
- Create: `docs/engineering/records/2026-08-05-llm-gateway-routing-governance.md`

**Interfaces:**
- Consumes: the v2 route contract and current dev/prod models (`qwen`, `deepseek`, `whisper-asr`, `dashscope-asr`).
- Produces: bootable dev/prod configuration with the same provider credentials and ASR capability boundary, plus operator documentation for migration, rollback and metrics.

- [ ] **Step 1: Add v2 bootstrap entries without duplicating credentials.**

For each environment, define every model in `models`, create explicit tier IDs, and create route policies for every supported language/scene combination. Keep ASR definitions in `models` with `SPEECH_TO_TEXT` and map them to the dedicated ASR tier. Do not copy any real API key into YAML.

- [ ] **Step 2: Add the fresh-install trace table.**

Insert the exact `model_call_trace` DDL from Task 4 into `src/main/resources/db/init.sql` after the existing model governance tables, keeping column names and indexes identical to the incremental migration.

- [ ] **Step 3: Document rollback and route evolution.**

Document that rollback means restoring the previous MySQL snapshot/version, publishing the restored Redis config, and verifying a preview for `zh/chat`, `zh/situation-analysis`, `zh/memory-extract`, `zh/emotion-analysis`, `zh/soul-match`, and ASR. Record the phase gates from the article: fixed rules first, cost/quality evidence before cascading, evaluation set and trace replay before semantic or learning routing.

- [ ] **Step 4: Run configuration and static verification.**

Run:

```bash
./mvnw -DskipTests compile
git diff --check
rg -n 'rawConfig|Textarea|/api/model/config|GroupStrategy|groupStrategy|groups:|matrix:' frontend/src/pages/admin/ModelManagement.tsx frontend/src/pages/admin/model-management src/main/java/com/aseubel/yusi/controller src/main/java/com/aseubel/yusi/pojo/dto/model
rg -n 'apikey|apiKey' src/main/java/com/aseubel/yusi/controller src/main/java/com/aseubel/yusi/pojo/dto/model
```

Expected: both YAML profiles bind v2 fields, no legacy console or group strategy symbols remain, DTO/controller search shows boolean key state rather than a returned secret, and `git diff --check` is clean.

- [ ] **Step 5: Commit migration documentation.**

```bash
git add src/main/resources/application-dev.yml src/main/resources/application-prod.yml src/main/resources/db/init.sql docs/design/model-management-framework.md docs/design/backend-design.md docs/engineering/records/2026-08-05-llm-gateway-routing-governance.md
git commit -m "docs: record LLM gateway routing governance rollout"
```

### Task 8: End-to-end verification and rollout checklist

**Files:**
- Modify: `docs/testing/test-report.md` only with observed command output and environment details after implementation.
- Modify: `docs/engineering/records/2026-08-05-llm-gateway-routing-governance.md` with rollout evidence.

- [ ] **Step 1: Run the backend focused suite.**

```bash
./mvnw -Dtest=ModelConfigCenterTest,ChatModelProviderRegistryTest,ModelRoutePolicyMatcherTest,ModelRouterServiceTest,ModelInvocationErrorClassifierTest,ModelProxyFactoryTest,ModelUsageExtractorTest,ModelManagementControllerTest test
```

Expected: PASS with no test using a real provider key.

- [ ] **Step 2: Run the frontend suite.**

```bash
pnpm --dir frontend test --run
pnpm --dir frontend lint
pnpm --dir frontend build
```

Expected: PASS with no TypeScript errors and no lint errors.

- [ ] **Step 3: Verify the route preview matrix manually.**

Using an admin session, verify:

1. `zh/chat` resolves the configured primary tier and ordered Fallback tier.
2. An unavailable primary model appears with an exclusion reason and does not disappear from the preview.
3. A disabled model cannot be selected into a route.
4. A stale save shows a conflict and does not overwrite the newer version.
5. API key status is visible without revealing the key.
6. A route edit can be completed entirely through visual controls and survives refresh.
7. Advanced JSON export/import is optional and cannot bypass server validation.
8. A failed attempt shows `route_reason`, tier, model, error kind and fallback status in activity.
9. A stream that emitted output does not switch models after failure.

- [ ] **Step 4: Verify rollout safety.**

Deploy the backend and frontend against the same schema v2 contract. Keep the previous Redis config snapshot and MySQL version available for rollback. If preview or save validation fails, keep the console read-only until the configuration is corrected; do not bypass validation with a second endpoint.

- [ ] **Step 5: Record evidence before claiming completion.**

Record exact test/build commands, pass/fail output, migration status, route preview screenshots, and any provider-specific usage gaps. Do not claim cost accuracy for models whose price snapshot is unknown; display those calls as `unknown cost` until an administrator enters a price version.

## Out of Scope for This Release

- LLM classification, embedding similarity routing, RouteLLM/LLMRouter training, A/B experiments and sticky assignment.
- Tenant/user quota accounting, token reservation/reconciliation enforcement and billing settlement.
- Prompt/response cache or semantic cache.
- Full OpenAI/Anthropic/Gemini native protocol parity.
- Raw prompt/response replay, model thinking capture or tool argument persistence.
- Independent gateway deployment or replacement of the existing Java application boundary.

These features can consume the immutable `ModelRouteDecision`, `ModelCallTrace` and Provider Adapter contracts after the first release has enough trace samples and a stable evaluation set.
