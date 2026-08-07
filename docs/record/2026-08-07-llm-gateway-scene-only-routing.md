# LLM Gateway scene-only 路由改造记录

日期：2026-08-07

## 背景

旧路由同时使用语言和场景维度，造成配置重复、治理界面复杂，并且新增业务场景容易遗漏。实际业务已经通过 Prompt 和调用入口区分任务，因此路由统一收敛到 `scene`，不再维护语言分组或语言能力过滤。

## 改造内容

- 删除模型配置、路由策略、调用上下文、运行实例、治理快照、预览请求和调用轨迹中的语言字段。
- 路由匹配只保留 scene 精确匹配、scene 通配回退和 priority 排序；路由原因只记录 scene。
- 补齐真实调用场景：`logic`、`graphrag-extract`、`graphrag-merge-suggest`、`memory-extract`、`memory-fusion`、`soul-match`、`soul-match-letter`、`emotion-analysis`、`cognition-routing`、`cognitive-conflict`、`soul-weekly-report`、`agent-proactive-greeting` 和 `image-understanding`。
- 所有直接调用共享 `chatModel` 的后台任务显式设置 scene，避免静默落到 chat 默认路由。
- 当前业务 scene 由 `PromptKey` 和实际调用入口共同定义；`chat`、`logic`、GraphRAG、记忆、匹配、情感、认知、报告、主动问候和图片理解均在 dev/prod 配置中有显式 route。
- 治理配置保存时会校验 primary/fallback tier 至少有一个支持该 scene 的启用 Chat 模型，避免把不可用的 scene 配置发布到运行时。
- 前端治理矩阵改为场景矩阵，模型注册仍保留 Chat Completions、Responses 和 Anthropic Messages 三种协议选项。
- `init.sql` 不再创建 `model_call_trace.language`；增量迁移会物理删除存量列。

## 验证

- `./mvnw -DskipTests compile`
- `./mvnw -Dtest=ChatModelProviderRegistryTest,ModelConfigCenterTest,ModelInvocationErrorClassifierTest,ModelProxyFactoryTest,ModelRoutePolicyMatcherTest,ModelRouterServiceTest,ModelTokenEstimatorTest,ModelUsageExtractorTest,ModelBudgetAdmissionTest,FailOverSelectionStrategyTest,RoundRobinSelectionStrategyTest,ModelManagementControllerTest,AiControllerCancellationTest test`
- `pnpm test --run`
- `pnpm run build`
