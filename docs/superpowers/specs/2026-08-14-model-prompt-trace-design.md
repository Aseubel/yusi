# Model Prompt Trace Design

## Goal

为 Phase 3 的模型调用 Trace 补齐 Prompt 身份信息，使一次模型调用可以关联到实际使用的 Prompt key 和版本，同时不持久化 Prompt 正文、用户输入、模型输出或思考内容。

## Scope

本切片只处理 Prompt 身份到模型调用 Trace 的传播和查询展示：

- `PromptManager` 提供不可变的 Prompt 快照，包含 key、版本和 locale。
- 业务调用点在构造模型上下文时显式携带 Prompt key 和版本。
- `ModelCallAttemptEvent`、`ModelCallTrace` 和管理端 Trace DTO 保存并返回 Prompt key、版本和 locale。
- 旧调用未提供快照时允许字段为空，不阻断模型调用；现有模型路由和业务结果保持不变。

本切片不做以下事情：

- 不记录 Prompt 正文、插值后的完整 Prompt、用户输入、模型输出或思考内容。
- 不建立独立 Prompt 使用事件表。
- 不改变 Prompt 选择、热更新、模型路由、重试或业务任务的行为。
- 不增加新的用户可见功能。

## Architecture

`PromptManager` 的缓存从单纯的 `key -> String` 扩展为 `key + locale -> PromptSnapshot`。数据库 Prompt 使用其持久化 `version`；classpath 或硬编码降级 Prompt 使用现有默认版本。已有 `getPrompt` API 继续只返回正文，调用方需要追踪时使用快照 API，避免让业务代码依赖缓存实现。

调用方在取得快照后，将 `promptKey`、`promptVersion` 和 `promptLocale` 放入当前 `ModelRouteContext`。`ModelProxyFactory` 只负责把上下文复制到 `ModelCallAttemptEvent`，不自行根据 scene 猜测 Prompt。这样同一场景下组合 Prompt 或未来多个 Prompt 的调用不会被错误归因。

模型调用事件仍采用现有 Spring application event 和低敏异步持久化边界。事件发布或数据库写入失败只记录日志并增加已有失败计数，不影响模型调用。数据库通过一次向后兼容迁移增加可空列和查询索引；历史 Trace 的 Prompt 字段保持为空。

## Data Flow

```text
PromptManager.getSnapshot(key)
        -> PromptSnapshot(key, version, locale, template)
        -> ModelRouteContext(promptKey, promptVersion, promptLocale)
        -> ModelCallAttemptEvent
        -> ModelCallTrace
        -> admin trace DTO/query
```

`PromptSnapshot.template` 只在当前调用栈内用于组装请求，不进入事件或实体。事件和实体只复制 key、version、locale 三个低敏标识字段。

## Call-site Rules

- 直接调用 `PromptManager` 并随后调用 `ChatModel` 的服务，使用同一个快照的 `template` 组装 Prompt，并把同一个快照的身份字段放入 `ModelRouteContext`。
- 通过 `AiServices` 的动态系统消息使用 Prompt 的调用点，也应在模型调用上下文中携带快照；如果当前调用链无法安全传播上下文，本切片保留为空，不在代理层猜测。
- 每个调用点在 `finally` 中清理 `ModelRouteContextHolder`，保持现有 ThreadLocal 生命周期约束。
- `promptVersion` 是原样字符串，不在 Trace 层解析或比较版本语义。
- 缺少 Prompt 或版本时，调用继续执行，Trace 字段为空；不得用正文 hash 或正文内容替代版本。

## Persistence Contract

`model_call_trace` 增加：

- `prompt_key VARCHAR(64)`：对应 `PromptKey` 的稳定字符串。
- `prompt_version VARCHAR(64)`：对应 `PromptTemplate.version` 或降级默认版本。
- `prompt_locale VARCHAR(16)`：对应 Prompt 的 locale。

三列均可为空，以兼容历史调用和尚未迁移的调用点。增加 `(prompt_key, prompt_version, created_at)` 索引，支持按 Prompt 版本回放前的基础查询。

## Error Handling and Security

- Prompt 数据库查询失败仍沿用当前缓存和降级逻辑；快照 API 不改变降级结果。
- Prompt 版本缺失不抛错，不生成伪造版本。
- Trace 层禁止接收模板正文和插值参数；DTO 只返回身份字段。
- 管理端现有权限边界不变；本切片不新增面向普通用户的 Trace API。

## Tests and Acceptance

- Prompt 快照能返回数据库版本、locale 和模板，并在旧版字符串 API 下保持兼容。
- 数据库不可用或无版本时，快照使用既有降级版本且不抛出新的异常。
- 模型调用事件和实体正确携带 Prompt key、版本、locale，未携带正文。
- 管理查询 DTO 能返回 Prompt 身份字段，并可按 key、版本筛选。
- 未设置 Prompt 上下文的旧调用仍能成功产生模型调用 Trace。
- 相关单元测试通过，后端完整测试通过，数据库迁移文件与 JPA 字段一致。
