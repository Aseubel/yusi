# LLM Gateway 协议选项与路由修复记录

日期：2026-08-07

## 背景

最近的 gateway 路由治理改造把模型注册表、provider adapter、请求参数覆盖和流式调用统一到了同一条调用链。原实现默认按 OpenAI Chat Completions 处理所有模型，导致 Responses 和 Anthropic Messages 无法在注册表中表达，也可能把思考消息直接序列化成不合法的 assistant 消息。

## 改造内容

- 模型定义、运行实例、治理快照和前端模型草稿增加 `protocol` 字段；UI 新建模型时默认选择 `CHAT_COMPLETIONS`。
- 注册表提供三种协议选项：`CHAT_COMPLETIONS`、`RESPONSES`、`ANTHROPIC_MESSAGES`。
- OpenAI、DeepSeek、DashScope 及 OpenAI-compatible provider 根据协议创建 Chat Completions 或 Responses 客户端；Responses 客户端沿用模型配置的连接和读取超时。
- Anthropic provider 创建 Anthropic Messages 同步和流式客户端，并校验 provider 与协议组合。
- ModelProxyFactory 根据目标模型协议重建 `ChatRequestParameters`，保留原有通用参数、工具定义和消息内容；Responses 与 Anthropic 使用 LangChain4j 1.18.0 提供的类型化字段。
- 对只有 `thinking` 的 assistant 消息补充空文本，保留思考内容、工具调用和 attributes，避免上游 OpenAI-compatible 接口拒绝 `content` 与 `tool_calls` 均为空的消息。
- 前端模型治理面板增加协议展示、编辑和 provider/protocol 联动，序列化保存时保留协议字段。

## 参数边界

路由级 `customParameters` 目前只通过 LangChain4j 的 OpenAI Chat Completions 请求参数透传。LangChain4j 1.18.0 的 Responses 和 Anthropic Messages 请求参数没有统一的请求级扩展 map，因此这些协议只应用通用及其已建模的参数，不能将任意自定义字段假定为已发送到供应商。

## 验证

- `./mvnw -DskipTests compile`
- `./mvnw -Dtest=ChatModelProviderRegistryTest,ModelProxyFactoryTest,ModelConfigCenterTest,ModelManagementControllerTest,AiControllerCancellationTest test`
- `cmd.exe /d /c "cd /d D:\develop\projects\yusi\frontend && pnpm test"`
- `cmd.exe /d /c "cd /d D:\develop\projects\yusi\frontend && pnpm build"`

后端协议与路由 focused suite、前端 Vitest 和 TypeScript/Vite production build 均通过；未使用真实供应商 API key。
