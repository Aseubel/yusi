# LangChain4j 1.18 架构演进记录

**日期：** 2026-07-29
**状态：** Accepted for staged adoption
**范围：** Yusi Java 后端的 LangChain4j 依赖、AI Service、Embedding、Agent 编排和 MCP 工具边界

## 背景

项目已从 LangChain4j `1.12.2` 升级到稳定版 `1.18.0`，社区模块和 MCP 使用对应的 `1.18.0-beta28`。1.18.0 引入了新的 Agentic 能力、可恢复 Human-in-the-Loop、Embedding request/response API 和更完整的多模态输入能力。

本次不把依赖升级和业务架构重写绑定。现有 `AiServices`、`ChatModel`、`StreamingChatModel`、`EmbeddingModel`、`ToolProvider` 和自定义 `ChatMemoryStore` 继续作为稳定边界。

## 决策

### 1. Embedding 逐步迁移到 request/response

LangChain4j 1.18 的 `EmbeddingModel` 提供实验性的 `embed(EmbeddingRequest)`，返回 `EmbeddingResponse`。下一阶段在 `EmbeddingBatchService` 和动态模型路由中逐步接入，记录模型、维度、耗时和 token/请求元数据。旧的 `embed(String)` 在迁移期间保留，避免一次性改动 Milvus 写入和检索链路。

### 2. 多模态统一进入认知摄取事件

语音和图片功能不直接扩散到各个认知服务。新增能力时，先将文本、ASR 转写和图片理解结果归一为 `CognitionInput`，再进入现有 `AgentCognitionOrchestrator`。原始音频/图片保存在 OSS，认知层只保存必要的脱敏理解结果。

### 3. Agentic 只承载长流程和确认流程

BDI 和 Human-in-the-Loop 适合“计划 -> 工具调用 -> 等待用户确认 -> 恢复”的流程，例如遗忘请求确认、复杂记忆整理或匹配后创建情景室。普通聊天、认知路由、记忆持久化和周报生成仍由 Spring Service 管理，避免把数据库事务和长生命周期 Agent execution 绑定。

### 4. MCP 外部适配层与 Java gRPC 能力边界

当前 MCP 不是 Java 后端专用服务：Go MCP Server 对外实现 MCP/HTTP 协议，工具执行时通过 gRPC 调用 Java 后端的内部记忆能力。Go 层只负责协议适配和 API Key 透传，Java gRPC 服务负责用户归属、scope 和数据访问校验。Phase 4 已落地 MEMORY_READ，后续新增写日记或匹配能力时沿用同一 capability 边界。

这比在 Go 工具描述或提示词中约束权限更可靠；Java 内部函数仍可被 Web、任务和 MCP 复用，MCP 也不会反向成为后端业务服务的所有者。

## 分阶段落地

| 阶段 | 内容 | 当前状态 |
|:---|:---|:---|
| 现在 | 统一依赖版本，保持现有 API 边界，完成静态兼容检查 | 已完成 |
| 下一步 | 为 Embedding request/response 增加适配器和观测字段，先覆盖批量写入链路 | ✅ 已完成：EmbeddingGateway 已接入 EmbeddingBatchService |
| Phase 4 | 接入图片日记、语音日记、scope 化 API Key 和 gRPC 边界鉴权 | ✅ 已完成 |
| 后续 | 在明确的确认型流程中试点 Human-in-the-Loop/Agentic 编排 | 待评估 |
| 后续 | 从单用户单 Key 演进为多应用 Key，并补齐工具级安装授权、配额与调用审计 | 待评估 |

### 5. 统一模型控制面，保留能力专用客户端

模型管理中心统一的是 endpoint 配置、能力声明、分组路由、密钥合并、健康状态和热更新，不要求所有模型使用同一个客户端类型。

- Chat/Streaming Chat 继续由 LangChain4j ChatModel / StreamingChatModel adapter 创建。
- Speech-to-Text 由 SpeechModelRegistry 根据 SPEECH_TO_TEXT capability 创建 multipart HTTP adapter。
- Embedding 后续迁移到同一 endpoint 配置契约，但保留 EmbeddingModel 专用 adapter。
- capability 组与 endpoint 成员独立于 Chat 场景矩阵，避免把 ASR 当作聊天模型创建或参与聊天路由。

当前 bootstrap 配置已将 ASR endpoint 放入 model.routing.models，并通过 capability-groups.SPEECH_TO_TEXT 选择 asr-default。model.speech.asr 不再是运行时配置来源。

## 约束与风险

- `EmbeddingRequest` API 在 1.18 中标记为实验性，正式迁移前需要验证 DashScope/OpenAI 兼容接口的参数和返回元数据。
- 多模态能力依赖具体模型和 provider 支持，不能仅依据 LangChain4j 接口存在就宣称所有模型可用。
- Agentic 流程必须把用户确认、幂等键、超时和恢复状态持久化，不能直接复用普通同步 Service 调用。
- MCP 权限过滤必须在工具暴露前完成，避免把敏感数据访问交给提示词约束。
- 当前 developer_config 仍保存明文 API Key，且一个用户只有一个 Key；生产化开放前应迁移为只存 hash 的多应用 Key 模型。
- 当前语音 ASR 采用 OpenAI-compatible HTTP 适配器，部署时需显式配置 provider、模型和密钥；默认关闭。

## 参考

- LangChain4j 1.18.0 release notes: <https://github.com/langchain4j/langchain4j/releases/tag/1.18.0>
- LangChain4j AI Services documentation: <https://docs.langchain4j.dev/tutorials/ai-services>
- LangChain4j MCP documentation: <https://docs.langchain4j.dev/tutorials/mcp>
