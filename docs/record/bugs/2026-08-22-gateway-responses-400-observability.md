# Gateway Responses 400 与 unknown 错误修复记录

日期：2026-08-22

## 现象

配置了 DeepSeek vision 模型后，纯文本和图片加文本请求都可能返回 HTTP 400。异步 SSE
只记录 `unknown`，后台日志缺少供应商返回的错误码、参数和原因，无法判断是模型、协议、
请求字段还是图片输入导致失败。

## 根因

1. 生效配置的优先级是 MySQL active、Redis canonical、YAML 默认值。只修改 YAML 不会覆盖
   已存在的 MySQL/Redis 快照，旧快照仍可能把模型按 `CHAT_COMPLETIONS` 创建。
2. OpenAI-compatible provider 原先没有按模型协议创建 Responses client，Responses 配置
   仍可能进入 Chat Completions 请求链路。
3. LangChain4j Responses 请求会携带 `store` 等字段，DeepSeek Responses 对部分字段更严格；
   历史 assistant 消息中的 thinking/encrypted reasoning 也不应直接作为 Responses 输入。
4. 图片请求没有在入口统一切换到 `image-understanding` scene，文本 tier 可能被错误用于视觉请求。
5. `DOWN` 状态被路由排除后，半开探测只在真正尝试模型时触发，导致到期模型没有探测机会；
   Redis 状态更新也可能没有及时合并到当前 Docker 实例的本地健康窗口。

## 修复

- 根据 `CHAT_COMPLETIONS`、`RESPONSES`、`ANTHROPIC_MESSAGES` 创建协议对应的同步和流式 client，
  并在启动日志记录实际 client 类型和脱敏 endpoint。
- 对 DeepSeek Responses 同步/流式请求统一移除不兼容字段；Responses 参数只保留协议支持的
  generation、tool 和 response-format 字段，并清理历史 thinking/encrypted reasoning。
- 图片请求切换到 `image-understanding` scene，vision tier 只允许声明 `VLM` 的模型。
- 记录 HTTP status、provider code/type/param、分类原因和受限错误详情；controller 沿 cause 链
  查找 `ModelInvocationException`，SSE 不再退化为 `unknown`。
- 按 `lastUpdatedAt` 合并 Redis、本地和 pub/sub 状态；到期 `DOWN` 模型可进入一次半开探测，
  同时保留本地 `allowRequest` 的并发探测门闩。
- 校验非正权重、非法 priority/timeout、重复 tier member、tier 能力不匹配和无可用 fallback，
  并修正 weighted random 只从正权重候选中抽取。

## 运行确认

源码验证不使用真实供应商 API key。Docker 重启后应先确认日志中的：

```text
Effective model routing config: source=...
Model instance loaded: modelId=deepseek, protocol=RESPONSES, ...
endpoint=https://api.deepseek.com/responses
chatClient=OpenAiResponsesChatModel
streamingClient=OpenAiResponsesStreamingChatModel
```

若 `source=mysql` 或 `source=redis`，需要通过模型治理配置发布正确的 `protocol`、model、
base URL 和 `VLM` 能力；YAML/env 只在没有更高优先级快照时生效。
